package com.example.blackflash;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BankaiListener implements Listener {

    private final BankaiAbility bankaiAbility;

    public BankaiListener(BankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!bankaiAbility.isBankaiItem(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true);
        bankaiAbility.handleItemUse(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bankaiAbility.endBankai(event.getPlayer());
    }
}
