package com.example.blackflash;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class ReverseCursedTechniqueListener implements Listener {

    private final ReverseCursedTechniqueAbility ability;

    public ReverseCursedTechniqueListener(ReverseCursedTechniqueAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!ability.canUseAbility(event.getPlayer())) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (ability.isAbilityItem(item)) {
            ability.tryActivate(event.getPlayer());
            event.setCancelled(true);
            return;
        }

        if (ability.isChanneling(event.getPlayer().getUniqueId()) || ability.isPenalized(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID id = player.getUniqueId();
        if (!ability.isChanneling(id)) {
            return;
        }

        ability.interrupt(player);
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!ability.canUseAbility(player)) {
            event.setCancelled(true);
            return;
        }

        UUID id = player.getUniqueId();
        if (ability.isChanneling(id) || ability.isPenalized(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!ability.isChanneling(id) && !ability.isPenalized(id)) {
            return;
        }

        if (event.getTo() != null && event.getFrom().toVector().distanceSquared(event.getTo().toVector()) > 0.0001) {
            event.setTo(event.getFrom().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ability.clearState(event.getPlayer());
    }
}
