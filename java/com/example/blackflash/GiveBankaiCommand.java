package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveBankaiCommand implements CommandExecutor {

    private final BankaiAbility ability;

    public GiveBankaiCommand(BankaiAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can receive this weapon.");
            return true;
        }

        ItemStack sword = ability.createItem();
        player.getInventory().addItem(sword);
        player.sendMessage(ChatColor.AQUA + "You feel Bankai power within Tensa Zangetsu.");
        return true;
    }
}
