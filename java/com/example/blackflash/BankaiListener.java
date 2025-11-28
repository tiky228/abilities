package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

public class BankaiListener implements Listener {

    private final BankaiAbility bankaiAbility;

    public BankaiListener(BankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!bankaiAbility.isBankaiItemInteraction(event.getPlayer(), event.getHand(), item)) {
            return;
        }
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        event.setCancelled(true);
        boolean started = bankaiAbility.startBankai(event.getPlayer());
        if (started) {
            event.getPlayer().sendMessage(ChatColor.AQUA + "Your power surges as Bankai awakens.");
        }
    }
}
