package com.example.bankai;

import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

public class BankaiState {
    private final UUID playerId;
    private final double originalMaxHealth;
    private long bankaiEndTimeMillis;
    private BukkitTask particleTask;
    private BukkitTask timerTask;

    public BankaiState(UUID playerId, double originalMaxHealth, long bankaiEndTimeMillis) {
        this.playerId = playerId;
        this.originalMaxHealth = originalMaxHealth;
        this.bankaiEndTimeMillis = bankaiEndTimeMillis;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getOriginalMaxHealth() {
        return originalMaxHealth;
    }

    public long getBankaiEndTimeMillis() {
        return bankaiEndTimeMillis;
    }

    public void setBankaiEndTimeMillis(long bankaiEndTimeMillis) {
        this.bankaiEndTimeMillis = bankaiEndTimeMillis;
    }

    public BukkitTask getParticleTask() {
        return particleTask;
    }

    public void setParticleTask(BukkitTask particleTask) {
        this.particleTask = particleTask;
    }

    public BukkitTask getTimerTask() {
        return timerTask;
    }

    public void setTimerTask(BukkitTask timerTask) {
        this.timerTask = timerTask;
    }
}
