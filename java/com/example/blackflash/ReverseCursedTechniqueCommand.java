package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReverseCursedTechniqueCommand implements CommandExecutor {

    private final ReverseCursedTechniqueAbility ability;

    public ReverseCursedTechniqueCommand(ReverseCursedTechniqueAbility ability) {
        this.ability = ability;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        ItemStack item = ability.createItem();
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.AQUA + "You feel a soothing cursed core form in your hand.");
        return true;
    }
}
