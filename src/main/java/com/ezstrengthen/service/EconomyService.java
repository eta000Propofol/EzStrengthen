package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济服务：通过 Vault 接口接入服务器的经济插件。
 * 注意：未安装 Vault 时也要能安全启动，因此先检查 Vault 插件是否存在，
 * 再访问 Vault 的 Economy 类，避免 NoClassDefFoundError 导致插件崩溃。
 * 经济提供者延迟解析：本插件只 softdepend Vault，经济插件可能在本插件之后启用，
 * 因此尚未注册时每次使用前重试，接入成功后缓存。
 */
public class EconomyService {

    private final EzStrengthen plugin;
    private Economy economy;
    private boolean lookupFailureLogged;

    public EconomyService(EzStrengthen plugin) {
        this.plugin = plugin;
        // 只有 Vault 确实安装了，才去触碰 Vault 的类；否则会在启用时崩溃。
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("未安装 Vault，经济功能已禁用（强化/修复/重置需要货币）。");
            return;
        }
        tryLookup();
        if (economy == null) {
            plugin.getLogger().warning("Vault 已安装，但尚未有经济插件注册，将在其启用后自动接入。");
        }
    }

    /** 尝试解析一次经济提供者；成功则缓存并提示（只在首次接入时打日志）。 */
    private void tryLookup() {
        try {
            RegisteredServiceProvider<Economy> provider = plugin.getServer()
                    .getServicesManager().getRegistration(Economy.class);
            if (provider != null && provider.getProvider() != null) {
                economy = provider.getProvider();
                plugin.getLogger().info("已接入 Vault 经济插件: " + economy.getName());
                // 接入瞬间刷新已打开的界面与物品描述，费用显示与结算同步可用
                plugin.refreshAllOnline();
            }
        } catch (Throwable t) {
            economy = null;
            if (!lookupFailureLogged) {
                lookupFailureLogged = true;
                plugin.getLogger().warning("接入 Vault 失败: " + t.getMessage());
            }
        }
    }

    /** 获取可用的经济提供者；尚未注册时重试解析，避免后启动的经济插件永远接不上。 */
    private Economy economy() {
        if (economy == null && plugin.getServer().getPluginManager().getPlugin("Vault") != null) {
            tryLookup();
        }
        return economy;
    }

    /** 经济是否可用。 */
    public boolean isAvailable() {
        return economy() != null;
    }

    /** 检查玩家余额是否足够。 */
    public boolean has(OfflinePlayer player, double amount) {
        Economy eco = economy();
        return eco != null && eco.has(player, amount);
    }

    /** 从玩家余额中扣除；成功返回 true。 */
    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy eco = economy();
        if (eco == null || amount <= 0) {
            return eco != null;
        }
        EconomyResponse response = eco.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    /** 格式化金额（带货币单位）。 */
    public String format(double amount) {
        Economy eco = economy();
        return eco != null ? eco.format(amount) : String.valueOf(amount);
    }
}
