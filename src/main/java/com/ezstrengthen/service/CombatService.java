package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.EquipmentStats;
import com.ezstrengthen.util.Text;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 战斗服务：把词条效果应用到实际战斗中。
 * 只处理近战（ENTITY_ATTACK / ENTITY_SWEEP_ATTACK）与远程（PROJECTILE）伤害，
 * 内部结算的真实伤害、流血、雷击、斩杀等不再进入本服务，避免循环触发。
 */
public class CombatService {

    private final EzStrengthen plugin;
    private final Random random = new Random();
    private final Map<UUID, BleedTask> bleedTasks = new HashMap<>();

    public CombatService(EzStrengthen plugin) {
        this.plugin = plugin;
    }

    /** 处理一次物理攻击。 */
    public void handleAttack(EntityDamageByEntityEvent event, LivingEntity attacker, LivingEntity victim, boolean ranged) {
        if (attacker.equals(victim)) {
            return;
        }
        EquipmentStats atk = statsOf(attacker);
        EquipmentStats def = statsOf(victim);

        double damage = event.getDamage();

        // ---- 攻击方加成 ----
        // 暴击：1.5 倍基础 + 暴击伤害加成
        if (atk.critChance > 0 && random.nextDouble() < atk.critChance) {
            damage *= (1.5 + atk.critDamagePct);
            spawnParticles(victim, Particle.CRIT, 30);
            if (attacker instanceof Player p) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1, 1.2f);
            }
        }
        damage *= (1 + atk.physicalDamagePct);
        damage *= ranged ? (1 + atk.rangedDamagePct) : (1 + atk.meleeDamagePct);
        damage += atk.lightningDamage;

        // ---- 受击方减伤 / 闪避 / 反弹 ----
        if (def.dodgeChance > 0 && random.nextDouble() < def.dodgeChance) {
            event.setCancelled(true);
            sendActionBar(victim, plugin.getMessage("dodge"));
            spawnParticles(victim, Particle.CLOUD, 20);
            return;
        }
        double defense = def.physicalDefensePct + (ranged ? def.rangedDefensePct : def.meleeDefensePct);
        defense = Math.min(defense, plugin.getDefenseCap());
        damage *= Math.max(0, 1 - defense);
        event.setDamage(damage);

        if (def.reflectChance > 0 && random.nextDouble() < def.reflectChance) {
            double reflected = damage * def.reflectPctMax / 100.0;
            damageTrue(attacker, reflected);
            sendActionBar(victim, plugin.getMessage("reflect"));
            spawnParticles(attacker, Particle.ENCHANT, 25);
            if (attacker instanceof Player p) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
            }
        }

        // ---- 命中后的攻击方效果 ----
        if (!event.isCancelled() && !victim.isDead() && victim.isValid()) {
            if (atk.trueDamage > 0) {
                damageTrue(victim, atk.trueDamage);
            }
            applyProcs(attacker, victim, atk);
        }
    }

    /** 应用攻击触发类词条。 */
    private void applyProcs(LivingEntity attacker, LivingEntity victim, EquipmentStats atk) {
        if (chance(atk.freezeChance)) {
            applyEffect(victim, PotionEffectType.SLOWNESS, duration("freeze_chance"), 5);
            victim.setFreezeTicks(80);
        }
        if (chance(atk.bleedChance)) {
            startBleed(victim, atk.bleedDpsMax);
        }
        if (chance(atk.blindChance)) {
            applyEffect(victim, PotionEffectType.BLINDNESS, duration("blind_chance"), 0);
        }
        if (chance(atk.levitationChance)) {
            applyEffect(victim, PotionEffectType.LEVITATION, duration("levitation_chance"), 0);
        }
        if (chance(atk.stunChance)) {
            int ticks = (int) (duration("stun_chance") * 20);
            applyEffect(victim, PotionEffectType.SLOWNESS, ticks / 20.0, 6);
            applyEffect(victim, PotionEffectType.WEAKNESS, ticks / 20.0, 4);
        }
        if (chance(atk.weaknessChance)) {
            applyEffect(victim, PotionEffectType.WEAKNESS, duration("weakness_chance"), 0);
        }
        if (chance(atk.confusionChance)) {
            applyEffect(victim, PotionEffectType.NAUSEA, duration("confusion_chance"), 0);
        }
        if (chance(atk.lightningChance)) {
            strikeLightning(attacker, victim, atk.lightningDamage);
        }
        if (chance(atk.knockbackChance)) {
            knockback(attacker, victim, atk.knockbackMultMax);
        }
        if (chance(atk.executeChance)) {
            execute(victim, atk.executeThresholdMax);
        }
    }

    private boolean chance(double pct) {
        return pct > 0 && random.nextDouble() < pct;
    }

    private double duration(String affixId) {
        var cfg = plugin.getAffixConfig(affixId);
        return cfg == null ? 0 : cfg.getDuration();
    }

    /** 施加状态效果。 */
    private void applyEffect(LivingEntity target, PotionEffectType type, double seconds, int amplifier) {
        if (target == null || target.isDead() || seconds <= 0) {
            return;
        }
        target.addPotionEffect(new PotionEffect(type, (int) (seconds * 20), amplifier, false, true, true));
    }

    /** 雷击：视觉效果 + 配置伤害 + 雷击伤害词条加成。 */
    private void strikeLightning(LivingEntity attacker, LivingEntity victim, double lightningDamageBonus) {
        Location loc = victim.getLocation();
        victim.getWorld().strikeLightningEffect(loc);
        double base = plugin.getConfig().getDouble("combat.lightning-base-damage", 5);
        damageTrue(victim, base + lightningDamageBonus);
        sendActionBar(victim, "&e你被雷击了！");
        if (attacker instanceof Player p) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1, 1);
        }
        spawnParticles(victim, Particle.SMOKE, 30);
    }

    /** 击退：沿攻击方向施加额外速度。 */
    private void knockback(LivingEntity attacker, LivingEntity victim, double multiplier) {
        if (multiplier <= 0) {
            return;
        }
        Vector dir = victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) {
            return;
        }
        dir.normalize().setY(0.25);
        victim.setVelocity(victim.getVelocity().add(dir.multiply(multiplier)));
    }

    /** 斩杀：目标血量低于阈值时直接击杀。 */
    private void execute(LivingEntity victim, double thresholdPct) {
        if (thresholdPct <= 0 || victim.isDead()) {
            return;
        }
        double maxHealth = maxHealthOf(victim);
        double threshold = maxHealth * Math.min(100, thresholdPct) / 100.0;
        if (victim.getHealth() <= threshold) {
            damageTrue(victim, maxHealth * 1000);
            sendActionBar(victim, plugin.getMessage("execute"));
            spawnParticles(victim, Particle.ENCHANT, 40);
        }
    }

    /**
     * 真实伤害：直接扣除生命值，无视护甲、防御词条与吸收。
     */
    /** 获取实体最大生命值。 */
    private double maxHealthOf(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20 : attr.getValue();
    }
    public void damageTrue(LivingEntity victim, double amount) {
        if (victim == null || victim.isDead() || !victim.isValid() || amount <= 0) {
            return;
        }
        if (victim instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                return;
            }
        }
        victim.setHealth(Math.max(0, victim.getHealth() - amount));
    }

    // ---------------- 流血 ----------------

    /** 开始流血效果；已流血则刷新持续时间和伤害。 */
    public void startBleed(LivingEntity victim, double dps) {
        if (victim == null || victim.isDead() || dps <= 0) {
            return;
        }
        UUID id = victim.getUniqueId();
        BleedTask existing = bleedTasks.get(id);
        if (existing != null) {
            existing.refresh(dps);
            return;
        }
        BleedTask task = new BleedTask(victim, dps);
        bleedTasks.put(id, task);
        task.runTaskTimer(plugin, 0L, 20L);
        sendActionBar(victim, plugin.getMessage("bleed"));
    }

    /** 实体死亡/退出时清理流血任务。 */
    public void cleanup(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        BleedTask task = bleedTasks.remove(entity.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private class BleedTask extends BukkitRunnable {
        private final LivingEntity victim;
        private double dps;
        private int remaining;

        BleedTask(LivingEntity victim, double dps) {
            this.victim = victim;
            this.dps = dps;
            this.remaining = (int) Math.max(1, Math.ceil(duration("bleed_chance")));
        }

        void refresh(double newDps) {
            this.dps = Math.max(this.dps, newDps);
            this.remaining = (int) Math.max(1, Math.ceil(duration("bleed_chance")));
        }

        @Override
        public void run() {
            if (!victim.isValid() || victim.isDead()) {
                cancel();
                bleedTasks.remove(victim.getUniqueId());
                return;
            }
            damageTrue(victim, dps);
            spawnParticles(victim, Particle.DAMAGE_INDICATOR, 10);
            remaining--;
            if (remaining <= 0) {
                cancel();
                bleedTasks.remove(victim.getUniqueId());
            }
        }
    }

    // ---------------- 辅助 ----------------

    /** 聚合实体主手、副手与四件防具上的词条属性。 */
    private EquipmentStats statsOf(LivingEntity entity) {
        List<ItemStack> items = new ArrayList<>();
        if (entity instanceof Player player) {
            items.add(player.getInventory().getItemInMainHand());
            items.add(player.getInventory().getItemInOffHand());
            for (ItemStack armor : player.getInventory().getArmorContents()) {
                items.add(armor);
            }
        } else {
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                items.add(equipment.getItemInMainHand());
                items.add(equipment.getItemInOffHand());
                for (ItemStack armor : equipment.getArmorContents()) {
                    items.add(armor);
                }
            }
        }
        return EquipmentStats.fromItems(items, plugin);
    }

    private void spawnParticles(LivingEntity entity, Particle particle, int count) {
        if (entity == null || entity.isDead()) {
            return;
        }
        entity.getWorld().spawnParticle(particle, entity.getEyeLocation().add(0, 0.3, 0), count, 0.4, 0.4, 0.4, 0.01);
    }

    private void sendActionBar(LivingEntity entity, String message) {
        if (entity instanceof Player player && message != null) {
            player.sendActionBar(Text.color(message));
        }
    }
}
