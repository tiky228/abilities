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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

public class BlackCoffinAbility {

    private static final double EFFECT_RADIUS = 5.0;
    private static final int COOLDOWN_SECONDS = 30;
    private static final double DAMAGE_AMOUNT = 20.0;
    private static final double COFFIN_HALF_SIZE = 5.0; // 10x10 square
    private static final int COFFIN_HEIGHT = 12;
    private static final int CONSTRUCTION_INTERVAL_TICKS = 2;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Integer> freezeCounts = new HashMap<>();
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    public BlackCoffinAbility(BlackFlashPlugin plugin, NamespacedKey itemKey) {
        this.plugin = plugin;
        this.itemKey = itemKey;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.END_CRYSTAL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "Hado #90: Black Coffin");
            meta.setLore(java.util.List.of(ChatColor.GRAY + "Compress space into a crushing prison.",
                    ChatColor.DARK_PURPLE + "Right-click to cast."));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAbilityItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.INTEGER);
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
        frozenPlayers.clear();
        freezeCounts.clear();
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
        playInitialAura(caster);

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

    private void playInitialAura(Player caster) {
        Location loc = caster.getLocation().add(0, 1, 0);
        caster.getWorld().spawnParticle(Particle.SPELL_WITCH, loc, 150, 1.6, 1.0, 1.6, 0.05);
        caster.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 120, 1.6, 1.0, 1.6, 0.03);
        caster.getWorld().spawnParticle(Particle.PORTAL, loc, 100, 1.6, 1.0, 1.6, 0.2);
        caster.playSound(loc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.4f, 0.5f);
        caster.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 0.7f);
    }

    private void startConstruction(Player caster, List<Player> targets) {
        Location center = caster.getLocation().clone();
        World world = caster.getWorld();
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.fromRGB(138, 43, 226), 1.6f);
        Particle.DustOptions darkDust = new Particle.DustOptions(Color.fromRGB(15, 5, 25), 1.6f);

        BukkitTask[] handle = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            private int layer = 0;
            private int holdTicks = 0;

            @Override
            public void run() {
                if (layer < COFFIN_HEIGHT) {
                    layer++;
                    drawCoffin(world, center, layer, purpleDust, darkDust);
                    return;
                }

                if (holdTicks < 2) {
                    holdTicks++;
                    drawCoffin(world, center, COFFIN_HEIGHT, purpleDust, darkDust);
                    return;
                }

                cancel();
                if (handle[0] != null) {
                    runningTasks.remove(handle[0]);
                }
                shatter(caster, center, targets);
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, CONSTRUCTION_INTERVAL_TICKS);
        handle[0] = task;
        runningTasks.add(task);
    }

    private void drawCoffin(World world, Location center, int currentHeight, Particle.DustOptions purple,
            Particle.DustOptions dark) {
        for (int i = 0; i < currentHeight; i++) {
            double y = center.getY() + i;
            spawnLayer(world, center, y, purple, dark);
        }
    }

    private void spawnLayer(World world, Location center, double y, Particle.DustOptions purple,
            Particle.DustOptions dark) {
        double step = 0.25;
        double radius = COFFIN_HALF_SIZE;
        for (double x = -radius; x <= radius; x += step) {
            Location north = new Location(world, center.getX() + x, y, center.getZ() - radius);
            Location south = new Location(world, center.getX() + x, y, center.getZ() + radius);
            world.spawnParticle(Particle.REDSTONE, north, 6, 0, 0, 0, 0, purple);
            world.spawnParticle(Particle.REDSTONE, south, 6, 0, 0, 0, 0, dark);
            world.spawnParticle(Particle.ASH, north, 3, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.ASH, south, 3, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.SPELL_WITCH, north, 2, 0, 0, 0, 0.01);
            world.spawnParticle(Particle.SPELL_WITCH, south, 2, 0, 0, 0, 0.01);
        }
        for (double z = -radius; z <= radius; z += step) {
            Location west = new Location(world, center.getX() - radius, y, center.getZ() + z);
            Location east = new Location(world, center.getX() + radius, y, center.getZ() + z);
            world.spawnParticle(Particle.REDSTONE, west, 6, 0, 0, 0, 0, dark);
            world.spawnParticle(Particle.REDSTONE, east, 6, 0, 0, 0, 0, purple);
            world.spawnParticle(Particle.SMOKE_LARGE, west, 4, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.SMOKE_LARGE, east, 4, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.SPELL_WITCH, west, 2, 0, 0, 0, 0.01);
            world.spawnParticle(Particle.SPELL_WITCH, east, 2, 0, 0, 0, 0.01);
        }

        world.spawnParticle(Particle.SMOKE_LARGE, center.clone().add(0, y - center.getY(), 0), 60, radius, 0.1,
                radius, 0.04);
        world.spawnParticle(Particle.ASH, center.clone().add(0, y - center.getY(), 0), 30, radius, 0.1, radius,
                0.02);
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, y - center.getY(), 0), 40,
                radius * 0.1, 0.1, radius * 0.1, 0.02);
    }

    private void shatter(Player caster, Location center, List<Player> targets) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.9f);
        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 0.8f, 0.7f);

        world.spawnParticle(Particle.EXPLOSION_HUGE, center, 1);
        world.spawnParticle(Particle.SMOKE_LARGE, center, 350, COFFIN_HALF_SIZE, 1.6, COFFIN_HALF_SIZE, 0.05);
        world.spawnParticle(Particle.ASH, center, 300, COFFIN_HALF_SIZE, 1.6, COFFIN_HALF_SIZE, 0.05);
        world.spawnParticle(Particle.SPELL_WITCH, center, 300, COFFIN_HALF_SIZE, 1.6, COFFIN_HALF_SIZE, 0.08);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 220, COFFIN_HALF_SIZE, 1.6, COFFIN_HALF_SIZE, 0.05);

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
            PotionEffectType darkness = PotionEffectType.DARKNESS;
            if (darkness != null) {
                living.addPotionEffect(new PotionEffect(darkness, 180, 0, false, true, true));
            }
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 180, 0, false, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0, false, true, true));
        }

        unfreezeTargets(targets);
    }
}
