package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Command to grant the special Black Flash Axe to a player. */
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
        player.sendMessage(ChatColor.DARK_RED + "You received the Black Flash Axe!");
        return true;
    }
}
