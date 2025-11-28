package com.example.blackflash.commands;

import com.example.blackflash.util.AbilityItems;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Provides the catalyst for casting Hado #90: Black Coffin.
 */
public class HadoCommand implements CommandExecutor {
    private final AbilityItems abilityItems;

    public HadoCommand(AbilityItems abilityItems) {
        this.abilityItems = abilityItems;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can wield this arcane catalyst.");
            return true;
        }

        player.getInventory().addItem(abilityItems.createHadoNinetyCatalyst());
        player.sendMessage(ChatColor.DARK_PURPLE + "You grasp a fragment of Black Coffin's power.");
        return true;
    }
}
