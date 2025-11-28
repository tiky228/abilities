package com.example.bankai;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class BankaiPlugin extends JavaPlugin {
    private BankaiManager bankaiManager;

    @Override
    public void onEnable() {
        this.bankaiManager = new BankaiManager(this);
        registerCommands();
        Bukkit.getPluginManager().registerEvents(new BankaiListener(bankaiManager), this);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(player -> bankaiManager.endBankai(player, true));
    }

    private void registerCommands() {
        PluginCommand giveBankai = getCommand("givebankai");
        if (giveBankai != null) {
            giveBankai.setExecutor(new GiveBankaiCommand(bankaiManager));
        }
        PluginCommand reset = getCommand("bankaireset");
        if (reset != null) {
            reset.setExecutor(new BankaiResetCommand(bankaiManager));
        }
    }
}
