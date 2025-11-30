package com.example.blackflash;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.util.Vector;

public class CoyoteStarrkAbility {

    public enum CeroVariant {
        RED(ChatColor.DARK_RED + "Cero Oscuras (Red)", Color.fromRGB(160, 30, 30), Color.fromRGB(20, 20, 20)),
        BLUE(ChatColor.AQUA + "Cero Oscuras (Blue)", Color.fromRGB(30, 120, 220), Color.fromRGB(240, 240, 255)),
        GREEN(ChatColor.GREEN + "Cero Oscuras (Green)", Color.fromRGB(40, 200, 90), Color.fromRGB(230, 255, 230)),
        CYAN(ChatColor.DARK_AQUA + "Cero Oscuras (Cyan)", Color.fromRGB(20, 200, 220), Color.fromRGB(230, 255, 255));

        private final String displayName;
        private final Color primary;
        private final Color secondary;

        CeroVariant(String displayName, Color primary, Color secondary) {
            this.displayName = displayName;
            this.primary = primary;
            this.secondary = secondary;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Color getPrimary() {
            return primary;
        }

        public Color getSecondary() {
            return secondary;
        }
    }

    private static final int PET_COOLDOWN_SECONDS = 20;
    private static final int STORM_COOLDOWN_SECONDS = 28;
    private static final int CERO_COOLDOWN_SECONDS = 12;
    private static final int PET_MIN_COUNT = 2;
    private static final int PET_MAX_COUNT = 4;
    private static final int PET_LIFETIME_TICKS = 240;
    private static final double PET_TARGET_RANGE = 18.0;
    private static final double PET_EXPLOSION_RADIUS = 3.5;
    private static final double PET_EXPLOSION_DAMAGE = 9.0;
    private static final int STORM_MAX_DURATION_TICKS = 160;
    private static final double STORM_DASH_SPEED = 0.7;
    private static final double STORM_EXPLOSION_RADIUS = 4.5;
    private static final double STORM_EXPLOSION_DAMAGE = 13.0;

    private static final double CERO_RANGE = 15.0;
    private static final double CERO_DOT = 0.86; // ~30 degrees cone
    private static final int CERO_DURATION_TICKS = 40;
    private static final int CERO_DAMAGE_INTERVAL = 6;
    private static final double CERO_DAMAGE = 4.0;
    private static final double CERO_KNOCKBACK = 0.8;

    private final BlackFlashPlugin plugin;
    private final AbilityRestrictionManager restrictionManager;
    private final NamespacedKey petsKey;
    private final Map<CeroVariant, NamespacedKey> ceroKeys = new EnumMap<>(CeroVariant.class);
    private final NamespacedKey wolfKey;
    private final NamespacedKey stormKey;
    private final CooldownManager petCooldown = new CooldownManager();
    private final CooldownManager stormCooldown = new CooldownManager();
    private final CooldownManager ceroCooldown = new CooldownManager();
    private final Map<UUID, List<UUID>> activeWolves = new HashMap<>();
    private final Map<UUID, BukkitTask> wolfTasks = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> stormTasks = new HashMap<>();
    private final Set<UUID> activeBeams = new HashSet<>();

    public CoyoteStarrkAbility(BlackFlashPlugin plugin, AbilityRestrictionManager restrictionManager, NamespacedKey petsKey,
            NamespacedKey ceroRedKey, NamespacedKey ceroBlueKey, NamespacedKey ceroGreenKey, NamespacedKey ceroCyanKey,
            NamespacedKey wolfKey, NamespacedKey stormKey) {
        this.plugin = plugin;
        this.restrictionManager = restrictionManager;
        this.petsKey = petsKey;
        this.ceroKeys.put(CeroVariant.RED, ceroRedKey);
        this.ceroKeys.put(CeroVariant.BLUE, ceroBlueKey);
        this.ceroKeys.put(CeroVariant.GREEN, ceroGreenKey);
        this.ceroKeys.put(CeroVariant.CYAN, ceroCyanKey);
        this.wolfKey = wolfKey;
        this.stormKey = stormKey;
    }

