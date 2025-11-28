package com.example.blackflash;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Gives the Bankai activation item.
 */
public class GiveTensaZangetsuCommand implements CommandExecutor {

    private final AdvancedBankaiAbility bankaiAbility;

    public GiveTensaZangetsuCommand(AdvancedBankaiAbility bankaiAbility) {
        this.bankaiAbility = bankaiAbility;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        player.getInventory().addItem(bankaiAbility.createBankaiItem());
        return true;
    }
}
