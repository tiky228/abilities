package com.example.blackflash;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class BlackFlashPlugin extends JavaPlugin {

    private BlackFlashAbility blackFlashAbility;

    @Override
    public void onEnable() {
        NamespacedKey axeKey = new NamespacedKey(this, "blackflash_axe");
        this.blackFlashAbility = new BlackFlashAbility(this, axeKey);

        getServer().getPluginManager().registerEvents(new BlackFlashListener(blackFlashAbility), this);
        if (getCommand("blackflash") != null) {
            getCommand("blackflash").setExecutor(new BlackFlashCommand(blackFlashAbility));
        } else {
            getLogger().warning("blackflash command is not defined in plugin.yml");
        }

        getLogger().info("Black Flash ability loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Black Flash ability disabled.");
    }
}
