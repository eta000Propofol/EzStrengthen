package com.ezstrengthen.model;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 装备词条聚合规则的回归测试：
 * - 概率类与减伤类叠加后受 cap 钳制（combat.chance-cap / combat.defense-cap）；
 * - 数值/加成类直接相加、不封顶；
 * - 次要数值（反弹比例、流血 DPS、击退倍率、斩杀阈值）跨词条取最大值；
 * - 未启用词条、未知词条 id、空物品一律跳过。
 */
@ExtendWith(MockitoExtension.class)
class EquipmentStatsTest {

    @Mock
    private EzStrengthen plugin;

    @BeforeEach
    void setUp() {
        // null 插件守卫用例不触达 cap 桩，共享桩用 lenient
        lenient().when(plugin.getChanceCap()).thenReturn(0.9);
        lenient().when(plugin.getDefenseCap()).thenReturn(0.8);
    }

    private AffixConfig configOf(Affix affix, double value) {
        AffixConfig cfg = mock(AffixConfig.class);
        lenient().when(cfg.getAffix()).thenReturn(affix);
        lenient().when(cfg.isEnabled()).thenReturn(true);
        lenient().when(cfg.value(1)).thenReturn(value);
        return cfg;
    }

    private ItemStack itemMock() {
        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        return item;
    }

