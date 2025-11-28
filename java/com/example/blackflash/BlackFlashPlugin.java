package com.example.blackflash;

import org.bukkit.plugin.java.JavaPlugin;

public class BlackFlashPlugin extends JavaPlugin {

    private BlackFlashAbility blackFlashAbility;

    @Override
    public void onEnable() {
        this.blackFlashAbility = new BlackFlashAbility(this);
        getServer().getPluginManager().registerEvents(new BlackFlashListener(blackFlashAbility), this);
        getLogger().info("Black Flash ability loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Black Flash ability disabled.");
    }
}
