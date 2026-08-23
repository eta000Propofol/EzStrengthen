package com.ezstrengthen.gui;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.model.AffixInstance;
import com.ezstrengthen.model.EnhanceData;
import com.ezstrengthen.service.EnhanceService.EnhanceResult;
import com.ezstrengthen.util.ItemUtil;
import com.ezstrengthen.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备强化 GUI（27 格箱子界面）。
 * 布局：11=说明，12=修复耐久，13=物品槽，14=重置属性，15=强化，其余灰色玻璃锁定。
 */
public class EnhanceGui implements InventoryHolder {

    public static final int ITEM_SLOT = 13;
    public static final int INFO_SLOT = 11;
    public static final int REPAIR_SLOT = 12;
    public static final int RESET_SLOT = 14;
    public static final int BUTTON_SLOT = 15;

    private final EzStrengthen plugin;
    private final Player player;
    private final Inventory inventory;

    public EnhanceGui(EzStrengthen plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        String title = plugin.getConfig().getString("gui.title", "&8装备强化");
        this.inventory = Bukkit.createInventory(this, 27, Text.color(title));
        build();
    }

    /** 打开界面。 */
    public void open() {
        plugin.registerOpenGui(player, this);
        player.openInventory(inventory);
    }

    private void build() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) {
            fm.displayName(Component.text(" "));
            filler.setItemMeta(fm);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != ITEM_SLOT && i != INFO_SLOT && i != REPAIR_SLOT && i != RESET_SLOT && i != BUTTON_SLOT) {
                inventory.setItem(i, filler.clone());
            }
        }
        inventory.setItem(INFO_SLOT, infoItem());
        updateButtons();
    }

    /** 说明物品（数值全部动态读取配置，重载配置后重新打开/刷新界面即生效）。 */
    private ItemStack infoItem() {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.color("&e&l装备强化说明"));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.color("&7把要强化的物品放到中间格"));
            lore.add(Text.color("&c每次只能处理 1 个物品，请拆分后放入"));
            lore.add(Text.color("&7点击绿宝石开始强化。"));
            lore.add(Component.empty());
            int max = plugin.getMaxLevel();
            lore.add(Text.color("&f最大强化等级: &e" + max));
            lore.add(Text.color("&7每次强化消耗货币"));
            // 至纯源石需求：动态读取配置（所需次数/数量/名称都可配置）
            List<Integer> tearLevels = plugin.getConfig().getIntegerList("dragon-tear.required-for-levels");
            if (!tearLevels.isEmpty()) {
                int tearAmount = Math.max(1, plugin.getConfig().getInt("dragon-tear.required-amount", 1));
                String tearName = plugin.getConfig().getString("dragon-tear.name", "&b&l至纯源石");
                StringBuilder levels = new StringBuilder();
                for (int i = 0; i < tearLevels.size(); i++) {
                    if (i > 0) {
                        levels.append("、");
                    }
                    levels.append(tearLevels.get(i));
                }
                lore.add(Text.color("&7第 " + levels + " 次强化额外消耗 " + tearAmount + " 个 " + tearName));
            }
            lore.add(Text.color("&7每次强化随机获得一个词条"));
            lore.add(Text.color("&7词条等级越高越强力、概率越低"));
            lore.add(Text.color("&7强化失败只消耗材料，不降级"));
            lore.add(Component.empty());
            String currency = plugin.getConfig().getString("economy.currency-name", "龙门币");
            double repairCost = plugin.getEnhanceService().getRepairCost();
            double resetCost = plugin.getEnhanceService().getResetCost();
            lore.add(Text.color("&f修复耐久: &a每次 " + plugin.getEconomyService().format(repairCost) + " " + currency));
            lore.add(Text.color("&f重置属性: &a每次 " + plugin.getEconomyService().format(resetCost) + " " + currency));
            lore.add(Text.color("&c重置会清空所有强化词条！"));
            lore.add(Component.empty());
            lore.add(Text.color("&7强化效果仅在主手、副手"));
            lore.add(Text.color("&7和身穿装备时生效"));
            meta.lore(lore);
            book.setItemMeta(meta);
        }
        return book;
    }

    /** 刷新全部按钮（强化 / 修复 / 重置）与说明。 */
    public void updateButtons() {
        inventory.setItem(INFO_SLOT, infoItem());
        ItemStack in = inventory.getItem(ITEM_SLOT);
        int count = 0;
        boolean hasItem = in != null && !in.getType().isAir();
        if (hasItem) {
            // 按当前配置重绘描述，保证 /st reload 后界面内物品的数值显示同步
            ItemUtil.refreshLoreIfEnhanced(in);
            inventory.setItem(ITEM_SLOT, in);
            count = ItemUtil.getEnhanceData(in).getCount();
        }
        String currency = plugin.getConfig().getString("economy.currency-name", "龙门币");

        // ---- 强化按钮 ----
        ItemStack btn = new ItemStack(Material.EMERALD);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.color("&a&l强化装备"));
            List<Component> lore = new ArrayList<>();
            if (!hasItem) {
                lore.add(Text.color("&7请先在中间放入要强化的物品"));
            } else {
                int max = plugin.getMaxLevel();
                int next = count + 1;
                if (next > max) {
                    lore.add(Text.color("&c已达最大强化等级！"));
                } else {
                    double cost = plugin.getEnhanceService().getCost(next);
                    lore.add(Text.color("&7当前等级: &f" + count + "&7/" + max));
                    lore.add(Text.color("&7本次费用: &6" + plugin.getEconomyService().format(cost) + " " + currency));
                    int tears = plugin.getEnhanceService().getRequiredTears(next);
                    if (tears > 0) {
                        lore.add(Text.color("&7需要至纯源石: &b" + tears + " 个"));
                    }
                    int rate = (int) Math.round(plugin.getEnhanceService().getSuccessRate(next) * 100);
                    lore.add(Text.color("&7成功率: &a" + rate + "%"));
                    lore.add(Text.color("&e点击开始强化！"));
                }
            }
            meta.lore(lore);
            btn.setItemMeta(meta);
        }
        inventory.setItem(BUTTON_SLOT, btn);

        // ---- 修复按钮 ----
        ItemStack repair = new ItemStack(Material.ANVIL);
        ItemMeta rm = repair.getItemMeta();
        if (rm != null) {
            rm.displayName(Text.color("&a&l修复装备耐久"));
            List<Component> lore = new ArrayList<>();
            if (!hasItem) {
                lore.add(Text.color("&7请先在中间放入要强化的物品"));
            } else {
                lore.add(Text.color("&7费用: &6" + plugin.getEconomyService().format(plugin.getEnhanceService().getRepairCost()) + " " + currency));
                lore.add(Text.color("&7将损耗的耐久修复至满"));
                lore.add(Text.color("&e点击修复！"));
            }
            rm.lore(lore);
            repair.setItemMeta(rm);
        }
        inventory.setItem(REPAIR_SLOT, repair);

        // ---- 重置按钮 ----
        ItemStack reset = new ItemStack(Material.BARRIER);
        ItemMeta rsm = reset.getItemMeta();
        if (rsm != null) {
            rsm.displayName(Text.color("&c&l重置装备属性"));
            List<Component> lore = new ArrayList<>();
            if (!hasItem) {
                lore.add(Text.color("&7请先在中间放入要强化的物品"));
            } else {
                lore.add(Text.color("&7费用: &6" + plugin.getEconomyService().format(plugin.getEnhanceService().getResetCost()) + " " + currency));
                lore.add(Text.color("&c警告：将清空所有强化词条！"));
                lore.add(Text.color("&e点击重置！"));
            }
            rsm.lore(lore);
            reset.setItemMeta(rsm);
        }
        inventory.setItem(RESET_SLOT, reset);
    }

    /** 点击强化按钮。 */
    public void onButtonClick() {
        ItemStack in = inventory.getItem(ITEM_SLOT);
        if (in == null || in.getType().isAir()) {
            player.sendMessage(Text.color(plugin.getMessage("no-item")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        if (in.getAmount() > 1) {
            player.sendMessage(Text.color(plugin.getMessage("single-item-only")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        EnhanceData before = ItemUtil.getEnhanceData(in);
        EnhanceResult result = plugin.getEnhanceService().attempt(player, in);
        switch (result) {
            case SUCCESS -> {
                EnhanceData after = ItemUtil.getEnhanceData(in);
                AffixInstance last = after.getAffixes().isEmpty() ? null : after.getAffixes().get(after.getAffixes().size() - 1);
                inventory.setItem(ITEM_SLOT, in);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
                player.getWorld().spawnParticle(Particle.ENCHANT, player.getEyeLocation().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, 0.01);
                if (last != null) {
                    AffixConfig cfg = plugin.getAffixConfig(last.getId());
                    String name = cfg == null ? last.getId() : cfg.getName();
                    player.sendMessage(Text.color(plugin.getMessage("enhance-success")
                            .replace("%affix%", name)
                            .replace("%level%", String.valueOf(last.getLevel()))));
                }
            }
            case FAIL -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                player.sendMessage(Text.color(plugin.getMessage("enhance-fail")));
            }
            case MAX_LEVEL -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                player.sendMessage(Text.color(plugin.getMessage("max-level")));
            }
            case NO_ECONOMY -> player.sendMessage(Text.color(plugin.getMessage("no-economy")));
            case NOT_ENOUGH_MONEY -> {
                double cost = plugin.getEnhanceService().getCost(before.getCount() + 1);
                String currency = plugin.getConfig().getString("economy.currency-name", "龙门币");
                player.sendMessage(Text.color(plugin.getMessage("not-enough-money")
                        .replace("%cost%", plugin.getEconomyService().format(cost))
                        .replace("%currency%", currency)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            case NOT_ENOUGH_TEARS -> {
                int tears = plugin.getEnhanceService().getRequiredTears(before.getCount() + 1);
                player.sendMessage(Text.color(plugin.getMessage("not-enough-tears")
                        .replace("%amount%", String.valueOf(tears))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            default -> {
            }
        }
        updateButtons();
    }

    /** 点击修复按钮。 */
    public void onRepairClick() {
        ItemStack in = inventory.getItem(ITEM_SLOT);
        if (in == null || in.getType().isAir()) {
            player.sendMessage(Text.color(plugin.getMessage("no-item")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        if (in.getAmount() > 1) {
            player.sendMessage(Text.color(plugin.getMessage("single-item-only")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        EnhanceResult result = plugin.getEnhanceService().repair(player, in);
        handleServiceResult(result, plugin.getEnhanceService().getRepairCost());
        if (result == EnhanceResult.REPAIR_SUCCESS) {
            inventory.setItem(ITEM_SLOT, in);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
            player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.01);
        }
        updateButtons();
    }

    /** 点击重置按钮。 */
    public void onResetClick() {
        ItemStack in = inventory.getItem(ITEM_SLOT);
        if (in == null || in.getType().isAir()) {
            player.sendMessage(Text.color(plugin.getMessage("no-item")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        if (in.getAmount() > 1) {
            player.sendMessage(Text.color(plugin.getMessage("single-item-only")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        EnhanceResult result = plugin.getEnhanceService().reset(player, in);
        handleServiceResult(result, plugin.getEnhanceService().getResetCost());
        if (result == EnhanceResult.RESET_SUCCESS) {
            inventory.setItem(ITEM_SLOT, in);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getEyeLocation().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.01);
        }
        updateButtons();
    }

    /** 处理重置/修复共用的结果提示。 */
    private void handleServiceResult(EnhanceResult result, double cost) {
        String currency = plugin.getConfig().getString("economy.currency-name", "龙门币");
        switch (result) {
            case RESET_SUCCESS -> player.sendMessage(Text.color(plugin.getMessage("reset-success")
                    .replace("%cost%", plugin.getEconomyService().format(cost))
                    .replace("%currency%", currency)));
            case REPAIR_SUCCESS -> player.sendMessage(Text.color(plugin.getMessage("repair-success")
                    .replace("%cost%", plugin.getEconomyService().format(cost))
                    .replace("%currency%", currency)));
            case NO_ENHANCEMENT -> {
                player.sendMessage(Text.color(plugin.getMessage("no-enhancement")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            case NOT_DAMAGED -> {
                player.sendMessage(Text.color(plugin.getMessage("not-damaged")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            case NOT_REPAIRABLE -> {
                player.sendMessage(Text.color(plugin.getMessage("not-repairable")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            case NOT_ENOUGH_MONEY -> {
                player.sendMessage(Text.color(plugin.getMessage("not-enough-money")
                        .replace("%cost%", plugin.getEconomyService().format(cost))
                        .replace("%currency%", currency)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            }
            case NO_ECONOMY -> player.sendMessage(Text.color(plugin.getMessage("no-economy")));
            default -> {
            }
        }
    }

    /** 界面所属玩家。 */
    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}