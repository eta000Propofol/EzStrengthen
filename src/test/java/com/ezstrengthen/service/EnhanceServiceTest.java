package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.Affix;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.model.EnhanceData;
import com.ezstrengthen.service.EnhanceService.EnhanceResult;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 强化服务的回归测试：
 * - 费用/成功率取值：配置列表长度不足时，越界取值沿用最后一档，绝不免费或必成；
 * - 扣款结果校验：has() 通过后 withdraw() 仍可能失败（经济实现取整/并发变动），必须中止且不消耗材料；
 * - 扣款前早退分支：满级、材料不足、无可强化/无可修复内容，一律不产生任何扣款；
 * - 随机源经构造器注入，成功率判定与词条等级抽取可确定化验证。
 */
@ExtendWith(MockitoExtension.class)
class EnhanceServiceTest {

    @Mock
    private EzStrengthen plugin;
    @Mock
    private Player player;
    @Mock
    private EconomyService economyService;

    private YamlConfiguration config;
    private EnhanceService service;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        // 早退分支（满级/无强化内容等）到不了取值逻辑，共享桩用 lenient 避免严格模式误报
        lenient().when(plugin.getConfig()).thenReturn(config);
        service = new EnhanceService(plugin);
    }

    // ---------------- 费用与成功率取值 ----------------

    private void loadDefaultSixEntries() {
        config.set("economy.costs", List.of(1000.0, 2000.0, 4000.0, 8000.0, 16000.0, 32000.0));
        config.set("enhance.success-rates", List.of(90.0, 80.0, 70.0, 60.0, 50.0, 40.0));
    }

    @Test
    @DisplayName("已配置等级使用各自的费用与成功率")
    void configuredLevelsUseTheirOwnCostAndRate() {
        loadDefaultSixEntries();
        assertEquals(1000.0, service.getCost(1));
        assertEquals(32000.0, service.getCost(6));
        assertEquals(0.9, service.getSuccessRate(1), 1e-9);
        assertEquals(0.4, service.getSuccessRate(6), 1e-9);
    }

    @Test
    @DisplayName("越界等级沿用最后一档费用与成功率，绝不免费或必成")
    void outOfBoundsLevelsFallBackToLastConfiguredEntry() {
        loadDefaultSixEntries();
        // 第 7、8 次强化：沿用第 6 档费用与成功率
        assertEquals(32000.0, service.getCost(7));
        assertEquals(32000.0, service.getCost(8));
        assertEquals(0.4, service.getSuccessRate(7), 1e-9);
        assertEquals(0.4, service.getSuccessRate(8), 1e-9);
        assertEquals(32000.0, service.getCost(99));
    }

    @Test
    @DisplayName("非正等级钳到第一档")
    void nonPositiveLevelClampsToFirstEntry() {
        loadDefaultSixEntries();
        assertEquals(1000.0, service.getCost(0));
        assertEquals(0.9, service.getSuccessRate(0), 1e-9);
    }

    // ---------------- 扣款前早退分支 ----------------

    private void stubEconomyForAttempt() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(plugin.getMaxLevel()).thenReturn(6);
        when(economyService.isAvailable()).thenReturn(true);
        when(plugin.getEnabledAffixes()).thenReturn(List.of(mock(AffixConfig.class)));
        config.set("economy.costs", List.of(1000.0));
    }

    private ItemStack itemMock() {
        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        return item;
    }

    @Test
    @DisplayName("满级物品强化请求在扣款前被拒绝（MAX_LEVEL）")
    void maxLevelItemsRejectEnhancementBeforeCharging() {
        stubEconomyForAttempt();
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            // count=6 已达 maxLevel：nextLevel=7 越界，必须在读费用与扣款之前中止
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(6, List.of()));

            assertEquals(EnhanceResult.MAX_LEVEL, service.attempt(player, item));

            verify(economyService, never()).has(same(player), anyDouble());
            verify(economyService, never()).withdraw(same(player), anyDouble());
            itemUtil.verify(() -> ItemUtil.consumeDragonTears(any(), anyInt()), never());
        }
    }

    @Test
    @DisplayName("整组物品（数量 > 1）强化请求在扣款前被拒绝（TOO_MANY_ITEMS）")
    void multiItemStackIsRejectedBeforeCharging() {
        // 护栏位于一切经济/配置读取之前，无需任何桩即可触发
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(64);

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            assertEquals(EnhanceResult.TOO_MANY_ITEMS, service.attempt(player, item));

            verify(economyService, never()).has(same(player), anyDouble());
            verify(economyService, never()).withdraw(same(player), anyDouble());
            itemUtil.verify(() -> ItemUtil.setEnhanceData(any(), any()), never());
        }
    }

    @Test
    @DisplayName("单件物品通过数量护栏继续正常流程")
    void singleItemPassesTheAmountGuard() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(plugin.getEnabledAffixes()).thenReturn(List.of(mock(AffixConfig.class)));
        when(economyService.isAvailable()).thenReturn(false);
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(1);

        // 未接通经济 → NO_ECONOMY：证明流程已越过数量护栏（否则会返回 TOO_MANY_ITEMS）
        assertEquals(EnhanceResult.NO_ECONOMY, service.attempt(player, item));
    }

    @Test
    @DisplayName("整组物品重置请求在扣款前被拒绝（TOO_MANY_ITEMS）")
    void multiItemStackIsRejectedBeforeResetting() {
        // 护栏位于一切经济/配置读取之前，无需任何桩即可触发
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(64);

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            assertEquals(EnhanceResult.TOO_MANY_ITEMS, service.reset(player, item));

            verify(economyService, never()).has(same(player), anyDouble());
            verify(economyService, never()).withdraw(same(player), anyDouble());
            itemUtil.verify(() -> ItemUtil.clearEnhanceData(any()), never());
        }
    }

    @Test
    @DisplayName("整组物品修复请求在扣款前被拒绝（TOO_MANY_ITEMS）")
    void multiItemStackIsRejectedBeforeRepairing() {
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(64);

        assertEquals(EnhanceResult.TOO_MANY_ITEMS, service.repair(player, item));

        verify(economyService, never()).withdraw(same(player), anyDouble());
        verify(item, never()).setItemMeta(any());
    }

    @Test
    @DisplayName("单件物品重置请求越过数量护栏（NO_ENHANCEMENT）")
    void singleItemResetPassesTheAmountGuard() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(1);

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(0, List.of()));

            assertEquals(EnhanceResult.NO_ENHANCEMENT, service.reset(player, item));
        }
    }

    @Test
    @DisplayName("单件物品修复请求越过数量护栏（NOT_REPAIRABLE）")
    void singleItemRepairPassesTheAmountGuard() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        ItemStack item = itemMock();
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));

        assertEquals(EnhanceResult.NOT_REPAIRABLE, service.repair(player, item));
    }

    @Test
    @DisplayName("至纯源石不足时中止强化且不扣款（NOT_ENOUGH_TEARS）")
    void missingTearsRejectEnhancementBeforeCharging() {
        stubEconomyForAttempt();
        when(economyService.has(player, 1000.0)).thenReturn(true);
        config.set("dragon-tear.required-for-levels", List.of(5));
        config.set("dragon-tear.required-amount", 2);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            // count=4 → 第 5 次需要 2 颗至纯源石，只持有 1 颗
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(4, List.of()));
            itemUtil.when(() -> ItemUtil.countDragonTears(any())).thenReturn(1);

            assertEquals(EnhanceResult.NOT_ENOUGH_TEARS, service.attempt(player, item));

            verify(economyService, never()).withdraw(same(player), anyDouble());
            itemUtil.verify(() -> ItemUtil.consumeDragonTears(any(), anyInt()), never());
        }
    }

    @Test
    @DisplayName("无强化内容的物品重置被拒绝且不扣款（NO_ENHANCEMENT）")
    void resetWithoutEnhancementRejectedBeforeCharging() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(0, List.of()));

            assertEquals(EnhanceResult.NO_ENHANCEMENT, service.reset(player, item));

            verify(economyService, never()).has(same(player), anyDouble());
            verify(economyService, never()).withdraw(same(player), anyDouble());
        }
    }

    @Test
    @DisplayName("无耐久损耗的物品修复被拒绝且不扣款（NOT_DAMAGED）")
    void repairOnlyForDamagedItems() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        ItemStack item = itemMock();
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        when(item.getItemMeta()).thenReturn(meta);
        when(((Damageable) meta).hasDamage()).thenReturn(false);

        assertEquals(EnhanceResult.NOT_DAMAGED, service.repair(player, item));

        verify(economyService, never()).has(same(player), anyDouble());
        verify(economyService, never()).withdraw(same(player), anyDouble());
        verify((Damageable) meta, never()).resetDamage();
    }

    @Test
    @DisplayName("不可修复类型的物品（无 Damageable 元数据）被拒绝（NOT_REPAIRABLE）")
    void repairOnlyForDamageableMeta() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        ItemStack item = itemMock();
        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));

        assertEquals(EnhanceResult.NOT_REPAIRABLE, service.repair(player, item));

        verify(economyService, never()).withdraw(same(player), anyDouble());
    }

    // ---------------- 扣款结果校验 ----------------

    @Test
    @DisplayName("has 通过但 withdraw 失败时强化中止且不写入数据")
    void withdrawFailureAbortsEnhancement() {
        stubEconomyForAttempt();
        when(economyService.has(player, 1000.0)).thenReturn(true);
        when(economyService.withdraw(player, 1000.0)).thenReturn(false);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(0, List.of()));

            EnhanceResult result = service.attempt(player, item);

            assertEquals(EnhanceResult.NOT_ENOUGH_MONEY, result);
            itemUtil.verify(() -> ItemUtil.setEnhanceData(any(), any()), never());
            itemUtil.verify(() -> ItemUtil.consumeDragonTears(any(), anyInt()), never());
        }
    }

    @Test
    @DisplayName("扣款成功且随机判定通过时完成强化并写回数据")
    void withdrawSuccessProceedsToEnhancement() {
        // 注入恒命中的随机源：nextDouble=0.0 既通过成功率判定，也命中第一档权重
        Random random = mock(Random.class);
        when(random.nextDouble()).thenReturn(0.0);
        EnhanceService deterministic = new EnhanceService(plugin, random);

        stubEconomyForAttempt();
        when(economyService.has(player, 1000.0)).thenReturn(true);
        when(economyService.withdraw(player, 1000.0)).thenReturn(true);
        config.set("enhance.affix-level-chances", List.of(50.0, 30.0, 10.0, 7.0, 3.0));
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        when(plugin.getEnabledAffixes()).thenReturn(List.of(cfg));
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(0, List.of()));

            EnhanceResult result = deterministic.attempt(player, item);

            assertEquals(EnhanceResult.SUCCESS, result);
            itemUtil.verify(() -> ItemUtil.setEnhanceData(same(item), any()));
        }
    }

    @Test
    @DisplayName("重置扣款失败时中止且不清空数据")
    void withdrawFailureAbortsReset() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        when(economyService.has(player, 5000.0)).thenReturn(true);
        when(economyService.withdraw(player, 5000.0)).thenReturn(false);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(1, List.of()));

            EnhanceResult result = service.reset(player, item);

            assertEquals(EnhanceResult.NOT_ENOUGH_MONEY, result);
            itemUtil.verify(() -> ItemUtil.clearEnhanceData(any()), never());
        }
    }

    @Test
    @DisplayName("修复扣款失败时中止且不重置耐久")
    void withdrawFailureAbortsRepair() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        when(economyService.has(player, 1000.0)).thenReturn(true);
        when(economyService.withdraw(player, 1000.0)).thenReturn(false);
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        ItemStack item = itemMock();
        when(item.getItemMeta()).thenReturn(meta);
        when(((Damageable) meta).hasDamage()).thenReturn(true);

        EnhanceResult result = service.repair(player, item);

        assertEquals(EnhanceResult.NOT_ENOUGH_MONEY, result);
        verify((Damageable) meta, never()).resetDamage();
    }

    // ---------------- 词条与等级抽取 ----------------

    private EnhanceService serviceWithAffixes(Random random, AffixConfig cfg) {
        EnhanceService svc = new EnhanceService(plugin, random);
        when(plugin.getEnabledAffixes()).thenReturn(List.of(cfg));
        return svc;
    }

    @Test
    @DisplayName("权重按配置档位划分等级（1/3/5 档边界）")
    void rollLevelFollowsConfiguredWeights() {
        Random random = mock(Random.class);
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        EnhanceService svc = serviceWithAffixes(random, cfg);
        config.set("enhance.affix-level-chances", List.of(50.0, 30.0, 10.0, 7.0, 3.0));

        when(random.nextDouble()).thenReturn(0.0);    // r=0 → 命中第 1 档
        assertEquals(1, svc.rollAffix().getLevel());
        when(random.nextDouble()).thenReturn(0.85);   // r=85 → 越过 50/30 → 命中第 3 档
        assertEquals(3, svc.rollAffix().getLevel());
        when(random.nextDouble()).thenReturn(0.999);  // r=99.9 → 依次越过前 4 档 → 第 5 档
        assertEquals(5, svc.rollAffix().getLevel());
    }

    @Test
    @DisplayName("权重全为零时回退均匀抽取且不越界")
    void rollLevelFallsBackToUniformWhenWeightsSumToZero() {
        Random random = mock(Random.class);
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        EnhanceService svc = serviceWithAffixes(random, cfg);
        config.set("enhance.affix-level-chances", List.of(0.0, 0.0, 0.0, 0.0, 0.0));

        // nextInt 有两类调用：nextInt(1) 选词条（唯一档位恒为 0）、nextInt(5) 抽等级
        final int uniformRoll = 3;
        when(random.nextInt(anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) == 1 ? 0 : uniformRoll);
        assertEquals(4, svc.rollAffix().getLevel());
    }

    @Test
    @DisplayName("权重列表缺失时同样回退均匀抽取")
    void rollLevelFallsBackToUniformWhenWeightsMissing() {
        Random random = mock(Random.class);
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        EnhanceService svc = serviceWithAffixes(random, cfg);
        // 不设置 affix-level-chances：getDoubleList 返回空表

        final int uniformRoll = 4;
        when(random.nextInt(anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) == 1 ? 0 : uniformRoll);
        assertEquals(5, svc.rollAffix().getLevel());
    }

    @Test
    @DisplayName("权重档位多于 5 级时等级钳到 5")
    void rollLevelClampsToFiveBeyondConfiguredEntries() {
        Random random = mock(Random.class);
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        EnhanceService svc = serviceWithAffixes(random, cfg);
        // 第 7 档才命中：返回值必须钳到 5
        config.set("enhance.affix-level-chances", List.of(100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 50.0));

        when(random.nextDouble()).thenReturn(0.999);
        assertEquals(5, svc.rollAffix().getLevel());
    }

    @Test
    @DisplayName("无启用词条时抽取返回 null")
    void rollAffixReturnsNullWithoutEnabledAffixes() {
        when(plugin.getEnabledAffixes()).thenReturn(List.of());
        assertNull(service.rollAffix());
    }
}
