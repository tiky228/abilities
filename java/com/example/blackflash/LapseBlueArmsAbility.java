package com.example.blackflash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class LapseBlueArmsAbility {

    private static final int COOLDOWN_SECONDS = 13;
    private static final int COMBO_HITS = 4;
    private static final int TICKS_BETWEEN_HITS = 8;
    private static final double PUNCH_RANGE = 6.0;
    private static final double FORWARD_ANGLE_DOT = 0.2;
    private static final double VERTICAL_TOLERANCE = 1.1;
    private static final double HORIZONTAL_TOLERANCE = 1.25;
    private static final double PUNCH_DAMAGE = 5.5;
    private static final double PUNCH_KNOCKBACK = 0.55;
    private static final double PULL_STRENGTH = 0.75;
    private static final double FINISHER_DAMAGE = 8.0;
    private static final double FINISHER_KNOCKBACK = 1.1;
    private static final double FINAL_PULL_STRENGTH = 0.45;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, BukkitTask> activeCombos = new HashMap<>();

    public LapseBlueArmsAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager, GojoAwakeningAbility awakeningAbility) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
        this.awakeningAbility = awakeningAbility;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Lapse Blue Arms");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Rapid strikes infused with blue pull.",
                    ChatColor.BLUE + "Right-click to unleash a dragging combo."));
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
            player.sendMessage(ChatColor.RED + "Lapse Blue Arms can only be used during Gojo's Awakening.");
            return;
        }
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Lapse Blue Arms is recharging for " + remaining + "s.");
            return;
        }

        cancelCombo(id);
        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        startCombo(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, 1.1f);
        player.sendMessage(ChatColor.AQUA + "Blue arms unleash a rapid barrage!");
    }

    private void startCombo(Player player) {
        UUID id = player.getUniqueId();
        BukkitRunnable runnable = new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id);
                    return;
                }
                boolean finisher = step >= COMBO_HITS - 1;
                performPunch(player, finisher);
                step++;
                if (step >= COMBO_HITS) {
                    cancel();
                    cleanup(id);
                }
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, TICKS_BETWEEN_HITS);
        activeCombos.put(id, task);
    }

    private void performPunch(Player player, boolean finisher) {
        LivingEntity target = findTarget(player);
        spawnSwingParticles(player.getEyeLocation());
        if (target == null) {
            return;
        }

        double damage = finisher ? FINISHER_DAMAGE : PUNCH_DAMAGE;
        double knockbackForce = finisher ? FINISHER_KNOCKBACK : PUNCH_KNOCKBACK;

        target.damage(damage, player);
        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (knockback.lengthSquared() > 1.0e-6) {
            Vector push = knockback.normalize().multiply(knockbackForce);
            push.setY(Math.max(0.25, push.getY() + 0.12));
            target.setVelocity(target.getVelocity().multiply(0.25).add(push));
        }

        double pullStrength = finisher ? FINAL_PULL_STRENGTH : PULL_STRENGTH;
        applyPull(player, target, pullStrength);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f,
                finisher ? 0.8f : 1.0f);
        spawnImpactParticles(target.getLocation(), finisher);
    }

    private LivingEntity findTarget(Player player) {
        LivingEntity closest = null;
        double closestDistance = PUNCH_RANGE * PUNCH_RANGE;
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();

        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (!entity.getWorld().equals(player.getWorld())) {
                continue;
            }
            Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector());
            double forwardDistance = forward.dot(toEntity);
            if (forwardDistance < 0.0 || forwardDistance > PUNCH_RANGE) {
                continue;
            }
            double distanceSq = toEntity.lengthSquared();
            if (distanceSq > closestDistance || distanceSq < 1.0e-6) {
                continue;
            }
            Vector perpendicular = toEntity.clone().subtract(forward.clone().multiply(forwardDistance));
            double verticalOffset = Math.abs(perpendicular.getY());
            double lateralDistance = Math.hypot(perpendicular.getX(), perpendicular.getZ());
            if (verticalOffset > VERTICAL_TOLERANCE || lateralDistance > HORIZONTAL_TOLERANCE) {
                continue;
            }
            if (forward.dot(toEntity.normalize()) < FORWARD_ANGLE_DOT) {
                continue;
            }
            closest = entity;
            closestDistance = distanceSq;
        }
        return closest;
    }

    private void applyPull(Player caster, LivingEntity target, double strength) {
        Vector toCaster = caster.getLocation().add(0, 0.6, 0).toVector().subtract(target.getLocation().toVector());
        if (toCaster.lengthSquared() < 1.0e-6) {
            return;
        }
        Vector pull = toCaster.normalize().multiply(strength);
        pull.setY(Math.min(0.45, pull.getY() + 0.05));
        target.setVelocity(target.getVelocity().add(pull));
        spawnPullParticles(target.getLocation().clone().add(0, 0.7, 0));
    }

    private void spawnSwingParticles(Location center) {
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 4, 0.3, 0.3, 0.3, 0.02);
        center.getWorld().spawnParticle(Particle.CRIT_MAGIC, center, 6, 0.24, 0.24, 0.24, 0.03);
    }

    private void spawnImpactParticles(Location location, boolean finisher) {
        float size = finisher ? 1.2f : 0.9f;
        location.getWorld().spawnParticle(Particle.REDSTONE, location, finisher ? 12 : 8, 0.2, 0.2, 0.2, 0.02,
                new Particle.DustOptions(Color.fromRGB(70, 160, 255), size));
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, finisher ? 6 : 4, 0.16, 0.16, 0.16,
                0.02);
    }

    private void spawnPullParticles(Location location) {
        location.getWorld().spawnParticle(Particle.REDSTONE, location, 10, 0.35, 0.35, 0.35, 0.02,
                new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.0f));
        location.getWorld().spawnParticle(Particle.END_ROD, location, 4, 0.2, 0.2, 0.2, 0.01);
    }

    private boolean isActive(Player player) {
        return player.isOnline() && awakeningAbility.isAwakening(player) && restrictionManager.canUseAbility(player);
    }

    private void cancelCombo(UUID id) {
        BukkitTask task = activeCombos.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private void cleanup(UUID id) {
        cancelCombo(id);
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        cancelCombo(id);
        cooldownManager.clear(id);
    }

    public void clearAll() {
        for (BukkitTask task : activeCombos.values()) {
            task.cancel();
        }
        activeCombos.clear();
    }
}