    public ItemStack createPetsItem() {
        ItemStack stack = new ItemStack(Material.LIGHT_BLUE_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Starrk Pets");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Summon spirit wolves that chase foes.",
                    ChatColor.BLUE + "Right-click to unleash the pack."));
            meta.getPersistentDataContainer().set(petsKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createCeroItem(CeroVariant variant) {
        ItemStack stack = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(variant.getDisplayName());
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Fire a stationary cone of devastation.",
                    ChatColor.RED + "Right-click to unleash."));
            meta.getPersistentDataContainer().set(ceroKeys.get(variant), PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createStormItem() {
        ItemStack stack = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Los Lobos Storm");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Command your wolves to spiral into a storm.",
                    ChatColor.BLUE + "Right-click while wolves are active."));
            meta.getPersistentDataContainer().set(stormKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isPetsItem(ItemStack itemStack) {
        return hasKey(itemStack, petsKey);
    }

    public boolean isCeroItem(ItemStack itemStack, CeroVariant variant) {
        return hasKey(itemStack, ceroKeys.get(variant));
    }

    public boolean isStormItem(ItemStack itemStack) {
        return hasKey(itemStack, stormKey);
    }

    private boolean hasKey(ItemStack stack, NamespacedKey key) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
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
        player.sendMessage(ChatColor.AQUA + "The wolves race toward your enemies!");
    }

