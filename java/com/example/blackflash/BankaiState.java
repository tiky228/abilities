package com.example.blackflash;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

public class BankaiState {
    private final UUID playerId;
    private final double originalMaxHealth;
    private final long startTime;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BankaiState(UUID playerId, double originalMaxHealth, long startTime) {
        this.playerId = playerId;
        this.originalMaxHealth = originalMaxHealth;
        this.startTime = startTime;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getOriginalMaxHealth() {
        return originalMaxHealth;
    }

    public long getStartTime() {
        return startTime;
    }

    public void addTask(BukkitTask task) {
        if (task != null) {
            tasks.add(task);
        }
    }

    public List<BukkitTask> getTasks() {
        return tasks;
    }
}
