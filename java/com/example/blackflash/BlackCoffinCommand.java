package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /hado90 command to trigger Black Coffin.
 */
public class BlackCoffinCommand implements CommandExecutor {

    private final BlackCoffinAbility ability;

    public BlackCoffinCommand(BlackCoffinAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can cast Black Coffin.");
            return true;
        }

        ability.tryCast(player);
        return true;
    }
}
