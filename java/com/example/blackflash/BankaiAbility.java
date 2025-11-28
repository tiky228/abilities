package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;

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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
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

public class BankaiAbility {

    private static final int BANKAI_DURATION_SECONDS = 90;
    private static final int AURA_PHASE_TICKS = 20;
    private static final int BEAM_PHASE_TICKS = 20;
    private static final int BEAM_HEIGHT = 7;
    private static final double BEAM_RADIUS = 1.2;
    private static final double AURA_RADIUS = 10.0;
    private static final double EXTRA_HEALTH = 10.0;
    private static final int GETSUGA_COOLDOWN_SECONDS = 15;
    private static final int GETSUGA_RANGE = 15;
    private static final double GETSUGA_DAMAGE = 15.0;
    private static final int GETSUGA_TIME_COST = 10;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, BankaiState> states = new HashMap<>();

    public BankaiAbility(BlackFlashPlugin plugin, NamespacedKey itemKey) {
        this.plugin = plugin;
        this.itemKey = itemKey;
    }

    public ItemStack createItem() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Tensa Zangetsu");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Ichigo's condensed power.");
            lore.add(ChatColor.DARK_AQUA + "Right-click to unleash Bankai.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        return sword;
    }

    public boolean isBankaiItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHERITE_SWORD) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        Byte marker = container.get(itemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public boolean handleInteract(Player player, ItemStack stack, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND) {
            return false;
        }
        BankaiState state = states.get(player.getUniqueId());
        boolean bankaiItem = isBankaiItem(stack);

        if (state != null && state.active) {
            if (bankaiItem) {
                player.sendMessage(ChatColor.RED + "Bankai is already active.");
                return true;
            }
            if (stack == null || stack.getType() == Material.AIR) {
                attemptGetsuga(player, state);
                return true;
            }
            return false;
        }

        if (!bankaiItem) {
            return false;
        }

        if (state != null && state.transforming) {
            player.sendMessage(ChatColor.RED + "Bankai is already active.");
            return true;
        }

        BankaiState newState = new BankaiState();
        states.put(player.getUniqueId(), newState);
        startPhaseOne(player, newState);
        return true;
    }

    public void reset(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        if (state != null) {
            cancelState(player, state, false);
        }
        player.sendMessage(ChatColor.YELLOW + "Your Bankai has been reset.");
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cancelState(player, states.get(id), true);
            }
        }
        states.clear();
    }

    public void handleQuit(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        if (state != null) {
            cancelState(player, state, true);
            states.remove(player.getUniqueId());
        }
    }

    private void startPhaseOne(Player player, BankaiState state) {
        state.transforming = true;
        Bukkit.broadcast(Component.text(ChatColor.AQUA + "I had enought..."));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.9f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.1f);

        BukkitTask auraTask = new BukkitRunnable() {
            @Override
            public void run() {
                playAura(player.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 3L);

        BukkitTask beamPhaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                auraTask.cancel();
                state.tasks.remove(auraTask);
                startPhaseTwo(player, state);
            }
        }.runTaskLater(plugin, AURA_PHASE_TICKS);

        state.tasks.add(auraTask);
        state.tasks.add(beamPhaseTask);
    }

    private void startPhaseTwo(Player player, BankaiState state) {
        Bukkit.broadcast(Component.text(ChatColor.AQUA + "BANKAI!"));
        Location origin = player.getLocation();
        World world = origin.getWorld();
        Particle.DustOptions aqua = new Particle.DustOptions(Color.fromRGB(80, 180, 255), 2.0f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(220, 230, 255), 1.8f);

        BukkitTask beamTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= BEAM_PHASE_TICKS) {
                    cancel();
                    state.tasks.remove(this);
                    startPhaseThree(player, state);
                    return;
                }
                ticks++;
                spawnBeam(world, player.getLocation(), aqua, white);
                if (ticks % 5 == 0) {
                    world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
                    world.playSound(origin, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.9f, 0.9f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        state.tasks.add(beamTask);
    }

    private void startPhaseThree(Player player, BankaiState state) {
        Bukkit.broadcast(Component.text(ChatColor.RED + "Tensa Zangetsu."));
        state.transforming = false;
        state.active = true;
        state.remainingSeconds = BANKAI_DURATION_SECONDS;
        applyBuffs(player, state);
        startRedParticles(player, state);
        startTimer(player, state);
    }

    private void attemptGetsuga(Player player, BankaiState state) {
        if (state.transforming || !state.active) {
            return;
        }
        long now = System.currentTimeMillis();
        if (state.nextGetsugaUse > now) {
            long remaining = (state.nextGetsugaUse - now + 999) / 1000;
            player.sendMessage(ChatColor.GRAY + "Getsuga Tenshou cooling down: " + remaining + "s");
            return;
        }
        state.nextGetsugaUse = now + GETSUGA_COOLDOWN_SECONDS * 1000L;
        deductTime(player, state, GETSUGA_TIME_COST);
        performGetsuga(player, state);
    }

    private void performGetsuga(Player player, BankaiState state) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 15, 6, false, false, false));
        BukkitTask stanceTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                ticks++;
                spawnBlackAura(player.getLocation());
                if (ticks >= 10) {
                    launchGetsuga(player, state);
                    cancel();
                    state.tasks.remove(this);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        state.tasks.add(stanceTask);
    }

    private void launchGetsuga(Player player, BankaiState state) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        World world = player.getWorld();
        Set<UUID> hit = new HashSet<>();

        BukkitTask waveTask = new BukkitRunnable() {
            private double travelled = 0;

            @Override
            public void run() {
                travelled += 1.0;
                Location current = start.clone().add(direction.clone().multiply(travelled));
                spawnGetsugaParticles(world, current);

                if (current.getBlock().getType().isSolid()) {
                    cancel();
                    state.tasks.remove(this);
                    return;
                }

                for (Entity entity : world.getNearbyEntities(current, 1.25, 1.25, 1.25)) {
                    if (!(entity instanceof LivingEntity target) || entity == player) {
                        continue;
                    }
                    if (hit.contains(target.getUniqueId())) {
                        continue;
                    }
                    hit.add(target.getUniqueId());
                    target.damage(GETSUGA_DAMAGE, player);
                    Vector knockback = direction.clone().multiply(1.6).setY(0.4);
                    target.setVelocity(knockback);
                }

                if (travelled >= GETSUGA_RANGE) {
                    cancel();
                    state.tasks.remove(this);
                }
            }
        }.runTaskTimer(plugin, 10L, 1L);
        state.tasks.add(waveTask);
    }

    private void deductTime(Player player, BankaiState state, int seconds) {
        state.remainingSeconds = Math.max(0, state.remainingSeconds - seconds);
        player.sendActionBar(Component.text(ChatColor.DARK_RED + "Bankai time left: " + state.remainingSeconds + "s"));
        if (state.remainingSeconds <= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> cancelState(player, state, false), 20L);
        }
    }

    private void applyBuffs(Player player, BankaiState state) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            state.originalMaxHealth = maxHealth.getBaseValue();
            maxHealth.setBaseValue(state.originalMaxHealth + EXTRA_HEALTH);
            player.setHealth(Math.min(maxHealth.getBaseValue(), player.getHealth() + EXTRA_HEALTH));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, BANKAI_DURATION_SECONDS * 20, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, BANKAI_DURATION_SECONDS * 20 + 60, 0, false, false, true));
    }

    private void startRedParticles(Player player, BankaiState state) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 40, 40), 1.4f);
        BukkitTask task = new BukkitRunnable() {
            private double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                angle += Math.PI / 10;
                Location base = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 8; i++) {
                    double theta = angle + (Math.PI * 2 * i / 8);
                    double x = Math.cos(theta) * 1.4;
                    double z = Math.sin(theta) * 1.4;
                    base.getWorld().spawnParticle(Particle.REDSTONE, base.clone().add(x, 0.2, z), 1, 0, 0, 0, 0, red);
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
        state.tasks.add(task);
    }

    private void startTimer(Player player, BankaiState state) {
        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                state.remainingSeconds--;
                if (state.remainingSeconds <= 0) {
                    player.sendActionBar(Component.text(ChatColor.GRAY + "Bankai time left: 0s"));
                    cancelState(player, state, false);
                    return;
                }
                player.sendActionBar(Component.text(ChatColor.DARK_RED + "Bankai time left: " + state.remainingSeconds + "s"));
            }
        }.runTaskTimer(plugin, 20L, 20L);
        state.tasks.add(timerTask);
    }

    private void cancelState(Player player, BankaiState state, boolean silent) {
        for (BukkitTask task : new ArrayList<>(state.tasks)) {
            task.cancel();
        }
        state.tasks.clear();
        state.active = false;
        state.transforming = false;

        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SLOW);

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null && state.originalMaxHealth > 0) {
            maxHealth.setBaseValue(state.originalMaxHealth);
            if (player.getHealth() > maxHealth.getBaseValue()) {
                player.setHealth(maxHealth.getBaseValue());
            }
        }
        states.remove(player.getUniqueId());
        if (!silent && player.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "Your Bankai has ended.");
        }
    }

    private void playAura(Location center) {
        World world = center.getWorld();
        Particle.DustOptions aqua = new Particle.DustOptions(Color.fromRGB(80, 180, 255), 1.4f);
        Particle.DustOptions pale = new Particle.DustOptions(Color.fromRGB(210, 230, 255), 1.2f);
        world.spawnParticle(Particle.END_ROD, center, 80, AURA_RADIUS, 1.2, AURA_RADIUS, 0.05);
        world.spawnParticle(Particle.CLOUD, center, 90, AURA_RADIUS, 1.0, AURA_RADIUS, 0.01);
        world.spawnParticle(Particle.SNOWFLAKE, center, 60, AURA_RADIUS, 1.0, AURA_RADIUS, 0.02);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 80, 3.5, 1.5, 3.5, 0.02);
        world.spawnParticle(Particle.REDSTONE, center, 120, AURA_RADIUS, 1.0, AURA_RADIUS, 0, aqua);
        world.spawnParticle(Particle.REDSTONE, center, 90, AURA_RADIUS, 1.0, AURA_RADIUS, 0, pale);
    }

    private void spawnBeam(World world, Location base, Particle.DustOptions aqua, Particle.DustOptions white) {
        Location start = base.clone().add(0, 0, 0);
        for (double y = 0; y <= BEAM_HEIGHT; y += 0.3) {
            Location column = start.clone().add(0, y, 0);
            world.spawnParticle(Particle.REDSTONE, column, 120, BEAM_RADIUS, 0.2, BEAM_RADIUS, 0, aqua);
            world.spawnParticle(Particle.REDSTONE, column, 100, BEAM_RADIUS, 0.2, BEAM_RADIUS, 0, white);
            world.spawnParticle(Particle.CLOUD, column, 80, BEAM_RADIUS, 0.05, BEAM_RADIUS, 0.01);
            world.spawnParticle(Particle.END_ROD, column, 60, BEAM_RADIUS, 0.05, BEAM_RADIUS, 0.01);
            world.spawnParticle(Particle.SNOWFLAKE, column, 40, BEAM_RADIUS, 0.05, BEAM_RADIUS, 0.02);
        }
    }

    private void spawnBlackAura(Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.SMOKE_LARGE, center, 40, 0.6, 1.0, 0.6, 0.02);
        world.spawnParticle(Particle.SQUID_INK, center, 20, 0.6, 1.0, 0.6, 0.05);
    }

    private void spawnGetsugaParticles(World world, Location location) {
        world.spawnParticle(Particle.REDSTONE, location, 12, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.5f));
        world.spawnParticle(Particle.REDSTONE, location, 10, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(30, 30, 30), 1.2f));
        world.spawnParticle(Particle.CRIT_MAGIC, location, 8, 0.2, 0.2, 0.2, 0.01);
    }

    private static class BankaiState {
        private boolean transforming;
        private boolean active;
        private double originalMaxHealth;
        private int remainingSeconds;
        private long nextGetsugaUse;
        private final List<BukkitTask> tasks = new ArrayList<>();
    }
}
