package com.example.blackflash;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public class BankaiListener implements Listener {

    private final BankaiAbility ability;

    public BankaiListener(BankaiAbility ability) {
        this.ability = ability;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) {
            return;
        }

        boolean handled = ability.handleInteract(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand(), hand);
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ability.handleQuit(event.getPlayer());
    }
}
