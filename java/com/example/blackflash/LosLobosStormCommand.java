package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LosLobosStormCommand implements CommandExecutor {

    private final CoyoteStarrkAbility starrkAbility;

    public LosLobosStormCommand(CoyoteStarrkAbility starrkAbility) {
        this.starrkAbility = starrkAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("starrk.storm")) {
            player.sendMessage(ChatColor.RED + "You lack permission to use this command.");
            return true;
        }
        player.getInventory().addItem(starrkAbility.createStormItem());
        player.sendMessage(ChatColor.AQUA + "Granted Los Lobos Storm item.");
        return true;
    }
}
