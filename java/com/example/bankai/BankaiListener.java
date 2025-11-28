package com.example.bankai;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class BankaiListener implements Listener {
    private final BankaiManager manager;

    public BankaiListener(BankaiManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (manager.isBankaiItem(item)) {
            event.setCancelled(true);
            manager.startBankai(player);
            return;
        }
        if (manager.isBankaiActive(player.getUniqueId())) {
            manager.useGetsuga(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }
}
