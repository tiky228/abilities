package com.example.blackflash.commands;

import com.example.blackflash.util.AbilityItems;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Supplies the player with the Reverse Cursed Technique focus item for healing.
 */
public class ReverseCursedTechniqueCommand implements CommandExecutor {
    private final AbilityItems abilityItems;

    public ReverseCursedTechniqueCommand(AbilityItems abilityItems) {
        this.abilityItems = abilityItems;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can channel the Reverse Cursed Technique.");
            return true;
        }

        player.getInventory().addItem(abilityItems.createReverseCursedTechniqueFocus());
        player.sendMessage(ChatColor.AQUA + "A calming focus resonates with restorative energy.");
        return true;
    }
}
