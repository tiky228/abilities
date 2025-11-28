package com.example.bankai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.scheduler.BukkitTask;

public class BankaiState {
    private final UUID playerId;
    private int remainingSeconds;
    private final double originalMaxHealth;
    private BukkitTask timerTask;
    private BukkitTask particleTask;

    public BankaiState(UUID playerId, int remainingSeconds, double originalMaxHealth) {
        this.playerId = playerId;
        this.remainingSeconds = remainingSeconds;
        this.originalMaxHealth = originalMaxHealth;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public double getOriginalMaxHealth() {
        return originalMaxHealth;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }

    public BukkitTask getParticleTask() {
        return particleTask;
    }

    public void setParticleTask(BukkitTask particleTask) {
        this.particleTask = particleTask;
    }

    public void cancelTasks() {
        List<BukkitTask> tasks = new ArrayList<>();
        if (timerTask != null) tasks.add(timerTask);
        if (particleTask != null) tasks.add(particleTask);
        tasks.forEach(BukkitTask::cancel);
    }
}
