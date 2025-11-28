package com.example.bankai;

import java.util.Arrays;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GiveBankaiCommand implements CommandExecutor {
    private final BankaiPlugin plugin;

    public GiveBankaiCommand(BankaiPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_GRAY + "Tensa Zangetsu");
        meta.setLore(Arrays.asList(ChatColor.GRAY + "Unleash Bankai."));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        NamespacedKey key = plugin.getBankaiItemKey();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.AQUA + "You feel the power of Bankai in your hands.");
        return true;
    }
}
