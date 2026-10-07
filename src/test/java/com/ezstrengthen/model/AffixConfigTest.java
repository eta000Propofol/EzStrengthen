package com.ezstrengthen.model;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 词条配置加载的回归测试：
 * - 配置缺失或列表不足 5 项时以枚举默认值补齐（防御性约定）；
 * - 超长列表截断，等级取值钳制到 1~5，绝不越界；
 * - enabled/name/duration 显式配置生效。
 * 全程使用真实 YamlConfiguration，无服务器依赖。
 */
@ExtendWith(MockitoExtension.class)
class AffixConfigTest {

    @Test
    @DisplayName("配置缺失时整段回退枚举默认值")
    void missingSectionFallsBackToEnumDefaults() {
        AffixConfig cfg = AffixConfig.load(new YamlConfiguration(), Affix.BLEED_CHANCE);

        assertTrue(cfg.isEnabled());
        assertEquals("流血几率", cfg.getName());
        assertEquals(5.0, cfg.value(1), 1e-9);
        assertEquals(20.0, cfg.value(5), 1e-9);
        assertEquals(2.0, cfg.extra(1), 1e-9);
        assertEquals(8.0, cfg.extra(5), 1e-9);
        assertEquals(5.0, cfg.getDuration(), 1e-9);
    }

    @Test
    @DisplayName("数值列表不足 5 项时尾部补枚举默认值")
    void shortValuesListPadsWithEnumDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("affixes.bleed_chance.values", List.of(10.0, 20.0));

        AffixConfig cfg = AffixConfig.load(config, Affix.BLEED_CHANCE);

        // 前 2 档用配置值，后 3 档补枚举默认 12/16/20
        assertEquals(10.0, cfg.value(1), 1e-9);
        assertEquals(20.0, cfg.value(2), 1e-9);
        assertEquals(12.0, cfg.value(3), 1e-9);
        assertEquals(16.0, cfg.value(4), 1e-9);
        assertEquals(20.0, cfg.value(5), 1e-9);
        // extra 未配置 → 全默认
        assertEquals(3.0, cfg.extra(2), 1e-9);
    }

    @Test
    @DisplayName("超长数值列表被截断到 5 项")
    void longValuesListIsTruncated() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("affixes.bleed_chance.values", List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0));

        AffixConfig cfg = AffixConfig.load(config, Affix.BLEED_CHANCE);

        assertEquals(5.0, cfg.value(5), 1e-9);
        assertEquals(1.0, cfg.value(1), 1e-9);
    }

    @Test
    @DisplayName("等级取值钳制到 1~5，绝不数组越界")
    void levelClampedToValidRange() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("affixes.bleed_chance.values", List.of(10.0, 20.0, 30.0, 40.0, 50.0));
        AffixConfig cfg = AffixConfig.load(config, Affix.BLEED_CHANCE);

        assertEquals(10.0, cfg.value(0), 1e-9);
        assertEquals(10.0, cfg.value(-3), 1e-9);
        assertEquals(50.0, cfg.value(6), 1e-9);
        assertEquals(50.0, cfg.value(999), 1e-9);
        // extra 未配置走枚举默认 {2,3,4,6,8}，钳制行为一致
        assertEquals(2.0, cfg.extra(0), 1e-9);
        assertEquals(8.0, cfg.extra(6), 1e-9);
    }

    @Test
    @DisplayName("enabled=false 显式禁用词条")
    void explicitDisabledFlagIsRespected() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("affixes.bleed_chance.enabled", false);

        assertFalse(AffixConfig.load(config, Affix.BLEED_CHANCE).isEnabled());
    }

    @Test
    @DisplayName("name 与 duration 显式配置生效")
    void explicitNameAndDurationAreRespected() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("affixes.bleed_chance.name", "自定义流血");
        config.set("affixes.bleed_chance.duration", 7.5);

        AffixConfig cfg = AffixConfig.load(config, Affix.BLEED_CHANCE);

        assertEquals("自定义流血", cfg.getName());
        assertEquals(7.5, cfg.getDuration(), 1e-9);
    }
}
