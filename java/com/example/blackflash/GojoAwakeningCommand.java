package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GojoAwakeningCommand implements CommandExecutor {

    private final GojoAwakeningAbility ability;

    public GojoAwakeningCommand(GojoAwakeningAbility ability) {
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
        player.sendMessage(ChatColor.AQUA + "A fragment of limitless energy forms in your hand.");
        return true;
    }
}
