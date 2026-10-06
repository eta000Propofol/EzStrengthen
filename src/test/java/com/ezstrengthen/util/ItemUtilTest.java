package com.ezstrengthen.util;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.EnhanceData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 原始描述持久化的回归测试：base lore 必须以 JSON 无损存取（legacy § 字符串会
 * 丢失 hex 颜色并拍平 translatable / hover 等组件），且旧版 legacy 存档仍可读取。
 */
@ExtendWith(MockitoExtension.class)
class ItemUtilTest {

    @Mock
    private EzStrengthen plugin;

    private ItemMeta meta;
    private FakePdc pdc;
    private ItemStack item;
    private List<Component> currentLore;

    @BeforeEach
    void setUp() throws Exception {
        setInstance(plugin);
        // Paper 26.1 的 NamespacedKey(Plugin, String) 通过 Plugin.namespace() 取命名空间
        when(plugin.namespace()).thenReturn("ezstrengthen");
        when(plugin.getMaxLevel()).thenReturn(6);

        pdc = new FakePdc();
        meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        currentLore = new ArrayList<>();
        // legacy 存档场景（base lore 已存在）不会读取现有描述，故标 lenient
        lenient().when(meta.lore()).thenAnswer(inv -> new ArrayList<>(currentLore));
        doAnswer(inv -> {
            currentLore = new ArrayList<>(inv.getArgument(0));
            return null;
        }).when(meta).lore(anyList());

        item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        when(item.getItemMeta()).thenReturn(meta);
    }

    @AfterEach
    void tearDown() throws Exception {
        setInstance(null);
    }

    private static void setInstance(EzStrengthen plugin) throws Exception {
        Field field = EzStrengthen.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
    }

    private List<Component> initialLore() {
        return List.of(
                Component.text("普通描述行"),
                Component.text("RGB描述行", TextColor.color(0x1A2B3C)),
                Component.text("带悬停").hoverEvent(HoverEvent.showText(Component.text("悬停内容"))),
                Component.translatable("block.minecraft.stone")
        );
    }

    @Test
    void baseLoreRoundTripsLosslessly() {
        when(meta.lore()).thenReturn(new ArrayList<>(initialLore()));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));

        List<Component> rebuilt = currentLore;
        for (int i = 0; i < initialLore().size(); i++) {
            assertEquals(Text.toJson(initialLore().get(i)), Text.toJson(rebuilt.get(i)),
                    "第 " + i + " 行原始描述在强化后必须无损保留");
        }
    }

    @Test
    void translatableAndHoverSurviveRoundTrip() {
        when(meta.lore()).thenReturn(new ArrayList<>(initialLore()));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));

        TranslatableComponent line = assertInstanceOf(TranslatableComponent.class, currentLore.get(3));
        assertEquals("block.minecraft.stone", line.key());
        assertTrue(currentLore.get(2).hoverEvent() != null, "hover 事件不能被拍平");
        assertEquals(TextColor.color(0x1A2B3C), currentLore.get(1).color());
    }

    @Test
    void repeatedEnhancesKeepSingleBaseLore() {
        when(meta.lore()).thenReturn(new ArrayList<>(initialLore()));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));
        ItemUtil.setEnhanceData(item, new EnhanceData(2, List.of()));

        // 原始描述 4 行 + 空行 + 等级行 = 6 行，base lore 不得重复拼接
        assertEquals(6, currentLore.size());
        for (int i = 0; i < initialLore().size(); i++) {
            assertEquals(Text.toJson(initialLore().get(i)), Text.toJson(currentLore.get(i)));
        }
    }

    @Test
    void legacyBaseLoreStillReadsAfterUpgrade() {
        // 模拟旧版本物品：base lore 以 legacy § 字符串存档，无格式标记
        pdc.set(new NamespacedKey(plugin, "base_lore"), PersistentDataType.LIST.strings(),
                List.of("§a旧的legacy描述", ""));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));

        assertEquals(LegacyComponentSerializer.legacySection()
                .deserialize("§a旧的legacy描述"), currentLore.get(0));
    }

    @Test
    void quotedLegacyLinesAreNotMisdetectedAsJson() {
        // legacy 描述行字面以引号开头（如引号包裹的名字）且恰好是合法 JSON 字符串时，
        // 必须按 legacy 原样读回，不能因 JSON 启发式剥掉引号
        pdc.set(new NamespacedKey(plugin, "base_lore"), PersistentDataType.LIST.strings(),
                List.of("\"屠龙宝刀\""));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));

        Component line = currentLore.get(0);
        assertEquals("\"屠龙宝刀\"", LegacyComponentSerializer.legacySection().serialize(line));
    }

    @Test
    void clearRestoresOriginalLoreAndRemovesData() {
        when(meta.lore()).thenReturn(new ArrayList<>(initialLore()));
        ItemUtil.setEnhanceData(item, new EnhanceData(1, List.of()));
        ItemUtil.clearEnhanceData(item);

        assertEquals(initialLore().size(), currentLore.size());
        for (int i = 0; i < initialLore().size(); i++) {
            assertEquals(Text.toJson(initialLore().get(i)), Text.toJson(currentLore.get(i)));
        }
        assertFalse(pdc.has(new NamespacedKey(plugin, "data"), PersistentDataType.STRING));
        assertFalse(pdc.has(new NamespacedKey(plugin, "base_lore"), PersistentDataType.LIST.strings()));
        assertFalse(pdc.has(new NamespacedKey(plugin, "base_lore_format"), PersistentDataType.STRING));
    }

    /** 内存版 PersistentDataContainer：只做存取，不涉及字节编码。 */
    private static final class FakePdc implements PersistentDataContainer {

        private final Map<String, Object> store = new HashMap<>();

        @Override
        public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
            store.put(key.toString(), value);
        }

        @Override
        public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
            @SuppressWarnings("unchecked")
            C value = (C) store.get(key.toString());
            return value;
        }

        @Override
        public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
            return store.containsKey(key.toString());
        }

        @Override
        public boolean has(NamespacedKey key) {
            return store.containsKey(key.toString());
        }

        @Override
        public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
            C value = get(key, type);
            return value != null ? value : defaultValue;
        }

        @Override
        public void remove(NamespacedKey key) {
            store.remove(key.toString());
        }

        @Override
        public Set<NamespacedKey> getKeys() {
            return store.keySet().stream()
                    .map(k -> new NamespacedKey(k.split(":", 2)[0], k.split(":", 2)[1]))
                    .collect(Collectors.toSet());
        }

        @Override
        public boolean isEmpty() {
            return store.isEmpty();
        }

        @Override
        public void copyTo(PersistentDataContainer other, boolean replace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistentDataAdapterContext getAdapterContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] serializeToBytes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void readFromBytes(byte[] bytes, boolean covered) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getSize() {
            return store.size();
        }
    }
}
