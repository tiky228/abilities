package com.example.blackflash;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Full-featured Bankai and Getsuga system for Tensa Zangetsu.
 */
public class AdvancedBankaiAbility {

    private static final int BANKAI_DURATION_SECONDS = 90;
    private static final int GETSUGA_COST_SECONDS = 10;
    private static final int GETSUGA_COOLDOWN_SECONDS = 7;
    private static final int DASH_COOLDOWN_SECONDS = 4;
    private static final double GETSUGA_RANGE = 15.0;
    private static final double SLASH_RADIUS = 1.2;
    private static final double TENSHOU_RADIUS = 3.4;
    private static final double SLASH_DAMAGE = 12.0;
    private static final double TENSHOU_DAMAGE = 22.0;
    private static final double DASH_DISTANCE = 5.0;
    private static final double DASH_IMPACT_DAMAGE = 10.0;
    private static final double DASH_IMPACT_RADIUS = 2.0;
    private static final int REATSU_COOLDOWN_SECONDS = 12;
    private static final int STAND_COOLDOWN_SECONDS = 22;
    private static final double REATSU_RADIUS = 10.0;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey bankaiItemKey;
    private final NamespacedKey reatsuItemKey;
    private final NamespacedKey standItemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final Map<UUID, BankaiData> states = new HashMap<>();

    public AdvancedBankaiAbility(BlackFlashPlugin plugin, NamespacedKey bankaiItemKey,
            AbilityRestrictionManager restrictionManager) {
        this.plugin = plugin;
        this.bankaiItemKey = bankaiItemKey;
        this.reatsuItemKey = new NamespacedKey(plugin, "reatsu_burst_item");
        this.standItemKey = new NamespacedKey(plugin, "stand_cutscene_item");
        this.restrictionManager = restrictionManager;
    }

    public ItemStack createBankaiItem() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bTensa Zangetsu");
            List<String> lore = new ArrayList<>();
            lore.add("§7Right-click to unleash your Bankai.");
            lore.add("§7Only the chosen can wield this.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(bankaiItemKey, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private ItemStack createReatsuItem() {
        ItemStack orb = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = orb.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cReatsu Burst");
            List<String> lore = new ArrayList<>();
            lore.add("§7Unleash a paralyzing spiritual burst.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(reatsuItemKey, PersistentDataType.BYTE, (byte) 1);
            orb.setItemMeta(meta);
        }
        return orb;
    }

    private ItemStack createStandItem() {
        ItemStack gem = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = gem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4I need to stand.");
            List<String> lore = new ArrayList<>();
            lore.add("§7Brace yourself and rise again.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(standItemKey, PersistentDataType.BYTE, (byte) 1);
            gem.setItemMeta(meta);
        }
        return gem;
    }

    public boolean canUseAbility(Player player) {
        return restrictionManager.canUseAbility(player);
    }

    public boolean isImmobilized(Player player) {
        BankaiData data = states.get(player.getUniqueId());
        return data != null && (data.reatsuLockedPlayers.contains(player.getUniqueId()) || data.standChanneling);
    }

