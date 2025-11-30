package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StarrkPetsCommand implements CommandExecutor {

    private final StarrkPetsAbility petsAbility;

    public StarrkPetsCommand(StarrkPetsAbility petsAbility) {
        this.petsAbility = petsAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        player.getInventory().addItem(petsAbility.createPetsItem());
        player.sendMessage(ChatColor.AQUA + "Starrk Pets item granted.");
        return true;
    }
}
