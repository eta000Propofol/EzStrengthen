package com.ezstrengthen.service;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.model.AffixConfig;
import com.ezstrengthen.model.AffixInstance;
import com.ezstrengthen.model.EnhanceData;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Random;

/**
 * 强化服务：费用计算、词条随机、强化/重置/修复执行。
 */
public class EnhanceService {

    /** 操作结果。 */
    public enum EnhanceResult {
        SUCCESS, FAIL, MAX_LEVEL, NO_ECONOMY, NOT_ENOUGH_MONEY, NOT_ENOUGH_TEARS, NO_ITEM,
        TOO_MANY_ITEMS, RESET_SUCCESS, REPAIR_SUCCESS, NO_ENHANCEMENT, NOT_DAMAGED, NOT_REPAIRABLE
    }

    private final EzStrengthen plugin;
    private final Random random;

    public EnhanceService(EzStrengthen plugin) {
        this(plugin, new Random());
    }

    /** 包私有：测试注入固定随机源，使成功率判定与词条/等级抽取可确定化。 */
    EnhanceService(EzStrengthen plugin, Random random) {
        this.plugin = plugin;
        this.random = random;
    }

    /** 第 level 次（1 起）强化所需的货币；超过配置长度时沿用最后一档，绝不返回 0（免费）。 */
    public double getCost(int level) {
        List<Double> costs = plugin.getConfig().getDoubleList("economy.costs");
        if (costs.isEmpty()) {
            return 0;
        }
        return costs.get(Math.min(Math.max(level, 1), costs.size()) - 1);
    }

    /** 重置装备属性的费用。 */
    public double getResetCost() {
        return plugin.getConfig().getDouble("economy.reset-cost", 5000);
    }

    /** 修复装备耐久的费用。 */
    public double getRepairCost() {
        return plugin.getConfig().getDouble("economy.repair-cost", 1000);
    }

    /** 第 level 次（1 起）强化的成功率（0~1）；超过配置长度时沿用最后一档。正常流程中最大等级已钳制到列表长度，空列表分支不可达。 */
    public double getSuccessRate(int level) {
        List<Double> rates = plugin.getConfig().getDoubleList("enhance.success-rates");
        if (rates.isEmpty()) {
            return 1;
        }
        return Math.max(0, Math.min(1, rates.get(Math.min(Math.max(level, 1), rates.size()) - 1) / 100.0));
    }

    /** 第 level 次（1 起）强化需要消耗的至纯源石数量。 */
    public int getRequiredTears(int level) {
        List<Integer> levels = plugin.getConfig().getIntegerList("dragon-tear.required-for-levels");
        if (levels.contains(level)) {
            return Math.max(1, plugin.getConfig().getInt("dragon-tear.required-amount", 1));
        }
        return 0;
    }

    /** 随机选择一个词条（全部启用的词条等概率）。 */
    public AffixInstance rollAffix() {
        List<AffixConfig> enabled = plugin.getEnabledAffixes();
        if (enabled.isEmpty()) {
            return null;
        }
        AffixConfig cfg = enabled.get(random.nextInt(enabled.size()));
        return new AffixInstance(cfg.getAffix().getId(), rollLevel());
    }

    /** 按权重随机词条等级 1~5。 */
    private int rollLevel() {
        List<Double> weights = plugin.getConfig().getDoubleList("enhance.affix-level-chances");
        if (weights.isEmpty()) {
            return 1 + random.nextInt(5);
        }
        double total = 0;
        for (double w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return 1 + random.nextInt(5);
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < weights.size(); i++) {
            r -= Math.max(0, weights.get(i));
            if (r <= 0) {
                return Math.min(5, i + 1);
            }
        }
        return 5;
    }

