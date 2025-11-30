package com.example.blackflash;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class BlackFlashListener implements Listener {

    private final BlackFlashAbility ability;

    public BlackFlashListener(BlackFlashAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!ability.canUseAbility(event.getPlayer())) {
            return;
        }

        if (event.getItem() != null) {
            ability.handleActivation(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (!ability.canUseAbility(player)) {
            event.setCancelled(true);
            return;
        }

        if (!ability.isBlackFlashAxe(player.getInventory().getItemInMainHand())) {
            return;
        }

        ability.handleStrike(player, target);
    }
}
