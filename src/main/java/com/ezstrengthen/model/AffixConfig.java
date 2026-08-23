package com.ezstrengthen.model;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 单个词条的配置（从 config.yml 的 affixes 节点加载）。
 * 数值数组下标为等级-1，即 index 0 对应 Lv1。
 */
public class AffixConfig {

    private final Affix affix;
    private final String name;
    private final boolean enabled;
    private final double[] values;
    private final double[] extra;
    private final double duration;

    public AffixConfig(Affix affix, String name, boolean enabled, double[] values, double[] extra, double duration) {
        this.affix = affix;
        this.name = name;
        this.enabled = enabled;
        this.values = values;
        this.extra = extra;
        this.duration = duration;
    }

    /** 从配置文件中加载某个词条，缺失的字段使用默认值。 */
    public static AffixConfig load(FileConfiguration config, Affix affix) {
        String path = "affixes." + affix.getId() + ".";
        boolean enabled = config.getBoolean(path + "enabled", true);
        String name = config.getString(path + "name", affix.getDefaultName());
        double[] values = readArray(config, path + "values", affix.getDefaultValues());
        double[] extra = readArray(config, path + "extra", affix.getDefaultExtra());
        double duration = config.getDouble(path + "duration", affix.getDefaultDuration());
        return new AffixConfig(affix, name, enabled, values, extra, duration);
    }

    private static double[] readArray(FileConfiguration config, String path, double[] defaults) {
        List<Double> list = config.getDoubleList(path);
        double[] result = new double[5];
        for (int i = 0; i < 5; i++) {
            result[i] = i < list.size() ? list.get(i) : defaults[i];
        }
        return result;
    }

    public Affix getAffix() {
        return affix;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 获取 Lv1~Lv5 的数值。 */
    public double value(int level) {
        return values[Math.max(1, Math.min(5, level)) - 1];
    }

    /** 获取 Lv1~Lv5 的次要数值。 */
    public double extra(int level) {
        return extra[Math.max(1, Math.min(5, level)) - 1];
    }

    /** 状态效果持续时间（秒）。 */
    public double getDuration() {
        return duration;
    }
}
