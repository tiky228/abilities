package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiveBankaiCommand implements CommandExecutor {

    private final BankaiAbility bankaiAbility;

    public GiveBankaiCommand(BankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players may use this command.");
            return true;
        }
        bankaiAbility.giveBankaiItem(player);
        return true;
    }
}
