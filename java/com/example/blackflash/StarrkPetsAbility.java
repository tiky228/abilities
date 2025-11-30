package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class StarrkPetsAbility {

    private static final int PET_COOLDOWN_SECONDS = 20;
    private static final int PET_MIN_COUNT = 2;
    private static final int PET_MAX_COUNT = 4;
    private static final int PET_LIFETIME_TICKS = 240;
    private static final double PET_TARGET_RANGE = 18.0;
    private static final double PET_EXPLOSION_RADIUS = 3.5;
    private static final double PET_EXPLOSION_DAMAGE = 9.0;

    private final BlackFlashPlugin plugin;
    private final AbilityRestrictionManager restrictionManager;
    private final NamespacedKey petsItemKey;
    private final NamespacedKey wolfKey;
    private final CooldownManager petCooldown = new CooldownManager();
    private final Map<UUID, List<UUID>> activeWolves = new HashMap<>();
    private final Map<UUID, BukkitTask> wolfTasks = new HashMap<>();

    public StarrkPetsAbility(BlackFlashPlugin plugin, AbilityRestrictionManager restrictionManager,
            NamespacedKey petsItemKey, NamespacedKey wolfKey) {
        this.plugin = plugin;
        this.restrictionManager = restrictionManager;
        this.petsItemKey = petsItemKey;
        this.wolfKey = wolfKey;
    }

    public ItemStack createPetsItem() {
        ItemStack stack = new ItemStack(Material.LIGHT_BLUE_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Starrk Pets");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Summon spiritual wolves that chase foes.",
                    ChatColor.BLUE + "Right-click to unleash the pack."));
            meta.getPersistentDataContainer().set(petsItemKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isPetsItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(petsItemKey, PersistentDataType.INTEGER);
    }

    public void tryActivatePets(Player player) {
        UUID id = player.getUniqueId();
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!petCooldown.isReady(id)) {
            player.sendMessage(ChatColor.YELLOW + "Starrk Pets cooling down for " + petCooldown.getRemainingSeconds(id)
                    + "s.");
            return;
        }
        petCooldown.setCooldown(id, PET_COOLDOWN_SECONDS);
        summonWolves(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.2f, 1.1f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.25f);
        player.sendMessage(ChatColor.AQUA + "The wolves sprint toward your enemies!");
    }

    private void summonWolves(Player player) {
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = PET_MIN_COUNT + random.nextInt(PET_MAX_COUNT - PET_MIN_COUNT + 1);
        List<UUID> wolves = activeWolves.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        for (int i = 0; i < count; i++) {
            Location spawn = player.getLocation().clone().add(random.nextDouble(-1.5, 1.5), 0.0,
                    random.nextDouble(-1.5, 1.5));
            Wolf wolf = world.spawn(spawn, Wolf.class, summoned -> {
                summoned.setAdult();
                summoned.setTamed(true);
                summoned.setOwner(player);
                summoned.setCustomName(ChatColor.AQUA + "Starrk Wolf");
                summoned.setCustomNameVisible(false);
                summoned.setAI(true);
                summoned.setAngry(true);
                summoned.getPersistentDataContainer().set(wolfKey, PersistentDataType.STRING,
                        player.getUniqueId().toString());
            });
            wolves.add(wolf.getUniqueId());
            runWolfLogic(player, wolf);
        }
    }

    private void runWolfLogic(Player owner, Wolf wolf) {
        BukkitTask task = new BukkitRunnable() {
            int life = 0;

            @Override
            public void run() {
                if (wolf.isDead() || !wolf.isValid() || life >= PET_LIFETIME_TICKS) {
                    cleanupWolf(owner.getUniqueId(), wolf.getUniqueId());
                    cancel();
                    return;
                }
                life++;
                spawnWolfParticles(wolf.getLocation());
                LivingEntity target = findWolfTarget(owner, wolf);
                if (target != null) {
                    wolf.setTarget(target);
                    if (wolf.getLocation().distanceSquared(target.getLocation()) < 2.0) {
                        explodeWolf(owner, wolf, target.getLocation());
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        wolfTasks.put(wolf.getUniqueId(), task);
    }

    private LivingEntity findWolfTarget(Player owner, Wolf wolf) {
        World world = wolf.getWorld();
        LivingEntity closest = null;
        double closestDistance = PET_TARGET_RANGE * PET_TARGET_RANGE;
        for (LivingEntity entity : world.getNearbyLivingEntities(wolf.getLocation(), PET_TARGET_RANGE)) {
            if (entity.equals(owner) || entity.equals(wolf)) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(wolf.getLocation());
            if (distance < closestDistance) {
                closest = entity;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void explodeWolf(Player owner, Wolf wolf, Location center) {
        World world = center.getWorld();
        cleanupWolf(owner.getUniqueId(), wolf.getUniqueId());
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.5f);
        world.spawnParticle(Particle.CLOUD, center, 60, 1.0, 0.7, 1.0, 0.05);
        world.spawnParticle(Particle.REDSTONE, center, 120, 1.2, 0.9, 1.2, dust);
        world.spawnParticle(Particle.END_ROD, center, 40, 0.6, 0.5, 0.6, 0.02);

        double radiusSq = PET_EXPLOSION_RADIUS * PET_EXPLOSION_RADIUS;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(owner)) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            entity.damage(PET_EXPLOSION_DAMAGE, owner);
        }
    }

    private void spawnWolfParticles(Location location) {
        World world = location.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(90, 210, 255), 1.0f);
        world.spawnParticle(Particle.REDSTONE, location, 8, 0.3, 0.4, 0.3, dust);
        world.spawnParticle(Particle.CRIT_MAGIC, location.clone().add(0, 0.5, 0), 5, 0.25, 0.25, 0.25, 0.02);
    }

    public void clearAll() {
        for (List<UUID> wolves : activeWolves.values()) {
            for (UUID wolfId : wolves) {
                Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                if (wolf != null) {
                    wolf.remove();
                }
                BukkitTask task = wolfTasks.remove(wolfId);
                if (task != null) {
                    task.cancel();
                }
            }
        }
        activeWolves.clear();
        petCooldown.clearAll();
    }

    public void clearPlayer(Player player) {
        UUID ownerId = player.getUniqueId();
        List<UUID> wolves = activeWolves.remove(ownerId);
        if (wolves != null) {
            for (UUID wolfId : wolves) {
                Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                if (wolf != null) {
                    wolf.remove();
                }
                BukkitTask task = wolfTasks.remove(wolfId);
                if (task != null) {
                    task.cancel();
                }
            }
        }
        petCooldown.clear(ownerId);
    }

    private void cleanupWolf(UUID ownerId, UUID wolfId) {
        Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
        if (wolf != null) {
            wolf.remove();
        }
        BukkitTask task = wolfTasks.remove(wolfId);
        if (task != null) {
            task.cancel();
        }
        List<UUID> wolves = activeWolves.get(ownerId);
        if (wolves != null) {
            wolves.remove(wolfId);
            if (wolves.isEmpty()) {
                activeWolves.remove(ownerId);
            }
        }
    }

    public boolean isStarrkWolf(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING);
    }

    public boolean isWolfOwner(LivingEntity wolf, Player player) {
        PersistentDataContainer container = wolf.getPersistentDataContainer();
        if (!container.has(wolfKey, PersistentDataType.STRING)) {
            return false;
        }
        String id = container.get(wolfKey, PersistentDataType.STRING);
        return id != null && id.equals(player.getUniqueId().toString());
    }
}
