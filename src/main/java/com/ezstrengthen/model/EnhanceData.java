package com.ezstrengthen.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一件装备的强化数据：强化次数 + 词条列表。
 * 序列化格式：count=3;true_damage:2,physical_damage:4
 */
public class EnhanceData {

    private int count;
    private final List<AffixInstance> affixes = new ArrayList<>();

    public EnhanceData() {
    }

    public EnhanceData(int count, List<AffixInstance> affixes) {
        this.count = count;
        if (affixes != null) {
            this.affixes.addAll(affixes);
        }
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<AffixInstance> getAffixes() {
        return affixes;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder("count=").append(count);
        if (!affixes.isEmpty()) {
            sb.append(';');
            for (int i = 0; i < affixes.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(affixes.get(i).serialize());
            }
        }
        return sb.toString();
    }

    /** 从持久化字符串解析，解析失败时返回空的强化数据。 */
    public static EnhanceData fromString(String raw) {
        EnhanceData data = new EnhanceData();
        if (raw == null || raw.isEmpty()) {
            return data;
        }
        String[] parts = raw.split(";", -1);
        if (parts.length > 0 && parts[0].startsWith("count=")) {
            try {
                data.count = Integer.parseInt(parts[0].substring("count=".length()));
            } catch (NumberFormatException ignored) {
                data.count = 0;
            }
        }
        if (parts.length > 1 && !parts[1].isEmpty()) {
            for (String token : parts[1].split(",")) {
                AffixInstance inst = AffixInstance.parse(token);
                if (inst != null) {
                    data.affixes.add(inst);
                }
            }
        }
        if (data.affixes.size() > data.count) {
            // 防御：词条数量不应超过强化次数
            data.count = data.affixes.size();
        }
        return data;
    }

    @Override
    public String toString() {
        return serialize();
    }
}
