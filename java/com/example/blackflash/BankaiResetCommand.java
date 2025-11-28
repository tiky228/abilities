package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BankaiResetCommand implements CommandExecutor {

    private final BankaiAbility ability;

    public BankaiResetCommand(BankaiAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can reset Bankai.");
            return true;
        }

        ability.reset(player);
        return true;
    }
}
