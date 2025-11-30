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
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private static final int FOLLOW_DURATION_TICKS = 50; // 2.5 seconds
    private static final int ATTRACTION_DURATION_TICKS = 100; // 5 seconds
    private static final int ENHANCED_ATTRACTION_DURATION_TICKS = 160; // 8 seconds
    private static final int DAMAGE_INTERVAL_TICKS = 10;
    private static final double FOLLOW_DISTANCE = 8.5;
    private static final double SPHERE_RADIUS = 2.25;
    private static final double ATTRACTION_RADIUS = 7.5;
    private static final double PULL_STRENGTH = 0.45;
    private static final double DAMAGE_PER_TICK = 1.0;
    private static final int HOLLOW_PURPLE_DURATION_TICKS = 70; // 3.5 seconds
    private static final double HOLLOW_PURPLE_RADIUS = SPHERE_RADIUS * 2.6;
    private static final double HOLLOW_PURPLE_PULL_STRENGTH = 1.2;
    private static final double HOLLOW_PURPLE_DAMAGE_PER_TICK = 7.0;
    private static final double HOLLOW_PURPLE_FINAL_DAMAGE = 26.0;
    private static final double HOLLOW_PURPLE_FINAL_KNOCKBACK = 3.2;
    private static final double HOLLOW_PURPLE_NOTIFICATION_RANGE = 20.0;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, List<BukkitTask>> activeTasks = new HashMap<>();
    private final Map<UUID, BlueSphereState> sphereStates = new HashMap<>();

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
        BlueSphereState state = new BlueSphereState(
                player.getEyeLocation().add(player.getLocation().getDirection().normalize().multiply(FOLLOW_DISTANCE)),
                enhanced);
        sphereStates.put(id, state);
        handle[0] = new BukkitRunnable() {
            int ticks = 0;
            Location currentCenter = player.getEyeLocation()
                    .add(player.getLocation().getDirection().normalize().multiply(FOLLOW_DISTANCE));

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    sphereStates.remove(id);
                    return;
                }
                currentCenter = player.getEyeLocation().add(player.getLocation().getDirection().normalize()
                        .multiply(FOLLOW_DISTANCE));
                state.setCenter(currentCenter.clone());
                spawnSphere(currentCenter);
                pullEntities(currentCenter, player);
                if (enhanced && ticks % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntities(currentCenter, player);
                }
                if (++ticks >= FOLLOW_DURATION_TICKS) {
                    cancel();
                    cleanup(id, handle[0]);
                    startAttractionPhase(player, currentCenter.clone(), enhanced, state);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
    }

    private void startAttractionPhase(Player player, Location center, boolean enhanced, BlueSphereState state) {
        UUID id = player.getUniqueId();
        int maxTicks = enhanced ? ENHANCED_ATTRACTION_DURATION_TICKS : ATTRACTION_DURATION_TICKS;
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    sphereStates.remove(id);
                    return;
                }
                state.setCenter(center.clone());
                spawnSphere(center);
                pullEntities(center, player);
                if (enhanced && ticks % DAMAGE_INTERVAL_TICKS == 0) {
                    damageEntities(center, player);
                }
                if (++ticks >= maxTicks) {
                    cancel();
                    cleanup(id, handle[0]);
                    sphereStates.remove(id);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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

    private void pullEntities(Location center, Player caster, double radius, double strength) {
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            Vector toCenter = center.toVector().subtract(entity.getLocation().toVector());
            Vector push = toCenter.normalize().multiply(strength);
            push.setY(Math.min(0.85, push.getY() + 0.1));
            entity.setVelocity(entity.getVelocity().multiply(0.35).add(push));
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

    private void damageEntities(Location center, Player caster, double radius, double damage) {
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (entity.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            entity.damage(damage, caster);
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

    public boolean tryTriggerHollowPurple(Player player) {
        UUID id = player.getUniqueId();
        BlueSphereState state = sphereStates.get(id);
        if (state == null || !state.isEnhanced() || state.isHollowPurpleActive()) {
            return false;
        }
        if (!awakeningAbility.consumeAbilityPoint(player)) {
            return false;
        }
        cancelTasks(id);
        startHollowPurple(player, state);
        return true;
    }

    private void startHollowPurple(Player player, BlueSphereState state) {
        UUID id = player.getUniqueId();
        Location center = state.getCenter().clone();
        state.setHollowPurpleActive(true);
        notifyHollowPurple(center);
        BukkitTask[] handle = new BukkitTask[1];
        handle[0] = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!isActive(player)) {
                    cancel();
                    cleanup(id, handle[0]);
                    sphereStates.remove(id);
                    return;
                }
                if (ticks < HOLLOW_PURPLE_DURATION_TICKS) {
                    spawnHollowPurpleSphere(center);
                    pullEntities(center, player, HOLLOW_PURPLE_RADIUS, HOLLOW_PURPLE_PULL_STRENGTH);
                    if (ticks % DAMAGE_INTERVAL_TICKS == 0) {
                        damageEntities(center, player, HOLLOW_PURPLE_RADIUS, HOLLOW_PURPLE_DAMAGE_PER_TICK);
                    }
                    ticks++;
                    return;
                }
                detonateHollowPurple(center, player);
                cancel();
                cleanup(id, handle[0]);
                sphereStates.remove(id);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        trackTask(id, handle[0]);
    }

    private void spawnHollowPurpleSphere(Location center) {
        double radius = HOLLOW_PURPLE_RADIUS;
        int verticalSteps = 10;
        int horizontalSteps = 18;
        for (int i = 0; i <= verticalSteps; i++) {
            double phi = Math.PI * i / verticalSteps;
            double y = radius * Math.cos(phi);
            double ringRadius = radius * Math.sin(phi);
            for (int j = 0; j < horizontalSteps; j++) {
                double theta = 2 * Math.PI * j / horizontalSteps;
                Vector offset = new Vector(Math.cos(theta) * ringRadius, y, Math.sin(theta) * ringRadius);
                Location point = center.clone().add(offset);
                center.getWorld().spawnParticle(Particle.REDSTONE, point, 1, 0.04, 0.04, 0.04, 0.0,
                        new Particle.DustOptions(Color.fromRGB(170, 70, 255), 1.6f));
                if (j % 3 == 0) {
                    center.getWorld().spawnParticle(Particle.PORTAL, point, 1, 0.07, 0.07, 0.07, 0.0);
                    center.getWorld().spawnParticle(Particle.SPELL_WITCH, point, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }
        }
    }

    private void notifyHollowPurple(Location center) {
        for (Player nearby : center.getWorld().getPlayers()) {
            if (nearby.getLocation().distance(center) <= HOLLOW_PURPLE_NOTIFICATION_RANGE) {
                nearby.sendMessage(ChatColor.LIGHT_PURPLE + "Hollow Purple!");
            }
        }
        center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.2f);
    }

    private void detonateHollowPurple(Location center, Player caster) {
        damageEntities(center, caster, HOLLOW_PURPLE_RADIUS, HOLLOW_PURPLE_FINAL_DAMAGE);
        for (LivingEntity entity : center.getNearbyLivingEntities(HOLLOW_PURPLE_RADIUS)) {
            Vector push = entity.getLocation().toVector().subtract(center.toVector()).normalize()
                    .multiply(HOLLOW_PURPLE_FINAL_KNOCKBACK);
            push.setY(Math.max(0.8, push.getY() + 0.2));
            entity.setVelocity(entity.getVelocity().multiply(0.3).add(push));
        }
        carveCrater(center);
        center.getWorld().spawnParticle(Particle.END_ROD, center, 60, HOLLOW_PURPLE_RADIUS, HOLLOW_PURPLE_RADIUS,
                HOLLOW_PURPLE_RADIUS, 0.12);
        center.getWorld().spawnParticle(Particle.SONIC_BOOM, center, 8, HOLLOW_PURPLE_RADIUS * 0.4,
                HOLLOW_PURPLE_RADIUS * 0.4, HOLLOW_PURPLE_RADIUS * 0.4, 0.05);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
    }

    private void carveCrater(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int radius = 32;
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), centerY - radius);
        int maxY = Math.min(world.getMaxHeight(), centerY + 2);

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double horizontalDistance = Math.hypot(x - centerX, z - centerZ);
                if (horizontalDistance > radius) {
                    continue;
                }
                int depth = (int) Math.round((radius - horizontalDistance) * 0.85) + 3;
                int lowestY = Math.max(minY, centerY - depth);
                for (int y = maxY; y >= lowestY; y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.BEDROCK) {
                        continue;
                    }
                    if (block.getType().isSolid()) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(activeTasks.keySet())) {
            cancelTasks(id);
        }
        sphereStates.clear();
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        cancelTasks(id);
        sphereStates.remove(id);
        cooldownManager.clear(id);
    }

    private void cancelTasks(UUID id) {
        List<BukkitTask> tasks = activeTasks.remove(id);
        if (tasks == null) {
            return;
        }
        for (BukkitTask task : tasks) {
            task.cancel();
        }
    }

    private static class BlueSphereState {
        private Location center;
        private final boolean enhanced;
        private boolean hollowPurpleActive;

        BlueSphereState(Location center, boolean enhanced) {
            this.center = center;
            this.enhanced = enhanced;
            this.hollowPurpleActive = false;
        }

        public Location getCenter() {
            return center;
        }

        public void setCenter(Location center) {
            this.center = center;
        }

        public boolean isEnhanced() {
            return enhanced;
        }

        public boolean isHollowPurpleActive() {
            return hollowPurpleActive;
        }

        public void setHollowPurpleActive(boolean hollowPurpleActive) {
            this.hollowPurpleActive = hollowPurpleActive;
        }
    }
}
