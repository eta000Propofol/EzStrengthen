package com.ezstrengthen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 有效最大强化等级的钳制规则：max-level 超过按次配置（economy.costs / enhance.success-rates）
 * 的列表长度时按较短列表钳制，否则超界强化会 0 费用且 100% 成功。
 */
class EzStrengthenTest {

    @Test
    void maxLevelBoundedByShortestPerLevelList() {
        assertEquals(6, EzStrengthen.boundMaxLevel(8, 6, 6)); // 报告的场景：max-level 8 但按次配置 6 项
        assertEquals(6, EzStrengthen.boundMaxLevel(6, 6, 6));
        assertEquals(3, EzStrengthen.boundMaxLevel(8, 6, 3));
        assertEquals(4, EzStrengthen.boundMaxLevel(4, 6, 6));
    }

    @Test
    void emptyPerLevelListsDisallowEnhancing() {
        assertEquals(0, EzStrengthen.boundMaxLevel(6, 0, 6));
        assertEquals(0, EzStrengthen.boundMaxLevel(6, 6, 0));
        assertEquals(0, EzStrengthen.boundMaxLevel(0, 0, 0));
    }
}
