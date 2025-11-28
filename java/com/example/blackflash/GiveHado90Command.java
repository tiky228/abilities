package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveHado90Command implements CommandExecutor {

    private final BlackCoffinAbility ability;

    public GiveHado90Command(BlackCoffinAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can receive this incantation.");
            return true;
        }

        ItemStack item = ability.createItem();
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.DARK_PURPLE + "You receive the catalyst for Hado #90: Black Coffin.");
        return true;
    }
}