    public boolean isReatsuItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(reatsuItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isStandItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(standItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isBankaiItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.NETHERITE_SWORD) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte marker = container.get(bankaiItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isInBankai(Player player) {
        BankaiData data = states.get(player.getUniqueId());
        return data != null && data.active;
    }

    public void handleUse(Player player) {
        BankaiData data = states.get(player.getUniqueId());
        if (data == null || (!data.active && !data.transforming)) {
            startTransformation(player);
            return;
        }

        if (data.transforming || data.standChanneling) {
            return;
        }
        if (player.isSneaking()) {
            attemptDash(player, data);
        } else {
            attemptGetsuga(player, data);
        }
    }

    public void handleReatsu(Player player) {
        BankaiData data = states.get(player.getUniqueId());
        if (data == null || !data.active || data.transforming || data.standChanneling) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        if (data.reatsuCooldownEnd > now) {
            long remaining = (data.reatsuCooldownEnd - now + 999) / 1000;
            player.sendActionBar(Component.text("Reatsu Burst cooling down: " + remaining + "s",
                    NamedTextColor.YELLOW));
            return;
        }
        startReatsuBurst(player, data);
    }

    public void handleStand(Player player) {
        BankaiData data = states.get(player.getUniqueId());
        if (data == null || !data.active || data.transforming || data.standChanneling) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        if (data.standCooldownEnd > now) {
            long remaining = (data.standCooldownEnd - now + 999) / 1000;
            player.sendActionBar(Component.text("Ability cooling down: " + remaining + "s", NamedTextColor.YELLOW));
            return;
        }
        beginStandCutscene(player, data);
    }

    private void attemptDash(Player player, BankaiData data) {
        if (!data.active) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        if (data.dashCooldownEnd > now) {
            long remaining = (data.dashCooldownEnd - now + 999) / 1000;
            player.sendActionBar(Component.text("Dash cooling down: " + remaining + "s", NamedTextColor.YELLOW));
            return;
        }

        data.dashCooldownEnd = now + DASH_COOLDOWN_SECONDS * 1000L;

        Location start = player.getLocation();
        Vector direction = start.getDirection().setY(0).normalize();
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 1);
        }

        Location lastSafe = start.clone();
        List<Location> trail = new ArrayList<>();
        for (double traveled = 0.5; traveled <= DASH_DISTANCE; traveled += 0.5) {
            Location next = start.clone().add(direction.clone().multiply(traveled));
            if (!isPassable(next)) {
                break;
            }
            lastSafe = next;
            trail.add(next.clone());
        }

        spawnDashTrail(trail);
        lastSafe.setYaw(start.getYaw());
        lastSafe.setPitch(start.getPitch());
        player.teleport(lastSafe);
        player.setVelocity(direction.clone().multiply(0.6).setY(0.0));
        player.getWorld().playSound(lastSafe, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.2f);

        boolean hit = applyDashImpact(player, lastSafe, direction);
        if (hit) {
            data.remainingSeconds += 5;
            sendTimerBar(player, data.remainingSeconds);
        }
    }

