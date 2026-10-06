package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 费用与成功率越界防御的回归测试：按次配置列表长度不足时（如 max-level 调大而列表未同步），
 * 越界强化绝不允许 0 费用（免费）或 100% 成功率（必成），而是沿用最后一档配置。
 */
@ExtendWith(MockitoExtension.class)
class EnhanceServiceTest {

    @Mock
    private EzStrengthen plugin;

    private YamlConfiguration config;
    private EnhanceService service;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        lenient().when(plugin.getConfig()).thenReturn(config);
        service = new EnhanceService(plugin);
    }

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
}
