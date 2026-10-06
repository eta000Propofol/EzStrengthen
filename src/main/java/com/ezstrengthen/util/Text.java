package com.ezstrengthen.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 文本工具：把带 & 颜色代码的字符串转成 Adventure 组件，并提供组件的无损 JSON 序列化。
 */
public final class Text {

    private Text() {
    }

    /** 把 "&a..." 形式的字符串转为组件。 */
    public static Component color(String text) {
        if (text == null) {
            return Component.empty();
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /** 把组件无损序列化为 JSON（保留 hex 颜色、translatable、hover 等全部组件语义）。 */
    public static String toJson(Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    /** 把 JSON 反序列化回组件。 */
    public static Component fromJson(String json) {
        return GsonComponentSerializer.gson().deserialize(json);
    }
}
