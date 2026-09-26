package com.ezstrengthen.listener;

import com.ezstrengthen.EzStrengthen;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 战斗事件监听：把词条效果应用到近战/远程/荆棘伤害中。
 */
public class CombatListener implements Listener {

    private final EzStrengthen plugin;

    public CombatListener(EzStrengthen plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (plugin.getCombatService().isInternalDamage(victim)) {
            return;
        }
        DamageCause cause = event.getCause();
        LivingEntity attacker = null;
        boolean ranged = false;

        if (cause == DamageCause.THORNS) {
            // 荆棘/守卫者反伤：伤害来源是穿着荆棘装备的实体，其词条同样生效
            if (!(event.getDamager() instanceof LivingEntity wearer)) {
                return;
            }
            attacker = wearer;
        } else if (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            attacker = resolvePlayerAttacker(event.getDamager());
            if (attacker == null) {
                return;
            }
        } else if (cause == DamageCause.PROJECTILE) {
            attacker = resolvePlayerAttacker(event.getDamager());
            if (attacker == null) {
                return;
            }
            ranged = true;
        } else {
            return;
        }

        plugin.getCombatService().handleAttack(event, attacker, victim, ranged);
    }

    /** 解析攻击者玩家：直接玩家，或箭/雪球等投射物的射出者。 */
    private Player resolvePlayerAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.getCombatService().cleanup(entity);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCombatService().cleanup(event.getPlayer());
    }
}
