package com.ezstrengthen.model;

/**
 * 一条已强化出的词条（词条 id + 等级）。
 * 序列化格式：true_damage:3
 */
public class AffixInstance {

    private final String id;
    private final int level;

    public AffixInstance(String id, int level) {
        this.id = id;
        this.level = level;
    }

    public String getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public String serialize() {
        return id + ":" + level;
    }

    /** 从字符串解析，格式非法时返回 null。 */
    public static AffixInstance parse(String raw) {
        if (raw == null) {
            return null;
        }
        int idx = raw.indexOf(':');
        if (idx <= 0 || idx == raw.length() - 1) {
            return null;
        }
        String id = raw.substring(0, idx);
        try {
            int level = Integer.parseInt(raw.substring(idx + 1));
            if (level < 1 || level > 5) {
                return null;
            }
            return new AffixInstance(id, level);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return serialize();
    }
}
