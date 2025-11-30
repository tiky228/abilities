package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GojoAwakeningListener implements Listener {

    private final GojoAwakeningAbility ability;
    private final AbilityRestrictionManager restrictionManager;

    public GojoAwakeningListener(GojoAwakeningAbility ability, AbilityRestrictionManager restrictionManager) {
        this.ability = ability;
        this.restrictionManager = restrictionManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (restrictionManager.isFrozenByGojo(player)) {
            event.setCancelled(true);
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (ability.isAbilityItem(player.getInventory().getItemInMainHand())) {
            if (!ability.canUseAbility(player)) {
                player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
                event.setCancelled(true);
                return;
            }
            ability.tryActivate(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!restrictionManager.isFrozenByGojo(player)) {
            return;
        }
        if (event.getTo() != null && event.getFrom().toVector().distanceSquared(event.getTo().toVector()) > 0.0001) {
            event.setTo(event.getFrom().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (restrictionManager.isFrozenByGojo(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (ability.isAwakening(player)) {
            event.setFoodLevel(20);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restrictionManager.setFrozenByGojo(event.getPlayer(), false);
        ability.clearState(event.getPlayer());
    }
}
