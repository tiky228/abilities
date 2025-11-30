package com.example.blackflash;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Resets the Bankai state for debugging.
 */
public class BankaiResetDebugCommand implements CommandExecutor {

    private final AdvancedBankaiAbility bankaiAbility;

    public BankaiResetDebugCommand(AdvancedBankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        bankaiAbility.reset(player);
        player.sendMessage("Your Bankai state has been reset.");
        return true;
    }
}