    private void summonWolves(Player player) {
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = PET_MIN_COUNT + random.nextInt(PET_MAX_COUNT - PET_MIN_COUNT + 1);
        List<UUID> wolves = new ArrayList<>();
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
                summoned.getPersistentDataContainer().set(wolfKey, PersistentDataType.STRING, player.getUniqueId().toString());
            });
            wolves.add(wolf.getUniqueId());
            runWolfLogic(player, wolf);
        }
        activeWolves.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(wolves);
    }

    private void runWolfLogic(Player owner, Wolf wolf) {
        BukkitRunnable wolfRoutine = new BukkitRunnable() {
            int life = 0;

            @Override
            public void run() {
                if (wolf.isDead() || !wolf.isValid() || life >= PET_LIFETIME_TICKS) {
                    wolf.remove();
                    removeTrackedWolf(owner.getUniqueId(), wolf.getUniqueId());
                    cancel();
                    return;
                }
                life++;
                spawnWolfParticles(wolf.getLocation());
                LivingEntity target = findWolfTarget(owner, wolf);
                if (target != null) {
                    wolf.setTarget(target);
                    if (wolf.getLocation().distanceSquared(target.getLocation()) < 2.2) {
                        explodeWolf(owner, wolf, target.getLocation());
                        cancel();
                    }
                }
            }
        };
        BukkitTask task = wolfRoutine.runTaskTimer(plugin, 0L, 2L);
        wolfTasks.put(wolf.getUniqueId(), task);
    }

    private LivingEntity findWolfTarget(Player owner, Wolf wolf) {
        World world = wolf.getWorld();
        if (world == null) {
            return null;
        }
        LivingEntity closest = null;
        double closestDistance = PET_TARGET_RANGE * PET_TARGET_RANGE;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(owner) || entity.equals(wolf)) {
                continue;
            }
            if (entity.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(wolf.getLocation());
            if (distance > closestDistance) {
                continue;
            }
            closest = entity;
            closestDistance = distance;
        }
        return closest;
    }

    private void explodeWolf(Player owner, Wolf wolf, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        wolf.remove();
        removeTrackedWolf(owner.getUniqueId(), wolf.getUniqueId());
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.4f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.5f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 4, 0.4, 0.3, 0.4, 0.02);
        world.spawnParticle(Particle.REDSTONE, center, 90, 1.0, 0.7, 1.0, dust);
        world.spawnParticle(Particle.CLOUD, center, 35, 0.8, 0.5, 0.8, 0.04);
        world.spawnParticle(Particle.END_ROD, center, 30, 0.5, 0.4, 0.5, 0.03);

        double radiusSq = PET_EXPLOSION_RADIUS * PET_EXPLOSION_RADIUS;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(owner)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            entity.damage(PET_EXPLOSION_DAMAGE, owner);
        }
    }

    private void spawnWolfParticles(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(90, 210, 255), 1.0f);
        world.spawnParticle(Particle.REDSTONE, location, 8, 0.3, 0.4, 0.3, dust);
        world.spawnParticle(Particle.CRIT_MAGIC, location.clone().add(0, 0.5, 0), 5, 0.25, 0.25, 0.25, 0.02);
    }

    public void tryActivateCero(Player player, CeroVariant variant) {
        UUID id = player.getUniqueId();
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!ceroCooldown.isReady(id)) {
            player.sendMessage(ChatColor.YELLOW + "Cero Oscuras cooling down for " + ceroCooldown.getRemainingSeconds(id)
                    + "s.");
            return;
        }
        ceroCooldown.setCooldown(id, CERO_COOLDOWN_SECONDS);
        fireCero(player, variant);
    }

    private void fireCero(Player player, CeroVariant variant) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        player.getWorld().playSound(origin, Sound.ENTITY_GHAST_WARN, 1.4f, 1.5f);
        player.getWorld().playSound(origin, Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.4f);
        player.sendMessage(ChatColor.GRAY + "Cero Oscuras unleashed!");

        activeBeams.add(player.getUniqueId());
        BukkitRunnable beamRunnable = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                World world = origin.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }
                if (ticks >= CERO_DURATION_TICKS) {
                    activeBeams.remove(player.getUniqueId());
                    world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
                    cancel();
                    return;
                }
                spawnCeroParticles(origin, direction, variant, ticks == 0);
                if (ticks % 10 == 0) {
                    world.playSound(origin, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 1.6f);
                }
                if (ticks % CERO_DAMAGE_INTERVAL == 0) {
                    applyCeroDamage(player, origin, direction);
                }
                ticks += 2;
            }
        };
        BukkitTask beamTask = beamRunnable.runTaskTimer(plugin, 0L, 2L);
        player.setCooldown(player.getInventory().getItemInMainHand().getType(), 10);
    }

    private void spawnCeroParticles(Location origin, Vector direction, CeroVariant variant, boolean firstTick) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions primary = new Particle.DustOptions(variant.getPrimary(), 1.4f);
        Particle.DustOptions secondary = new Particle.DustOptions(variant.getSecondary(), 1.2f);

        double maxRadius = Math.tan(Math.acos(CERO_DOT)) * CERO_RANGE;
        double step = 0.9;
        for (double dist = 0; dist <= CERO_RANGE; dist += step) {
            double radius = (dist / CERO_RANGE) * maxRadius;
            Location center = origin.clone().add(direction.clone().multiply(dist));
            for (int i = 0; i < 16; i++) {
                double angle = (Math.PI * 2 / 16) * i;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Vector offset = rotateAroundAxis(direction, new Vector(x, 0, z));
                Location point = center.clone().add(offset);
                world.spawnParticle(Particle.REDSTONE, point, 2, 0.08, 0.08, 0.08, primary);
                world.spawnParticle(Particle.REDSTONE, point, 1, 0.05, 0.05, 0.05, secondary);
            }
            if (firstTick) {
                world.spawnParticle(Particle.SPELL_MOB, center, 8, radius * 0.6, radius * 0.3, radius * 0.6, 0.0);
            }
        }
    }

    private Vector rotateAroundAxis(Vector direction, Vector offset) {
        if (direction.lengthSquared() == 0) {
            return offset;
        }
        Vector normalized = direction.clone().normalize();
        Vector up = new Vector(0, 1, 0);
        if (Math.abs(normalized.dot(up)) > 0.99) {
            up = new Vector(1, 0, 0);
        }
        Vector right = normalized.clone().crossProduct(up).normalize();
        Vector adjustedUp = right.clone().crossProduct(normalized).normalize();
        return right.multiply(offset.getX()).add(adjustedUp.multiply(offset.getZ()));
    }

    private void applyCeroDamage(Player player, Location origin, Vector direction) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(player)) {
                continue;
            }
            if (!isInCone(origin, direction, entity.getLocation())) {
                continue;
            }
            entity.damage(CERO_DAMAGE, player);
            Vector push = direction.clone().multiply(CERO_KNOCKBACK);
            push.setY(0.25);
            entity.setVelocity(entity.getVelocity().add(push));
        }
    }

    private boolean isInCone(Location origin, Vector direction, Location target) {
        Vector relative = target.toVector().subtract(origin.toVector());
        double lengthSq = relative.lengthSquared();
        if (lengthSq > CERO_RANGE * CERO_RANGE || lengthSq < 0.25) {
            return false;
        }
        Vector forward = direction.clone().normalize();
        double dot = forward.dot(relative.clone().normalize());
        return dot >= CERO_DOT;
    }

    public void clearAll() {
        for (List<UUID> wolves : activeWolves.values()) {
            for (UUID uuid : wolves) {
                Wolf wolf = (Wolf) Bukkit.getEntity(uuid);
                if (wolf != null) {
                    wolf.remove();
                }
            }
        }
        activeWolves.clear();
        for (BukkitTask task : wolfTasks.values()) {
            task.cancel();
        }
        wolfTasks.clear();
        for (List<BukkitTask> tasks : stormTasks.values()) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
        stormTasks.clear();
        petCooldown.clearAll();
        stormCooldown.clearAll();
        ceroCooldown.clearAll();
    }

    public void clearPlayer(Player player) {
        UUID id = player.getUniqueId();
        List<UUID> wolves = activeWolves.remove(id);
        if (wolves != null) {
            for (UUID wolfId : wolves) {
                Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                if (wolf != null) {
                    wolf.remove();
                }
                BukkitTask wolfTask = wolfTasks.remove(wolfId);
                if (wolfTask != null) {
                    wolfTask.cancel();
                }
            }
        }
        List<BukkitTask> tasks = stormTasks.remove(id);
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                task.cancel();
            }
        }
        petCooldown.clear(id);
        stormCooldown.clear(id);
        ceroCooldown.clear(id);
    }

    private void removeTrackedWolf(UUID ownerId, UUID wolfId) {
        List<UUID> wolves = activeWolves.get(ownerId);
        if (wolves == null) {
            return;
        }
        wolves.remove(wolfId);
        if (wolves.isEmpty()) {
            activeWolves.remove(ownerId);
        }
        BukkitTask task = wolfTasks.remove(wolfId);
        if (task != null) {
            task.cancel();
        }
    }

    public boolean isStarrkWolf(LivingEntity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        return container.has(wolfKey, PersistentDataType.STRING);
    }

    public UUID getWolfOwnerId(LivingEntity wolf) {
        PersistentDataContainer container = wolf.getPersistentDataContainer();
        if (!container.has(wolfKey, PersistentDataType.STRING)) {
            return null;
        }
        String id = container.get(wolfKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isWolfOwner(LivingEntity wolf, Player player) {
        PersistentDataContainer container = wolf.getPersistentDataContainer();
        if (!container.has(wolfKey, PersistentDataType.STRING)) {
            return false;
        }
        String id = container.get(wolfKey, PersistentDataType.STRING);
        return id != null && id.equals(player.getUniqueId().toString());
    }

    public void tryActivateStorm(Player player) {
        UUID id = player.getUniqueId();
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }
        if (!stormCooldown.isReady(id)) {
            player.sendMessage(
                    ChatColor.YELLOW + "Los Lobos Storm cooling down for " + stormCooldown.getRemainingSeconds(id)
                            + "s.");
            return;
        }
        List<UUID> wolves = activeWolves.get(id);
        if (wolves == null || wolves.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no wolves to command.");
            return;
        }
        List<Wolf> active = new ArrayList<>();
        for (UUID wolfId : new ArrayList<>(wolves)) {
            if (!(Bukkit.getEntity(wolfId) instanceof Wolf wolf) || wolf.isDead() || !wolf.isValid()) {
                removeTrackedWolf(id, wolfId);
                continue;
            }
            active.add(wolf);
        }
        if (active.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Your wolves are already gone.");
            return;
        }
        stormCooldown.setCooldown(id, STORM_COOLDOWN_SECONDS);
        triggerStorm(player, active);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0f, 1.35f);
        player.sendMessage(ChatColor.AQUA + "Los Lobos Storm unleashed!");
    }

    private void triggerStorm(Player player, List<Wolf> wolves) {
        UUID ownerId = player.getUniqueId();
        List<BukkitTask> tasks = new ArrayList<>();
        stormTasks.put(ownerId, tasks);
        for (Wolf wolf : wolves) {
            BukkitTask existing = wolfTasks.remove(wolf.getUniqueId());
            if (existing != null) {
                existing.cancel();
            }
            int delay = ThreadLocalRandom.current().nextInt(6, 20);
            BukkitTask[] dashHolder = new BukkitTask[1];
            BukkitRunnable dashRunnable = new BukkitRunnable() {
                int ticks = 0;
                LivingEntity target = findStormTarget(player, wolf);

                @Override
                public void run() {
                    if (wolf.isDead() || !wolf.isValid()) {
                        removeTrackedWolf(ownerId, wolf.getUniqueId());
                        cancel();
                        cleanupStormTask(ownerId, this);
                        return;
                    }
                    if (ticks == 0 && target != null) {
                        wolf.setTarget(target);
                    }
                    Location wolfLoc = wolf.getLocation();
                    spawnStormTrail(wolfLoc);
                    if (target != null && !target.isDead()) {
                        Vector direction = target.getLocation().toVector().subtract(wolfLoc.toVector()).normalize();
                        direction.setY(0.12);
                        wolf.setVelocity(direction.multiply(STORM_DASH_SPEED));
                        if (wolfLoc.distanceSquared(target.getLocation()) < 2.25) {
                            stormExplode(player, wolf, target.getLocation());
                            cancel();
                            cleanupStormTask(ownerId, dashHolder[0]);
                            return;
                        }
                    } else if (ticks > 50) {
                        stormExplode(player, wolf, wolfLoc);
                        cancel();
                        cleanupStormTask(ownerId, dashHolder[0]);
                        return;
                    }
                    ticks += 2;
                    if (ticks >= STORM_MAX_DURATION_TICKS) {
                        stormExplode(player, wolf, wolfLoc);
                        cancel();
                        cleanupStormTask(ownerId, dashHolder[0]);
                    }
                }
            };
            dashHolder[0] = dashRunnable.runTaskTimer(plugin, delay, 2L);
            tasks.add(dashHolder[0]);
        }

        BukkitRunnable monitorRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> remaining = activeWolves.get(ownerId);
                if (remaining == null || remaining.isEmpty()) {
                    cancel();
                    stormTasks.remove(ownerId);
                    return;
                }
                if (stormTasks.get(ownerId) == null || stormTasks.get(ownerId).isEmpty()) {
                    for (UUID wolfId : remaining) {
                        Wolf wolf = (Wolf) Bukkit.getEntity(wolfId);
                        if (wolf != null) {
                            wolf.remove();
                        }
                    }
                    activeWolves.remove(ownerId);
                    cancel();
                }
            }
        };
        BukkitTask monitorTask = monitorRunnable.runTaskTimer(plugin, STORM_MAX_DURATION_TICKS, 20L);
        tasks.add(monitorTask);
    }

    private LivingEntity findStormTarget(Player owner, Wolf wolf) {
        World world = wolf.getWorld();
        if (world == null) {
            return null;
        }
        LivingEntity closest = null;
        double closestDistance = PET_TARGET_RANGE * PET_TARGET_RANGE;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(owner) || entity.equals(wolf)) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(wolf.getLocation());
            if (distance > closestDistance) {
                continue;
            }
            closest = entity;
            closestDistance = distance;
        }
        return closest;
    }

    private void spawnStormTrail(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 230, 255), 1.1f);
        world.spawnParticle(Particle.REDSTONE, location, 10, 0.35, 0.0, 0.35, dust);
        world.spawnParticle(Particle.FIREWORKS_SPARK, location.clone().add(0, 0.1, 0), 6, 0.2, 0.0, 0.2, 0.02);
    }

    private void stormExplode(Player owner, Wolf wolf, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        wolf.remove();
        removeTrackedWolf(owner.getUniqueId(), wolf.getUniqueId());
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.1f, 0.7f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(70, 200, 255), 1.7f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 6, 0.6, 0.4, 0.6, 0.02);
        world.spawnParticle(Particle.REDSTONE, center, 140, 1.2, 0.8, 1.2, dust);
        world.spawnParticle(Particle.CLOUD, center, 45, 1.0, 0.6, 1.0, 0.05);
        world.spawnParticle(Particle.END_ROD, center, 30, 0.7, 0.5, 0.7, 0.03);

        double radiusSq = STORM_EXPLOSION_RADIUS * STORM_EXPLOSION_RADIUS;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(owner)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            if (entity.getPersistentDataContainer().has(wolfKey, PersistentDataType.STRING)) {
                continue;
            }
            entity.damage(STORM_EXPLOSION_DAMAGE, owner);
        }
    }

    private void cleanupStormTask(UUID ownerId, BukkitTask task) {
        List<BukkitTask> tasks = stormTasks.get(ownerId);
        if (tasks == null) {
            return;
        }
        tasks.removeIf(existing -> existing == task);
        if (tasks.isEmpty()) {
            stormTasks.remove(ownerId);
        }
    }
}
