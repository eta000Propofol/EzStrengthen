package com.ezstrengthen.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 强化数据解析的回归测试：PDC 内容可被伪造 NBT 篡改，
 * 解析必须对非法/伪造值防御（负数 count、乱码、词条数与次数不一致）。
 */
class EnhanceDataTest {

    @Test
    void negativeCountClampedToZero() {
        // 伪造 NBT 把 count 改成负数时，钳制回 0，按全新物品正常结算
        assertEquals(0, EnhanceData.fromString("count=-5").getCount());
        assertEquals(0, EnhanceData.fromString("count=-1").getCount());
    }

    @Test
    void malformedCountFallsBackToZero() {
        assertEquals(0, EnhanceData.fromString("count=abc").getCount());
        assertEquals(0, EnhanceData.fromString("count=").getCount());
        assertEquals(0, EnhanceData.fromString("garbage").getCount());
        assertEquals(0, EnhanceData.fromString(null).getCount());
    }

    @Test
    void affixCountRaisesCountToStayConsistent() {
        EnhanceData data = EnhanceData.fromString("count=1;crit_chance:2,bleed_chance:3");
        assertEquals(2, data.getCount());
        assertEquals(2, data.getAffixes().size());
    }

    @Test
    void negativeCountWithAffixesStaysSelfConsistent() {
        // count=-5 但带着 1 条词条：先钳 0，再按词条数抬到 1，数据保持自洽
        EnhanceData data = EnhanceData.fromString("count=-5;crit_chance:2");
        assertEquals(1, data.getCount());
        assertEquals(1, data.getAffixes().size());
    }

    @Test
    void serializeRoundTrip() {
        EnhanceData data = new EnhanceData(3, List.of(new AffixInstance("crit_chance", 2), new AffixInstance("true_damage", 1)));
        EnhanceData parsed = EnhanceData.fromString(data.serialize());
        assertEquals(3, parsed.getCount());
        assertEquals(2, parsed.getAffixes().size());
        assertEquals("crit_chance", parsed.getAffixes().get(0).getId());
        assertEquals(2, parsed.getAffixes().get(0).getLevel());
    }

    @Test
    void invalidAffixTokensAreSkipped() {
        // 格式非法与等级越界的 token 跳过；格式合法但 id 未知的词条保留，
        // 由消费侧（EquipmentStats / affixLine）按配置缺失过滤
        EnhanceData data = EnhanceData.fromString("count=3;crit_chance:9,bad_token,unknown:2,true_damage:1");
        assertEquals(2, data.getAffixes().size());
        assertEquals(3, data.getCount());
        assertEquals("unknown", data.getAffixes().get(0).getId());
        assertEquals("true_damage", data.getAffixes().get(1).getId());
    }
}
