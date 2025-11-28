package com.example.blackflash.util;

import com.example.blackflash.BlackFlashPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class responsible for creating pre-configured custom items used by the plugin.
 */
public class AbilityItems {
    private final NamespacedKey blackFlashKey;
    private final NamespacedKey reverseCursedTechniqueKey;
    private final NamespacedKey hadoNinetyKey;
    private final NamespacedKey bankaiKey;

    public AbilityItems(BlackFlashPlugin plugin) {
        this.blackFlashKey = plugin.getBlackFlashKey();
        this.reverseCursedTechniqueKey = plugin.getReverseCursedTechniqueKey();
        this.hadoNinetyKey = plugin.getHadoNinetyKey();
        this.bankaiKey = plugin.getBankaiKey();
    }

    public ItemStack createBlackFlashAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_RED + "Black Flash Axe");
        meta.setLore(List.of(
                ChatColor.GRAY + "Deliver a sudden burst of cursed energy",
                ChatColor.GRAY + "for bonus damage and crackling lightning."
        ));
        meta.getPersistentDataContainer().set(blackFlashKey, PersistentDataType.INTEGER, 1);
        axe.setItemMeta(meta);
        return axe;
    }

    public ItemStack createReverseCursedTechniqueFocus() {
        ItemStack focus = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = focus.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Reverse Cursed Technique Focus");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right click to rapidly heal,",
                ChatColor.GRAY + "cooling down between uses."
        ));
        meta.getPersistentDataContainer().set(reverseCursedTechniqueKey, PersistentDataType.INTEGER, 1);
        focus.setItemMeta(meta);
        return focus;
    }

    public ItemStack createHadoNinetyCatalyst() {
        ItemStack catalyst = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = catalyst.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "Hado #90: Black Coffin");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Envelop foes in a crushing prison,",
                ChatColor.GRAY + "inflicting wither and slowness."
        ));
        meta.getPersistentDataContainer().set(hadoNinetyKey, PersistentDataType.INTEGER, 1);
        catalyst.setItemMeta(meta);
        return catalyst;
    }

    public ItemStack createBankaiBlade() {
        ItemStack blade = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = blade.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Bankai: Tensa Zangetsu");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right click to enter Bankai.",
                ChatColor.GRAY + "Grants strength and speed for a short time."
        ));
        meta.getPersistentDataContainer().set(bankaiKey, PersistentDataType.INTEGER, 1);
        blade.setItemMeta(meta);
        return blade;
    }

    public NamespacedKey getBlackFlashKey() {
        return blackFlashKey;
    }

    public NamespacedKey getReverseCursedTechniqueKey() {
        return reverseCursedTechniqueKey;
    }

    public NamespacedKey getHadoNinetyKey() {
        return hadoNinetyKey;
    }

    public NamespacedKey getBankaiKey() {
        return bankaiKey;
    }
}
