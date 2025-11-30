package com.example.blackflash;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * Handles creation of the Bankai item, transformation sequence, active buffs, and Getsuga Tenshou ability.
 */
public class BankaiAbility {

    private static final int BANKAI_DURATION_SECONDS = 90;
    private static final int GETSUGA_COST_SECONDS = 10;
    private static final int GETSUGA_COOLDOWN_SECONDS = 7;
    private static final int GETSUGA_RANGE = 15;
    private static final int GETSUGA_DAMAGE = 15;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey bankaiItemKey;
    private final Map<UUID, BankaiState> states = new HashMap<>();

    public BankaiAbility(BlackFlashPlugin plugin, NamespacedKey bankaiItemKey) {
        this.plugin = plugin;
        this.bankaiItemKey = bankaiItemKey;
    }

    public ItemStack createBankaiItem() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Tensa Zangetsu");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Unleash Bankai.");
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
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte marker = data.get(bankaiItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean isInBankai(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        return state != null && state.active;
    }

    public void handleItemUse(Player player) {
        BankaiState existing = states.get(player.getUniqueId());
        if (existing != null && (existing.transforming || existing.active)) {
            if (existing.active) {
                tryCastGetsuga(player, existing);
            } else {
                player.sendMessage(ChatColor.YELLOW + "Bankai is already charging.");
            }
            return;
        }
        startTransformation(player);
    }

    private void startTransformation(Player player) {
        BankaiState state = new BankaiState();
        state.transforming = true;
        states.put(player.getUniqueId(), state);

        World world = player.getWorld();
        broadcastMessage(ChatColor.AQUA + "I had enought...");
        spawnAuraParticles(player.getLocation());
        world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.1f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);

        BukkitTask phaseTwo = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastMessage(ChatColor.AQUA + "BANKAI!");
                state.beamTask = createBeamTask(player);
            }
        }.runTaskLater(plugin, 20L);

        BukkitTask phaseThree = new BukkitRunnable() {
            @Override
            public void run() {
                if (state.beamTask != null) {
                    state.beamTask.cancel();
                }
                broadcastMessage(ChatColor.RED + "Tensa Zangetsu.");
                beginBankai(player, state);
            }
        }.runTaskLater(plugin, 40L);

        state.transformationTasks.add(phaseTwo);
        state.transformationTasks.add(phaseThree);
    }

    private BukkitTask createBeamTask(Player player) {
        BukkitRunnable runnable = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) {
                    cancel();
                    return;
                }
                ticks++;
                Location loc = player.getLocation();
                World world = loc.getWorld();
                for (double y = 0; y <= 7; y += 0.5) {
                    world.spawnParticle(Particle.END_ROD, loc.getX(), loc.getY() + y, loc.getZ(), 12, 1.0, 0.0, 1.0, 0.01);
                    world.spawnParticle(Particle.CLOUD, loc.getX(), loc.getY() + y, loc.getZ(), 8, 1.0, 0.0, 1.0, 0.03);
                    world.spawnParticle(Particle.SNOWFLAKE, loc.getX(), loc.getY() + y, loc.getZ(), 8, 1.0, 0.0, 1.0, 0.02);
                    Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.2f);
                    Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.1f);
                    world.spawnParticle(Particle.REDSTONE, loc.clone().add(0, y, 0), 15, 0.9, 0.1, 0.9, cyan);
                    world.spawnParticle(Particle.REDSTONE, loc.clone().add(0, y, 0), 15, 0.9, 0.1, 0.9, white);
                }
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.7f);
            }
        };
        return runnable.runTaskTimer(plugin, 0L, 1L);
    }

    private void beginBankai(Player player, BankaiState state) {
        state.transforming = false;
        state.active = true;
        state.remainingSeconds = BANKAI_DURATION_SECONDS;
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            state.originalMaxHealth = health.getBaseValue();
            health.setBaseValue(state.originalMaxHealth + 10.0);
            player.setHealth(health.getBaseValue());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BANKAI_DURATION_SECONDS * 20, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, BANKAI_DURATION_SECONDS * 20, 0, false, false, true));

        state.particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    endBankai(player);
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.0f);
                player.getWorld().spawnParticle(Particle.REDSTONE, loc, 8, 0.4, 0.6, 0.4, red);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        state.countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endBankai(player);
                    return;
                }
                state.remainingSeconds--;
                sendActionBar(player, ChatColor.RED + "Bankai time left: " + state.remainingSeconds + "s");
                if (state.remainingSeconds <= 0) {
                    endBankai(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnAuraParticles(Location center) {
        World world = center.getWorld();
        for (int i = 0; i < 80; i++) {
            double radius = 10.0;
            double angle = Math.random() * Math.PI * 2;
            double distance = Math.random() * radius;
            double xOffset = Math.cos(angle) * distance;
            double zOffset = Math.sin(angle) * distance;
            Location loc = center.clone().add(xOffset, 0.5 + Math.random() * 1.5, zOffset);
            world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0.02);
            world.spawnParticle(Particle.CLOUD, loc, 1, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticle(Particle.SNOWFLAKE, loc, 1, 0.1, 0.1, 0.1, 0.01);
            Particle.DustOptions cyan = new Particle.DustOptions(Color.AQUA, 1.0f);
            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.0f);
            world.spawnParticle(Particle.REDSTONE, loc, 1, 0.2, 0.2, 0.2, cyan);
            world.spawnParticle(Particle.REDSTONE, loc, 1, 0.2, 0.2, 0.2, white);
        }
    }

    private void tryCastGetsuga(Player player, BankaiState state) {
        long now = Instant.now().toEpochMilli();
        if (state.getsugaCooldownEnd > now) {
            long remaining = (state.getsugaCooldownEnd - now + 999) / 1000;
            player.sendMessage(ChatColor.YELLOW + "Getsuga Tenshou cooling down: " + remaining + "s");
            return;
        }
        if (state.remainingSeconds <= GETSUGA_COST_SECONDS) {
            player.sendMessage(ChatColor.RED + "Not enough Bankai time to cast Getsuga.");
            return;
        }

        state.remainingSeconds -= GETSUGA_COST_SECONDS;
        sendActionBar(player, ChatColor.RED + "Bankai time left: " + state.remainingSeconds + "s");
        state.getsugaCooldownEnd = now + GETSUGA_COOLDOWN_SECONDS * 1000L;

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 15, 5, false, false, true));
        spawnStanceParticles(player.getLocation());

        BukkitTask getsugaTask = new BukkitRunnable() {
            @Override
            public void run() {
                fireGetsuga(player, state);
            }
        }.runTaskLater(plugin, 12L);
        state.transformationTasks.add(getsugaTask);
    }

    private void spawnStanceParticles(Location loc) {
        World world = loc.getWorld();
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.0f);
        world.spawnParticle(Particle.REDSTONE, loc.add(0, 1, 0), 20, 0.5, 0.6, 0.5, dark);
        world.spawnParticle(Particle.SMOKE_LARGE, loc, 12, 0.6, 0.4, 0.6, 0.02);
        world.spawnParticle(Particle.ASH, loc, 8, 0.5, 0.3, 0.5, 0.01);
    }

    private void fireGetsuga(Player player, BankaiState state) {
        if (!player.isOnline()) {
            return;
        }
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World world = player.getWorld();
        Set<LivingEntity> hit = new HashSet<>();

        for (int i = 1; i <= GETSUGA_RANGE; i++) {
            Vector step = direction.clone().multiply(i);
            Location point = start.clone().add(step);
            if (point.getBlock().getType().isSolid()) {
                break;
            }
            spawnGetsugaParticles(world, point);
            for (LivingEntity entity : player.getWorld().getLivingEntities()) {
                if (entity.equals(player)) {
                    continue;
                }
                if (entity.getLocation().distanceSquared(point) <= 1.0) {
                    if (hit.add(entity)) {
                        entity.damage(GETSUGA_DAMAGE, player);
                        Vector knockback = entity.getLocation().toVector().subtract(player.getLocation().toVector())
                                .normalize().multiply(1.2);
                        knockback.setY(0.35);
                        entity.setVelocity(knockback);
                    }
                }
            }
        }
    }

    private void spawnGetsugaParticles(World world, Location loc) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.4f);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(15, 15, 15), 1.2f);
        world.spawnParticle(Particle.REDSTONE, loc, 12, 0.3, 0.3, 0.3, red);
        world.spawnParticle(Particle.REDSTONE, loc, 10, 0.3, 0.3, 0.3, black);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.1, 0.1, 0.1, 0.01);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.4f);
    }

    public void endBankai(Player player) {
        UUID id = player.getUniqueId();
        BankaiState state = states.remove(id);
        if (state == null) {
            return;
        }
        state.cancelAll();
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null && state.originalMaxHealth > 0) {
            health.setBaseValue(state.originalMaxHealth);
            if (player.getHealth() > health.getBaseValue()) {
                player.setHealth(health.getBaseValue());
            }
        }
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SLOW);
        sendActionBar(player, ChatColor.GRAY + "Bankai ended.");
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                endBankai(player);
            }
        }
        states.clear();
    }

    public boolean resetBankai(Player player) {
        if (states.containsKey(player.getUniqueId())) {
            endBankai(player);
            return true;
        }
        return false;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void broadcastMessage(String message) {
        Bukkit.getServer().broadcastMessage(message);
    }

    private static class BankaiState {
        private boolean active;
        private boolean transforming;
        private int remainingSeconds;
        private double originalMaxHealth;
        private long getsugaCooldownEnd;
        private BukkitTask particleTask;
        private BukkitTask countdownTask;
        private BukkitTask beamTask;
        private final List<BukkitTask> transformationTasks = new ArrayList<>();

        private void cancelAll() {
            if (particleTask != null) {
                particleTask.cancel();
            }
            if (countdownTask != null) {
                countdownTask.cancel();
            }
            if (beamTask != null) {
                beamTask.cancel();
            }
            for (BukkitTask task : transformationTasks) {
                task.cancel();
            }
        }
    }
}
