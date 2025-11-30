package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CeroCommand implements CommandExecutor {

    private final CoyoteStarrkAbility starrkAbility;
    private final CoyoteStarrkAbility.CeroVariant variant;

    public CeroCommand(CoyoteStarrkAbility starrkAbility, CoyoteStarrkAbility.CeroVariant variant) {
        this.starrkAbility = starrkAbility;
        this.variant = variant;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("starrk.cero." + variant.name().toLowerCase())) {
            player.sendMessage(ChatColor.RED + "You lack permission to use this command.");
            return true;
        }
        player.getInventory().addItem(starrkAbility.createCeroItem(variant));
        player.sendMessage(ChatColor.AQUA + "Granted " + variant.getDisplayName() + ChatColor.AQUA + ".");
        return true;
    }
}
