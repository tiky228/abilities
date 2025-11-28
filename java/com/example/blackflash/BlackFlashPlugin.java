package com.example.blackflash;

import com.example.blackflash.commands.BankaiCommand;
import com.example.blackflash.commands.BankaiResetCommand;
import com.example.blackflash.commands.BlackFlashCommand;
import com.example.blackflash.commands.HadoCommand;
import com.example.blackflash.commands.ReverseCursedTechniqueCommand;
import com.example.blackflash.listener.AbilityListener;
import com.example.blackflash.util.AbilityItems;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entrypoint for the BankaiPlugin. Handles command registration and
 * initialises shared NamespacedKey values for marking the custom items.
 */
public class BlackFlashPlugin extends JavaPlugin {
    private NamespacedKey blackFlashKey;
    private NamespacedKey reverseCursedTechniqueKey;
    private NamespacedKey hadoNinetyKey;
    private NamespacedKey bankaiKey;

    @Override
    public void onEnable() {
        this.blackFlashKey = new NamespacedKey(this, "black_flash_item");
        this.reverseCursedTechniqueKey = new NamespacedKey(this, "reverse_cursed_technique_item");
        this.hadoNinetyKey = new NamespacedKey(this, "hado_ninety_item");
        this.bankaiKey = new NamespacedKey(this, "bankai_item");

        AbilityItems abilityItems = new AbilityItems(this);
        AbilityListener listener = new AbilityListener(this, abilityItems);

        // Register command executors that hand out the ability items.
        getCommand("blackflash").setExecutor(new BlackFlashCommand(abilityItems));
        getCommand("giverct").setExecutor(new ReverseCursedTechniqueCommand(abilityItems));
        getCommand("givehado90").setExecutor(new HadoCommand(abilityItems));
        getCommand("givebankai").setExecutor(new BankaiCommand(abilityItems));
        getCommand("bankaireset").setExecutor(new BankaiResetCommand(listener));

        // Register the shared listener handling interactions with the custom items.
        getServer().getPluginManager().registerEvents(listener, this);

        getLogger().info("BankaiPlugin enabled. Commands registered and listeners active.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BankaiPlugin disabled. See you next time.");
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
