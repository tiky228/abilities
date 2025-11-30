package com.example.blackflash;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BlackFlashPlugin extends JavaPlugin {

    private BlackFlashAbility blackFlashAbility;
    private ReverseCursedTechniqueAbility reverseCursedTechniqueAbility;
    private BlackCoffinAbility blackCoffinAbility;
    private AdvancedBankaiAbility bankaiAbility;
    private GojoAwakeningAbility gojoAwakeningAbility;
    private AbilityRestrictionManager abilityRestrictionManager;
    private NamespacedKey blackFlashAxeKey;
    private NamespacedKey reverseTechniqueItemKey;
    private NamespacedKey hadoItemKey;
    private NamespacedKey bankaiItemKey;
    private NamespacedKey gojoAwakeningItemKey;

    @Override
    public void onEnable() {
        this.abilityRestrictionManager = new AbilityRestrictionManager();
        this.blackFlashAxeKey = new NamespacedKey(this, "blackflash_axe");
        this.reverseTechniqueItemKey = new NamespacedKey(this, "rct_item");
        this.hadoItemKey = new NamespacedKey(this, "hado_item");
        this.bankaiItemKey = new NamespacedKey(this, "bankai_item");
        this.gojoAwakeningItemKey = new NamespacedKey(this, "gojo_awakening_item");

        this.blackFlashAbility = new BlackFlashAbility(this, blackFlashAxeKey, abilityRestrictionManager);
        this.reverseCursedTechniqueAbility = new ReverseCursedTechniqueAbility(this, reverseTechniqueItemKey,
                abilityRestrictionManager);
        this.blackCoffinAbility = new BlackCoffinAbility(this, hadoItemKey, abilityRestrictionManager);
        this.bankaiAbility = new AdvancedBankaiAbility(this, bankaiItemKey, abilityRestrictionManager);
        this.gojoAwakeningAbility = new GojoAwakeningAbility(this, gojoAwakeningItemKey, abilityRestrictionManager);
        getServer().getPluginManager().registerEvents(new BlackFlashListener(blackFlashAbility), this);
        getServer().getPluginManager()
                .registerEvents(new ReverseCursedTechniqueListener(reverseCursedTechniqueAbility), this);
        getServer().getPluginManager().registerEvents(new BlackCoffinListener(blackCoffinAbility), this);
        getServer().getPluginManager().registerEvents(new AdvancedBankaiListener(bankaiAbility), this);
        getServer().getPluginManager()
                .registerEvents(new GojoAwakeningListener(gojoAwakeningAbility, abilityRestrictionManager), this);
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
        if (bankaiAbility != null) {
            bankaiAbility.clearAll();
        }
        if (gojoAwakeningAbility != null) {
            gojoAwakeningAbility.clearAll();
        }
        if (abilityRestrictionManager != null) {
            abilityRestrictionManager.clearAll();
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

        PluginCommand bankaiCommand = getCommand("givebankai");
        if (bankaiCommand != null) {
            bankaiCommand.setExecutor(new GiveTensaZangetsuCommand(bankaiAbility));
        } else {
            getLogger().warning("Failed to register /givebankai command.");
        }

        PluginCommand bankaiResetCommand = getCommand("bankaireset");
        if (bankaiResetCommand != null) {
            bankaiResetCommand.setExecutor(new BankaiResetDebugCommand(bankaiAbility));
        } else {
            getLogger().warning("Failed to register /bankaireset command.");
        }

        PluginCommand gojoCommand = getCommand("gojoawakening");
        if (gojoCommand != null) {
            gojoCommand.setExecutor(new GojoAwakeningCommand(gojoAwakeningAbility));
        } else {
            getLogger().warning("Failed to register /gojoawakening command.");
        }
    }

    public AbilityRestrictionManager getAbilityRestrictionManager() {
        return abilityRestrictionManager;
    }
}
