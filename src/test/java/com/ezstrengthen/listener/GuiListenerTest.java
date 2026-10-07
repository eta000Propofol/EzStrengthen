package com.ezstrengthen.listener;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.gui.EnhanceGui;
import com.ezstrengthen.service.CombatService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GUI 关闭/退出路径的回归测试：必须注销正在关闭的那个界面实例（而非按玩家盲目删除登记）。
 * 注意：新版 Paper API 的 Material.isAir() 需要真实服务器的注册表，无服务器环境下
 * 物品槽为空路径（早退分支）才可执行；物品返还逻辑不在本次修复范围内。
 */
@ExtendWith(MockitoExtension.class)
class GuiListenerTest {

    @Mock
    private EzStrengthen plugin;
    @Mock
    private Player player;
    @Mock
    private InventoryView view;
    @Mock
    private Inventory top;
    @Mock
    private EnhanceGui gui;
    @Mock
    private PlayerInventory playerInventory;
    @Mock
    private CombatService combatService;

    @Test
    @DisplayName("关闭界面注销对应实例且不重复返还物品")
    void onCloseUnregistersTheClosedGui() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(gui);
        when(gui.getInventory()).thenReturn(top);
        when(top.getItem(EnhanceGui.ITEM_SLOT)).thenReturn(null);
        when(event.getPlayer()).thenReturn(player);

        new GuiListener(plugin).onClose(event);

        verify(plugin).unregisterOpenGui(same(player), same(gui));
        verify(playerInventory, never()).addItem(any());
    }

    @Test
    @DisplayName("非插件容器的界面关闭不做任何处理")
    void onCloseIgnoresInventoryWithoutEnhanceGuiHolder() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(null);

        new GuiListener(plugin).onClose(event);

        verifyNoInteractions(plugin);
    }

    @Test
    @DisplayName("物品槽内 Q 键丢弃被取消（托管物品防丢失）")
    void onClickDropsFromItemSlotAreCancelled() {
        // 物品槽按 Q 丢弃：托管中的物品掉落地面可能因超时消失，必须取消
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(gui);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(top);
        when(gui.getInventory()).thenReturn(top);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ONE_SLOT);
        when(event.getSlot()).thenReturn(EnhanceGui.ITEM_SLOT);

        new GuiListener(plugin).onClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("光标持有物品时在界面内 Q 键丢弃被取消")
    void onClickCursorDropsInGuiAreCancelled() {
        // 光标持有物品时在界面上按 Q 丢弃，同样必须取消
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getHolder()).thenReturn(gui);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClickedInventory()).thenReturn(top);
        when(gui.getInventory()).thenReturn(top);
        when(event.getAction()).thenReturn(InventoryAction.DROP_ONE_CURSOR);
        // 守卫启用时不会读取 getSlot（提前返回），禁用时才会走到放行分支，故用 lenient
        lenient().when(event.getSlot()).thenReturn(EnhanceGui.ITEM_SLOT);

        new GuiListener(plugin).onClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    @DisplayName("玩家退出时注销界面并清理战斗任务")
    void onQuitUnregistersRegisteredGuiAndCleansUpCombat() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(plugin.getOpenGui(player)).thenReturn(gui);
        when(gui.getInventory()).thenReturn(top);
        when(top.getItem(EnhanceGui.ITEM_SLOT)).thenReturn(null);
        when(plugin.getCombatService()).thenReturn(combatService);

        new GuiListener(plugin).onQuit(event);

        verify(plugin).unregisterOpenGui(same(player), same(gui));
        verify(combatService).cleanup(same(player));
        verify(playerInventory, never()).addItem(any());
    }

    @Test
    @DisplayName("无登记界面时退出仅清理战斗任务")
    void onQuitWithoutRegisteredGuiOnlyCleansUpCombat() {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(plugin.getCombatService()).thenReturn(combatService);

        new GuiListener(plugin).onQuit(event);

        verify(combatService).cleanup(same(player));
        verify(plugin, never()).unregisterOpenGui(any(Player.class), any(EnhanceGui.class));
    }
}
