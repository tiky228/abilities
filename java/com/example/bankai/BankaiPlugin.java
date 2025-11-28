package com.example.bankai;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class BankaiPlugin extends JavaPlugin {
    private BankaiManager bankaiManager;
    private GetsugaAbility getsugaAbility;
    private NamespacedKey bankaiItemKey;

    @Override
    public void onEnable() {
        this.bankaiItemKey = new NamespacedKey(this, "bankai_item");
        this.getsugaAbility = new GetsugaAbility(this);
        this.bankaiManager = new BankaiManager(this, getsugaAbility);

        getServer().getPluginManager().registerEvents(new BankaiListener(this, bankaiManager, getsugaAbility), this);
        getCommand("givebankai").setExecutor(new GiveBankaiCommand(this));
        getCommand("bankaireset").setExecutor(new BankaiResetCommand(bankaiManager));
    }

    @Override
    public void onDisable() {
        bankaiManager.resetAll();
    }

    public NamespacedKey getBankaiItemKey() {
        return bankaiItemKey;
    }

    public BankaiManager getBankaiManager() {
        return bankaiManager;
    }
}
