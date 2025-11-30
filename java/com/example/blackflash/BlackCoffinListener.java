package com.example.blackflash;

import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for events to enforce the Black Coffin freeze effect.
 */
public class BlackCoffinListener implements Listener {

    private final BlackCoffinAbility ability;

    public BlackCoffinListener(BlackCoffinAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!ability.isFrozen(player.getUniqueId())) {
            return;
        }
        if (event.getTo() != null && event.getFrom().toVector().distanceSquared(event.getTo().toVector()) > 0.0001) {
            event.setTo(event.getFrom().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (ability.isFrozen(player.getUniqueId())) {
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR
                    || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (ability.isAbilityItem(player.getInventory().getItemInMainHand())) {
                if (!ability.canUseAbility(player)) {
                    player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
                    event.setCancelled(true);
                    return;
                }
                ability.tryCast(player);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        UUID id = player.getUniqueId();
        if (ability.isFrozen(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ability.clearState(event.getPlayer());
    }
}
