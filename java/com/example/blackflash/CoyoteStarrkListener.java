package com.example.blackflash;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CoyoteStarrkListener implements Listener {

    private final CoyoteStarrkAbility ability;

    public CoyoteStarrkListener(CoyoteStarrkAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (ability.isPetsItem(event.getItem())) {
            ability.tryActivatePets(player);
            event.setCancelled(true);
            return;
        }
        if (ability.isStormItem(event.getItem())) {
            ability.tryActivateStorm(player);
            event.setCancelled(true);
            return;
        }
        for (CoyoteStarrkAbility.CeroVariant variant : CoyoteStarrkAbility.CeroVariant.values()) {
            if (ability.isCeroItem(event.getItem(), variant)) {
                ability.tryActivateCero(player, variant);
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(damager instanceof LivingEntity livingDamager)) {
            return;
        }
        if (!ability.isStarrkWolf(livingDamager)) {
            return;
        }
        if (victim instanceof Player player && ability.isWolfOwner(livingDamager, player)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof LivingEntity livingVictim && ability.isStarrkWolf(livingVictim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ability.clearPlayer(event.getPlayer());
    }
}
