package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class GojoResetCommand implements CommandExecutor {

    private final GojoAwakeningAbility gojoAwakeningAbility;
    private final BlackFlashAbility blackFlashAbility;
    private final ReverseCursedTechniqueAbility reverseCursedTechniqueAbility;
    private final LapseBlueAbility lapseBlueAbility;
    private final ReverseRedAbility reverseRedAbility;
    private final AbilityRestrictionManager restrictionManager;

    public GojoResetCommand(GojoAwakeningAbility gojoAwakeningAbility, BlackFlashAbility blackFlashAbility,
            ReverseCursedTechniqueAbility reverseCursedTechniqueAbility, LapseBlueAbility lapseBlueAbility,
            ReverseRedAbility reverseRedAbility, AbilityRestrictionManager restrictionManager) {
        this.gojoAwakeningAbility = gojoAwakeningAbility;
        this.blackFlashAbility = blackFlashAbility;
        this.reverseCursedTechniqueAbility = reverseCursedTechniqueAbility;
        this.lapseBlueAbility = lapseBlueAbility;
        this.reverseRedAbility = reverseRedAbility;
        this.restrictionManager = restrictionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        resetPlayer(player);
        player.sendMessage(ChatColor.AQUA + "Gojo state fully reset.");
        return true;
    }

    private void resetPlayer(Player player) {
        gojoAwakeningAbility.clearState(player);
        blackFlashAbility.clearState(player);
        reverseCursedTechniqueAbility.clearState(player);
        lapseBlueAbility.clearState(player);
        reverseRedAbility.clearState(player);
        restrictionManager.setFrozenByGojo(player, false);
        removeGojoItems(player.getInventory());
    }

    private void removeGojoItems(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGojoItem(contents[i])) {
                contents[i] = null;
            }
        }
        inventory.setContents(contents);

        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (isGojoItem(armor[i])) {
                armor[i] = null;
            }
        }
        inventory.setArmorContents(armor);

        ItemStack[] extra = inventory.getExtraContents();
        for (int i = 0; i < extra.length; i++) {
            if (isGojoItem(extra[i])) {
                extra[i] = null;
            }
        }
        inventory.setExtraContents(extra);

        ItemStack offHand = inventory.getItemInOffHand();
        if (isGojoItem(offHand)) {
            inventory.setItemInOffHand(null);
        }
    }

    private boolean isGojoItem(ItemStack itemStack) {
        return gojoAwakeningAbility.isAbilityItem(itemStack) || blackFlashAbility.isBlackFlashAxe(itemStack)
                || reverseCursedTechniqueAbility.isAbilityItem(itemStack) || lapseBlueAbility.isAbilityItem(itemStack)
                || reverseRedAbility.isAbilityItem(itemStack);
    }
}
