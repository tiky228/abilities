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

    @Override
    public void onEnable() {
        this.blackFlashAxeKey = new NamespacedKey(this, "blackflash_axe");
        this.blackFlashAbility = new BlackFlashAbility(this, blackFlashAxeKey);
        this.reverseCursedTechniqueAbility = new ReverseCursedTechniqueAbility(this);
        this.blackCoffinAbility = new BlackCoffinAbility(this);
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

        PluginCommand coffinCommand = getCommand("hado90");
        if (coffinCommand != null) {
            coffinCommand.setExecutor(new BlackCoffinCommand(blackCoffinAbility));
        } else {
            getLogger().warning("Failed to register /hado90 command.");
        }
    }
}
