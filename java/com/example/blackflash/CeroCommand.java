package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CeroCommand implements CommandExecutor {

    private final CeroOscurasAbility ceroAbility;
    private final CeroOscurasAbility.CeroVariant variant;

    public CeroCommand(CeroOscurasAbility ceroAbility, CeroOscurasAbility.CeroVariant variant) {
        this.ceroAbility = ceroAbility;
        this.variant = variant;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        player.getInventory().addItem(ceroAbility.createCeroItem(variant));
        player.sendMessage(ChatColor.GRAY + "Granted " + variant.getDisplayName() + ChatColor.GRAY + " item.");
        return true;
    }
}
