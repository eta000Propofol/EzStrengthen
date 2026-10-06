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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 费用/成功率取值与扣款校验的回归测试：
 * - 配置列表长度不足时，越界取值沿用最后一档，绝不免费或必成；
 * - has() 通过后 withdraw() 仍可能失败（经济实现取整/并发变动），必须中止且不消耗材料。
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
        when(plugin.getConfig()).thenReturn(config);
        service = new EnhanceService(plugin);
    }

    // ---------------- 费用与成功率取值 ----------------

    private void loadDefaultSixEntries() {
        config.set("economy.costs", List.of(1000.0, 2000.0, 4000.0, 8000.0, 16000.0, 32000.0));
        config.set("enhance.success-rates", List.of(90.0, 80.0, 70.0, 60.0, 50.0, 40.0));
    }

    @Test
    void configuredLevelsUseTheirOwnCostAndRate() {
        loadDefaultSixEntries();
        assertEquals(1000.0, service.getCost(1));
        assertEquals(32000.0, service.getCost(6));
        assertEquals(0.9, service.getSuccessRate(1), 1e-9);
        assertEquals(0.4, service.getSuccessRate(6), 1e-9);
    }

    @Test
    void outOfBoundsLevelsFallBackToLastConfiguredEntry() {
        loadDefaultSixEntries();
        // 第 7、8 次强化：沿用第 6 档费用与成功率，绝不免费或必成
        assertEquals(32000.0, service.getCost(7));
        assertEquals(32000.0, service.getCost(8));
        assertEquals(0.4, service.getSuccessRate(7), 1e-9);
        assertEquals(0.4, service.getSuccessRate(8), 1e-9);
        assertEquals(32000.0, service.getCost(99));
    }

    @Test
    void nonPositiveLevelClampsToFirstEntry() {
        loadDefaultSixEntries();
        assertEquals(1000.0, service.getCost(0));
        assertEquals(0.9, service.getSuccessRate(0), 1e-9);
    }

    // ---------------- 扣款结果校验 ----------------

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
    void withdrawSuccessProceedsToEnhancement() {
        stubEconomyForAttempt();
        when(economyService.has(player, 1000.0)).thenReturn(true);
        when(economyService.withdraw(player, 1000.0)).thenReturn(true);
        // 成功率 100% 使结果确定化（random.nextDouble() 恒 <= 1.0，不触发 FAIL）
        config.set("enhance.success-rates", List.of(100.0));
        config.set("enhance.affix-level-chances", List.of(50.0, 30.0, 10.0, 7.0, 3.0));
        AffixConfig cfg = mock(AffixConfig.class);
        when(cfg.getAffix()).thenReturn(Affix.TRUE_DAMAGE);
        when(plugin.getEnabledAffixes()).thenReturn(List.of(cfg));
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item)).thenReturn(new EnhanceData(0, List.of()));

            EnhanceResult result = service.attempt(player, item);

            assertEquals(EnhanceResult.SUCCESS, result);
            itemUtil.verify(() -> ItemUtil.setEnhanceData(same(item), any()));
        }
    }

    @Test
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
    void withdrawFailureAbortsRepair() {
        when(plugin.getEconomyService()).thenReturn(economyService);
        when(economyService.isAvailable()).thenReturn(true);
        when(economyService.has(player, 1000.0)).thenReturn(true);
        when(economyService.withdraw(player, 1000.0)).thenReturn(false);
        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        when(item.getItemMeta()).thenReturn(meta);
        when(((Damageable) meta).hasDamage()).thenReturn(true);

        EnhanceResult result = service.repair(player, item);

        assertEquals(EnhanceResult.NOT_ENOUGH_MONEY, result);
        verify((Damageable) meta, never()).resetDamage();
    }
}
