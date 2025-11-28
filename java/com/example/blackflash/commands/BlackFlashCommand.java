package com.example.blackflash.commands;

import com.example.blackflash.util.AbilityItems;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Gives the executing player the Black Flash axe for empowered melee strikes.
 */
public class BlackFlashCommand implements CommandExecutor {
    private final AbilityItems abilityItems;

    public BlackFlashCommand(AbilityItems abilityItems) {
        this.abilityItems = abilityItems;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can receive the Black Flash axe.");
            return true;
        }

        player.getInventory().addItem(abilityItems.createBlackFlashAxe());
        player.sendMessage(ChatColor.DARK_RED + "Harness the Black Flash with your new axe!");
        return true;
    }
}