    private void startReatsuBurst(Player player, BankaiData data) {
        long now = Instant.now().toEpochMilli();
        data.reatsuCooldownEnd = now + REATSU_COOLDOWN_SECONDS * 1000L;
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions ring = new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.3f);
        Particle.DustOptions bright = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.6f);
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 32) {
            double x = Math.cos(angle) * REATSU_RADIUS;
            double z = Math.sin(angle) * REATSU_RADIUS;
            Location edge = center.clone().add(x, 0.1, z);
            world.spawnParticle(Particle.REDSTONE, edge, 10, 0.25, 0.08, 0.25, ring);
            world.spawnParticle(Particle.REDSTONE, edge, 6, 0.15, 0.05, 0.15, bright);
            world.spawnParticle(Particle.SMOKE_NORMAL, edge, 5, 0.2, 0.05, 0.2, 0.01);
        }
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.7f);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.9f);

        BukkitTask delay = new BukkitRunnable() {
            @Override
            public void run() {
                applyReatsuEffects(player, center.clone(), data);
            }
        }.runTaskLater(plugin, 20L);
        data.reatsuTasks.add(delay);
    }

    private void applyReatsuEffects(Player player, Location center, BankaiData data) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radiusSquared = REATSU_RADIUS * REATSU_RADIUS;
        Set<UUID> affected = new HashSet<>();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity.getLocation().getWorld() != world) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            affected.add(entity.getUniqueId());
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 6, false, false, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 80, 250, false, false, true));
            entity.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 80, 1, false, false, true));
            if (entity instanceof Player targetPlayer) {
                data.reatsuLockedPlayers.add(targetPlayer.getUniqueId());
            }
        }

        BukkitTask damageTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 80) {
                    releaseReatsuTargets(center, affected, data);
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                Particle.DustOptions deepAura = new Particle.DustOptions(Color.fromRGB(230, 30, 30), 1.8f);
                Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(255, 90, 90), 1.3f);
                for (int i = 0; i < 24; i++) {
                    double radius = random.nextDouble() * REATSU_RADIUS;
                    double angle = random.nextDouble() * Math.PI * 2;
                    double yOffset = random.nextDouble(0.1, 1.4);
                    Location swirl = center.clone().add(Math.cos(angle) * radius, yOffset,
                            Math.sin(angle) * radius);
                    world.spawnParticle(Particle.REDSTONE, swirl, 4, 0.25, 0.25, 0.25, deepAura);
                    world.spawnParticle(Particle.REDSTONE, swirl, 3, 0.2, 0.2, 0.2, ember);
                    world.spawnParticle(Particle.CRIT_MAGIC, swirl, 3, 0.28, 0.28, 0.28, 0.05);
                    world.spawnParticle(Particle.CRIT, swirl, 4, 0.26, 0.18, 0.26, 0.04);
                    world.spawnParticle(Particle.SMOKE_LARGE, swirl, 2, 0.3, 0.3, 0.3, 0.01);
                    world.spawnParticle(Particle.DRAGON_BREATH, swirl, 3, 0.24, 0.24, 0.24, 0.0);
                    if (i % 6 == 0) {
                        world.spawnParticle(Particle.EXPLOSION_NORMAL, swirl, 1, 0.15, 0.15, 0.15, 0.0);
                    }
                }
                ticks += 10;
                if (ticks % 20 == 0) {
                    world.playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 0.6f, 0.7f);
                    world.playSound(center, Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f,
                            (float) (0.65 + random.nextDouble(0.25)));
                    world.playSound(center, Sound.ENTITY_ZOMBIE_INFECT, 0.6f,
                            (float) (0.7 + random.nextDouble(0.15)));
                    world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.35f, 1.4f);
                }
                for (UUID uuid : affected) {
                    LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
                    if (entity == null || entity.isDead()) {
                        continue;
                    }
                    if (entity.getLocation().getWorld() != world) {
                        continue;
                    }
                    if (entity.getLocation().distanceSquared(center) > radiusSquared) {
                        continue;
                    }
                    entity.damage(2.0, player);
                    Location display = entity.getLocation().clone().add(0, 1.0, 0);
                    Particle.DustOptions deepRed = new Particle.DustOptions(Color.fromRGB(220, 20, 20), 1.6f);
                    Particle.DustOptions brightRed = new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1.4f);
                    world.spawnParticle(Particle.REDSTONE, display, 32, 0.6, 0.9, 0.6, deepRed);
                    world.spawnParticle(Particle.REDSTONE, display, 20, 0.5, 0.7, 0.5, brightRed);
                    world.spawnParticle(Particle.CRIT_MAGIC, display.clone().add(0, 0.2, 0), 24, 0.4, 0.6, 0.4, 0.05);
                    world.spawnParticle(Particle.CRIT, display.clone().add(0, 0.2, 0), 18, 0.35, 0.45, 0.35, 0.02);
                    world.spawnParticle(Particle.SMOKE_NORMAL, display, 16, 0.4, 0.5, 0.4, 0.02);
                    world.spawnParticle(Particle.SMOKE_LARGE, display, 8, 0.35, 0.45, 0.35, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        data.reatsuTasks.add(damageTask);
    }

    private void releaseReatsuTargets(Location center, Set<UUID> affected, BankaiData data) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (UUID uuid : affected) {
            LivingEntity entity = (LivingEntity) Bukkit.getEntity(uuid);
            if (entity == null) {
                continue;
            }
            entity.removePotionEffect(PotionEffectType.SLOW);
            entity.removePotionEffect(PotionEffectType.JUMP);
            entity.removePotionEffect(PotionEffectType.CONFUSION);
            Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.2);
            knockback.setY(0.55);
            if (!Double.isNaN(knockback.length())) {
                entity.setVelocity(knockback);
            }
            entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, false, true));
            if (entity instanceof Player targetPlayer) {
                data.reatsuLockedPlayers.remove(targetPlayer.getUniqueId());
            }
        }
        Particle.DustOptions burst = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.8f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.4f);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 6, 0.8, 0.8, 0.8, 0.0);
        world.spawnParticle(Particle.EXPLOSION_NORMAL, center, 30, 1.2, 0.9, 1.2, 0.1);
        world.spawnParticle(Particle.REDSTONE, center, 200, 2.3, 1.0, 2.3, burst);
        world.spawnParticle(Particle.REDSTONE, center, 120, 1.4, 0.8, 1.4, white);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.85f);
        world.playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.0f);
    }

    private void beginStandCutscene(Player player, BankaiData data) {
        long now = Instant.now().toEpochMilli();
        data.standCooldownEnd = now + STAND_COOLDOWN_SECONDS * 1000L;
        data.standChanneling = true;
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            data.standPreviousMaxHealth = maxHealth.getBaseValue();
            maxHealth.setBaseValue(data.standPreviousMaxHealth + 20.0);
            player.setHealth(maxHealth.getBaseValue());
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 9, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 99, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 9, false, false, true));
        player.sendActionBar(Component.text("Steeling yourself...", NamedTextColor.RED));

        BukkitTask standTask = new BukkitRunnable() {
            @Override
            public void run() {
                finishStandCutscene(player, data);
            }
        }.runTaskLater(plugin, 100L);
        data.standTasks.add(standTask);
    }

    private void finishStandCutscene(Player player, BankaiData data) {
        if (!player.isOnline()) {
            return;
        }
        data.standChanneling = false;
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 240, 0, false, false, true));
        data.remainingSeconds = 20;
        data.timerLocked = true;
        sendTimerBar(player, data.remainingSeconds);
    }

    private boolean isPassable(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        return world.getBlockAt(location).isPassable() && world.getBlockAt(location.clone().add(0, 1, 0)).isPassable();
    }

    private void spawnDashTrail(List<Location> trail) {
        for (Location point : trail) {
            World world = point.getWorld();
            if (world == null) {
                continue;
            }
            Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.4f);
            Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.2f);
            world.spawnParticle(Particle.REDSTONE, point, 8, 0.2, 0.1, 0.2, red);
            world.spawnParticle(Particle.REDSTONE, point, 6, 0.2, 0.1, 0.2, black);
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private boolean applyDashImpact(Player player, Location center, Vector direction) {
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        world.spawnParticle(Particle.EXPLOSION_NORMAL, center, 12, 0.6, 0.4, 0.6, 0.05);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.6f);
        world.spawnParticle(Particle.REDSTONE, center.clone().add(0, 1, 0), 40, 0.8, 0.8, 0.8, red);
        world.playSound(center, Sound.ENTITY_WITHER_HURT, 0.6f, 1.2f);
        boolean hit = false;
        double radiusSquared = DASH_IMPACT_RADIUS * DASH_IMPACT_RADIUS;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(player)) {
                continue;
            }
            if (entity.getLocation().getWorld() != world) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            entity.damage(DASH_IMPACT_DAMAGE, player);
            Vector knockback = entity.getLocation().toVector().subtract(center.toVector()).normalize().multiply(0.9);
            knockback.setY(0.35);
            if (Double.isNaN(knockback.getX()) || Double.isNaN(knockback.getZ())) {
                knockback = direction.clone().multiply(0.9).setY(0.35);
            }
            entity.setVelocity(knockback);
            hit = true;
        }
        return hit;
    }

    public void reset(Player player) {
        endBankai(player, true);
    }

    public void clearAll() {
        for (UUID uuid : new ArrayList<>(states.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                endBankai(player, true);
            }
        }
        states.clear();
    }

    private void startTransformation(Player player) {
        BankaiData data = new BankaiData();
        data.transforming = true;
        states.put(player.getUniqueId(), data);

        sendTitleToNearby(player.getLocation(), Component.text("I had enought...", NamedTextColor.AQUA));
        spawnAura(player.getLocation());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);

        BukkitTask phaseTwo = new BukkitRunnable() {
            @Override
            public void run() {
                sendTitleToNearby(player.getLocation(), Component.text("BANKAI!", NamedTextColor.AQUA));
                data.beamTask = createBeamTask(player);
            }
        }.runTaskLater(plugin, 40L);

        BukkitTask phaseThree = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.beamTask != null) {
                    data.beamTask.cancel();
                }
                sendTitleToNearby(player.getLocation(), Component.text("Tensa Zangetsu.", NamedTextColor.RED));
                beginBankai(player, data);
            }
        }.runTaskLater(plugin, 80L);

        data.phaseTasks.add(phaseTwo);
        data.phaseTasks.add(phaseThree);
    }

    private void beginBankai(Player player, BankaiData data) {
        data.transforming = false;
        data.active = true;
        data.remainingSeconds = BANKAI_DURATION_SECONDS;
        data.combo = 0;
        data.cooldownEnd = 0L;
        data.dashCooldownEnd = 0L;
        data.reatsuCooldownEnd = 0L;
        data.standCooldownEnd = 0L;
        data.timerLocked = false;

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            data.originalMaxHealth = maxHealth.getBaseValue();
            maxHealth.setBaseValue(data.originalMaxHealth + 10.0);
            player.setHealth(maxHealth.getBaseValue());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BANKAI_DURATION_SECONDS * 20, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, BANKAI_DURATION_SECONDS * 20, 0, false, false, true));

        data.particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    endBankai(player, true);
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.2f);
                player.getWorld().spawnParticle(Particle.REDSTONE, loc, 12, 0.6, 0.7, 0.6, red);
            }
        }.runTaskTimer(plugin, 0L, 4L);

        data.timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBankai(player, true);
                    return;
                }
                if (data.standChanneling) {
                    sendTimerBar(player, data.remainingSeconds);
                    return;
                }
                data.remainingSeconds--;
                if (data.remainingSeconds < 0) {
                    data.remainingSeconds = 0;
                }
                sendTimerBar(player, data.remainingSeconds);
                if (data.remainingSeconds <= 0) {
                    endBankai(player, false);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        sendTimerBar(player, data.remainingSeconds);
        giveAdditionalItems(player);
    }

    private void attemptGetsuga(Player player, BankaiData data) {
        long now = Instant.now().toEpochMilli();
        if (data.cooldownEnd > now) {
            long remaining = (data.cooldownEnd - now + 999) / 1000;
            player.sendActionBar(Component.text("Getsuga cooling down: " + remaining + "s", NamedTextColor.YELLOW));
            return;
        }
        if (!data.timerLocked && data.remainingSeconds <= GETSUGA_COST_SECONDS) {
            player.sendActionBar(Component.text("Not enough Bankai time!", NamedTextColor.RED));
            return;
        }

        if (!data.timerLocked) {
            data.remainingSeconds -= GETSUGA_COST_SECONDS;
            if (data.remainingSeconds < 0) {
                data.remainingSeconds = 0;
            }
            sendTimerBar(player, data.remainingSeconds);
        }
        data.cooldownEnd = now + GETSUGA_COOLDOWN_SECONDS * 1000L;

        data.combo++;
        boolean tenshou = data.combo >= 3;
        if (tenshou) {
            data.combo = 0;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, tenshou ? 0.6f : 1.1f);
        spawnReadyParticles(player.getLocation(), tenshou);

        BukkitTask getsugaTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (tenshou) {
                    fireTenshou(player, data);
                } else {
                    fireSlash(player, data);
                }
            }
        }.runTaskLater(plugin, 6L);
        data.phaseTasks.add(getsugaTask);
    }

    private void fireSlash(Player player, BankaiData data) {
        if (!player.isOnline()) {
            return;
        }
        Location origin = player.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        World world = player.getWorld();
        Set<LivingEntity> hit = new HashSet<>();

        for (double i = 1; i <= GETSUGA_RANGE; i += 0.6) {
            Vector offset = dir.clone().multiply(i);
            Location point = origin.clone().add(offset);
            spawnSlashParticles(world, point, dir, false);
            damageCone(player, point, dir, SLASH_RADIUS, 1.6, SLASH_DAMAGE, 1.0, -2.0, hit);
        }
    }

    private void fireTenshou(Player player, BankaiData data) {
        if (!player.isOnline()) {
            return;
        }
        Location origin = player.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        World world = player.getWorld();
        Set<LivingEntity> hit = new HashSet<>();

        for (double i = 1; i <= GETSUGA_RANGE; i += 0.8) {
            Vector offset = dir.clone().multiply(i);
            Location point = origin.clone().add(offset);
            spawnSlashParticles(world, point, dir, true);
            damageCone(player, point, dir, TENSHOU_RADIUS, 6.5, TENSHOU_DAMAGE, 1.9, -2.5, hit);
        }
    }

    private void damageCone(Player player, Location point, Vector direction, double radius, double height, double damage,
            double knockbackStrength, double lowerBound, Set<LivingEntity> hit) {
        World world = point.getWorld();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.equals(player)) {
                continue;
            }
            Location entityLoc = entity.getLocation();
            if (entityLoc.getWorld() != world) {
                continue;
            }
            double dy = entityLoc.getY() - point.getY();
            if (dy < lowerBound || dy > height) {
                continue;
            }
            double distanceSquared = entityLoc.toVector().setY(0).distanceSquared(point.toVector().setY(0));
            if (distanceSquared > radius * radius) {
                continue;
            }
            Vector toEntity = entityLoc.toVector().subtract(point.toVector());
            double alignment = toEntity.normalize().dot(direction);
            if (alignment < 0.25) {
                continue;
            }
            if (hit.add(entity)) {
                entity.damage(damage, player);
                Vector kb = direction.clone().multiply(knockbackStrength);
                kb.setY(0.35);
                entity.setVelocity(kb);
            }
        }
    }

    private void spawnSlashParticles(World world, Location point, Vector dir, boolean tenshou) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 30, 30), tenshou ? 1.8f : 1.4f);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), tenshou ? 1.6f : 1.2f);

        Vector side = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        double height = tenshou ? 6.5 : 2.4;
        double width = tenshou ? 3.3 : 1.2;

        for (double y = -0.8; y <= height; y += 0.7) {
            double scale = tenshou ? 1.2 - (y / (height + 0.8)) * 0.35 : 1.0;
            for (double s = -width; s <= width; s += 0.6) {
                Location draw = point.clone().add(side.clone().multiply(s * scale)).add(0, y * 0.5, 0);
                world.spawnParticle(Particle.REDSTONE, draw, 2, 0.2, 0.2, 0.2, red);
                world.spawnParticle(Particle.REDSTONE, draw, 2, 0.1, 0.1, 0.1, black);
                if (!tenshou) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, draw, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
        if (tenshou) {
            for (double y = 0.5; y <= 3.0; y += 0.5) {
                Location streakCenter = point.clone().add(0, y, 0);
                world.spawnParticle(Particle.REDSTONE, streakCenter, 10, 0.4, 0.6, 0.4, red);
                world.spawnParticle(Particle.REDSTONE, streakCenter, 8, 0.3, 0.5, 0.3, black);
                world.spawnParticle(Particle.CRIT_MAGIC, streakCenter, 6, 0.2, 0.4, 0.2, 0.01);
            }
            world.playSound(point, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.6f);
        } else {
            world.playSound(point, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 1.2f);
        }
    }

    private void sendTimerBar(Player player, int remainingSeconds) {
        player.sendActionBar(Component.text("Bankai time left: " + remainingSeconds + "s", NamedTextColor.RED));
    }

    private void giveAdditionalItems(Player player) {
        player.getInventory().addItem(createReatsuItem());
        player.getInventory().addItem(createStandItem());
    }

    private void removeSpecialItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                continue;
            }
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if (container.has(reatsuItemKey, PersistentDataType.BYTE)
                    || container.has(standItemKey, PersistentDataType.BYTE)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
    }

    private BukkitTask createBeamTask(Player player) {
        return new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60) {
                    cancel();
                    return;
                }
                ticks++;
                World world = player.getWorld();
                Location base = player.getLocation().clone();
                for (double y = 0; y <= 7; y += 0.5) {
                    for (double x = -1; x <= 1; x += 0.4) {
                        for (double z = -1; z <= 1; z += 0.4) {
                            Location cloud = base.clone().add(x, y, z);
                            Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.4f);
                            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.2f);
                            world.spawnParticle(Particle.REDSTONE, cloud, 3, 0.1, 0.1, 0.1, cyan);
                            world.spawnParticle(Particle.REDSTONE, cloud, 2, 0.1, 0.1, 0.1, white);
                            world.spawnParticle(Particle.CLOUD, cloud, 1, 0.0, 0.0, 0.0, 0.0);
                        }
                    }
                }
                world.playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawnAura(Location center) {
        World world = center.getWorld();
        for (int i = 0; i < 150; i++) {
            double radius = 10.0 * Math.random();
            double angle = Math.random() * Math.PI * 2;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0.3 + Math.random() * 1.5, z);
            world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0.0);
            world.spawnParticle(Particle.CLOUD, loc, 1, 0.2, 0.2, 0.2, 0.01);
            world.spawnParticle(Particle.SNOWFLAKE, loc, 1, 0.1, 0.1, 0.1, 0.01);
            Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.0f);
            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.0f);
            world.spawnParticle(Particle.REDSTONE, loc, 2, 0.3, 0.3, 0.3, cyan);
            world.spawnParticle(Particle.REDSTONE, loc, 2, 0.3, 0.3, 0.3, white);
        }
    }

    private void spawnReadyParticles(Location loc, boolean tenshou) {
        World world = loc.getWorld();
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 0, 0), tenshou ? 1.8f : 1.3f);
        world.spawnParticle(Particle.REDSTONE, loc.clone().add(0, 1, 0), tenshou ? 50 : 25, 1, 1.2, 1, red);
        world.spawnParticle(Particle.SMOKE_LARGE, loc, tenshou ? 25 : 12, 0.5, 0.5, 0.5, 0.02);
    }

    private void sendTitleToNearby(Location center, Component title) {
        for (Player viewer : center.getWorld().getPlayers()) {
            if (viewer.getLocation().distanceSquared(center) <= 50 * 50) {
                viewer.showTitle(net.kyori.adventure.title.Title.title(title, Component.empty()));
            }
        }
    }

    private void endBankai(Player player, boolean clearState) {
        BankaiData data = states.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        data.cancelAll();
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.CONFUSION);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.JUMP);

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null && data.originalMaxHealth > 0) {
            maxHealth.setBaseValue(data.originalMaxHealth);
            if (player.getHealth() > maxHealth.getBaseValue()) {
                player.setHealth(maxHealth.getBaseValue());
            }
        }
        removeSpecialItems(player);
        for (UUID locked : new HashSet<>(data.reatsuLockedPlayers)) {
            Player target = Bukkit.getPlayer(locked);
            if (target != null) {
                target.removePotionEffect(PotionEffectType.SLOW);
                target.removePotionEffect(PotionEffectType.JUMP);
                target.removePotionEffect(PotionEffectType.CONFUSION);
                target.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }
        data.reatsuLockedPlayers.clear();
        data.active = false;
        data.transforming = false;
        data.combo = 0;
        data.cooldownEnd = 0L;
        data.dashCooldownEnd = 0L;
        states.remove(player.getUniqueId());
        player.sendActionBar(Component.text("Bankai ended.", NamedTextColor.GRAY));
    }

    private static class BankaiData {
        private boolean active;
        private boolean transforming;
        private int remainingSeconds;
        private double originalMaxHealth;
        private int combo;
        private long cooldownEnd;
        private long dashCooldownEnd;
        private long reatsuCooldownEnd;
        private long standCooldownEnd;
        private boolean timerLocked;
        private boolean standChanneling;
        private double standPreviousMaxHealth;
        private BukkitTask particleTask;
        private BukkitTask timerTask;
        private BukkitTask beamTask;
        private final List<BukkitTask> reatsuTasks = new ArrayList<>();
        private final List<BukkitTask> standTasks = new ArrayList<>();
        private final List<BukkitTask> phaseTasks = new ArrayList<>();
        private final Set<UUID> reatsuLockedPlayers = new HashSet<>();

        private void cancelAll() {
            if (particleTask != null) {
                particleTask.cancel();
            }
            if (timerTask != null) {
                timerTask.cancel();
            }
            if (beamTask != null) {
                beamTask.cancel();
            }
            for (BukkitTask task : phaseTasks) {
                task.cancel();
            }
            for (BukkitTask task : reatsuTasks) {
                task.cancel();
            }
            for (BukkitTask task : standTasks) {
                task.cancel();
            }
            standChanneling = false;
        }
    }
}
