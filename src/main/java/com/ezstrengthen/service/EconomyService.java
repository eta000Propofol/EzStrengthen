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
 */
public class EconomyService {

    private final EzStrengthen plugin;
    private Economy economy;

    public EconomyService(EzStrengthen plugin) {
        this.plugin = plugin;
        // 只有 Vault 确实安装了，才去触碰 Vault 的类；否则会在启用时崩溃。
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("未安装 Vault，经济功能已禁用（强化/修复/重置需要货币）。");
            return;
        }
        try {
            RegisteredServiceProvider<Economy> provider = plugin.getServer()
                    .getServicesManager().getRegistration(Economy.class);
            if (provider != null && provider.getProvider() != null) {
                economy = provider.getProvider();
                plugin.getLogger().info("已接入 Vault 经济插件: " + economy.getName());
            } else {
                plugin.getLogger().warning("Vault 已安装，但没有可用的经济插件，强化按钮将暂时禁用。");
            }
        } catch (Throwable t) {
            economy = null;
            plugin.getLogger().warning("接入 Vault 失败，经济功能已禁用: " + t.getMessage());
        }
    }

    /** 经济是否可用。 */
    public boolean isAvailable() {
        return economy != null;
    }

    /** 检查玩家余额是否足够。 */
    public boolean has(OfflinePlayer player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    /** 从玩家余额中扣除；成功返回 true。 */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null || amount <= 0) {
            return economy != null;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response != null && response.transactionSuccess();
    }

    /** 格式化金额（带货币单位）。 */
    public String format(double amount) {
        if (economy != null) {
            return economy.format(amount);
        }
        return String.valueOf(amount);
    }
}