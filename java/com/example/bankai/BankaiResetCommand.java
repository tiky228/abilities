package com.example.bankai;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BankaiResetCommand implements CommandExecutor {
    private final BankaiManager bankaiManager;

    public BankaiResetCommand(BankaiManager bankaiManager) {
        this.bankaiManager = bankaiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (bankaiManager.isInBankai(player)) {
            bankaiManager.resetBankai(player);
        } else {
            player.sendMessage("You are not currently in Bankai.");
        }
        return true;
    }
}
