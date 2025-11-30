package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiveStarrkCommand implements CommandExecutor {

    private final CoyoteStarrkAbility starrkAbility;

    public GiveStarrkCommand(CoyoteStarrkAbility starrkAbility) {
        this.starrkAbility = starrkAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        player.getInventory().addItem(starrkAbility.createPetsItem());
        for (CoyoteStarrkAbility.CeroVariant variant : CoyoteStarrkAbility.CeroVariant.values()) {
            player.getInventory().addItem(starrkAbility.createCeroItem(variant));
        }
        player.sendMessage(ChatColor.AQUA + "Granted Starrk abilities.");
        return true;
    }
}
