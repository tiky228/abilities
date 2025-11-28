package com.example.blackflash;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BlackFlashPlugin extends JavaPlugin {

    private BlackFlashAbility blackFlashAbility;
    private NamespacedKey blackFlashAxeKey;

    @Override
    public void onEnable() {
        this.blackFlashAxeKey = new NamespacedKey(this, "blackflash_axe");
        this.blackFlashAbility = new BlackFlashAbility(this, blackFlashAxeKey);
        getServer().getPluginManager().registerEvents(new BlackFlashListener(blackFlashAbility), this);
        registerCommands();
        getLogger().info("Black Flash ability loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Black Flash ability disabled.");
    }

    private void registerCommands() {
        PluginCommand command = getCommand("blackflash");
        if (command != null) {
            command.setExecutor(new BlackFlashCommand(blackFlashAbility));
        } else {
            getLogger().warning("Failed to register /blackflash command.");
        }
    }
}
