package com.example.bankai;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

public class BankaiManager {
    private static final int BANKAI_DURATION_SECONDS = 90;
    private static final int GETSUGA_COST_SECONDS = 10;
    private static final int GETSUGA_COOLDOWN_SECONDS = 15;
    private static final Set<Particle> AURA_PARTICLES = EnumSet.of(
            Particle.END_ROD,
            Particle.CLOUD,
            Particle.SNOWFLAKE,
            Particle.REVERSE_PORTAL
    );

    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, BankaiState> states = new HashMap<>();
    private final Map<UUID, Long> getsugaCooldowns = new HashMap<>();

    public BankaiManager(Plugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "bankai_item");
    }

    public NamespacedKey getItemKey() {
        return itemKey;
    }

    public boolean isBankaiActive(UUID playerId) {
        return states.containsKey(playerId);
    }

    public ItemStack createBankaiItem() {
        ItemStack sword = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "Tensa Zangetsu");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Unleash Bankai."));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    public boolean isBankaiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(itemKey, PersistentDataType.INTEGER);
    }

    public void startBankai(Player player) {
        if (isBankaiActive(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Bankai is already active.");
            return;
        }
        runPhaseOne(player);
    }

    private void runPhaseOne(Player player) {
        spawnAura(player.getLocation());
        Bukkit.broadcastMessage(ChatColor.AQUA + "I had enought...");
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runPhaseTwo(player), 20L);
    }

    private void runPhaseTwo(Player player) {
        Location origin = player.getLocation();
        for (int i = 0; i < 4; i++) {
            final int offsetY = i;
            new BukkitRunnable() {
                private int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 20) {
                        cancel();
                        return;
                    }
                    spawnBeam(origin.clone().add(0, offsetY, 0));
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        Bukkit.broadcastMessage(ChatColor.AQUA + "BANKAI!");
        player.getWorld().playSound(origin, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);
        player.getWorld().playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> runPhaseThree(player), 20L);
    }

    private void runPhaseThree(Player player) {
        Bukkit.broadcastMessage(ChatColor.RED + "Tensa Zangetsu.");
        activateBankai(player);
    }

    private void activateBankai(Player player) {
        UUID id = player.getUniqueId();
        double originalMax = getMaxHealth(player);
        long endTime = System.currentTimeMillis() + BANKAI_DURATION_SECONDS * 1000L;
        BankaiState state = new BankaiState(id, originalMax, endTime);
        states.put(id, state);

        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.setBaseValue(originalMax + 10.0);
            player.setHealth(healthAttribute.getBaseValue());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BANKAI_DURATION_SECONDS * 20, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, BANKAI_DURATION_SECONDS * 20, 0, false, false, false));

        BukkitTask particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isBankaiActive(id)) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 10, 0.5, 0.8, 0.5, new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f));
            }
        }.runTaskTimer(plugin, 0L, 5L);
        state.setParticleTask(particleTask);

        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBankai(player, false);
                    cancel();
                    return;
                }
                long remaining = (state.getBankaiEndTimeMillis() - System.currentTimeMillis()) / 1000L;
                if (remaining <= 0) {
                    player.sendActionBar(Component.text(ChatColor.RED + "Bankai expired."));
                    endBankai(player, true);
                    cancel();
                    return;
                }
                player.sendActionBar(Component.text(ChatColor.RED + "Bankai time left: " + remaining + "s"));
            }
        }.runTaskTimer(plugin, 0L, 20L);
        state.setTimerTask(timerTask);
    }

    private void spawnAura(Location origin) {
        for (int i = 0; i < 50; i++) {
            double offsetX = (Math.random() - 0.5) * 10;
            double offsetZ = (Math.random() - 0.5) * 10;
            double offsetY = Math.random() * 2;
            Particle particle = AURA_PARTICLES.stream().skip((int) (Math.random() * AURA_PARTICLES.size())).findFirst().orElse(Particle.END_ROD);
            origin.getWorld().spawnParticle(particle, origin.clone().add(offsetX, offsetY, offsetZ), 5, 0.1, 0.1, 0.1, 0);
        }
        origin.getWorld().spawnParticle(Particle.REDSTONE, origin, 30, 5, 2, 5, new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.2f));
    }

    private void spawnBeam(Location center) {
        for (double y = 0; y < 7; y += 0.5) {
            for (double x = -1; x <= 1; x += 0.3) {
                for (double z = -1; z <= 1; z += 0.3) {
                    Location point = center.clone().add(x, y, z);
                    center.getWorld().spawnParticle(Particle.END_ROD, point, 0, 0, 0, 0);
                    center.getWorld().spawnParticle(Particle.CLOUD, point, 0, 0, 0, 0);
                    center.getWorld().spawnParticle(Particle.REDSTONE, point, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(200, 230, 255), 1.2f));
                }
            }
        }
    }

    private double getMaxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) {
            return attribute.getBaseValue();
        }
        return 20.0;
    }

    public void endBankai(Player player, boolean naturalExpire) {
        UUID id = player.getUniqueId();
        BankaiState state = states.remove(id);
        getsugaCooldowns.remove(id);
        if (state != null) {
            if (state.getParticleTask() != null) {
                state.getParticleTask().cancel();
            }
            if (state.getTimerTask() != null) {
                state.getTimerTask().cancel();
            }
            AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(state.getOriginalMaxHealth());
                if (player.getHealth() > attribute.getBaseValue()) {
                    player.setHealth(attribute.getBaseValue());
                }
            }
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        if (!naturalExpire) {
            player.sendMessage(ChatColor.RED + "Your Bankai has been reset.");
        }
    }

    public boolean isGetsugaOnCooldown(Player player) {
        Long until = getsugaCooldowns.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    public long getGetsugaCooldownSeconds(Player player) {
        Long until = getsugaCooldowns.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long diff = until - System.currentTimeMillis();
        return Math.max(0, (diff + 999) / 1000);
    }

    public boolean hasSufficientBankaiTime(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        long remaining = state.getBankaiEndTimeMillis() - System.currentTimeMillis();
        return remaining >= GETSUGA_COST_SECONDS * 1000L;
    }

    public void useGetsuga(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage(ChatColor.RED + "You must be in Bankai to use Getsuga.");
            return;
        }
        if (isGetsugaOnCooldown(player)) {
            player.sendMessage(ChatColor.RED + "Getsuga cooldown: " + getGetsugaCooldownSeconds(player) + "s remaining.");
            return;
        }
        if (!hasSufficientBankaiTime(player)) {
            player.sendMessage(ChatColor.RED + "Not enough Bankai time for Getsuga.");
            return;
        }
        long newEnd = state.getBankaiEndTimeMillis() - GETSUGA_COST_SECONDS * 1000L;
        state.setBankaiEndTimeMillis(newEnd);
        getsugaCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + GETSUGA_COOLDOWN_SECONDS * 1000L);
        performGetsuga(player);
    }

    private void performGetsuga(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 15, 5, false, false, false));
        Location start = player.getEyeLocation();
        start.getWorld().spawnParticle(Particle.SMOKE_LARGE, start, 30, 0.5, 0.5, 0.5, 0.01);
        start.getWorld().spawnParticle(Particle.REDSTONE, start, 20, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1.2f));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> fireWave(player), 10L);
    }

    private void fireWave(Player player) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        Set<UUID> hit = new java.util.HashSet<>();
        new BukkitRunnable() {
            private int steps = 0;
            @Override
            public void run() {
                steps++;
                if (steps > 15 || origin.getWorld() == null) {
                    cancel();
                    return;
                }
                Location point = origin.add(direction.clone().multiply(1));
                if (point.getBlock().getType().isSolid()) {
                    cancel();
                    return;
                }
                origin.getWorld().spawnParticle(Particle.REDSTONE, point, 10, 0.2, 0.2, 0.2, new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f));
                origin.getWorld().spawnParticle(Particle.SWEEP_ATTACK, point, 5, 0.1, 0.1, 0.1, 0);
                origin.getWorld().spawnParticle(Particle.CRIT, point, 5, 0.1, 0.1, 0.1, 0.01);
                for (LivingEntity entity : point.getNearbyLivingEntities(1.0)) {
                    if (entity.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    if (hit.contains(entity.getUniqueId())) {
                        continue;
                    }
                    hit.add(entity.getUniqueId());
                    entity.damage(15.0, player);
                    Vector knock = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.4).setY(0.4);
                    entity.setVelocity(knock);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void handleQuit(Player player) {
        if (isBankaiActive(player.getUniqueId())) {
            endBankai(player, true);
        }
        getsugaCooldowns.remove(player.getUniqueId());
    }
}
