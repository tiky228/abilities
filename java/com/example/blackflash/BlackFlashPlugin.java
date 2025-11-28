package com.example.blackflash;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BlackFlashPlugin extends JavaPlugin {

    private BlackFlashAbility blackFlashAbility;
    private ReverseCursedTechniqueAbility reverseCursedTechniqueAbility;
    private BlackCoffinAbility blackCoffinAbility;
    private NamespacedKey blackFlashAxeKey;
    private NamespacedKey reverseTechniqueItemKey;
    private NamespacedKey hadoItemKey;

    @Override
    public void onEnable() {
        this.blackFlashAxeKey = new NamespacedKey(this, "blackflash_axe");
        this.reverseTechniqueItemKey = new NamespacedKey(this, "rct_item");
        this.hadoItemKey = new NamespacedKey(this, "hado_item");

        this.blackFlashAbility = new BlackFlashAbility(this, blackFlashAxeKey);
        this.reverseCursedTechniqueAbility = new ReverseCursedTechniqueAbility(this, reverseTechniqueItemKey);
        this.blackCoffinAbility = new BlackCoffinAbility(this, hadoItemKey);
        getServer().getPluginManager().registerEvents(new BlackFlashListener(blackFlashAbility), this);
        getServer().getPluginManager().registerEvents(new ReverseCursedTechniqueListener(reverseCursedTechniqueAbility), this);
        getServer().getPluginManager().registerEvents(new BlackCoffinListener(blackCoffinAbility), this);
        registerCommands();
        getLogger().info("Black Flash and Reverse Cursed Technique abilities loaded.");
    }

    @Override
    public void onDisable() {
        if (reverseCursedTechniqueAbility != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                reverseCursedTechniqueAbility.clearState(player);
            }
        }
        if (blackCoffinAbility != null) {
            blackCoffinAbility.clearAll();
        }
        getLogger().info("Black Flash abilities disabled.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("blackflash");
        if (command != null) {
            command.setExecutor(new BlackFlashCommand(blackFlashAbility));
        } else {
            getLogger().warning("Failed to register /blackflash command.");
        }

        PluginCommand rctCommand = getCommand("giverct");
        if (rctCommand != null) {
            rctCommand.setExecutor(new ReverseCursedTechniqueCommand(reverseCursedTechniqueAbility));
        } else {
            getLogger().warning("Failed to register /giverct command.");
        }

        PluginCommand hadoCommand = getCommand("givehado90");
        if (hadoCommand != null) {
            hadoCommand.setExecutor(new GiveHado90Command(blackCoffinAbility));
        } else {
            getLogger().warning("Failed to register /givehado90 command.");
        }
    }
}
