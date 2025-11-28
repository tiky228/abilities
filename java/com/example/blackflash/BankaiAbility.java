package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;

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

public class BankaiAbility {

    private static final int TRANSFORMATION_DURATION_SECONDS = 90;
    private static final int AURA_DURATION_TICKS = 20; // 1 second
    private static final int BEAM_DURATION_TICKS = 40; // 2 seconds
    private static final double BEAM_RADIUS = 1.0;
    private static final double BEAM_HEIGHT = 7.0;
    private static final double NEARBY_TITLE_RADIUS = 50.0;
    private static final double AURA_RADIUS = 10.0;
    private static final double EXTRA_HEALTH = 10.0; // 5 hearts

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
            lore.add(ChatColor.GRAY + "Unleash Bankai.");
            lore.add(ChatColor.DARK_AQUA + "Right-click to transform.");
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

    public void tryActivate(Player player, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        UUID id = player.getUniqueId();
        BankaiState current = states.get(id);
        if (current != null && (current.transforming || current.active)) {
            player.sendActionBar(Component.text(ChatColor.RED + "Bankai is already active!"));
            return;
        }
        states.put(id, new BankaiState());
        startAura(player);
    }

    public void reset(Player player) {
        UUID id = player.getUniqueId();
        BankaiState state = states.remove(id);
        if (state == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not currently in Bankai.");
            return;
        }
        cancelState(player, state, true);
        player.sendMessage(ChatColor.GRAY + "Your Bankai has been reset.");
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                cancelState(player, states.get(id), true);
            }
        }
        states.clear();
    }

    private void startAura(Player player) {
        UUID id = player.getUniqueId();
        BankaiState state = states.get(id);
        if (state == null) {
            return;
        }
        state.transforming = true;
        sendTitleToNearby(player, ChatColor.AQUA + "I had enought...", "");
        playAuraParticles(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.9f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);

        BukkitTask auraTask = new BukkitRunnable() {
            @Override
            public void run() {
                playAuraParticles(player);
            }
        }.runTaskTimer(plugin, 0L, 5L);

        BukkitTask beamStart = new BukkitRunnable() {
            @Override
            public void run() {
                auraTask.cancel();
                state.tasks.remove(auraTask);
                startBeam(player);
            }
        }.runTaskLater(plugin, AURA_DURATION_TICKS);

        state.tasks.add(auraTask);
        state.tasks.add(beamStart);
    }

    private void startBeam(Player player) {
        UUID id = player.getUniqueId();
        BankaiState state = states.get(id);
        if (state == null) {
            return;
        }
        sendTitleToNearby(player, ChatColor.BOLD + "" + ChatColor.AQUA + "BANKAI!", "");
        Location center = player.getLocation();
        World world = player.getWorld();
        Particle.DustOptions aqua = new Particle.DustOptions(Color.fromRGB(80, 180, 255), 1.8f);
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(220, 230, 255), 1.5f);

        BukkitTask beamTask = new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= BEAM_DURATION_TICKS) {
                    cancel();
                    state.tasks.remove(this);
                    finishTransformation(player);
                    return;
                }
                ticks += 2;
                spawnBeam(world, center.clone(), aqua, white);
                if (ticks % 10 == 0) {
                    world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);
                    world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        state.tasks.add(beamTask);
    }

    private void finishTransformation(Player player) {
        UUID id = player.getUniqueId();
        BankaiState state = states.get(id);
        if (state == null) {
            return;
        }
        state.transforming = false;
        state.active = true;
        state.startTime = System.currentTimeMillis();
        sendTitleToNearby(player, ChatColor.RED + "Tensa Zangetsu.", "");

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            state.originalMaxHealth = maxHealth.getBaseValue();
            maxHealth.setBaseValue(state.originalMaxHealth + EXTRA_HEALTH);
            player.setHealth(Math.min(player.getHealth() + EXTRA_HEALTH, maxHealth.getBaseValue()));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, TRANSFORMATION_DURATION_SECONDS * 20, 0, false, false,
                true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, TRANSFORMATION_DURATION_SECONDS * 20 + 60, 0,
                false, false, true));

        startRedParticles(player, state);
        startTimer(player, state);
        scheduleEnd(player, state);
    }

    private void startRedParticles(Player player, BankaiState state) {
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.4f);
        BukkitTask task = new BukkitRunnable() {
            private double angle = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                angle += Math.PI / 8;
                Location base = player.getLocation().add(0, 1, 0);
                for (int i = 0; i < 6; i++) {
                    double theta = angle + (Math.PI * 2 * i / 6);
                    double x = Math.cos(theta) * 1.2;
                    double z = Math.sin(theta) * 1.2;
                    base.getWorld().spawnParticle(Particle.REDSTONE, base.clone().add(x, 0, z), 2, 0, 0, 0, 0, red);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
        state.tasks.add(task);
    }

    private void startTimer(Player player, BankaiState state) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                long elapsed = (System.currentTimeMillis() - state.startTime) / 1000;
                long remaining = Math.max(0, TRANSFORMATION_DURATION_SECONDS - elapsed);
                player.sendActionBar(Component.text(ChatColor.DARK_RED + "Bankai time left: " + remaining + "s"));
            }
        }.runTaskTimer(plugin, 0L, 20L);
        state.tasks.add(task);
    }

    private void scheduleEnd(Player player, BankaiState state) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                cancelState(player, state, false);
                player.sendActionBar(Component.text(ChatColor.GRAY + "Your Bankai has ended."));
            }
        }.runTaskLater(plugin, TRANSFORMATION_DURATION_SECONDS * 20L);
        state.tasks.add(task);
    }

    private void cancelState(Player player, BankaiState state, boolean silent) {
        for (BukkitTask task : new ArrayList<>(state.tasks)) {
            task.cancel();
        }
        state.tasks.clear();

        if (state.active || state.transforming) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null && state.originalMaxHealth > 0) {
                maxHealth.setBaseValue(state.originalMaxHealth);
                if (player.getHealth() > maxHealth.getBaseValue()) {
                    player.setHealth(maxHealth.getBaseValue());
                }
            }
        }

        states.remove(player.getUniqueId());
        if (!silent && player.isOnline()) {
            player.sendMessage(ChatColor.RED + "Bankai cancelled.");
        }
    }

    private void playAuraParticles(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();
        Particle.DustOptions aqua = new Particle.DustOptions(Color.fromRGB(80, 180, 255), 1.2f);
        Particle.DustOptions pale = new Particle.DustOptions(Color.fromRGB(220, 230, 255), 1.0f);
        world.spawnParticle(Particle.REDSTONE, center, 120, AURA_RADIUS, 1.2, AURA_RADIUS, 0, aqua);
        world.spawnParticle(Particle.REDSTONE, center, 90, AURA_RADIUS, 1.0, AURA_RADIUS, 0, pale);
        world.spawnParticle(Particle.END_ROD, center, 60, AURA_RADIUS, 1.0, AURA_RADIUS, 0.05);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 80, 3.5, 1.5, 3.5, 0.02);
    }

    private void spawnBeam(World world, Location center, Particle.DustOptions aqua, Particle.DustOptions white) {
        Location base = center.clone().add(0, 1, 0);
        for (double y = 0; y <= BEAM_HEIGHT; y += 0.4) {
            world.spawnParticle(Particle.REDSTONE, base.clone().add(0, y, 0), 80, BEAM_RADIUS, 0.1, BEAM_RADIUS, 0, aqua);
            world.spawnParticle(Particle.REDSTONE, base.clone().add(0, y, 0), 70, BEAM_RADIUS, 0.1, BEAM_RADIUS, 0, white);
            world.spawnParticle(Particle.CLOUD, base.clone().add(0, y, 0), 50, BEAM_RADIUS, 0.05, BEAM_RADIUS, 0.01);
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 40, BEAM_RADIUS, 0.05, BEAM_RADIUS, 0.01);
        }
    }

    private void sendTitleToNearby(Player player, String title, String subtitle) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        for (Player nearby : world.getPlayers()) {
            if (nearby.getLocation().distance(origin) <= NEARBY_TITLE_RADIUS) {
                nearby.sendTitle(title, subtitle, 10, 40, 10);
            }
        }
    }

    public void handleQuit(Player player) {
        BankaiState state = states.get(player.getUniqueId());
        if (state != null) {
            cancelState(player, state, true);
            states.remove(player.getUniqueId());
        }
    }

    private static class BankaiState {
        private boolean transforming;
        private boolean active;
        private double originalMaxHealth;
        private long startTime;
        private final List<BukkitTask> tasks = new ArrayList<>();
    }
}
