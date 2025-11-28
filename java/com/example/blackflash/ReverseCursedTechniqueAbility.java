package com.example.blackflash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ReverseCursedTechniqueAbility {

    private static final int COOLDOWN_SECONDS = 60;
    private static final int PENALTY_SECONDS = 5;
    private static final int PARTICLE_INTERVAL_TICKS = 10;
    private static final int HEALTH_CHECK_TICKS = 10;

    private final BlackFlashPlugin plugin;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, ChannelState> activeChannels = new HashMap<>();
    private final Map<UUID, Long> penaltyUntil = new HashMap<>();

    public ReverseCursedTechniqueAbility(BlackFlashPlugin plugin) {
        this.plugin = plugin;
    }

    public void tryActivate(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            return;
        }

        if (activeChannels.containsKey(id)) {
            player.sendMessage(ChatColor.YELLOW + "Reverse Cursed Technique is already active.");
            return;
        }

        if (isPenalized(id)) {
            long remaining = getPenaltyRemainingSeconds(id);
            player.sendMessage(ChatColor.RED + "You are staggered for another " + remaining + "s.");
            return;
        }

        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Reverse Cursed Technique is recharging. " + remaining + "s remaining.");
            return;
        }

        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        startChannel(player, id);
    }

    private void startChannel(Player player, UUID id) {
        applyImmobility(player, Integer.MAX_VALUE);
        applyRegeneration(player);

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        player.sendMessage(ChatColor.AQUA + "Reverse Cursed Technique activated! You are regenerating.");

        BukkitTask particleTask = startParticleTask(player, id);
        BukkitTask healthTask = startHealthMonitor(player, id);
        activeChannels.put(id, new ChannelState(particleTask, healthTask));
    }

    private void applyImmobility(Player player, int durationTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 255, false, false, false));
    }

    private void applyRegeneration(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 3, false, false, true));
    }

    private BukkitTask startParticleTask(Player player, UUID id) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeChannels.containsKey(id)) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.SOUL, loc, 12, 0.6, 0.4, 0.6, 0.02);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 4, 0.3, 0.2, 0.3, 0.01);
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_INTERVAL_TICKS);
    }

    private BukkitTask startHealthMonitor(Player player, UUID id) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endChannel(player, false, false);
                    cancel();
                    return;
                }

                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                if (player.getHealth() >= maxHealth) {
                    endChannel(player, false, true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, HEALTH_CHECK_TICKS, HEALTH_CHECK_TICKS);
    }

    public void interrupt(Player player) {
        UUID id = player.getUniqueId();
        if (!activeChannels.containsKey(id)) {
            return;
        }

        endChannel(player, true, false);
        applyPenalty(player, id);
    }

    public void endChannel(Player player, boolean interrupted, boolean healed) {
        UUID id = player.getUniqueId();
        ChannelState state = activeChannels.remove(id);
        if (state != null) {
            state.cancel();
        }

        player.removePotionEffect(PotionEffectType.REGENERATION);
        if (!isPenalized(id)) {
            removeChannelImmobility(player);
        }

        if (interrupted) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            player.sendMessage(ChatColor.RED + "Your Reverse Cursed Technique was interrupted! You are staggered.");
        } else if (healed) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.3f);
            player.sendMessage(ChatColor.GREEN + "Your wounds are fully healed.");
        }
    }

    private void removeChannelImmobility(Player player) {
        PotionEffect slowness = player.getPotionEffect(PotionEffectType.SLOW);
        if (slowness != null && slowness.getAmplifier() >= 10) {
            player.removePotionEffect(PotionEffectType.SLOW);
        }
    }

    private void applyPenalty(Player player, UUID id) {
        long expiry = System.currentTimeMillis() + (PENALTY_SECONDS * 1000L);
        penaltyUntil.put(id, expiry);
        applyImmobility(player, PENALTY_SECONDS * 20 + 10);

        new BukkitRunnable() {
            @Override
            public void run() {
                penaltyUntil.remove(id);
                player.removePotionEffect(PotionEffectType.SLOW);
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GRAY + "You regain your footing.");
                }
            }
        }.runTaskLater(plugin, PENALTY_SECONDS * 20L);
    }

    public boolean isChanneling(UUID id) {
        return activeChannels.containsKey(id);
    }

    public boolean isPenalized(UUID id) {
        Long until = penaltyUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public long getPenaltyRemainingSeconds(UUID id) {
        Long until = penaltyUntil.get(id);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            penaltyUntil.remove(id);
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    public boolean isOnCooldown(UUID id) {
        return !cooldownManager.isReady(id);
    }

    public long getCooldownRemainingSeconds(UUID id) {
        return cooldownManager.getRemainingSeconds(id);
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        ChannelState state = activeChannels.remove(id);
        if (state != null) {
            state.cancel();
        }
        penaltyUntil.remove(id);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    private static class ChannelState {
        private final BukkitTask particleTask;
        private final BukkitTask healthTask;

        private ChannelState(BukkitTask particleTask, BukkitTask healthTask) {
            this.particleTask = particleTask;
            this.healthTask = healthTask;
        }

        private void cancel() {
            particleTask.cancel();
            healthTask.cancel();
        }
    }
}
