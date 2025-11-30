package com.example.blackflash;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CeroOscurasListener implements Listener {

    private final CeroOscurasAbility ceroAbility;

    public CeroOscurasListener(CeroOscurasAbility ceroAbility) {
        this.ceroAbility = ceroAbility;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        for (CeroOscurasAbility.CeroVariant variant : CeroOscurasAbility.CeroVariant.values()) {
            if (ceroAbility.isCeroItem(event.getItem(), variant)) {
                ceroAbility.tryActivateCero(event.getPlayer(), variant);
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ceroAbility.clearPlayer(event.getPlayer());
    }
}
