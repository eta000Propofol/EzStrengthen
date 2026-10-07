package com.ezstrengthen.model;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * 聚合后的装备词条属性。
 * 跨装备同类词条叠加规则：
 * - 概率类：相加（上限见 combat.chance-cap，默认 90%）
 * - 减伤类：相加（上限见 combat.defense-cap，默认 80%）
 * - 数值/加成类：直接相加
 * - 次要数值（反弹比例、流血伤害、击退倍率、斩杀阈值）：取最大值
 */
public class EquipmentStats {

    // ---- 攻击侧 ----
    public double trueDamage;
    public double physicalDamagePct;
    public double rangedDamagePct;
    public double meleeDamagePct;
    public double lightningDamage;
    public double critChance;
    public double critDamagePct;

    // ---- 防御侧 ----
    public double physicalDefensePct;
    public double rangedDefensePct;
    public double meleeDefensePct;
    public double dodgeChance;
    public double reflectChance;
    public double reflectPctMax;

    // ---- 攻击触发的几率类词条 ----
    public double freezeChance;
    public double bleedChance;
    public double bleedDpsMax;
    public double blindChance;
    public double levitationChance;
    public double lightningChance;
    public double weaknessChance;
    public double knockbackChance;
    public double knockbackMultMax;
    public double confusionChance;
    public double stunChance;
    public double executeChance;
    public double executeThresholdMax;

    /**
     * 从一组物品（主手/副手/防具）聚合属性。
     */
    public static EquipmentStats fromItems(Collection<ItemStack> items, EzStrengthen plugin) {
        EquipmentStats s = new EquipmentStats();
        if (items == null || plugin == null) {
            return s;
        }
        double chanceCap = plugin.getChanceCap();
        double defenseCap = plugin.getDefenseCap();
        for (ItemStack item : items) {
            // 判空统一走 isEmpty()（AGENTS.md 约定）：isAir() 需要服务器注册表，无服务器环境会崩
            if (item == null || item.isEmpty()) {
                continue;
            }
            EnhanceData data = ItemUtil.getEnhanceData(item);
            if (data == null || data.getAffixes().isEmpty()) {
                continue;
            }
            for (AffixInstance inst : data.getAffixes()) {
                AffixConfig cfg = plugin.getAffixConfig(inst.getId());
                if (cfg == null || !cfg.isEnabled()) {
                    continue;
                }
                double v = cfg.value(inst.getLevel());
                double ex = cfg.extra(inst.getLevel());
                switch (cfg.getAffix()) {
                    case TRUE_DAMAGE -> s.trueDamage += v;
                    case PHYSICAL_DAMAGE -> s.physicalDamagePct += v / 100.0;
                    case PHYSICAL_DEFENSE -> s.physicalDefensePct = cap(s.physicalDefensePct + v / 100.0, defenseCap);
                    case CRIT_CHANCE -> s.critChance = cap(s.critChance + v / 100.0, chanceCap);
                    case CRIT_DAMAGE -> s.critDamagePct += v / 100.0;
                    case FREEZE_CHANCE -> s.freezeChance = cap(s.freezeChance + v / 100.0, chanceCap);
                    case BLEED_CHANCE -> {
                        s.bleedChance = cap(s.bleedChance + v / 100.0, chanceCap);
                        s.bleedDpsMax = Math.max(s.bleedDpsMax, ex);
                    }
                    case BLIND_CHANCE -> s.blindChance = cap(s.blindChance + v / 100.0, chanceCap);
                    case DODGE_CHANCE -> s.dodgeChance = cap(s.dodgeChance + v / 100.0, chanceCap);
                    case LEVITATION_CHANCE -> s.levitationChance = cap(s.levitationChance + v / 100.0, chanceCap);
                    case REFLECT_CHANCE -> {
                        s.reflectChance = cap(s.reflectChance + v / 100.0, chanceCap);
                        s.reflectPctMax = Math.max(s.reflectPctMax, ex);
                    }
                    case RANGED_DAMAGE -> s.rangedDamagePct += v / 100.0;
                    case MELEE_DAMAGE -> s.meleeDamagePct += v / 100.0;
                    case RANGED_DEFENSE -> s.rangedDefensePct = cap(s.rangedDefensePct + v / 100.0, defenseCap);
                    case MELEE_DEFENSE -> s.meleeDefensePct = cap(s.meleeDefensePct + v / 100.0, defenseCap);
                    case STUN_CHANCE -> s.stunChance = cap(s.stunChance + v / 100.0, chanceCap);
                    case LIGHTNING_CHANCE -> s.lightningChance = cap(s.lightningChance + v / 100.0, chanceCap);
                    case WEAKNESS_CHANCE -> s.weaknessChance = cap(s.weaknessChance + v / 100.0, chanceCap);
                    case KNOCKBACK_CHANCE -> {
                        s.knockbackChance = cap(s.knockbackChance + v / 100.0, chanceCap);
                        s.knockbackMultMax = Math.max(s.knockbackMultMax, ex);
                    }
                    case CONFUSION_CHANCE -> s.confusionChance = cap(s.confusionChance + v / 100.0, chanceCap);
                    case LIGHTNING_DAMAGE -> s.lightningDamage += v;
                    case EXECUTE_CHANCE -> {
                        s.executeChance = cap(s.executeChance + v / 100.0, chanceCap);
                        s.executeThresholdMax = Math.max(s.executeThresholdMax, ex);
                    }
                }
            }
        }
        return s;
    }

    private static double cap(double value, double max) {
        return Math.min(max, Math.max(0, value));
    }
}
