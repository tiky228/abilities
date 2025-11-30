package com.example.blackflash;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class CeroOscurasAbility {

    public enum CeroVariant {
        RED(ChatColor.DARK_RED + "Cero Oscuras (Red)", Color.fromRGB(135, 10, 10), Color.fromRGB(8, 8, 8)),
        BLUE(ChatColor.AQUA + "Cero Oscuras (Blue)", Color.fromRGB(20, 80, 200), Color.fromRGB(60, 150, 255)),
        GREEN(ChatColor.GREEN + "Cero Oscuras (Green)", Color.fromRGB(70, 230, 80), Color.fromRGB(170, 255, 170)),
        CYAN(ChatColor.DARK_AQUA + "Cero Oscuras (Cyan)", Color.fromRGB(30, 215, 240), Color.fromRGB(150, 255, 255));

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

    private static final int CERO_COOLDOWN_SECONDS = 12;
    private static final double BEAM_LENGTH = 16.0;
    private static final double BEAM_RADIUS = 1.5;
    private static final int BEAM_DURATION_TICKS = 36;
    private static final int DAMAGE_INTERVAL_TICKS = 5;
    private static final double DAMAGE = 6.0;

    private final BlackFlashPlugin plugin;
    private final AbilityRestrictionManager restrictionManager;
    private final Map<CeroVariant, NamespacedKey> ceroKeys = new EnumMap<>(CeroVariant.class);
    private final CooldownManager ceroCooldown = new CooldownManager();
    private final Map<UUID, List<CeroBeam>> activeBeams = new HashMap<>();

    public CeroOscurasAbility(BlackFlashPlugin plugin, AbilityRestrictionManager restrictionManager,
            NamespacedKey redKey, NamespacedKey blueKey, NamespacedKey greenKey, NamespacedKey cyanKey) {
        this.plugin = plugin;
        this.restrictionManager = restrictionManager;
        this.ceroKeys.put(CeroVariant.RED, redKey);
        this.ceroKeys.put(CeroVariant.BLUE, blueKey);
        this.ceroKeys.put(CeroVariant.GREEN, greenKey);
        this.ceroKeys.put(CeroVariant.CYAN, cyanKey);
    }

    public ItemStack createCeroItem(CeroVariant variant) {
        ItemStack stack = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(variant.getDisplayName());
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Fire a massive fixed beam.",
                    ChatColor.RED + "Right-click to unleash."));
            meta.getPersistentDataContainer().set(ceroKeys.get(variant), PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isCeroItem(ItemStack stack, CeroVariant variant) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(ceroKeys.get(variant), PersistentDataType.INTEGER);
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
        fireBeam(player, variant);
    }

    private void fireBeam(Player player, CeroVariant variant) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        player.getWorld().playSound(origin, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.75f);
        player.getWorld().playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 0.85f, 0.78f);
        player.getWorld().playSound(origin, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.65f, 0.72f);
        player.sendMessage(ChatColor.GRAY + "Cero Oscuras unleashed!");

        CeroBeam beam = new CeroBeam(origin, direction, variant);
        activeBeams.computeIfAbsent(player.getUniqueId(), k -> new java.util.ArrayList<>()).add(beam);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= BEAM_DURATION_TICKS) {
                    beam.finish();
                    removeBeam(player, beam);
                    cancel();
                    return;
                }
                spawnBeamParticles(beam);
                if (ticks % DAMAGE_INTERVAL_TICKS == 0) {
                    applyDamage(beam, player);
                }
                if (ticks % 4 == 0) {
                    Location beamCenter = origin.clone().add(direction.clone().multiply(BEAM_LENGTH / 2.0));
                    player.getWorld().playSound(beamCenter, Sound.BLOCK_BEACON_AMBIENT, 0.28f, 0.86f);
                    player.getWorld().playSound(beamCenter, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.22f, 0.82f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        beam.setTask(task);
    }

    private void spawnBeamParticles(CeroBeam beam) {
        World world = beam.origin.getWorld();
        if (world == null) {
            return;
        }
        Particle.DustOptions primary = new Particle.DustOptions(beam.variant.getPrimary(), 1.6f);
        Particle.DustOptions secondary = new Particle.DustOptions(beam.variant.getSecondary(), 1.2f);

        Vector dir = beam.direction;
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) {
            right = dir.clone().crossProduct(new Vector(1, 0, 0));
        }
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        Location startCenter = beam.origin.clone();
        Location endCenter = beam.origin.clone().add(dir.clone().multiply(BEAM_LENGTH));

        spawnSphere(world, startCenter, primary, secondary);

        double startDistance = BEAM_RADIUS * 0.6;
        double endDistance = BEAM_LENGTH - BEAM_RADIUS * 0.6;
        double step = 0.45;
        for (double dist = startDistance; dist <= endDistance; dist += step) {
            Location center = beam.origin.clone().add(dir.clone().multiply(dist));
            world.spawnParticle(Particle.REDSTONE, center, 8, 0.08, 0.08, 0.08, primary);
            for (int i = 0; i < 22; i++) {
                double angle = (Math.PI * 2 / 22) * i;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                Vector offset = right.clone().multiply(cos * BEAM_RADIUS).add(up.clone().multiply(sin * BEAM_RADIUS));
                Location edge = center.clone().add(offset);
                world.spawnParticle(Particle.REDSTONE, edge, 2, 0.035, 0.035, 0.035, secondary);
                if (i % 2 == 0) {
                    Vector innerOffset = offset.clone().multiply(0.6);
                    Location inner = center.clone().add(innerOffset);
                    world.spawnParticle(Particle.REDSTONE, inner, 2, 0.02, 0.02, 0.02, primary);
                }
            }
        }

        spawnSphere(world, endCenter, primary, secondary);
    }

    private void applyDamage(CeroBeam beam, Player owner) {
        World world = owner.getWorld();
        double maxDistance = BEAM_LENGTH + BEAM_RADIUS + 1;
        for (LivingEntity entity : world.getNearbyLivingEntities(beam.origin, maxDistance)) {
            if (entity.equals(owner)) {
                continue;
            }
            if (!beam.isInside(entity.getLocation())) {
                continue;
            }
            beam.tryDamage(entity, owner);
        }
    }

    public void clearAll() {
        for (List<CeroBeam> beams : activeBeams.values()) {
            for (CeroBeam beam : beams) {
                beam.finish();
            }
        }
        activeBeams.clear();
        ceroCooldown.clearAll();
    }

    public void clearPlayer(Player player) {
        List<CeroBeam> beams = activeBeams.remove(player.getUniqueId());
        if (beams != null) {
            for (CeroBeam beam : beams) {
                beam.finish();
            }
        }
        ceroCooldown.clear(player.getUniqueId());
    }

    private void removeBeam(Player player, CeroBeam beam) {
        List<CeroBeam> beams = activeBeams.get(player.getUniqueId());
        if (beams == null) {
            return;
        }
        beams.remove(beam);
        if (beams.isEmpty()) {
            activeBeams.remove(player.getUniqueId());
        }
    }

    private class CeroBeam {
        private final Location origin;
        private final Vector direction;
        private final CeroVariant variant;
        private final Map<UUID, Integer> lastDamage = new HashMap<>();
        private BukkitTask task;

        CeroBeam(Location origin, Vector direction, CeroVariant variant) {
            this.origin = origin.clone();
            this.direction = direction.clone();
            this.variant = variant;
        }

        void setTask(BukkitTask task) {
            this.task = task;
        }

        boolean isInside(Location location) {
            Vector toTarget = location.toVector().subtract(origin.toVector());
            if (toTarget.lengthSquared() <= (BEAM_RADIUS + 0.3) * (BEAM_RADIUS + 0.3)) {
                return true;
            }

            double projection = toTarget.dot(direction);
            if (projection < 0) {
                return false;
            }
            if (projection > BEAM_LENGTH) {
                Vector fromEnd = location.toVector()
                        .subtract(origin.toVector().add(direction.clone().multiply(BEAM_LENGTH)));
                return fromEnd.lengthSquared() <= (BEAM_RADIUS + 0.3) * (BEAM_RADIUS + 0.3);
            }
            Vector closest = direction.clone().multiply(projection);
            double distance = toTarget.subtract(closest).length();
            return distance <= BEAM_RADIUS + 0.3;
        }

        void tryDamage(LivingEntity entity, Player owner) {
            int currentTick = owner.getServer().getCurrentTick();
            Integer last = lastDamage.get(entity.getUniqueId());
            if (last != null && currentTick - last < DAMAGE_INTERVAL_TICKS) {
                return;
            }
            lastDamage.put(entity.getUniqueId(), currentTick);
            entity.damage(DAMAGE, owner);
            Vector push = direction.clone().multiply(0.45);
            push.setY(0.2);
            entity.setVelocity(entity.getVelocity().add(push));
        }

        void finish() {
            if (task != null) {
                task.cancel();
            }
            World world = origin.getWorld();
            if (world != null) {
                world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.65f, 0.66f);
                world.playSound(origin, Sound.ENTITY_WITHER_DEATH, 0.55f, 0.6f);
            }
        }
    }

    private void spawnSphere(World world, Location center, Particle.DustOptions primary, Particle.DustOptions secondary) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int samples = 85;
        for (int i = 0; i < samples; i++) {
            double radius = BEAM_RADIUS * Math.cbrt(random.nextDouble());
            double theta = random.nextDouble(0, Math.PI * 2);
            double phi = Math.acos(1 - 2 * random.nextDouble());
            double sinPhi = Math.sin(phi);
            Vector offset = new Vector(radius * sinPhi * Math.cos(theta), radius * Math.cos(phi),
                    radius * sinPhi * Math.sin(theta));
            Location particleLoc = center.clone().add(offset);
            Particle.DustOptions option = (i % 3 == 0) ? secondary : primary;
            world.spawnParticle(Particle.REDSTONE, particleLoc, 1, 0.04, 0.04, 0.04, option);
        }
    }
}
