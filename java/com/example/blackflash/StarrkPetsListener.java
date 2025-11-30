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

public class StarrkPetsListener implements Listener {

    private final StarrkPetsAbility petsAbility;

    public StarrkPetsListener(StarrkPetsAbility petsAbility) {
        this.petsAbility = petsAbility;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (petsAbility.isPetsItem(event.getItem())) {
            petsAbility.tryActivatePets(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(damager instanceof LivingEntity livingDamager)) {
            return;
        }
        if (!petsAbility.isStarrkWolf(livingDamager)) {
            return;
        }
        if (victim instanceof Player player && petsAbility.isWolfOwner(livingDamager, player)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof LivingEntity livingVictim && petsAbility.isStarrkWolf(livingVictim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petsAbility.clearPlayer(event.getPlayer());
    }
}
