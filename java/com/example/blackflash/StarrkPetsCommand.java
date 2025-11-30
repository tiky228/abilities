package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StarrkPetsCommand implements CommandExecutor {

    private final CoyoteStarrkAbility starrkAbility;

    public StarrkPetsCommand(CoyoteStarrkAbility starrkAbility) {
        this.starrkAbility = starrkAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("starrk.pets")) {
            player.sendMessage(ChatColor.RED + "You lack permission to use this command.");
            return true;
        }
        player.getInventory().addItem(starrkAbility.createPetsItem());
        player.sendMessage(ChatColor.AQUA + "Granted Starrk Pets item.");
        return true;
    }
}
