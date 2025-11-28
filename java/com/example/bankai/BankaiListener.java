package com.example.bankai;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BankaiListener implements Listener {
    private final BankaiPlugin plugin;
    private final BankaiManager bankaiManager;
    private final GetsugaAbility getsugaAbility;

    public BankaiListener(BankaiPlugin plugin, BankaiManager bankaiManager, GetsugaAbility getsugaAbility) {
        this.plugin = plugin;
        this.bankaiManager = bankaiManager;
        this.getsugaAbility = getsugaAbility;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        NamespacedKey key = plugin.getBankaiItemKey();
        PersistentDataContainer container = item.getItemMeta() != null ? item.getItemMeta().getPersistentDataContainer() : null;
        if (container == null || !container.has(key, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        if (bankaiManager.isInBankai(player)) {
            getsugaAbility.castGetsuga(player, bankaiManager);
        } else {
            bankaiManager.startTransformation(player);
        }
    }
}
