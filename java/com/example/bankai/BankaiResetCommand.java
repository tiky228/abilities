package com.example.bankai;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BankaiResetCommand implements CommandExecutor {
    private final BankaiManager manager;

    public BankaiResetCommand(BankaiManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (manager.isBankaiActive(player.getUniqueId())) {
            manager.endBankai(player, false);
        } else {
            player.sendMessage(ChatColor.GRAY + "You are not currently in Bankai.");
        }
        return true;
    }
}
