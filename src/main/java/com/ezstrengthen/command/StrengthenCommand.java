package com.ezstrengthen.command;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.gui.EnhanceGui;
import com.ezstrengthen.model.AffixInstance;
import com.ezstrengthen.model.EnhanceData;
import com.ezstrengthen.util.ItemUtil;
import com.ezstrengthen.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令：
 * /st                          打开强化界面
 * /st reload                   重载配置
 * /st give <玩家> <数量>        发放至纯源石
 * /st stamp <玩家> <词条id:等级>...  把手上的物品生成强化版交给玩家（管理员）
 */
public class StrengthenCommand implements CommandExecutor, TabCompleter {

    private final EzStrengthen plugin;

    public StrengthenCommand(EzStrengthen plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.color(plugin.getMessage("not-player")));
                return true;
            }
            if (!player.hasPermission("ezstrengthen.use")) {
                player.sendMessage(Text.color(plugin.getMessage("no-permission")));
                return true;
            }
            new EnhanceGui(plugin, player).open();
            player.sendMessage(Text.color(plugin.getMessage("gui-opened")));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("ezstrengthen.admin")) {
                    sender.sendMessage(Text.color(plugin.getMessage("no-permission")));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(Text.color(plugin.getMessage("reloaded")));
            }
            case "give" -> {
                if (!sender.hasPermission("ezstrengthen.admin")) {
                    sender.sendMessage(Text.color(plugin.getMessage("no-permission")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color("&c用法: /st give <玩家> <数量>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Text.color(plugin.getMessage("player-not-found")));
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Text.color("&c数量必须是正整数。"));
                        return true;
                    }
                }
                ItemStack tear = ItemUtil.createDragonTear(plugin, amount);
                Map<Integer, ItemStack> leftover = target.getInventory().addItem(tear);
                for (ItemStack rest : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                sender.sendMessage(Text.color(plugin.getMessage("gave-tears")
                        .replace("%player%", target.getName())
                        .replace("%amount%", String.valueOf(amount))));
            }
            case "stamp" -> {
                if (!sender.hasPermission("ezstrengthen.admin")) {
                    sender.sendMessage(Text.color(plugin.getMessage("no-permission")));
                    return true;
                }
                if (!(sender instanceof Player admin)) {
                    sender.sendMessage(Text.color(plugin.getMessage("not-player")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color("&c用法: /st stamp <玩家> <词条id:等级> [词条id:等级 ...]"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Text.color(plugin.getMessage("player-not-found")));
                    return true;
                }
                ItemStack base = admin.getInventory().getItemInMainHand();
                if (base == null || base.getType().isAir()) {
                    sender.sendMessage(Text.color(plugin.getMessage("no-item-hand")));
                    return true;
                }
                List<AffixInstance> affixes = new ArrayList<>();
                for (int i = 2; i < args.length; i++) {
                    AffixInstance inst = AffixInstance.parse(args[i]);
                    if (inst == null || plugin.getAffixConfig(inst.getId()) == null) {
                        sender.sendMessage(Text.color("&c无效词条: " + args[i] + "（格式: 词条id:等级，等级 1~5）"));
                        return true;
                    }
                    affixes.add(inst);
                }
                int max = plugin.getMaxLevel();
                if (affixes.isEmpty() || affixes.size() > max) {
                    sender.sendMessage(Text.color("&c词条数量需为 1~" + max + " 个。"));
                    return true;
                }
                EnhanceData data = new EnhanceData(affixes.size(), affixes);
                ItemStack copy = base.clone();
                ItemUtil.setEnhanceData(copy, data);
                Map<Integer, ItemStack> leftover = target.getInventory().addItem(copy);
                for (ItemStack rest : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                sender.sendMessage(Text.color(plugin.getMessage("stamp-success")
                        .replace("%player%", target.getName())
                        .replace("%count%", String.valueOf(affixes.size()))));
            }
            default -> sender.sendMessage(Text.color("&c用法: /st | /st reload | /st give <玩家> <数量> | /st stamp <玩家> <词条:等级> ..."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("ezstrengthen.admin")) {
                result.add("reload");
                result.add("give");
                result.add("stamp");
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("stamp"))) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                result.add(online.getName());
            }
        }
        return result;
    }
}