package com.example.blackflash;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

public class AbilityRestrictionManager {

    private final Set<UUID> gojoFrozenPlayers = new HashSet<>();
    private final Set<UUID> timeStoppedPlayers = new HashSet<>();

    public boolean canUseAbility(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        return !timeStoppedPlayers.contains(id) && !gojoFrozenPlayers.contains(id);
    }

    public void setFrozenByGojo(Player player, boolean frozen) {
        UUID id = player.getUniqueId();
        if (frozen) {
            gojoFrozenPlayers.add(id);
        } else {
            gojoFrozenPlayers.remove(id);
        }
    }

    public boolean isFrozenByGojo(Player player) {
        return gojoFrozenPlayers.contains(player.getUniqueId());
    }

    public void setTimeStopped(Player player, boolean stopped) {
        UUID id = player.getUniqueId();
        if (stopped) {
            timeStoppedPlayers.add(id);
        } else {
            timeStoppedPlayers.remove(id);
        }
    }

    public boolean isTimeStopped(Player player) {
        return timeStoppedPlayers.contains(player.getUniqueId());
    }

    public void clearAll() {
        gojoFrozenPlayers.clear();
        timeStoppedPlayers.clear();
    }
}
