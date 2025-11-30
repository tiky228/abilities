package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles interactions for the advanced Bankai system.
 */
public class AdvancedBankaiListener implements Listener {

    private final AdvancedBankaiAbility bankaiAbility;

    public AdvancedBankaiListener(AdvancedBankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (bankaiAbility.isImmobilized(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        boolean isBankai = bankaiAbility.isBankaiItem(item);
        boolean isReatsu = bankaiAbility.isReatsuItem(item);
        boolean isStand = bankaiAbility.isStandItem(item);
        if (!isBankai && !isReatsu && !isStand) {
            return;
        }
        if (!bankaiAbility.canUseAbility(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        event.setCancelled(true);
        if (isReatsu) {
            bankaiAbility.handleReatsu(event.getPlayer());
        } else if (isStand) {
            bankaiAbility.handleStand(event.getPlayer());
        } else {
            bankaiAbility.handleUse(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bankaiAbility.reset(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        bankaiAbility.reset(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!bankaiAbility.isImmobilized(event.getPlayer())) {
            return;
        }
        if (event.getFrom().distanceSquared(event.getTo()) == 0) {
            return;
        }
        event.setTo(event.getFrom());
    }
}
