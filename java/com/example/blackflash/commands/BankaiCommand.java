package com.example.blackflash.commands;

import com.example.blackflash.util.AbilityItems;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Hands the player the Bankai blade used to toggle the transformation.
 */
public class BankaiCommand implements CommandExecutor {
    private final AbilityItems abilityItems;

    public BankaiCommand(AbilityItems abilityItems) {
        this.abilityItems = abilityItems;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can wield the Bankai blade.");
            return true;
        }

        player.getInventory().addItem(abilityItems.createBankaiBlade());
        player.sendMessage(ChatColor.RED + "Tensa Zangetsu hums in your grasp.");
        return true;
    }
}
