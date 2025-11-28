package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BankaiResetCommand implements CommandExecutor {

    private final BankaiAbility bankaiAbility;

    public BankaiResetCommand(BankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (bankaiAbility.resetBankai(player)) {
            player.sendMessage(ChatColor.YELLOW + "Your Bankai has been reset.");
        } else {
            player.sendMessage(ChatColor.GRAY + "You are not currently in Bankai.");
        }
        return true;
    }
}
