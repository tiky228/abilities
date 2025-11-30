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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class LapseBlueAbility {

    private static final int COOLDOWN_SECONDS = 25;
    private static final int FOLLOW_DURATION_TICKS = 20; // 1 second
    private static final int ATTRACTION_DURATION_TICKS = 100; // 5 seconds
    private static final int ENHANCED_ATTRACTION_DURATION_TICKS = 160; // 8 seconds
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final double FOLLOW_DISTANCE = 8.5;
    private static final double SPHERE_RADIUS = 2.25;
    private static final double ATTRACTION_RADIUS = 7.5;
    private static final double PULL_STRENGTH = 0.45;
    private static final double DAMAGE_PER_TICK = 3.0;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, List<BukkitTask>> activeTasks = new HashMap<>();

    public LapseBlueAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager, GojoAwakeningAbility awakeningAbility) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
        this.awakeningAbility = awakeningAbility;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.LAPIS_LAZULI);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Lapse Blue Maximum Output");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Create a crushing blue singularity.");
            lore.add(ChatColor.DARK_AQUA + "Right-click to release Lapse Blue.");
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
            player.sendMessage(ChatColor.RED + "Lapse Blue can only be used during Gojo's Awakening.");
            return;
        }
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Lapse Blue is recharging for " + remaining + "s.");
            return;
        }

        boolean enhanced = awakeningAbility.hasAbilityPoint(player);
        if (enhanced && !awakeningAbility.consumeAbilityPoint(player)) {
            enhanced = false;
        }
        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        startFollowPhase(player, enhanced);
        if (enhanced) {
            player.sendMessage(ChatColor.AQUA + "Ability Point consumed — Lapse Blue: Maximum Output (Enhanced).");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.1f);
        player.sendMessage(ChatColor.AQUA + "Lapse Blue surges ahead of you!");
    }

    private void startFollowPhase(Player player, boolean enhanced) {
        UUID id = player.getUniqueId();
        BukkitTask[] handle = new BukkitTask[1];
        BukkitRunnable runnable = new BukkitRunnable() {
            int ticks = 0;
            Location currentCenter = player.getEyeLocation()
                    .add(player.getLocation().getDirection().normalize().multiply(FOLLOW_DISTANCE));

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    return;
                }
                currentCenter = player.getEyeLocation().add(player.getLocation().getDirection().normalize()
                        .multiply(FOLLOW_DISTANCE));
                spawnSphere(currentCenter);
                pullEntities(currentCenter, player);
                if (enhanced && ticks % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntities(currentCenter, player);
                }
                if (++ticks >= FOLLOW_DURATION_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                    startAttractionPhase(player, currentCenter.clone(), enhanced);
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
    }

    private void startAttractionPhase(Player player, Location center, boolean enhanced) {
        UUID id = player.getUniqueId();
        int maxTicks = enhanced ? ENHANCED_ATTRACTION_DURATION_TICKS : ATTRACTION_DURATION_TICKS;
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
                spawnSphere(center);
                pullEntities(center, player);
                if (enhanced && ticks % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntities(center, player);
                }
                if (++ticks >= maxTicks) {
                    cancel();
                    cleanup(id, handle[0]);
                }
            }
        };
        handle[0] = runnable.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
    }

    private void spawnSphere(Location center) {
        int verticalSteps = 8;
        int horizontalSteps = 14;
        for (int i = 0; i <= verticalSteps; i++) {
            double phi = Math.PI * i / verticalSteps;
            double y = SPHERE_RADIUS * Math.cos(phi);
            double ringRadius = SPHERE_RADIUS * Math.sin(phi);
            for (int j = 0; j < horizontalSteps; j++) {
                double theta = 2 * Math.PI * j / horizontalSteps;
                Vector offset = new Vector(Math.cos(theta) * ringRadius, y, Math.sin(theta) * ringRadius);
                Location point = center.clone().add(offset);
                center.getWorld().spawnParticle(Particle.REDSTONE, point, 1, 0.03, 0.03, 0.03, 0.0,
                        new Particle.DustOptions(Color.fromRGB(90, 170, 255), 1.05f));
                if (j % 4 == 0) {
                    center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.01, 0.01, 0.01, 0.0);
                }
            }
        }
    }

    private void pullEntities(Location center, Player caster) {
        for (LivingEntity entity : center.getNearbyLivingEntities(ATTRACTION_RADIUS)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            Vector toCenter = center.toVector().subtract(entity.getLocation().toVector());
            Vector push = toCenter.normalize().multiply(PULL_STRENGTH);
            push.setY(Math.min(0.45, push.getY() + 0.05));
            entity.setVelocity(entity.getVelocity().multiply(0.4).add(push));
        }
    }

    private void damageEntities(Location center, Player caster) {
        for (LivingEntity entity : center.getNearbyLivingEntities(ATTRACTION_RADIUS)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            entity.damage(DAMAGE_PER_TICK, caster);
        }
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
