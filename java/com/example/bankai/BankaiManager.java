package com.example.bankai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class BankaiManager {
    private final BankaiPlugin plugin;
    private final GetsugaAbility getsugaAbility;
    private final Map<UUID, BankaiState> activeBankai = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> pendingTransforms = new HashMap<>();

    public BankaiManager(BankaiPlugin plugin, GetsugaAbility getsugaAbility) {
        this.plugin = plugin;
        this.getsugaAbility = getsugaAbility;
    }

    public boolean isInBankai(Player player) {
        return activeBankai.containsKey(player.getUniqueId());
    }

    public int getRemainingTime(Player player) {
        BankaiState state = activeBankai.get(player.getUniqueId());
        return state == null ? 0 : state.getRemainingSeconds();
    }

    public void startTransformation(Player player) {
        cancelTransformation(player.getUniqueId());

        playAuraPhase(player);
        BukkitTask phase2 = new BukkitRunnable() {
            @Override
            public void run() {
                playBeamPhase(player);
            }
        }.runTaskLater(plugin, 20L);

        BukkitTask phase3 = new BukkitRunnable() {
            @Override
            public void run() {
                endBeamPhase(player);
                activateBankai(player);
            }
        }.runTaskLater(plugin, 40L);

        List<BukkitTask> tasks = new ArrayList<>();
        tasks.add(phase2);
        tasks.add(phase3);
        pendingTransforms.put(player.getUniqueId(), tasks);
    }

    private void playAuraPhase(Player player) {
        Location loc = player.getLocation();
        for (int i = 0; i < 150; i++) {
            Vector offset = Vector.getRandom().multiply(10).subtract(new Vector(5, 0, 5));
            Location pLoc = loc.clone().add(offset.getX(), java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 3, offset.getZ());
            player.getWorld().spawnParticle(Particle.END_ROD, pLoc, 1, 0, 0, 0, 0);
            player.getWorld().spawnParticle(Particle.CLOUD, pLoc, 1, 0.1, 0.1, 0.1, 0);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, pLoc, 1, 0.05, 0.05, 0.05, 0);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, pLoc, 1, 0, 0, 0, 0);
            player.getWorld().spawnParticle(Particle.REDSTONE, pLoc, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 150, 255), 1.5f));
        }
        player.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.5f, 1.0f);
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.2f);
        Bukkit.broadcastMessage(ChatColor.AQUA + "I had enought...");
    }

    private void playBeamPhase(Player player) {
        Location loc = player.getLocation();
        for (int i = 0; i < 80; i++) {
            Location beamLoc = loc.clone().add((Math.random() - 0.5) * 2, Math.random() * 7, (Math.random() - 0.5) * 2);
            player.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 4, 0.2, 0.2, 0.2, 0.01);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, beamLoc, 6, 0.2, 0.2, 0.2, 0);
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, beamLoc, 4, 0.1, 0.1, 0.1, 0);
            player.getWorld().spawnParticle(Particle.REDSTONE, beamLoc, 2, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 200, 255), 1.8f));
        }
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 1.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
        Bukkit.broadcastMessage(ChatColor.AQUA + "BANKAI!");
    }

    private void endBeamPhase(Player player) {
        Bukkit.broadcastMessage(ChatColor.RED + "Tensa Zangetsu.");
    }

    public void activateBankai(Player player) {
        cancelTransformation(player.getUniqueId());
        UUID id = player.getUniqueId();
        if (activeBankai.containsKey(id)) {
            player.sendMessage(ChatColor.AQUA + "Bankai is already active.");
            return;
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double originalMax = maxHealth != null ? maxHealth.getBaseValue() : 20.0;
        if (maxHealth != null) {
            maxHealth.setBaseValue(originalMax + 10);
        }
        player.setHealth(Math.min(player.getHealth() + 10, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
        player.addPotionEffect(GetsugaAbility.createPotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 90, 0));
        player.addPotionEffect(GetsugaAbility.createPotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20 * 90, 0));

        BankaiState state = new BankaiState(player.getUniqueId(), 90, originalMax);
        activeBankai.put(id, state);

        startRedParticles(player, state);
        startTimer(player, state);
    }

    private void startRedParticles(Player player, BankaiState state) {
        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBankai(player, ChatColor.GRAY + "Bankai ended.");
                    return;
                }
                Location base = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = 0.6 + Math.random() * 0.4;
                    Location particleLoc = base.clone().add(Math.cos(angle) * radius, Math.random() * 0.8, Math.sin(angle) * radius);
                    player.getWorld().spawnParticle(Particle.REDSTONE, particleLoc, 1, new Particle.DustOptions(org.bukkit.Color.fromRGB(220, 20, 20), 1.2f));
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
        state.setParticleTask(particleTask);
    }

    private void startTimer(Player player, BankaiState state) {
        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBankai(player, ChatColor.GRAY + "Bankai ended.");
                    return;
                }
                int remaining = state.getRemainingSeconds();
                player.sendActionBar(ChatColor.RED + "Bankai time left: " + remaining + "s");
                state.setRemainingSeconds(remaining - 1);
                if (remaining - 1 < 0) {
                    endBankai(player, ChatColor.GRAY + "Your Bankai has faded.");
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        state.setTimerTask(timerTask);
    }

    public void endBankai(Player player, String message) {
        UUID id = player.getUniqueId();
        BankaiState state = activeBankai.remove(id);
        cancelTransformation(id);
        getsugaAbility.clearCooldown(player.getUniqueId());
        if (state != null) {
            state.cancelTasks();
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(state.getOriginalMaxHealth());
            }
            if (player.getHealth() > player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()) {
                player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            }
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        }
        if (message != null) {
            player.sendMessage(message);
        }
    }

    public void resetBankai(Player player) {
        endBankai(player, ChatColor.GRAY + "Your Bankai has been reset.");
    }

    public void resetAll() {
        new ArrayList<>(activeBankai.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                endBankai(player, ChatColor.GRAY + "Server stopping, Bankai removed.");
            }
        });
        pendingTransforms.values().forEach(list -> list.forEach(BukkitTask::cancel));
        pendingTransforms.clear();
    }

    public boolean subtractTime(Player player, int seconds) {
        BankaiState state = activeBankai.get(player.getUniqueId());
        if (state == null) return false;
        int newTime = state.getRemainingSeconds() - seconds;
        state.setRemainingSeconds(Math.max(newTime, 0));
        if (newTime < 0) {
            endBankai(player, ChatColor.GRAY + "Your Bankai time ran out.");
            return false;
        }
        return true;
    }

    private void cancelTransformation(UUID uuid) {
        List<BukkitTask> tasks = pendingTransforms.remove(uuid);
        if (tasks != null) {
            tasks.forEach(BukkitTask::cancel);
        }
    }
}
