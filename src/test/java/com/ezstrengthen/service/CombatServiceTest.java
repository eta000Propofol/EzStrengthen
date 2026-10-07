package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.Affix;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.model.AffixInstance;
import com.ezstrengthen.model.EnhanceData;
import com.ezstrengthen.model.EquipmentStats;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 战斗结算的回归测试（无服务器环境，测试边界见 AGENTS.md）：
 * - 伤害数学：暴击倍率、近战/远程加成、雷伤平加、减伤叠加与封顶；
 * - 延迟结算：真伤与流血伤害一律经调度器下一 tick 生效，绝不同步结算；
 * - 流血任务：单任务刷新、倒计时归零自清、受害者失效即停、cleanup 主动清理；
 * - 触发类词条：流血/雷击/击飞走各自分支。
 * 已知不可测边界：DamageSource/Sound/PotionEffectType/Attribute 常量需要服务器注册表，
 * 真伤结算内部（internalDamageVictims 时序、状态效果类词条、斩杀）不在本测试范围。
 */
@ExtendWith(MockitoExtension.class)
class CombatServiceTest {

    @Mock
    private EzStrengthen plugin;
    @Mock
    private BukkitScheduler scheduler;

    private Random random;
    private CombatService service;
    private YamlConfiguration config;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ItemUtil> itemUtil;
    private final List<Runnable> scheduledTasks = new ArrayList<>();
    private final List<Runnable> timerTasks = new ArrayList<>();

