package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the /blackflash command which grants the player the special Black Flash Axe.
 */
public class BlackFlashCommand implements CommandExecutor {

    private final BlackFlashAbility ability;

    public BlackFlashCommand(BlackFlashAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can receive the Black Flash Axe.");
            return true;
        }

        ItemStack axe = ability.createBlackFlashAxe();
        player.getInventory().addItem(axe);
        player.sendMessage(ChatColor.GOLD + "You feel cursed energy gather around the Black Flash Axe!");
        player.sendMessage(ChatColor.GRAY + "Right-click while holding it to arm Black Flash.");
        return true;
    }
}