    /**
     * 执行一次强化。
     * 仅接受单件物品：数量 > 1 时拒绝（GUI 已前置校验，此处纵深防御，
     * 防止未来调用方把整组物品一次性写入强化数据）。
     * 成功：增加词条并写入物品；失败：材料已消耗，物品不变。
     */
    public EnhanceResult attempt(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return EnhanceResult.NO_ITEM;
        }
        if (item.getAmount() > 1) {
            return EnhanceResult.TOO_MANY_ITEMS;
        }
        if (plugin.getEnabledAffixes().isEmpty()) {
            return EnhanceResult.FAIL;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            return EnhanceResult.NO_ECONOMY;
        }
        EnhanceData data = ItemUtil.getEnhanceData(item);
        int nextLevel = data.getCount() + 1;
        if (nextLevel > plugin.getMaxLevel()) {
            return EnhanceResult.MAX_LEVEL;
        }
        double cost = getCost(nextLevel);
        int tears = getRequiredTears(nextLevel);

        // 校验并扣除材料
        if (!plugin.getEconomyService().has(player, cost)) {
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        if (tears > 0 && ItemUtil.countDragonTears(player.getInventory()) < tears) {
            return EnhanceResult.NOT_ENOUGH_TEARS;
        }
        if (!plugin.getEconomyService().withdraw(player, cost)) {
            // has() 通过但扣款失败（经济实现异常或余额被并发变动）：中止强化，材料不消耗
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        if (tears > 0) {
            ItemUtil.consumeDragonTears(player.getInventory(), tears);
        }

        // 成功率判定
        if (random.nextDouble() > getSuccessRate(nextLevel)) {
            return EnhanceResult.FAIL;
        }

        // 成功：随机词条 + 随机等级
        AffixInstance affix = rollAffix();
        if (affix == null) {
            return EnhanceResult.FAIL;
        }
        data.getAffixes().add(affix);
        data.setCount(nextLevel);
        ItemUtil.setEnhanceData(item, data);
        return EnhanceResult.SUCCESS;
    }

    /**
     * 重置装备属性：清空所有强化词条，恢复原始描述。
     * 仅接受单件物品：数量 > 1 时拒绝（GUI 已前置校验，此处纵深防御）。
     * 需要消耗货币（economy.reset-cost，默认 5000）。
     */
    public EnhanceResult reset(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return EnhanceResult.NO_ITEM;
        }
        if (item.getAmount() > 1) {
            return EnhanceResult.TOO_MANY_ITEMS;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            return EnhanceResult.NO_ECONOMY;
        }
        EnhanceData data = ItemUtil.getEnhanceData(item);
        if (data.getCount() == 0 && data.getAffixes().isEmpty()) {
            return EnhanceResult.NO_ENHANCEMENT;
        }
        double cost = getResetCost();
        if (!plugin.getEconomyService().has(player, cost)) {
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        if (!plugin.getEconomyService().withdraw(player, cost)) {
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        ItemUtil.clearEnhanceData(item);
        return EnhanceResult.RESET_SUCCESS;
    }

    /**
     * 修复装备耐久：把物品的损耗清零。
     * 仅接受单件物品：数量 > 1 时拒绝（GUI 已前置校验，此处纵深防御）。
     * 需要消耗货币（economy.repair-cost，默认 5000）。
     */
    public EnhanceResult repair(Player player, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return EnhanceResult.NO_ITEM;
        }
        if (item.getAmount() > 1) {
            return EnhanceResult.TOO_MANY_ITEMS;
        }
        if (!plugin.getEconomyService().isAvailable()) {
            return EnhanceResult.NO_ECONOMY;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return EnhanceResult.NOT_REPAIRABLE;
        }
        if (!damageable.hasDamage()) {
            return EnhanceResult.NOT_DAMAGED;
        }
        double cost = getRepairCost();
        if (!plugin.getEconomyService().has(player, cost)) {
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        if (!plugin.getEconomyService().withdraw(player, cost)) {
            return EnhanceResult.NOT_ENOUGH_MONEY;
        }
        damageable.resetDamage();
        item.setItemMeta(damageable);
        return EnhanceResult.REPAIR_SUCCESS;
    }
}