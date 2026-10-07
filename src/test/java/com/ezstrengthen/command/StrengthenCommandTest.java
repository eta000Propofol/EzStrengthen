package com.ezstrengthen.command;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.util.ItemUtil;
import com.ezstrengthen.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

/**
 * /st give 发放数量钳制的回归测试（也是命令层的第一批用例）：
 * - 数量下限 1、上限 2304（36 格 × 64 = 一整背包），双向钳制防手滑刷爆背包与掉落物；
 * - 合法数量原样透传，缺省数量参数默认 1；
 * - 发放反馈消息必须携带钳制后的实际数量，避免管理员误以为如数发放。
 */
@ExtendWith(MockitoExtension.class)
class StrengthenCommandTest {

    @Mock
    private EzStrengthen plugin;
    @Mock
    private CommandSender sender;
    @Mock
    private Command command;
    @Mock
    private Player target;

    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ItemUtil> itemUtil;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer("Steve")).thenReturn(target);

        itemUtil = mockStatic(ItemUtil.class);

        lenient().when(sender.hasPermission("ezstrengthen.admin")).thenReturn(true);
        lenient().when(target.getName()).thenReturn("Steve");
        PlayerInventory inventory = mock(PlayerInventory.class);
        lenient().when(target.getInventory()).thenReturn(inventory);
        // 背包必装得下（返回空 leftover），聚焦数量钳制本身
        lenient().when(inventory.addItem(any())).thenReturn(new HashMap<>());
        lenient().when(plugin.getMessage("gave-tears")).thenReturn("已向 %player% 发放 %amount% 颗至纯源石");
    }

    @AfterEach
    void tearDown() {
        itemUtil.close();
        bukkit.close();
    }

    private void runGive(String... args) {
        new StrengthenCommand(plugin).onCommand(sender, command, "st", args);
    }

    /** 断言发放反馈消息携带实际数量（消息经 Text.color 转组件，用 JSON 表示比对）。 */
    private void assertFeedbackCarriesAmount(String expectedAmount) {
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(message.capture());
        assertTrue(message.getAllValues().stream()
                        .map(Text::toJson)
                        .anyMatch(json -> json.contains(expectedAmount)),
                "反馈消息应包含实际数量 " + expectedAmount);
    }

    @Test
    @DisplayName("数量超过上限 2304 时被钳制，反馈携带钳后实际值")
    void giveAmountAboveLimitIsClampedTo2304() {
        runGive("give", "Steve", "999999");

        itemUtil.verify(() -> ItemUtil.createDragonTear(same(plugin), eq(2304)));
        assertFeedbackCarriesAmount("2304");
    }

    @Test
    @DisplayName("数量小于 1 时被钳到下限 1")
    void giveAmountBelowOneIsClampedToOne() {
        runGive("give", "Steve", "0");

        itemUtil.verify(() -> ItemUtil.createDragonTear(same(plugin), eq(1)));
        assertFeedbackCarriesAmount("1");
    }

    @Test
    @DisplayName("合法数量原样透传")
    void giveAmountWithinRangePassesThrough() {
        runGive("give", "Steve", "64");

        itemUtil.verify(() -> ItemUtil.createDragonTear(same(plugin), eq(64)));
        assertFeedbackCarriesAmount("64");
    }

    @Test
    @DisplayName("缺省数量参数时默认发放 1")
    void giveWithoutAmountDefaultsToOne() {
        runGive("give", "Steve");

        itemUtil.verify(() -> ItemUtil.createDragonTear(same(plugin), eq(1)));
        assertFeedbackCarriesAmount("1");
    }
}
