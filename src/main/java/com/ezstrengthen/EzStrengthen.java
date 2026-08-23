package com.ezstrengthen;

import com.ezstrengthen.command.StrengthenCommand;
import com.ezstrengthen.gui.EnhanceGui;
import com.ezstrengthen.listener.CombatListener;
import com.ezstrengthen.listener.DragonDropListener;
import com.ezstrengthen.listener.GuiListener;
import com.ezstrengthen.model.Affix;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.service.CombatService;
import com.ezstrengthen.service.EconomyService;
import com.ezstrengthen.service.EnhanceService;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EzStrengthen 主类：装备强化插件。
 */
public final class EzStrengthen extends JavaPlugin {

    private static EzStrengthen instance;

    private EconomyService economyService;
    private EnhanceService enhanceService;
    private CombatService combatService;

    private final Map<String, AffixConfig> affixConfigs = new LinkedHashMap<>();
    private List<AffixConfig> enabledAffixes = new ArrayList<>();
    private final Map<UUID, EnhanceGui> openGuis = new LinkedHashMap<>();

    public static EzStrengthen instance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reload();

        economyService = new EconomyService(this);
        enhanceService = new EnhanceService(this);
        combatService = new CombatService(this);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new DragonDropListener(this), this);

        StrengthenCommand command = new StrengthenCommand(this);
        var st = getCommand("st");
        if (st != null) {
            st.setExecutor(command);
            st.setTabCompleter(command);
        }

        getLogger().info("EzStrengthen 已启用。");
    }

    @Override
    public void onDisable() {
        // 关闭所有打开的强化界面并返还物品
        for (EnhanceGui gui : new ArrayList<>(openGuis.values())) {
            Player player = gui.getPlayer();
            if (player != null && player.isOnline()) {
                ItemStack item = gui.getInventory().getItem(EnhanceGui.ITEM_SLOT);
                if (item != null && !item.getType().isAir()) {
                    gui.getInventory().setItem(EnhanceGui.ITEM_SLOT, null);
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    for (ItemStack rest : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                }
                player.closeInventory();
            }
        }
        openGuis.clear();
        getLogger().info("EzStrengthen 已卸载。");
    }

    /** 重载配置并重新加载词条。 */
    public void reload() {
        reloadConfig();
        affixConfigs.clear();
        for (Affix affix : Affix.values()) {
            affixConfigs.put(affix.getId(), AffixConfig.load(getConfig(), affix));
        }
        enabledAffixes = affixConfigs.values().stream().filter(AffixConfig::isEnabled).toList();
        refreshAllOnline();
    }

    /** 刷新所有在线玩家身上/末影箱及打开中的强化界面里的装备描述。 */
    public void refreshAllOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerInventory(player);
        }
        for (EnhanceGui gui : new ArrayList<>(openGuis.values())) {
            ItemStack item = gui.getInventory().getItem(EnhanceGui.ITEM_SLOT);
            if (item != null && !item.getType().isAir()) {
                ItemUtil.refreshLoreIfEnhanced(item);
                gui.getInventory().setItem(EnhanceGui.ITEM_SLOT, item);
            }
            gui.updateButtons();
        }
    }

    /** 刷新玩家背包/装备栏/副手/末影箱中所有强化装备的描述。 */
    public static void refreshPlayerInventory(Player player) {
        if (player == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            ItemUtil.refreshLoreIfEnhanced(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            ItemUtil.refreshLoreIfEnhanced(item);
        }
        ItemUtil.refreshLoreIfEnhanced(player.getInventory().getItemInOffHand());
        for (ItemStack item : player.getEnderChest().getContents()) {
            ItemUtil.refreshLoreIfEnhanced(item);
        }
    }
    // ---------------- 配置访问 ----------------

    public int getMaxLevel() {
        return Math.max(1, getConfig().getInt("enhance.max-level", 6));
    }

    public double getChanceCap() {
        return Math.min(1, getConfig().getDouble("combat.chance-cap", 90) / 100.0);
    }

    public double getDefenseCap() {
        return Math.min(1, getConfig().getDouble("combat.defense-cap", 80) / 100.0);
    }

    /** 获取带 & 颜色代码的原始消息文本。 */
    public String getMessage(String key) {
        return getConfig().getString("messages." + key, "");
    }

    public AffixConfig getAffixConfig(String id) {
        return affixConfigs.get(id);
    }

    public List<AffixConfig> getEnabledAffixes() {
        return enabledAffixes;
    }

    // ---------------- 服务 ----------------

    public EconomyService getEconomyService() {
        return economyService;
    }

    public EnhanceService getEnhanceService() {
        return enhanceService;
    }

    public CombatService getCombatService() {
        return combatService;
    }

    // ---------------- GUI 管理 ----------------

    public void registerOpenGui(Player player, EnhanceGui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    public void unregisterOpenGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    public EnhanceGui getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    /** 供 GUI 内部使用的玩家访问器。 */
    public static Player getPlayer(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }
}
