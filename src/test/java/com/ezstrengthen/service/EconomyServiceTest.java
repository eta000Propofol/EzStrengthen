package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 经济提供者懒解析的回归测试：经济插件晚于本插件启用时（本插件只 softdepend Vault），
 * 构造时解析为 null 后必须在后续使用前重试并自动接入，而不是等到重启。
 */
@ExtendWith(MockitoExtension.class)
class EconomyServiceTest {

    @Mock
    private EzStrengthen plugin;
    @Mock
    private Server server;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private ServicesManager servicesManager;
    @Mock
    private OfflinePlayer player;

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("EconomyServiceTest"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectsWhenEconomyPluginRegistersLater() {
        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        when(server.getServicesManager()).thenReturn(servicesManager);
        Economy economy = mock(Economy.class);
        RegisteredServiceProvider<Economy> provider = mock(RegisteredServiceProvider.class);
        when(provider.getProvider()).thenReturn(economy);
        // 构造时经济插件尚未注册（第一次查询为 null），随后才完成注册
        when(servicesManager.getRegistration(Economy.class)).thenReturn(null, provider);

        EconomyService service = new EconomyService(plugin);
        verify(servicesManager, times(1)).getRegistration(Economy.class);

        // 下一次使用前重试解析，自动接入并完成结算
        when(economy.getName()).thenReturn("Essentials");
        assertTrue(service.isAvailable());
        when(economy.format(5.0)).thenReturn("$5");
        assertEquals("$5", service.format(5.0));
        when(economy.withdrawPlayer(player, 5.0)).thenReturn(new EconomyResponse(5.0, 95.0, EconomyResponse.ResponseType.SUCCESS, null));
        assertTrue(service.withdraw(player, 5.0));
        assertTrue(service.withdraw(player, 0)); // 金额为 0 且经济可用时视为成功
    }

    @Test
    void staysSafeWithoutVault() {
        when(pluginManager.getPlugin("Vault")).thenReturn(null);
        EconomyService service = new EconomyService(plugin);

        assertFalse(service.isAvailable());
        assertFalse(service.withdraw(player, 10.0));
        assertEquals("5.0", service.format(5.0));
        // 未安装 Vault 时绝不能触碰 Vault 的 Economy 类
        verifyNoInteractions(servicesManager);
    }
}
