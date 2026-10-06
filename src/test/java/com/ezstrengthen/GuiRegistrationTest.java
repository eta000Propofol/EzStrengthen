package com.ezstrengthen;

import com.ezstrengthen.gui.EnhanceGui;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * openGuis 登记表的回归测试：重复打开界面时，旧界面的关闭事件不得注销新界面的登记，
 * 否则服务器关停时 onDisable 遍历不到该界面，物品槽里的物品会丢失。
 */
class GuiRegistrationTest {

    private EzStrengthen plugin;

    @BeforeEach
    void setUp() throws Exception {
        // JavaPlugin 构造器依赖服务器环境，跳过构造器创建实例后注入空的 openGuis
        plugin = mock(EzStrengthen.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
        Field openGuis = EzStrengthen.class.getDeclaredField("openGuis");
        openGuis.setAccessible(true);
        openGuis.set(plugin, new LinkedHashMap<>());
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    @Test
    void closingOldGuiKeepsRegistrationOfReopenedGui() {
        Player player = player();
        EnhanceGui old = mock(EnhanceGui.class);
        EnhanceGui current = mock(EnhanceGui.class);

        plugin.registerOpenGui(player, old);
        plugin.registerOpenGui(player, current); // 界面 A 打开时执行 /st，新界面 B 覆盖登记
        plugin.unregisterOpenGui(player, old);   // openInventory 触发旧界面 A 的关闭事件

        assertSame(current, plugin.getOpenGui(player));
        plugin.unregisterOpenGui(player, current); // 新界面 B 正常关闭
        assertNull(plugin.getOpenGui(player));
    }

    @Test
    void unregisteringForeignGuiKeepsRegistration() {
        Player player = player();
        EnhanceGui current = mock(EnhanceGui.class);

        plugin.registerOpenGui(player, current);
        plugin.unregisterOpenGui(player, mock(EnhanceGui.class));

        assertSame(current, plugin.getOpenGui(player));
    }
}
