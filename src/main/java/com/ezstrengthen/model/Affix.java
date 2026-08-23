package com.ezstrengthen.model;

/**
 * 词条枚举。
 * 每个词条包含：配置键 id、默认显示名称、Lv1~Lv5 默认数值、Lv1~Lv5 默认次要数值、默认状态时长（秒）。
 * 实际生效数值以 config.yml 中 affixes 节点为准。
 */
public enum Affix {

    TRUE_DAMAGE("true_damage", "真实伤害", new double[]{1, 2, 4, 7, 10}, new double[]{0, 0, 0, 0, 0}, 0),
    PHYSICAL_DAMAGE("physical_damage", "物理伤害", new double[]{10, 18, 28, 40, 55}, new double[]{0, 0, 0, 0, 0}, 0),
    PHYSICAL_DEFENSE("physical_defense", "物理防御", new double[]{3, 6, 10, 15, 22}, new double[]{0, 0, 0, 0, 0}, 0),
    CRIT_CHANCE("crit_chance", "暴击几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 0),
    CRIT_DAMAGE("crit_damage", "暴击伤害", new double[]{15, 30, 50, 75, 100}, new double[]{0, 0, 0, 0, 0}, 0),
    FREEZE_CHANCE("freeze_chance", "冰冻几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 3),
    BLEED_CHANCE("bleed_chance", "流血几率", new double[]{5, 8, 12, 16, 20}, new double[]{2, 3, 4, 6, 8}, 5),
    BLIND_CHANCE("blind_chance", "致盲几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 5),
    DODGE_CHANCE("dodge_chance", "闪避几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 0),
    LEVITATION_CHANCE("levitation_chance", "漂浮几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 2),
    REFLECT_CHANCE("reflect_chance", "反弹几率", new double[]{5, 8, 12, 16, 20}, new double[]{40, 50, 60, 70, 85}, 0),
    RANGED_DAMAGE("ranged_damage", "远程伤害", new double[]{10, 18, 28, 40, 55}, new double[]{0, 0, 0, 0, 0}, 0),
    MELEE_DAMAGE("melee_damage", "近战伤害", new double[]{10, 18, 28, 40, 55}, new double[]{0, 0, 0, 0, 0}, 0),
    RANGED_DEFENSE("ranged_defense", "远程防御", new double[]{3, 6, 10, 15, 22}, new double[]{0, 0, 0, 0, 0}, 0),
    MELEE_DEFENSE("melee_defense", "近战防御", new double[]{3, 6, 10, 15, 22}, new double[]{0, 0, 0, 0, 0}, 0),
    STUN_CHANCE("stun_chance", "眩晕几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 1.5),
    LIGHTNING_CHANCE("lightning_chance", "雷击几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 0),
    WEAKNESS_CHANCE("weakness_chance", "虚弱几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 5),
    KNOCKBACK_CHANCE("knockback_chance", "击飞几率", new double[]{5, 8, 12, 16, 20}, new double[]{2.0, 2.4, 2.8, 3.2, 3.6}, 0),
    CONFUSION_CHANCE("confusion_chance", "混乱几率", new double[]{5, 8, 12, 16, 20}, new double[]{0, 0, 0, 0, 0}, 5),
    LIGHTNING_DAMAGE("lightning_damage", "雷击伤害", new double[]{2, 4, 7, 10, 14}, new double[]{0, 0, 0, 0, 0}, 0),
    EXECUTE_CHANCE("execute_chance", "斩杀几率", new double[]{5, 8, 12, 16, 20}, new double[]{25, 30, 35, 40, 45}, 0);

    private final String id;
    private final String defaultName;
    private final double[] defaultValues;
    private final double[] defaultExtra;
    private final double defaultDuration;

    Affix(String id, String defaultName, double[] defaultValues, double[] defaultExtra, double defaultDuration) {
        this.id = id;
        this.defaultName = defaultName;
        this.defaultValues = defaultValues;
        this.defaultExtra = defaultExtra;
        this.defaultDuration = defaultDuration;
    }

    public String getId() {
        return id;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public double[] getDefaultValues() {
        return defaultValues;
    }

    public double[] getDefaultExtra() {
        return defaultExtra;
    }

    public double getDefaultDuration() {
        return defaultDuration;
    }

    /** 根据配置键查找词条，找不到返回 null。 */
    public static Affix fromId(String id) {
        for (Affix affix : values()) {
            if (affix.id.equalsIgnoreCase(id)) {
                return affix;
            }
        }
        return null;
    }
}