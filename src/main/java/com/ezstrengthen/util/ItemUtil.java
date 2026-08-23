package com.ezstrengthen.util;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.Affix;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.model.AffixInstance;
import com.ezstrengthen.model.EnhanceData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品工具：读写强化数据、构建强化描述、创建/识别至纯源石。
 * 所有数据保存在 PersistentDataContainer 中，不会影响物品本身。
 */
public final class ItemUtil {

    private static final String DATA_KEY = "data";
    private static final String TEAR_KEY = "dragon_tear";
    private static final String BASE_LORE_KEY = "base_lore";

    private ItemUtil() {
    }

    // ---------------- 强化数据 ----------------

    /** 读取物品上的强化数据；没有则返回空数据。 */
    public static EnhanceData getEnhanceData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new EnhanceData();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new EnhanceData();
        }
        String raw = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(EzStrengthen.instance(), DATA_KEY),
                PersistentDataType.STRING);
        return EnhanceData.fromString(raw);
    }

    /** 写入强化数据，并重建物品描述（保留原始描述 + 强化词条）。 */
    public static void setEnhanceData(ItemStack item, EnhanceData data) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 第一次强化时保存原始描述，之后每次都用它重建
        if (!pdc.has(new org.bukkit.NamespacedKey(EzStrengthen.instance(), BASE_LORE_KEY), PersistentDataType.LIST.strings())) {
            List<String> base = new ArrayList<>();
            List<Component> existingLore = meta.lore();
            if (existingLore != null) {
                for (Component line : existingLore) {
                    base.add(Text.toLegacy(line));
                }
            }
            pdc.set(new org.bukkit.NamespacedKey(EzStrengthen.instance(), BASE_LORE_KEY),
                    PersistentDataType.LIST.strings(), base);
        }
        pdc.set(new org.bukkit.NamespacedKey(EzStrengthen.instance(), DATA_KEY),
                PersistentDataType.STRING, data.serialize());
        meta.lore(buildLore(meta, data));
        item.setItemMeta(meta);
    }

    /** 根据强化数据生成描述：原始描述 + 强化等级行 + 词条行。 */
    private static List<Component> buildLore(ItemMeta meta, EnhanceData data) {
        List<Component> lore = new ArrayList<>();
        List<String> base = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(EzStrengthen.instance(), BASE_LORE_KEY),
                PersistentDataType.LIST.strings());
        if (base != null) {
            for (String line : base) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
        }
        if (data.getCount() > 0) {
            lore.add(Component.empty());
            lore.add(Text.color("&6⚡ &f强化等级 &e" + data.getCount() + "&7/" + EzStrengthen.instance().getMaxLevel()));
        }
        for (AffixInstance inst : data.getAffixes()) {
            lore.add(Text.color(affixLine(inst)));
        }
        return lore;
    }

    /** 生成单条词条描述文本。 */
    private static String affixLine(AffixInstance inst) {
        AffixConfig cfg = EzStrengthen.instance().getAffixConfig(inst.getId());
        if (cfg == null) {
            return "";
        }
        String name = cfg.getName();
        int lv = inst.getLevel();
        double v = cfg.value(lv);
        double ex = cfg.extra(lv);
        String lvColor = switch (lv) {
            case 1 -> "&7";
            case 2 -> "&a";
            case 3 -> "&9";
            case 4 -> "&5";
            default -> "&6";
        };
        String detail = switch (cfg.getAffix()) {
            case TRUE_DAMAGE, LIGHTNING_DAMAGE -> "&8(+&f" + trim(v) + "&8)";
            case PHYSICAL_DAMAGE, RANGED_DAMAGE, MELEE_DAMAGE, CRIT_DAMAGE -> "&8(+&f" + trim(v) + "%&8)";
            case PHYSICAL_DEFENSE, RANGED_DEFENSE, MELEE_DEFENSE -> "&8(-&f" + trim(v) + "%&8)";
            case CRIT_CHANCE, FREEZE_CHANCE, BLIND_CHANCE, DODGE_CHANCE, LEVITATION_CHANCE,
                 LIGHTNING_CHANCE, WEAKNESS_CHANCE, CONFUSION_CHANCE, STUN_CHANCE -> "&8(&f" + trim(v) + "%&8)";
            case BLEED_CHANCE -> "&8(&f" + trim(v) + "%&8, 每秒&f" + trim(ex) + "&8伤害)";
            case REFLECT_CHANCE -> "&8(&f" + trim(v) + "%&8, 反弹&f" + trim(ex) + "%&8)";
            case KNOCKBACK_CHANCE -> "&8(&f" + trim(v) + "%&8, 击退&f" + trim(ex) + "&8倍)";
            case EXECUTE_CHANCE -> "&8(&f" + trim(v) + "%&8, 低于&f" + trim(ex) + "%&8血秒杀)";
        };
        return lvColor + name + " &7Lv." + lv + " " + detail;
    }

    private static String trim(double d) {
        if (d == Math.floor(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }


    /** 若物品有强化数据，则按当前配置重新生成描述（不改动词条与等级）。 */
    public static void refreshLoreIfEnhanced(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        EnhanceData data = getEnhanceData(item);
        if (data.getCount() == 0 && data.getAffixes().isEmpty()) {
            return;
        }
        setEnhanceData(item, data);
    }

    /** 清空强化数据并恢复原始描述（重置装备属性用）。 */
    public static void clearEnhanceData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<String> base = pdc.get(new org.bukkit.NamespacedKey(EzStrengthen.instance(), BASE_LORE_KEY),
                PersistentDataType.LIST.strings());
        pdc.remove(new org.bukkit.NamespacedKey(EzStrengthen.instance(), DATA_KEY));
        pdc.remove(new org.bukkit.NamespacedKey(EzStrengthen.instance(), BASE_LORE_KEY));
        if (base == null || base.isEmpty()) {
            meta.lore(null);
        } else {
            List<Component> lore = new ArrayList<>();
            for (String line : base) {
                lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
    }

    // ---------------- 至纯源石 ----------------

    /** 判断物品是否为至纯源石（由下界之星改来的特殊物品）。 */
    public static boolean isDragonTear(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Integer v = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(EzStrengthen.instance(), TEAR_KEY),
                PersistentDataType.INTEGER);
        return v != null && v == 1;
    }

    /** 创建至纯源石。 */
    public static ItemStack createDragonTear(EzStrengthen plugin, int amount) {
        String materialName = plugin.getConfig().getString("dragon-tear.material", "GLOWSTONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.GLOWSTONE;
        }
        ItemStack tear = new ItemStack(material);
        tear.setAmount(Math.max(1, amount));
        ItemMeta meta = tear.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.color(plugin.getConfig().getString("dragon-tear.name", "&b&l至纯源石")));
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.getConfig().getStringList("dragon-tear.lore")) {
                lore.add(Text.color(line));
            }
            meta.lore(lore);
            int cmd = plugin.getConfig().getInt("dragon-tear.custom-model-data", 0);
            if (cmd > 0) {
                CustomModelDataComponent component = meta.getCustomModelDataComponent();
                component.setFloats(List.of((float) cmd));
                meta.setCustomModelDataComponent(component);
            }
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, TEAR_KEY), PersistentDataType.INTEGER, 1);
            tear.setItemMeta(meta);
        }
        return tear;
    }

    /** 统计玩家背包（含主手/副手/防具）中的至纯源石数量。 */
    public static int countDragonTears(PlayerInventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (isDragonTear(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** 消耗指定数量的至纯源石；数量不足时返回 false 且不消耗。 */
    public static boolean consumeDragonTears(PlayerInventory inventory, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countDragonTears(inventory) < amount) {
            return false;
        }
        int need = amount;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && need > 0; i++) {
            ItemStack item = contents[i];
            if (!isDragonTear(item)) {
                continue;
            }
            int take = Math.min(need, item.getAmount());
            item.setAmount(item.getAmount() - take);
            need -= take;
            if (item.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
        }
        return true;
    }

    /** 获取强化描述中的等级颜色。 */
    public static String levelColor(int level) {
        return switch (level) {
            case 1 -> "&7";
            case 2 -> "&a";
            case 3 -> "&9";
            case 4 -> "&5";
            default -> "&6";
        };
    }
}
