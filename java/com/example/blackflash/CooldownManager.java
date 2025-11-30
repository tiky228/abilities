package com.example.blackflash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public boolean isReady(UUID id) {
        return getRemainingMillis(id) <= 0;
    }

    public long getRemainingSeconds(UUID id) {
        long remainingMillis = getRemainingMillis(id);
        if (remainingMillis <= 0) {
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    public void setCooldown(UUID id, int seconds) {
        cooldowns.put(id, System.currentTimeMillis() + (seconds * 1000L));
    }

    public void clear(UUID id) {
        cooldowns.remove(id);
    }

    public void clearAll() {
        cooldowns.clear();
    }

    private long getRemainingMillis(UUID id) {
        Long expires = cooldowns.get(id);
        if (expires == null) {
            return 0;
        }
        return expires - System.currentTimeMillis();
    }
}
