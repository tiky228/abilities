package com.example.blackflash;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class BankaiAbility {

    private static final int MODE_DURATION_SECONDS = 90;
    private static final int EXTRA_HEARTS = 10;
    private static final String BASE_LORE = ChatColor.GRAY + "Unleash Bankai.";
    private static final int TITLE_RADIUS = 50;
    private static final int BEAM_DURATION_TICKS = 40;
    private static final int BEAM_FREQUENCY_TICKS = 2;
    private static final int AURA_RADIUS = 10;
    private static final double BEAM_RADIUS = 1.0;

    private final JavaPlugin plugin;
    private final NamespacedKey bankaiItemKey;
    private final Map<UUID, BankaiState> activeBankai = new HashMap<>();

    public BankaiAbility(JavaPlugin plugin, NamespacedKey bankaiItemKey) {
        this.plugin = plugin;
        this.bankaiItemKey = bankaiItemKey;
    }

    public ItemStack createBankaiItem() {
        ItemStack item = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Tensa Zangetsu");
            meta.setLore(List.of(BASE_LORE));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(bankaiItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isBankaiItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.has(bankaiItemKey, PersistentDataType.BYTE);
    }

    public void giveBankaiItem(Player player) {
        player.getInventory().addItem(createBankaiItem());
        player.sendMessage(ChatColor.AQUA + "You feel the weight of Tensa Zangetsu." );
    }

    public boolean startBankai(Player player) {
        UUID id = player.getUniqueId();
        if (activeBankai.containsKey(id)) {
            player.sendMessage(ChatColor.RED + "Bankai is already active!");
            return false;
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) {
            player.sendMessage(ChatColor.RED + "Unable to harness Bankai right now.");
            return false;
        }

        BankaiState state = new BankaiState(id, maxHealth.getBaseValue(), System.currentTimeMillis());
        activeBankai.put(id, state);

        startAuraPhase(player, state);

        BukkitTask beamStart = plugin.getServer().getScheduler().runTaskLater(plugin, () -> startBeamPhase(player, state), 20L);
        state.addTask(beamStart);
        return true;
    }

    private void startAuraPhase(Player player, BankaiState state) {
        spawnAura(player);
        sendTitleNearby(player, ChatColor.AQUA + "I had enought...", "", 10, 40, 20);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.7f);
    }

    private void startBeamPhase(Player player, BankaiState state) {
        if (!isActive(player)) {
            return;
        }
        sendTitleNearby(player, ChatColor.AQUA.toString() + ChatColor.BOLD + "BANKAI!", "", 2, 30, 10);
        BukkitTask beamTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> spawnBeam(player), 0L, BEAM_FREQUENCY_TICKS);
        state.addTask(beamTask);

        BukkitTask activateTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            beamTask.cancel();
            activateBankai(player, state);
        }, BEAM_DURATION_TICKS);
        state.addTask(activateTask);
    }

    private void activateBankai(Player player, BankaiState state) {
        if (!isActive(player)) {
            return;
        }
        state.setStartTime(System.currentTimeMillis());
        sendTitleNearby(player, ChatColor.RED + "Tensa Zangetsu.", "", 10, 40, 20);

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(state.getOriginalMaxHealth() + EXTRA_HEARTS);
            player.setHealth(Math.min(player.getHealth() + EXTRA_HEARTS, maxHealth.getBaseValue()));
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, MODE_DURATION_SECONDS * 20 + 40, 0, true, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, MODE_DURATION_SECONDS * 20 + 40, 0, true, false));

        BukkitTask redParticles = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> spawnRedAura(player), 0L, 5L);
        state.addTask(redParticles);

        BukkitTask countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> updateCountdown(player, state), 0L, 20L);
        state.addTask(countdownTask);

        BukkitTask endTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> resetBankai(player, ChatColor.GRAY + "Your Bankai fades."), MODE_DURATION_SECONDS * 20L);
        state.addTask(endTask);
    }

    public boolean isActive(Player player) {
        return activeBankai.containsKey(player.getUniqueId());
    }

    private void updateCountdown(Player player, BankaiState state) {
        long elapsedMillis = System.currentTimeMillis() - state.getStartTime();
        int elapsedSeconds = (int) (elapsedMillis / 1000);
        int remaining = Math.max(0, MODE_DURATION_SECONDS - elapsedSeconds);
        player.sendActionBar(Component.text(ChatColor.RED + "Bankai time remaining: " + remaining + "s"));
        updateItemLore(player, remaining);
    }

    private void updateItemLore(Player player, int remainingSeconds) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !isBankaiItem(stack)) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                continue;
            }
            if (remainingSeconds < 0) {
                meta.setLore(List.of(BASE_LORE));
            } else {
                meta.setLore(List.of(BASE_LORE, ChatColor.RED + "Time left: " + remainingSeconds + "s"));
            }
            stack.setItemMeta(meta);
            contents[i] = stack;
        }
        player.getInventory().setContents(contents);
    }

    private void spawnAura(Player player) {
        Particle.DustOptions deepBlue = new Particle.DustOptions(Color.fromRGB(35, 105, 255), 1.6f);
        Particle.DustOptions softBlue = new Particle.DustOptions(Color.fromRGB(180, 220, 255), 1.2f);
        for (int i = 0; i < 220; i++) {
            double radius = ThreadLocalRandom.current().nextDouble(0, AURA_RADIUS);
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = ThreadLocalRandom.current().nextDouble(0.1, 2.8);
            player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(offsetX, offsetY, offsetZ), 1, 0.0, 0.0, 0.0, 0.0, ThreadLocalRandom.current().nextBoolean() ? deepBlue : softBlue);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(offsetX * 0.5, offsetY, offsetZ * 0.5), 1, 0.05, 0.1, 0.05, 0.0);
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(offsetX, offsetY, offsetZ), 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private void spawnBeam(Player player) {
        Particle.DustOptions beamBlue = new Particle.DustOptions(Color.fromRGB(75, 160, 255), 1.7f);
        Particle.DustOptions whiteCore = new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.3f);
        var world = player.getWorld();

        for (int i = 0; i < 140; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(0, BEAM_RADIUS);
            double height = ThreadLocalRandom.current().nextDouble(0.0, 2.5);
            double x = player.getLocation().getX() + Math.cos(angle) * radius;
            double y = player.getLocation().getY() + 0.2 + height;
            double z = player.getLocation().getZ() + Math.sin(angle) * radius;
            world.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, beamBlue, true);
            world.spawnParticle(Particle.REDSTONE, x, y, z, 0, 0, 0, 0, whiteCore, true);
            world.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.03, 0.15, 0.03, 0.0);
        }

        for (int i = 0; i < 35; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double startRadius = ThreadLocalRandom.current().nextDouble(AURA_RADIUS * 0.6, AURA_RADIUS);
            double targetX = player.getLocation().getX();
            double targetY = player.getLocation().getY() + 1.0;
            double targetZ = player.getLocation().getZ();
            for (int step = 0; step < 4; step++) {
                double radius = startRadius - (startRadius * (step / 3.0));
                double x = targetX + Math.cos(angle) * radius;
                double y = targetY + ThreadLocalRandom.current().nextDouble(-0.2, 0.6);
                double z = targetZ + Math.sin(angle) * radius;
                world.spawnParticle(Particle.CLOUD, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
                world.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, whiteCore, true);
            }
        }

        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.25f);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.05f);
        world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.6f, 0.9f);
    }

    private void spawnRedAura(Player player) {
        double baseAngle = (player.getWorld().getGameTime() % 360) / 10.0;
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.2f);
        for (int i = 0; i < 16; i++) {
            double angle = baseAngle + (Math.PI * 2 * i / 16);
            double radius = 1.2 + 0.2 * Math.sin(baseAngle + i);
            double x = player.getLocation().getX() + Math.cos(angle) * radius;
            double z = player.getLocation().getZ() + Math.sin(angle) * radius;
            double y = player.getLocation().getY() + 0.9 + 0.4 * Math.sin(baseAngle + angle);
            player.getWorld().spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, redDust, true);
        }
    }

    private void sendTitleNearby(Player source, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        double radiusSquared = TITLE_RADIUS * TITLE_RADIUS;
        source.getWorld().getPlayers().stream()
                .filter(target -> target.getLocation().distanceSquared(source.getLocation()) <= radiusSquared)
                .forEach(target -> target.sendTitle(title, subtitle, fadeIn, stay, fadeOut));
    }

    public void resetBankai(Player player, String message) {
        UUID id = player.getUniqueId();
        BankaiState state = activeBankai.remove(id);
        if (state == null) {
            if (message != null && !message.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "You are not currently in Bankai.");
            }
            return;
        }

        for (BukkitTask task : state.getTasks()) {
            if (task != null) {
                task.cancel();
            }
        }

        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(state.getOriginalMaxHealth());
            if (player.getHealth() > maxHealth.getBaseValue()) {
                player.setHealth(maxHealth.getBaseValue());
            }
        }

        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        updateItemLore(player, -1);

        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    public void clearAll() {
        for (UUID id : java.util.Set.copyOf(activeBankai.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                resetBankai(player, ChatColor.GRAY + "Your Bankai fades.");
            }
        }
        activeBankai.clear();
    }

    public boolean isBankaiItemInteraction(Player player, EquipmentSlot hand, ItemStack item) {
        return hand == EquipmentSlot.HAND && isBankaiItem(item);
    }
}
