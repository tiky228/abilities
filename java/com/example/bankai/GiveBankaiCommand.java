package com.example.bankai;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveBankaiCommand implements CommandExecutor {
    private final BankaiManager manager;

    public GiveBankaiCommand(BankaiManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        ItemStack item = manager.createBankaiItem();
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.AQUA + "You received Tensa Zangetsu.");
        return true;
    }
}
