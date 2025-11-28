package com.example.blackflash;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private static final int GETSUGA_COOLDOWN_SECONDS = 15;
    private static final int DASH_COOLDOWN_SECONDS = 9;
    private static final int DASH_COST_SECONDS = 5;
    private static final double GETSUGA_RANGE = 15.0;
    private static final double SLASH_RADIUS = 0.9;
    private static final double TENSHOU_RADIUS = 2.6;
    private static final double SLASH_DAMAGE = 8.0;
    private static final double TENSHOU_DAMAGE = 14.0;
    private static final double DASH_DISTANCE = 5.0;
    private static final double DASH_IMPACT_DAMAGE = 10.0;
    private static final double DASH_IMPACT_RADIUS = 2.0;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey bankaiItemKey;
    private final Map<UUID, BankaiData> states = new HashMap<>();

    public AdvancedBankaiAbility(BlackFlashPlugin plugin, NamespacedKey bankaiItemKey) {
        this.plugin = plugin;
        this.bankaiItemKey = bankaiItemKey;
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

        if (data.transforming) {
            return;
        }
        if (player.isSneaking()) {
            attemptDash(player, data);
        } else {
            attemptGetsuga(player, data);
        }
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
        if (data.remainingSeconds <= DASH_COST_SECONDS) {
            player.sendActionBar(Component.text("Not enough Bankai time!", NamedTextColor.RED));
            return;
        }

        data.remainingSeconds -= DASH_COST_SECONDS;
        sendTimerBar(player, data.remainingSeconds);
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

        applyDashImpact(player, lastSafe, direction);
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

    private void applyDashImpact(Player player, Location center, Vector direction) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_NORMAL, center, 12, 0.6, 0.4, 0.6, 0.05);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.6f);
        world.spawnParticle(Particle.REDSTONE, center.clone().add(0, 1, 0), 40, 0.8, 0.8, 0.8, red);
        world.playSound(center, Sound.ENTITY_WITHER_HURT, 0.6f, 1.2f);

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
        }
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
    }

    private void attemptGetsuga(Player player, BankaiData data) {
        long now = Instant.now().toEpochMilli();
        if (data.cooldownEnd > now) {
            long remaining = (data.cooldownEnd - now + 999) / 1000;
            player.sendActionBar(Component.text("Getsuga cooling down: " + remaining + "s", NamedTextColor.YELLOW));
            return;
        }
        if (data.remainingSeconds <= GETSUGA_COST_SECONDS) {
            player.sendActionBar(Component.text("Not enough Bankai time!", NamedTextColor.RED));
            return;
        }

        data.remainingSeconds -= GETSUGA_COST_SECONDS;
        if (data.remainingSeconds < 0) {
            data.remainingSeconds = 0;
        }
        sendTimerBar(player, data.remainingSeconds);
        data.cooldownEnd = now + GETSUGA_COOLDOWN_SECONDS * 1000L;

        data.combo++;
        boolean tenshou = data.combo >= 3;
        if (tenshou) {
            data.combo = 0;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, tenshou ? 0.6f : 1.1f);
        spawnReadyParticles(player.getLocation(), tenshou);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (tenshou) {
                    fireTenshou(player, data);
                } else {
                    fireSlash(player, data);
                }
            }
        }.runTaskLater(plugin, 6L);
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
            damageCone(player, point, dir, SLASH_RADIUS, 1.2, SLASH_DAMAGE, 1.0, hit);
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
            damageCone(player, point, dir, TENSHOU_RADIUS, 5.0, TENSHOU_DAMAGE, 1.7, hit);
        }
    }

    private void damageCone(Player player, Location point, Vector direction, double radius, double height, double damage, double knockbackStrength, Set<LivingEntity> hit) {
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
            if (dy < -1.0 || dy > height) {
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
        double height = tenshou ? 5.0 : 2.0;
        double width = tenshou ? 2.5 : 0.9;

        for (double y = -0.5; y <= height; y += 0.8) {
            double scale = tenshou ? 1.2 - (y / (height + 0.5)) * 0.3 : 1.0;
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
            world.playSound(point, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.6f);
        } else {
            world.playSound(point, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.4f, 1.2f);
        }
    }

    private void sendTimerBar(Player player, int remainingSeconds) {
        player.sendActionBar(Component.text("Bankai time left: " + remainingSeconds + "s", NamedTextColor.RED));
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

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null && data.originalMaxHealth > 0) {
            maxHealth.setBaseValue(data.originalMaxHealth);
            if (player.getHealth() > maxHealth.getBaseValue()) {
                player.setHealth(maxHealth.getBaseValue());
            }
        }
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
        private BukkitTask particleTask;
        private BukkitTask timerTask;
        private BukkitTask beamTask;
        private final List<BukkitTask> phaseTasks = new ArrayList<>();

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
        }
    }
}
