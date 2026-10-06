package com.ezstrengthen.listener;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.gui.EnhanceGui;
import com.ezstrengthen.util.ItemUtil;
import com.ezstrengthen.util.Text;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * GUI 事件监听：点击、拖拽、关闭、退出时返还物品。
 */
public class GuiListener implements Listener {

    private final EzStrengthen plugin;

    public GuiListener(EzStrengthen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnhanceGui gui)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        boolean top = event.getClickedInventory() == gui.getInventory();
        boolean bottom = event.getClickedInventory() == player.getInventory();
        boolean normal = !event.isShiftClick()
                && event.getAction() != InventoryAction.HOTBAR_SWAP
                && event.getAction() != InventoryAction.COLLECT_TO_CURSOR;

        // 点击功能按钮：执行对应操作
        if (top && event.getSlot() == EnhanceGui.BUTTON_SLOT) {
            event.setCancelled(true);
            gui.onButtonClick();
            return;
        }
        if (top && event.getSlot() == EnhanceGui.REPAIR_SLOT) {
            event.setCancelled(true);
            gui.onRepairClick();
            return;
        }
        if (top && event.getSlot() == EnhanceGui.RESET_SLOT) {
            event.setCancelled(true);
            gui.onResetClick();
            return;
        }
        // 允许在物品槽与玩家背包中正常点按（非 Shift / 非数字键 / 非双击收集）
        if (top && event.getSlot() == EnhanceGui.ITEM_SLOT && normal) {
            // 防止放入成组物品：一次强化只处理单个物品
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            InventoryAction action = event.getAction();
            boolean willBeStack = false;
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                willBeStack = cursor != null && !cursor.getType().isAir() && cursor.getAmount() > 1;
            } else if (action == InventoryAction.PLACE_ONE) {
                willBeStack = current != null && !current.getType().isAir();
            } else if (action == InventoryAction.PICKUP_HALF || action == InventoryAction.PICKUP_ONE) {
                willBeStack = current != null && !current.getType().isAir() && current.getAmount() > 1;
            }
            if (willBeStack) {
                event.setCancelled(true);
                player.sendMessage(Text.color(plugin.getMessage("single-item-only")));
                plugin.getServer().getScheduler().runTask(plugin, gui::updateButtons);
                return;
            }
            event.setCancelled(false);
            plugin.getServer().getScheduler().runTask(plugin, gui::updateButtons);
            return;
        }
        if (bottom && normal) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnhanceGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof EnhanceGui gui)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            returnItem(player, gui);
            plugin.unregisterOpenGui(player, gui);
        }
    }

    /** 玩家上线时刷新其身上装备的描述（配置可能已更新）。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        EzStrengthen.refreshPlayerInventory(event.getPlayer());
    }

    /** 打开任意容器/界面时，刷新容器内与玩家背包中强化装备的描述。 */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory opened = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack item : opened.getContents()) {
                ItemUtil.refreshLoreIfEnhanced(item);
            }
            EzStrengthen.refreshPlayerInventory(player);
        });
    }

    /** 拾取掉落物时刷新描述。 */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item entityItem = event.getItem();
        ItemStack stack = entityItem.getItemStack();
        ItemUtil.refreshLoreIfEnhanced(stack);
        entityItem.setItemStack(stack);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        EnhanceGui gui = plugin.getOpenGui(player);
        if (gui != null) {
            returnItem(player, gui);
            plugin.unregisterOpenGui(player, gui);
        }
        plugin.getCombatService().cleanup(player);
    }

    /** 把物品槽中的物品返还给玩家，背包满则掉落在原地。 */
    private void returnItem(Player player, EnhanceGui gui) {
        ItemStack item = gui.getInventory().getItem(EnhanceGui.ITEM_SLOT);
        if (item == null || item.getType().isAir()) {
            return;
        }
        gui.getInventory().setItem(EnhanceGui.ITEM_SLOT, null);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
