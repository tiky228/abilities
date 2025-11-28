package com.example.blackflash.commands;

import com.example.blackflash.listener.AbilityListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Allows players to manually dispel their Bankai state via command.
 */
public class BankaiResetCommand implements CommandExecutor {
    private final AbilityListener abilityListener;

    public BankaiResetCommand(AbilityListener abilityListener) {
        this.abilityListener = abilityListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can reset their Bankai.");
            return true;
        }

        abilityListener.resetBankai(player);
        return true;
    }
}