    @Test
    @DisplayName("概率类词条叠加未到 cap 时取累加值")
    void chanceAffixesStackBelowCap() {
        AffixConfig low = configOf(Affix.CRIT_CHANCE, 30.0);
        AffixConfig high = configOf(Affix.CRIT_CHANCE, 40.0);
        ItemStack a = itemMock();
        ItemStack b = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(a))
                    .thenReturn(new EnhanceData(1, List.of(new AffixInstance("crit_chance", 1))));
            itemUtil.when(() -> ItemUtil.getEnhanceData(b))
                    .thenReturn(new EnhanceData(1, List.of(new AffixInstance("crit_chance", 1))));
            // 连续桩与装备顺序耦合：fromItems 按 List 顺序聚合，a 先 b 后 → 第一件 30%、第二件 40%
            when(plugin.getAffixConfig("crit_chance")).thenReturn(low, high);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(a, b), plugin);

            // 30% + 40% = 70%，未到 90% 封顶
            assertEquals(0.7, stats.critChance, 1e-9);
        }
    }

    @Test
    @DisplayName("概率类叠加超过 chance-cap 时被钳制")
    void chanceAffixesExceedingCapAreClamped() {
        AffixConfig cfg = configOf(Affix.CRIT_CHANCE, 50.0);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            // 同一件装备两个 50% 暴击：100% 超过 90% 封顶
            itemUtil.when(() -> ItemUtil.getEnhanceData(item))
                    .thenReturn(new EnhanceData(1, List.of(
                            new AffixInstance("crit_chance", 1),
                            new AffixInstance("crit_chance", 1))));
            when(plugin.getAffixConfig("crit_chance")).thenReturn(cfg);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(item), plugin);

            assertEquals(0.9, stats.critChance, 1e-9);
        }
    }

    @Test
    @DisplayName("减伤类词条叠加后受 defense-cap 封顶")
    void defenseAffixesStackThenCap() {
        AffixConfig cfg = configOf(Affix.PHYSICAL_DEFENSE, 50.0);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item))
                    .thenReturn(new EnhanceData(1, List.of(
                            new AffixInstance("physical_defense", 1),
                            new AffixInstance("physical_defense", 1))));
            when(plugin.getAffixConfig("physical_defense")).thenReturn(cfg);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(item), plugin);

            assertEquals(0.8, stats.physicalDefensePct, 1e-9);
        }
    }

    @Test
    @DisplayName("数值与百分比加成类直接相加、不封顶")
    void numericAffixesStackWithoutCap() {
        AffixConfig trueDmg = configOf(Affix.TRUE_DAMAGE, 5.0);
        AffixConfig physDmg = configOf(Affix.PHYSICAL_DAMAGE, 30.0);
        AffixConfig lightning = configOf(Affix.LIGHTNING_DAMAGE, 3.0);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item))
                    .thenReturn(new EnhanceData(1, List.of(
                            new AffixInstance("true_damage", 1),
                            new AffixInstance("physical_damage", 1),
                            new AffixInstance("lightning_damage", 1),
                            new AffixInstance("true_damage", 1),
                            new AffixInstance("physical_damage", 1),
                            new AffixInstance("lightning_damage", 1))));
            when(plugin.getAffixConfig("true_damage")).thenReturn(trueDmg);
            when(plugin.getAffixConfig("physical_damage")).thenReturn(physDmg);
            when(plugin.getAffixConfig("lightning_damage")).thenReturn(lightning);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(item), plugin);

            assertEquals(10.0, stats.trueDamage, 1e-9);
            assertEquals(0.6, stats.physicalDamagePct, 1e-9);
            assertEquals(6.0, stats.lightningDamage, 1e-9);
        }
    }

    @Test
    @DisplayName("次要数值跨词条取最大值而非相加")
    void secondaryValuesTakeMaximum() {
        AffixConfig bleed = configOf(Affix.BLEED_CHANCE, 20.0);
        AffixConfig reflect = configOf(Affix.REFLECT_CHANCE, 15.0);
        AffixConfig knockback = configOf(Affix.KNOCKBACK_CHANCE, 10.0);
        AffixConfig execute = configOf(Affix.EXECUTE_CHANCE, 10.0);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item))
                    .thenReturn(new EnhanceData(1, List.of(
                            new AffixInstance("bleed_chance", 1),
                            new AffixInstance("bleed_chance", 1),
                            new AffixInstance("reflect_chance", 1),
                            new AffixInstance("reflect_chance", 1),
                            new AffixInstance("knockback_chance", 1),
                            new AffixInstance("knockback_chance", 1),
                            new AffixInstance("execute_chance", 1),
                            new AffixInstance("execute_chance", 1))));
            when(plugin.getAffixConfig("bleed_chance")).thenReturn(bleed);
            when(plugin.getAffixConfig("reflect_chance")).thenReturn(reflect);
            when(plugin.getAffixConfig("knockback_chance")).thenReturn(knockback);
            when(plugin.getAffixConfig("execute_chance")).thenReturn(execute);

            // extra 依次返回 2/5、30/45、1.5/2.0、0.2/0.35 → 全部取最大
            when(bleed.extra(1)).thenReturn(2.0, 5.0);
            when(reflect.extra(1)).thenReturn(30.0, 45.0);
            when(knockback.extra(1)).thenReturn(1.5, 2.0);
            when(execute.extra(1)).thenReturn(0.2, 0.35);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(item), plugin);

            assertEquals(5.0, stats.bleedDpsMax, 1e-9);
            assertEquals(45.0, stats.reflectPctMax, 1e-9);
            assertEquals(2.0, stats.knockbackMultMax, 1e-9);
            assertEquals(0.35, stats.executeThresholdMax, 1e-9);
            // 概率部分仍正常叠加并封顶：20%*2=40%、15%*2=30%、10%*2=20%、10%*2=20%
            assertEquals(0.4, stats.bleedChance, 1e-9);
            assertEquals(0.3, stats.reflectChance, 1e-9);
            assertEquals(0.2, stats.knockbackChance, 1e-9);
            assertEquals(0.2, stats.executeChance, 1e-9);
        }
    }

    @Test
    @DisplayName("未启用词条与未知词条 id 一律跳过")
    void disabledAndUnknownAffixesAreSkipped() {
        AffixConfig disabled = configOf(Affix.CRIT_CHANCE, 90.0);
        when(disabled.isEnabled()).thenReturn(false);
        ItemStack item = itemMock();

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(item))
                    .thenReturn(new EnhanceData(1, List.of(
                            new AffixInstance("crit_chance", 1),
                            new AffixInstance("totally_unknown", 1))));
            when(plugin.getAffixConfig("crit_chance")).thenReturn(disabled);
            when(plugin.getAffixConfig("totally_unknown")).thenReturn(null);

            EquipmentStats stats = EquipmentStats.fromItems(List.of(item), plugin);

            assertEquals(0.0, stats.critChance, 1e-9);
        }
    }

    @Test
    @DisplayName("空物品与空数据不参与聚合")
    void emptyItemsAreSkipped() {
        ItemStack empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);
        ItemStack noData = mock(ItemStack.class);
        when(noData.isEmpty()).thenReturn(false);

        try (MockedStatic<ItemUtil> itemUtil = mockStatic(ItemUtil.class)) {
            itemUtil.when(() -> ItemUtil.getEnhanceData(noData))
                    .thenReturn(new EnhanceData(0, List.of()));

            // Arrays.asList 允许 null 元素，模拟背包中可能出现的空位
            EquipmentStats stats = EquipmentStats.fromItems(Arrays.asList(null, empty, noData), plugin);

            assertEquals(0.0, stats.trueDamage, 1e-9);
            assertEquals(0.0, stats.critChance, 1e-9);
        }
    }

    @Test
    @DisplayName("null 集合或 null 插件返回全零面板")
    void nullInputsYieldZeroedStats() {
        assertNotNull(EquipmentStats.fromItems(null, plugin));
        assertNotNull(EquipmentStats.fromItems(List.of(), null));
    }
}
