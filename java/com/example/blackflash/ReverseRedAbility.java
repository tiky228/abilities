package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class ReverseRedAbility {

    private static final int COOLDOWN_SECONDS = 20;
    private static final int CHARGE_TICKS = 20;
    private static final int MAX_TRAVEL_TICKS = 22;
    private static final double STEP_DISTANCE = 4.8;
    private static final double HIT_RADIUS = 1.25;
    private static final double DAMAGE = 14.0;
    private static final double KNOCKBACK = 1.6;
    private static final double HOMING_RANGE = 20.0;
    private static final double HOMING_STEP_DISTANCE = 9.4;
    private static final int HOMING_MAX_TICKS = 35;
    private static final double HOMING_DAMAGE = 18.0;
    private static final double HOMING_KNOCKBACK = 2.0;
    private static final double ENHANCED_PULL_STRENGTH = 1.4;
    private static final double ENHANCED_PULL_VERTICAL_LIMIT = 0.9;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final LapseBlueAbility lapseBlueAbility;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, List<BukkitTask>> activeTasks = new HashMap<>();

    public ReverseRedAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager, GojoAwakeningAbility awakeningAbility,
            LapseBlueAbility lapseBlueAbility) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
        this.awakeningAbility = awakeningAbility;
        this.lapseBlueAbility = lapseBlueAbility;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.REDSTONE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Reverse Red Maximum Output");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Overwhelm foes with concentrated repulsion.");
            lore.add(ChatColor.DARK_RED + "Right-click to unleash Reverse Red.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isAbilityItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.INTEGER);
    }

    public void tryActivate(Player player) {
        UUID id = player.getUniqueId();
        if (!awakeningAbility.isAwakening(player)) {
            player.sendMessage(ChatColor.RED + "Reverse Red is only accessible during Gojo's Awakening.");
            return;
        }
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Reverse Red is recharging for " + remaining + "s.");
            return;
        }

        boolean enhanced = awakeningAbility.hasAbilityPoint(player);
        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        Vector direction = player.getLocation().getDirection().normalize();
        Location spawnLocation = player.getEyeLocation().add(direction.clone().multiply(1.4));
        beginCharge(player, spawnLocation, direction, enhanced);
    }

    private void beginCharge(Player player, Location origin, Vector direction, boolean enhanced) {
        UUID id = player.getUniqueId();
        BukkitTask[] handle = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                spawnSphere(origin, 0.45f);
                if (++ticks >= CHARGE_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                    launchProjectile(player, origin.clone(), direction.clone(), enhanced);
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.1f);
    }

    private void launchProjectile(Player player, Location current, Vector direction, boolean enhanced) {
        UUID id = player.getUniqueId();
        BukkitTask[] handle = new BukkitTask[1];
        Vector[] adjustedDirection = { direction.normalize() };
        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;
            boolean hitTarget = false;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                Vector facing = player.getLocation().getDirection();
                if (facing.lengthSquared() > 1.0e-4) {
                    adjustedDirection[0] = facing.normalize();
                }
                current.add(adjustedDirection[0].clone().multiply(STEP_DISTANCE));
                spawnSphere(current, 0.6f);
                if (hitSolidBlock(current)) {
                    cancel();
                    cleanup(id, handle[0]);
                    if (enhanced && !hitTarget) {
                        tryStartHoming(player, current.clone());
                    }
                    return;
                }
                if (checkEntityHit(player, current, HIT_RADIUS, DAMAGE, KNOCKBACK, enhanced)) {
                    hitTarget = true;
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                if (++ticks >= MAX_TRAVEL_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                    if (enhanced && !hitTarget) {
                        tryStartHoming(player, current.clone());
                    }
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 1L, 1L);
        trackTask(id, handle[0]);
    }

    private void tryStartHoming(Player player, Location current) {
        UUID id = player.getUniqueId();
        LivingEntity target = findNearestTarget(player, current);
        if (target == null) {
            return;
        }
        if (!awakeningAbility.consumeAbilityPoint(player)) {
            return;
        }

        startHoming(player, current, target);
    }

    private void startHoming(Player player, Location current, LivingEntity target) {
        UUID id = player.getUniqueId();
        BukkitTask[] handle = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                Vector targetFacing = target.getLocation().getDirection().normalize();
                Vector targetPoint = target.getLocation().add(0, 1, 0).toVector()
                        .add(targetFacing.multiply(-1.6));
                Vector travel = targetPoint.subtract(current.toVector()).normalize().multiply(HOMING_STEP_DISTANCE);
                current.add(travel);
                spawnSphere(current, 0.8f);
                if (checkEntityHit(player, current, HIT_RADIUS, HOMING_DAMAGE, HOMING_KNOCKBACK, true)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                if (++ticks >= HOMING_MAX_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 1L, 1L);
        trackTask(id, handle[0]);
    }

    private LivingEntity findNearestTarget(Player caster, Location origin) {
        LivingEntity nearest = null;
        double nearestDistanceSq = HOMING_RANGE * HOMING_RANGE;
        for (LivingEntity entity : origin.getNearbyLivingEntities(HOMING_RANGE)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            double distanceSq = entity.getLocation().distanceSquared(origin);
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = entity;
            }
        }
        return nearest;
    }

    private boolean hitSolidBlock(Location location) {
        Block block = location.getBlock();
        return block != null && block.getType().isSolid();
    }

    private boolean checkEntityHit(Player caster, Location center, double radius, double damage, double knockback,
            boolean enhanced) {
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            applyImpact(caster, entity, center, damage, knockback, enhanced);
            return true;
        }
        return false;
    }

    private void applyImpact(Player caster, LivingEntity target, Location origin, double damage, double knockbackForce,
            boolean enhanced) {
        if (enhanced) {
            Vector pull = origin.toVector().subtract(target.getLocation().toVector()).normalize()
                    .multiply(ENHANCED_PULL_STRENGTH);
            pull.setY(Math.min(ENHANCED_PULL_VERTICAL_LIMIT, pull.getY() + 0.2));
            target.setVelocity(target.getVelocity().multiply(0.2).add(pull));
        }
        target.damage(damage, caster);
        Vector knockback = target.getLocation().toVector().subtract(origin.toVector()).normalize()
                .multiply(knockbackForce);
        knockback.setY(Math.max(0.5, knockback.getY()));
        target.setVelocity(target.getVelocity().add(knockback));
        spawnSphere(origin, 0.9f);
        origin.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
        if (enhanced && lapseBlueAbility != null) {
            lapseBlueAbility.tryTriggerHollowPurple(caster);
        }
    }

    private void spawnSphere(Location center, float size) {
        center.getWorld().spawnParticle(Particle.REDSTONE, center, 14, size * 0.22, size * 0.22, size * 0.22, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 40, 40), size));
        center.getWorld().spawnParticle(Particle.CRIT_MAGIC, center, 5, 0.16, 0.16, 0.16, 0.0);
        center.getWorld().spawnParticle(Particle.FLAME, center, 6, 0.12, 0.12, 0.12, 0.0);
    }

    private boolean isActive(Player player) {
        return player.isOnline() && awakeningAbility.isAwakening(player) && restrictionManager.canUseAbility(player);
    }

    private void trackTask(UUID id, BukkitTask task) {
        activeTasks.computeIfAbsent(id, ignored -> new ArrayList<>()).add(task);
    }

    private void cleanup(UUID id, BukkitTask finishedTask) {
        List<BukkitTask> tasks = activeTasks.get(id);
        if (tasks == null) {
            return;
        }
        tasks.remove(finishedTask);
        if (tasks.isEmpty()) {
            activeTasks.remove(id);
        }
    }

    public void clearAll() {
        for (List<BukkitTask> tasks : activeTasks.values()) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        List<BukkitTask> tasks = activeTasks.remove(id);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
        cooldownManager.clear(id);
    }
}
