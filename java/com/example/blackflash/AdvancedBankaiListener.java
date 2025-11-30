package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!bankaiAbility.isBankaiItem(event.getItem())) {
            return;
        }
        if (!bankaiAbility.canUseAbility(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        event.setCancelled(true);
        bankaiAbility.handleUse(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bankaiAbility.reset(event.getPlayer());
    }
}
