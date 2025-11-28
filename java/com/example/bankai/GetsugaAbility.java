package com.example.bankai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GetsugaAbility {
    private final BankaiPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final int STANCE_DELAY = 12;
    private static final int RANGE_STEPS = 30; // 15 blocks / 0.5 step

    public GetsugaAbility(BankaiPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(UUID uuid) {
        Long ready = cooldowns.get(uuid);
        return ready != null && ready > System.currentTimeMillis();
    }

    public long getCooldownRemaining(UUID uuid) {
        Long ready = cooldowns.get(uuid);
        if (ready == null) return 0;
        long diff = ready - System.currentTimeMillis();
        return Math.max(diff, 0);
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }

    public void castGetsuga(Player player, BankaiManager bankaiManager) {
        UUID uuid = player.getUniqueId();
        if (isOnCooldown(uuid)) {
            long seconds = (long) Math.ceil(getCooldownRemaining(uuid) / 1000.0);
            player.sendMessage("Getsuga is on cooldown for " + seconds + "s.");
            return;
        }

        int remaining = bankaiManager.getRemainingTime(player);
        if (remaining < 10) {
            player.sendMessage("Not enough Bankai time to cast Getsuga!");
            return;
        }

        cooldowns.put(uuid, System.currentTimeMillis() + 15_000);
        bankaiManager.subtractTime(player, 10);

        player.addPotionEffect(createPotionEffect(PotionEffectType.SLOW, STANCE_DELAY + 5, 6));
        Location base = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < 20; i++) {
            Vector offset = Vector.getRandom().multiply(0.5);
            base.getWorld().spawnParticle(Particle.SMOKE_LARGE, base.clone().add(offset), 1, 0, 0, 0, 0);
            base.getWorld().spawnParticle(Particle.REDSTONE, base.clone().add(offset), 0, new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.5f));
        }
        base.getWorld().playSound(base, Sound.ENTITY_WITHER_AMBIENT, 1.2f, 0.6f);

        new BukkitRunnable() {
            @Override
            public void run() {
                fireWave(player);
            }
        }.runTaskLater(plugin, STANCE_DELAY);
    }

    private void fireWave(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize().multiply(0.5);
        player.getWorld().playSound(start, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 0.7f);

        new BukkitRunnable() {
            int step = 0;
            Location current = start.clone();

            @Override
            public void run() {
                if (step > RANGE_STEPS) {
                    cancel();
                    return;
                }
                current.add(direction);
                if (current.getBlock().getType().isSolid()) {
                    cancel();
                    return;
                }

                spawnWaveParticles(current);
                damageTargets(player, current);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnWaveParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 8, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.2f));
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 4, 0.2, 0.2, 0.2, 0, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.4f));
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 2, 0.1, 0.1, 0.1, 0);
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.1, 0.1, 0.1, 0.01);
    }

    private void damageTargets(Player caster, Location point) {
        double radius = 1.2;
        for (Entity entity : point.getWorld().getNearbyEntities(point, radius, radius, radius, e -> e instanceof LivingEntity && e != caster)) {
            LivingEntity target = (LivingEntity) entity;
            target.damage(15.0, caster);
            Vector knockback = target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(1.4).setY(0.4);
            target.setVelocity(knockback);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
        }
    }

    public static PotionEffect createPotionEffect(PotionEffectType type, int duration, int amplifier) {
        return new PotionEffect(type, duration, amplifier, true, false, true);
    }
}
