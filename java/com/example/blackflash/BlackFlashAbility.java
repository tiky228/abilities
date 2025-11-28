package com.example.blackflash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class BlackFlashAbility {

    private static final long WINDOW_MILLIS = 5_000L;
    private static final int SUCCESS_COOLDOWN_SECONDS = 15;
    private static final int MISS_COOLDOWN_SECONDS = 60;

    private final BlackFlashPlugin plugin;
    private final CooldownManager cooldownManager;
    private final Map<UUID, Long> activeAttempts = new HashMap<>();

    public BlackFlashAbility(BlackFlashPlugin plugin) {
        this.plugin = plugin;
        this.cooldownManager = new CooldownManager();
    }

    public void handleActivation(Player player) {
        UUID id = player.getUniqueId();
        if (!isHoldingGoldenAxe(player)) {
            player.sendMessage(ChatColor.RED + "You must hold a Golden Axe to unleash Black Flash.");
            return;
        }

        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Black Flash is recharging. " + remaining + "s remaining.");
            return;
        }

        if (hasActiveAttempt(player)) {
            player.sendMessage(ChatColor.YELLOW + "Black Flash is already primed! Strike now.");
            return;
        }

        activateAttempt(player);
    }

    public void handleStrike(Player player, LivingEntity target) {
        if (!isHoldingGoldenAxe(player)) {
            return;
        }

        if (!hasActiveAttempt(player)) {
            return;
        }

        consumeAttempt(player.getUniqueId());
        cooldownManager.setCooldown(player.getUniqueId(), SUCCESS_COOLDOWN_SECONDS);

        applyEffects(player, target);
        spawnParticles(player.getLocation());
        spawnParticles(target.getLocation());

        player.sendMessage(ChatColor.DARK_RED + "You land a devastating Black Flash!");
        if (target instanceof Player otherPlayer) {
            otherPlayer.sendMessage(ChatColor.RED + player.getName() + " struck you with Black Flash!");
        }
    }

    private boolean hasActiveAttempt(Player player) {
        UUID id = player.getUniqueId();
        Long expires = activeAttempts.get(id);
        if (expires == null) {
            return false;
        }
        if (expires < System.currentTimeMillis()) {
            activeAttempts.remove(id);
            return false;
        }
        return true;
    }

    private void activateAttempt(Player player) {
        UUID id = player.getUniqueId();
        activeAttempts.put(id, System.currentTimeMillis() + WINDOW_MILLIS);
        player.sendMessage(ChatColor.GRAY + "You focus cursed energy... Black Flash ready! (5s)");

        new BukkitRunnable() {
            @Override
            public void run() {
                if (hasActiveAttempt(player)) {
                    consumeAttempt(id);
                    cooldownManager.setCooldown(id, MISS_COOLDOWN_SECONDS);
                    player.sendMessage(ChatColor.RED + "Black Flash fizzled. 60s cooldown applied.");
                }
            }
        }.runTaskLater(plugin, WINDOW_MILLIS / 50L);
    }

    private void consumeAttempt(UUID id) {
        activeAttempts.remove(id);
    }

    private boolean isHoldingGoldenAxe(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_AXE;
    }

    private void applyEffects(Player player, LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 5 * 20, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 8 * 20, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 4 * 20, 10, false, true, true));

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, false, true, true));
    }

    private void spawnParticles(Location location) {
        Location loc = location.clone().add(0, 1, 0);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(220, 25, 25), 1.2f);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.1f);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 25, 0.4, 0.4, 0.4, red);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 20, 0.3, 0.3, 0.3, black);
        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 10, 0.4, 0.4, 0.4, 0.01);
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}
