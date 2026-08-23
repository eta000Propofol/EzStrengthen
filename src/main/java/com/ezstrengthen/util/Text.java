package com.ezstrengthen.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 文本工具：把带 & 颜色代码的字符串转成 Adventure 组件。
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

    /** 把组件转回带 § 颜色代码的字符串。 */
    public static String toLegacy(Component component) {
        if (component == null) {
            return "";
        }
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
