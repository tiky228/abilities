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
    private static final int MAX_TRAVEL_TICKS = 30;
    private static final double STEP_DISTANCE = 1.4;
    private static final double HIT_RADIUS = 1.3;
    private static final double DAMAGE = 14.0;
    private static final double KNOCKBACK = 1.6;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, List<BukkitTask>> activeTasks = new HashMap<>();

    public ReverseRedAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager, GojoAwakeningAbility awakeningAbility) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
        this.awakeningAbility = awakeningAbility;
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

        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        Vector direction = player.getLocation().getDirection().normalize();
        Location spawnLocation = player.getEyeLocation().add(direction.clone().multiply(1.4));
        beginCharge(player, spawnLocation, direction);
    }

    private void beginCharge(Player player, Location origin, Vector direction) {
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
                spawnSphere(origin, 0.9f);
                if (++ticks >= CHARGE_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                    launchProjectile(player, origin.clone(), direction.clone());
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.9f, 1.1f);
    }

    private void launchProjectile(Player player, Location current, Vector direction) {
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
                current.add(direction.clone().multiply(STEP_DISTANCE));
                spawnSphere(current, 1.2f);
                if (hitSolidBlock(current)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                if (checkEntityHit(player, current)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                if (++ticks >= MAX_TRAVEL_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 1L, 1L);
        trackTask(id, handle[0]);
    }

    private boolean hitSolidBlock(Location location) {
        Block block = location.getBlock();
        return block != null && block.getType().isSolid();
    }

    private boolean checkEntityHit(Player caster, Location center) {
        for (LivingEntity entity : center.getNearbyLivingEntities(HIT_RADIUS)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            applyImpact(caster, entity, center);
            return true;
        }
        return false;
    }

    private void applyImpact(Player caster, LivingEntity target, Location origin) {
        target.damage(DAMAGE, caster);
        Vector knockback = target.getLocation().toVector().subtract(origin.toVector()).normalize().multiply(KNOCKBACK);
        knockback.setY(Math.max(0.5, knockback.getY()));
        target.setVelocity(knockback);
        spawnSphere(origin, 1.6f);
        origin.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.0f);
    }

    private void spawnSphere(Location center, float size) {
        center.getWorld().spawnParticle(Particle.REDSTONE, center, 35, size * 0.4, size * 0.4, size * 0.4, 0.03,
                new Particle.DustOptions(Color.fromRGB(255, 40, 40), size));
        center.getWorld().spawnParticle(Particle.CRIT_MAGIC, center, 16, 0.35, 0.35, 0.35, 0.02);
        center.getWorld().spawnParticle(Particle.FLAME, center, 14, 0.25, 0.25, 0.25, 0.01);
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
}
