package com.example.blackflash;

import java.util.HashMap;
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
    private static final String BASE_LORE = ChatColor.GRAY + "Unleash Bankai Ichigo.";

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
            meta.setDisplayName(ChatColor.DARK_GRAY + "Tensa Zangetsu");
            meta.setLore(java.util.List.of(BASE_LORE));
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
        Bukkit.broadcastMessage(ChatColor.AQUA + "I had enought...");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 0.7f);
    }

    private void startBeamPhase(Player player, BankaiState state) {
        if (!isActive(player)) {
            return;
        }
        Bukkit.broadcastMessage(ChatColor.AQUA.toString() + ChatColor.BOLD + "BANKAI!");
        BukkitTask beamTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> spawnBeam(player), 0L, 2L);
        state.addTask(beamTask);

        BukkitTask activateTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            beamTask.cancel();
            activateBankai(player, state);
        }, 40L);
        state.addTask(activateTask);
    }

    private void activateBankai(Player player, BankaiState state) {
        if (!isActive(player)) {
            return;
        }
        Bukkit.broadcastMessage(ChatColor.RED + "Tensa Zangetsu.");

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
                meta.setLore(java.util.List.of(BASE_LORE));
            } else {
                meta.setLore(java.util.List.of(BASE_LORE, ChatColor.RED + "Time left: " + remainingSeconds + "s"));
            }
            stack.setItemMeta(meta);
            contents[i] = stack;
        }
        player.getInventory().setContents(contents);
    }

    private void spawnAura(Player player) {
        for (int i = 0; i < 150; i++) {
            double radius = 10 * ThreadLocalRandom.current().nextDouble();
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            double offsetY = ThreadLocalRandom.current().nextDouble(0, 2.5);
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(offsetX, offsetY, offsetZ), 2, 0.2, 0.2, 0.2, 0);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(offsetX, offsetY, offsetZ), 1, 0.1, 0.1, 0.1, 0);
        }
    }

    private void spawnBeam(Player player) {
        for (int i = 0; i < 40; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(3, 8);
            double height = ThreadLocalRandom.current().nextDouble(0.5, 3.5);
            double x = player.getLocation().getX() + Math.cos(angle) * radius;
            double y = player.getLocation().getY() + height;
            double z = player.getLocation().getZ() + Math.sin(angle) * radius;
            player.getWorld().spawnParticle(Particle.SPELL_WITCH, x, y, z, 8, 0.2, 0.4, 0.2, 0.02);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, x, y, z, 6, 0.2, 0.4, 0.2, 0.02);
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.0f);
    }

    private void spawnRedAura(Player player) {
        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.2f);
        player.getWorld().spawnParticle(Particle.REDSTONE, player.getLocation().add(0, 1, 0), 12, 0.6, 0.8, 0.6, 0, redDust);
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
