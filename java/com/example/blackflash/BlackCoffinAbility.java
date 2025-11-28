package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/**
 * Handles the logic for casting Hado #90: Black Coffin.
 */
public class BlackCoffinAbility {

    private static final double EFFECT_RADIUS = 5.0;
    private static final int COOLDOWN_SECONDS = 30;
    private static final double DAMAGE_AMOUNT = 10.0;
    private static final double COFFIN_HALF_SIZE = 2.5;
    private static final int COFFIN_HEIGHT = 8;
    private static final int CONSTRUCTION_INTERVAL_TICKS = 4;
    private static final int SHATTER_DELAY_TICKS = 10;

    private final BlackFlashPlugin plugin;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Integer> freezeCounts = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    public BlackCoffinAbility(BlackFlashPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(UUID id) {
        return frozenPlayers.contains(id);
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        frozenPlayers.remove(id);
        freezeCounts.remove(id);
    }

    public void clearAll() {
        for (UUID id : new HashSet<>(frozenPlayers)) {
            freezeCounts.remove(id);
        }
        frozenPlayers.clear();
        for (BukkitTask task : new ArrayList<>(runningTasks)) {
            task.cancel();
        }
        runningTasks.clear();
    }

    public void tryCast(Player caster) {
        UUID casterId = caster.getUniqueId();
        if (!cooldownManager.isReady(casterId)) {
            long remaining = cooldownManager.getRemainingSeconds(casterId);
            caster.sendMessage(ChatColor.YELLOW + "Black Coffin is on cooldown for " + remaining + "s.");
            return;
        }

        cooldownManager.setCooldown(casterId, COOLDOWN_SECONDS);
        caster.sendMessage(ChatColor.DARK_PURPLE + "You unleash Hado #90: Black Coffin!");
        caster.playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.7f);
        spawnAura(caster);

        List<Player> targets = findTargets(caster);
        freezeTargets(targets);

        startConstruction(caster, targets);
    }

    private List<Player> findTargets(Player caster) {
        List<Player> targets = new ArrayList<>();
        Location center = caster.getLocation();
        for (Player player : caster.getWorld().getPlayers()) {
            if (player.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (player.getLocation().distance(center) <= EFFECT_RADIUS) {
                targets.add(player);
            }
        }
        return targets;
    }

    private void freezeTargets(List<Player> targets) {
        for (Player target : targets) {
            UUID id = target.getUniqueId();
            freezeCounts.put(id, freezeCounts.getOrDefault(id, 0) + 1);
            frozenPlayers.add(id);
            target.sendMessage(ChatColor.DARK_GRAY + "You are trapped in a Black Coffin!");
        }
    }

    private void unfreezeTargets(List<Player> targets) {
        for (Player target : targets) {
            UUID id = target.getUniqueId();
            Integer count = freezeCounts.get(id);
            if (count == null) {
                continue;
            }
            if (count <= 1) {
                freezeCounts.remove(id);
                frozenPlayers.remove(id);
            } else {
                freezeCounts.put(id, count - 1);
            }
            if (target.isOnline()) {
                target.sendMessage(ChatColor.GRAY + "The coffin shatters, releasing you.");
            }
        }
    }

    private void startConstruction(Player caster, List<Player> targets) {
        Location center = caster.getLocation().clone();
        World world = caster.getWorld();
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(126, 35, 182), 1.3f);
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(25, 15, 25), 1.4f);

        BukkitTask[] handle = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            private int layer = 0;
            private int holdTicks = 0;

            @Override
            public void run() {
                if (layer < COFFIN_HEIGHT) {
                    spawnCoffinLayers(world, center, layer + 1, purpleDust, darkDust);
                    if (layer % 2 == 0) {
                        world.playSound(center, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 0.5f);
                    }
                    layer++;
                    return;
                }

                if (holdTicks < SHATTER_DELAY_TICKS) {
                    spawnCoffinLayers(world, center, COFFIN_HEIGHT, purpleDust, darkDust);
                    holdTicks += CONSTRUCTION_INTERVAL_TICKS;
                    return;
                }

                cancel();
                runningTasks.remove(handle[0]);
                shatter(caster, center, targets);
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, CONSTRUCTION_INTERVAL_TICKS);
        handle[0] = task;
        runningTasks.add(task);
    }

    private void spawnAura(Player caster) {
        Location loc = caster.getLocation().add(0, 1, 0);
        caster.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 50, 0.8, 0.6, 0.8, 0.01);
        caster.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 0.8, 0.5, 0.8, 0.02);
    }

    private void spawnBoxLayer(World world, Location center, double y, Particle.DustOptions purple, Particle.DustOptions dark) {
        double radius = COFFIN_HALF_SIZE;
        double step = 0.5;
        for (double x = -radius; x <= radius; x += step) {
            Location loc1 = new Location(world, center.getX() + x, y, center.getZ() - radius);
            Location loc2 = new Location(world, center.getX() + x, y, center.getZ() + radius);
            world.spawnParticle(Particle.REDSTONE, loc1, 2, 0, 0, 0, 0, dark);
            world.spawnParticle(Particle.REDSTONE, loc2, 2, 0, 0, 0, 0, purple);
        }
        for (double z = -radius; z <= radius; z += step) {
            Location loc1 = new Location(world, center.getX() - radius, y, center.getZ() + z);
            Location loc2 = new Location(world, center.getX() + radius, y, center.getZ() + z);
            world.spawnParticle(Particle.REDSTONE, loc1, 2, 0, 0, 0, 0, purple);
            world.spawnParticle(Particle.REDSTONE, loc2, 2, 0, 0, 0, 0, dark);
        }
        world.spawnParticle(Particle.SMOKE_LARGE, center.clone().add(0, y - center.getY(), 0), 14, radius, 0.1, radius, 0.02);
        world.spawnParticle(Particle.PORTAL, center.clone().add(0, y - center.getY(), 0), 6, 0.2, 0.1, 0.2, 0.02);
    }

    private void spawnCoffinLayers(World world, Location center, int layers, Particle.DustOptions purple,
            Particle.DustOptions dark) {
        for (int i = 0; i < layers; i++) {
            double y = center.getY() + i;
            spawnBoxLayer(world, center, y, purple, dark);
        }
    }

    private void shatter(Player caster, Location center, List<Player> targets) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.9f);
        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 0.7f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 6, 0.6, 0.6, 0.6, 0.02);
        world.spawnParticle(Particle.ASH, center, 120, COFFIN_HALF_SIZE, 1.2, COFFIN_HALF_SIZE, 0.02);
        world.spawnParticle(Particle.SPELL_WITCH, center, 100, COFFIN_HALF_SIZE, 1.2, COFFIN_HALF_SIZE, 0.02);

        BoundingBox box = BoundingBox.of(
                new Location(world, center.getX() - COFFIN_HALF_SIZE, center.getY(), center.getZ() - COFFIN_HALF_SIZE),
                new Location(world, center.getX() + COFFIN_HALF_SIZE, center.getY() + COFFIN_HEIGHT,
                        center.getZ() + COFFIN_HALF_SIZE));
        for (Entity entity : world.getNearbyEntities(box)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (!box.contains(living.getLocation().toVector())) {
                continue;
            }
            living.damage(DAMAGE_AMOUNT, caster);
            if (living instanceof Player player) {
                player.sendMessage(ChatColor.DARK_PURPLE + "The coffin shatters, crushing you in darkness!");
            }
        }

        unfreezeTargets(targets);
    }
}
