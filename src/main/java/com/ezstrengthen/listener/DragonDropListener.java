package com.ezstrengthen.listener;

import com.ezstrengthen.EzStrengthen;
import com.ezstrengthen.util.ItemUtil;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 末影龙死亡时掉落至纯源石。
 */
public class DragonDropListener implements Listener {

    private final EzStrengthen plugin;

    public DragonDropListener(EzStrengthen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnderDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        int amount = plugin.getConfig().getInt("dragon-tear.drop-amount", 1);
        if (amount <= 0) {
            return;
        }
        ItemStack tear = ItemUtil.createDragonTear(plugin, amount);
        dragon.getWorld().dropItemNaturally(dragon.getLocation(), tear);
    }
}