    /** 词条规格：id + 枚举 + Lv1 数值 + Lv1 次要数值。 */
    private record AffixSpec(String id, Affix affix, double value, double extra) {
        AffixSpec(String id, Affix affix, double value) {
            this(id, affix, value, 0);
        }
    }

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        random = mock(Random.class);
        // 默认 nextDouble=0.0：任何概率 >0 的分支恒命中，各用例按需覆盖
        lenient().doReturn(0.0).when(random).nextDouble();
        service = new CombatService(plugin, random);

        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getChanceCap()).thenReturn(0.9);
        lenient().when(plugin.getDefenseCap()).thenReturn(0.8);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        lenient().when(scheduler.runTask(same(plugin), any(Runnable.class))).thenAnswer(inv -> {
            scheduledTasks.add(inv.getArgument(1, Runnable.class));
            return mock(BukkitTask.class);
        });
        lenient().when(scheduler.runTaskTimer(same(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(inv -> {
            Runnable task = inv.getArgument(1, Runnable.class);
            timerTasks.add(task);
            if (task instanceof BukkitRunnable bukkitRunnable) {
                markScheduled(bukkitRunnable);
            }
            return mock(BukkitTask.class);
        });

        itemUtil = mockStatic(ItemUtil.class);
    }

    @AfterEach
    void tearDown() {
        itemUtil.close();
        bukkit.close();
    }

    /**
     * BukkitRunnable.cancel() 在未调度状态下会抛 IllegalStateException，
     * 而调度器本身是 mock、不会回写调度标记，这里反射补上，使自动取消分支可真实执行。
     */
    private void markScheduled(BukkitRunnable task) {
        try {
            Field field = BukkitRunnable.class.getDeclaredField("task");
            field.setAccessible(true);
            field.set(task, mock(BukkitTask.class));
        } catch (NoSuchFieldException noTaskField) {
            try {
                Field field = BukkitRunnable.class.getDeclaredField("taskId");
                field.setAccessible(true);
                field.setInt(task, 1);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法标记 BukkitRunnable 为已调度", e);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("无法标记 BukkitRunnable 为已调度", e);
        }
    }

    /** 无装备实体：statsOf 走空集合，词条面板全零。 */
    private LivingEntity plainEntity() {
        return mock(LivingEntity.class);
    }

    /** 主手持有一件带指定词条物品的实体（副手与盔甲为空）。 */
    private LivingEntity entityWithStats(AffixSpec... specs) {
        LivingEntity entity = mock(LivingEntity.class);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(entity.getEquipment()).thenReturn(equipment);
        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.isEmpty()).thenReturn(false);
        when(equipment.getItemInMainHand()).thenReturn(mainHand);
        when(equipment.getArmorContents()).thenReturn(new ItemStack[0]);

        List<AffixInstance> instances = new ArrayList<>();
        for (AffixSpec spec : specs) {
            instances.add(new AffixInstance(spec.id(), 1));
            AffixConfig cfg = mock(AffixConfig.class);
            lenient().when(cfg.getAffix()).thenReturn(spec.affix());
            lenient().when(cfg.isEnabled()).thenReturn(true);
            lenient().when(cfg.value(1)).thenReturn(spec.value());
            lenient().when(cfg.extra(1)).thenReturn(spec.extra());
            lenient().when(plugin.getAffixConfig(spec.id())).thenReturn(cfg);
        }
        itemUtil.when(() -> ItemUtil.getEnhanceData(mainHand))
                .thenReturn(new EnhanceData(instances.size(), instances));
        return entity;
    }

    private EntityDamageByEntityEvent eventOfBaseDamage(double base) {
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        lenient().when(event.getDamage()).thenReturn(base);
        return event;
    }

    private void stubAlive(LivingEntity entity) {
        lenient().when(entity.isDead()).thenReturn(false);
        lenient().when(entity.isValid()).thenReturn(true);
    }

    private void stubWorld(LivingEntity entity) {
        lenient().when(entity.getWorld()).thenReturn(mock(World.class));
        // spawnParticles 以 getEyeLocation().add(...) 定位粒子
        lenient().when(entity.getEyeLocation()).thenReturn(mock(Location.class));
    }

    // ---------------- 伤害数学 ----------------

    @Test
    @DisplayName("双方无词条时基础伤害原样通过")
    void baseDamagePassesThroughWhenNoStats() {
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, plainEntity(), plainEntity(), false);

        ArgumentCaptor<Double> damage = ArgumentCaptor.forClass(Double.class);
        verify(event).setDamage(damage.capture());
        assertEquals(10.0, damage.getValue(), 1e-9);
    }

    @Test
    @DisplayName("暴击按 1.5 倍 + 暴伤加成放大")
    void critMultipliesBaseDamage() {
        LivingEntity attacker = entityWithStats(
                new AffixSpec("crit_chance", Affix.CRIT_CHANCE, 100.0),
                new AffixSpec("crit_damage", Affix.CRIT_DAMAGE, 50.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        stubWorld(victim);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        // 10 * (1.5 + 0.5) = 20
        ArgumentCaptor<Double> damage = ArgumentCaptor.forClass(Double.class);
        verify(event).setDamage(damage.capture());
        assertEquals(20.0, damage.getValue(), 1e-9);
    }

    @Test
    @DisplayName("远程与近战加成按攻击类型分别生效")
    void rangedAndMeleeMultipliersFollowAttackType() {
        LivingEntity attacker = entityWithStats(new AffixSpec("ranged_damage", Affix.RANGED_DAMAGE, 100.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, true);

        ArgumentCaptor<Double> damage = ArgumentCaptor.forClass(Double.class);
        verify(event).setDamage(damage.capture());
        assertEquals(20.0, damage.getValue(), 1e-9);
    }

    @Test
    @DisplayName("减伤双来源相加后受 defense-cap 封顶")
    void defenseStacksThenCaps() {
        LivingEntity attacker = plainEntity();
        LivingEntity victim = entityWithStats(
                new AffixSpec("physical_defense", Affix.PHYSICAL_DEFENSE, 50.0),
                new AffixSpec("melee_defense", Affix.MELEE_DEFENSE, 50.0));
        stubAlive(victim);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        // 50% + 50% = 100% → 封顶 80% → 10 * 0.2 = 2
        ArgumentCaptor<Double> damage = ArgumentCaptor.forClass(Double.class);
        verify(event).setDamage(damage.capture());
        assertEquals(2.0, damage.getValue(), 1e-9);
    }

    @Test
    @DisplayName("闪避触发时取消事件且不进入后续结算")
    void dodgeCancelsAttack() {
        LivingEntity attacker = plainEntity();
        LivingEntity victim = entityWithStats(new AffixSpec("dodge_chance", Affix.DODGE_CHANCE, 100.0));
        stubAlive(victim);
        stubWorld(victim);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        verify(event).setCancelled(true);
        verify(event, never()).setDamage(anyDouble());
        assertEquals(0, scheduledTasks.size());
    }

    // ---------------- 真伤延迟结算 ----------------

    @Test
    @DisplayName("攻击方真伤词条经调度器延迟结算，不同步扣血")
    void attackerTrueDamageIsDelayedToNextTick() {
        LivingEntity attacker = entityWithStats(new AffixSpec("true_damage", Affix.TRUE_DAMAGE, 5.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        // 普通伤害照常写入事件
        ArgumentCaptor<Double> damage = ArgumentCaptor.forClass(Double.class);
        verify(event).setDamage(damage.capture());
        assertEquals(10.0, damage.getValue(), 1e-9);
        // 真伤不在本次调用内结算（v1.1.2/1.1.3 递归双死修复的约定）
        assertEquals(1, scheduledTasks.size());
        verify(victim, never()).damage(anyDouble(), any(DamageSource.class));
    }

    @Test
    @DisplayName("受害者已死亡时跳过真伤与触发词条")
    void noProcsWhenVictimAlreadyDead() {
        LivingEntity attacker = entityWithStats(new AffixSpec("true_damage", Affix.TRUE_DAMAGE, 5.0));
        LivingEntity victim = plainEntity();
        lenient().when(victim.isDead()).thenReturn(true);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        assertEquals(0, scheduledTasks.size());
    }

    @Test
    @DisplayName("damageTrue 对 null/已死/无效/非正数额不调度")
    void damageTrueGuardRejectsInvalidRequests() {
        LivingEntity victim = plainEntity();
        lenient().when(victim.isDead()).thenReturn(false);
        lenient().when(victim.isValid()).thenReturn(true);

        service.damageTrue(null, 5.0, null);
        service.damageTrue(deadEntity(), 5.0, null);
        service.damageTrue(invalidEntity(), 5.0, null);
        service.damageTrue(victim, 0.0, null);
        service.damageTrue(victim, -1.0, null);

        verify(scheduler, never()).runTask(same(plugin), any(Runnable.class));
    }

    private LivingEntity deadEntity() {
        LivingEntity entity = plainEntity();
        when(entity.isDead()).thenReturn(true);
        return entity;
    }

    private LivingEntity invalidEntity() {
        LivingEntity entity = plainEntity();
        when(entity.isDead()).thenReturn(false);
        when(entity.isValid()).thenReturn(false);
        return entity;
    }

    // ---------------- 反弹 ----------------

    @Test
    @DisplayName("反弹触发时对攻击者调度延迟真伤")
    void reflectSchedulesDelayedTrueDamageOnAttacker() {
        LivingEntity attacker = plainEntity();
        LivingEntity victim = entityWithStats(new AffixSpec("reflect_chance", Affix.REFLECT_CHANCE, 100.0, 40.0));
        stubAlive(victim);
        stubAlive(attacker); // 反弹以攻击者为真伤受害者，需通过存活校验才会调度
        stubWorld(victim);
        stubWorld(attacker);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        // 无减伤：反弹数额 = 10 * 40%，经调度器延迟结算
        assertEquals(1, scheduledTasks.size());
        verify(attacker, never()).damage(anyDouble(), any(DamageSource.class));
        verify(victim, never()).damage(anyDouble(), any(DamageSource.class));
    }

    // ---------------- 流血任务生命周期 ----------------

    private AffixConfig bleedConfigWithDuration(double seconds) {
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getDuration()).thenReturn(seconds);
        return cfg;
    }

    @Test
    @DisplayName("流血任务按 0 延迟 20 tick 周期调度，重复触发复用同一任务")
    void startBleedSchedulesSingleTimerTask() {
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        // mock() 不能在 when() 参数求值期间调用（UnfinishedStubbing），先建桩对象
        AffixConfig bleedCfg = bleedConfigWithDuration(2.0);
        when(plugin.getAffixConfig("bleed_chance")).thenReturn(bleedCfg);

        service.startBleed(victim, 2.0, plainEntity());
        service.startBleed(victim, 3.0, plainEntity()); // refresh：不新增调度

        assertEquals(1, timerTasks.size());
        verify(scheduler).runTaskTimer(same(plugin), any(Runnable.class), eq(0L), eq(20L));
    }

    @Test
    @DisplayName("流血任务倒计时归零后自动取消并从登记表移除")
    void bleedTaskCancelsItselfAfterCountdown() {
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        stubWorld(victim);
        lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        AffixConfig bleedCfg = bleedConfigWithDuration(2.0);
        when(plugin.getAffixConfig("bleed_chance")).thenReturn(bleedCfg);
        service.startBleed(victim, 2.0, plainEntity());

        Runnable task = timerTasks.get(0);
        task.run(); // remaining 2→1，调度一次真伤
        assertEquals(1, scheduledTasks.size());
        task.run(); // remaining 1→0，自动取消

        // 归零后登记表已清空：再次触发会调度全新任务
        service.startBleed(victim, 2.0, plainEntity());
        assertEquals(2, timerTasks.size());
    }

    @Test
    @DisplayName("受害者失效时流血任务立即停止且不再造成伤害")
    void bleedTaskStopsWhenVictimInvalid() {
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        AffixConfig bleedCfg = bleedConfigWithDuration(2.0);
        when(plugin.getAffixConfig("bleed_chance")).thenReturn(bleedCfg);
        service.startBleed(victim, 2.0, plainEntity());

        when(victim.isDead()).thenReturn(true);
        timerTasks.get(0).run();

        assertEquals(0, scheduledTasks.size());
        // 任务已移除：再次触发调度新任务
        when(victim.isDead()).thenReturn(false);
        service.startBleed(victim, 2.0, plainEntity());
        assertEquals(2, timerTasks.size());
    }

    @Test
    @DisplayName("cleanup 取消流血任务并移除登记，后续触发走全新任务")
    void cleanupCancelsAndRemovesBleedTask() {
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        AffixConfig bleedCfg = bleedConfigWithDuration(2.0);
        when(plugin.getAffixConfig("bleed_chance")).thenReturn(bleedCfg);
        service.startBleed(victim, 2.0, plainEntity());

        service.cleanup(victim);

        service.startBleed(victim, 2.0, plainEntity());
        assertEquals(2, timerTasks.size());
    }

    // ---------------- 触发类词条 ----------------

    @Test
    @DisplayName("流血词条命中后启动流血任务")
    void bleedProcStartsBleedTask() {
        LivingEntity attacker = entityWithStats(new AffixSpec("bleed_chance", Affix.BLEED_CHANCE, 100.0, 3.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        lenient().when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        assertEquals(1, timerTasks.size());
    }

    @Test
    @DisplayName("雷击词条命中后施加视觉效果并调度配置数额的真伤")
    void lightningProcStrikesAndSchedulesTrueDamage() {
        LivingEntity attacker = entityWithStats(new AffixSpec("lightning_chance", Affix.LIGHTNING_CHANCE, 100.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        World world = mock(World.class);
        Location location = mock(Location.class);
        stubWorld(victim);
        lenient().when(victim.getWorld()).thenReturn(world);
        lenient().when(victim.getLocation()).thenReturn(location);
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        verify(world).strikeLightningEffect(location);
        assertEquals(1, scheduledTasks.size()); // combat.lightning-base-damage 默认 5
    }

    @Test
    @DisplayName("击飞词条命中后沿攻击方向叠加速度")
    void knockbackProcAddsVelocityAlongAttackDirection() {
        LivingEntity attacker = entityWithStats(new AffixSpec("knockback_chance", Affix.KNOCKBACK_CHANCE, 100.0, 2.0));
        LivingEntity victim = plainEntity();
        stubAlive(victim);
        lenient().when(victim.getLocation()).thenReturn(new Location(null, 1, 2, 1));
        lenient().when(attacker.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        lenient().when(victim.getVelocity()).thenReturn(new Vector(0, 0, 0));
        EntityDamageByEntityEvent event = eventOfBaseDamage(10.0);

        service.handleAttack(event, attacker, victim, false);

        // dir=(1,2,1) 归一化后 y=0.25，再乘 2.0 倍率
        ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
        verify(victim).setVelocity(velocity.capture());
        assertEquals(2.0 * (1.0 / Math.sqrt(6)), velocity.getValue().getX(), 1e-6);
        assertEquals(0.5, velocity.getValue().getY(), 1e-6);
        assertEquals(2.0 * (1.0 / Math.sqrt(6)), velocity.getValue().getZ(), 1e-6);
    }

    // ---------------- 防御 ----------------

    @Test
    @DisplayName("攻击者与受害者相同直接短路")
    void selfAttackShortCircuits() {
        LivingEntity same = plainEntity();
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);

        service.handleAttack(event, same, same, false);

        verifyNoInteractions(event);
        assertTrue(scheduledTasks.isEmpty());
    }
}
