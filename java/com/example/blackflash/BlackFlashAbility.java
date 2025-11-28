package com.example.blackflash;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles creation of the special Black Flash axe, activation window logic, and applying
 * effects on hit. The ability is limited to the custom-marked golden axe distributed by the
 * plugin command.
 */
public class BlackFlashAbility {

    private static final long WINDOW_MILLIS = 3_000L;
    private static final int SUCCESS_COOLDOWN_SECONDS = 5;
    private static final int MISS_COOLDOWN_SECONDS = 30;

    private static final int BASE_NAUSEA_TICKS = 140; // Reduced by 20 ticks on apply
    private static final int BASE_WEAKNESS_TICKS = 120; // Reduced by 20 ticks on apply
    private static final int BASE_SLOW_TICKS = 100; // Reduced by 20 ticks on apply

    private final BlackFlashPlugin plugin;
    private final CooldownManager cooldownManager;
    private final NamespacedKey axeKey;
    private final Map<UUID, AttemptState> activeAttempts = new HashMap<>();

    public BlackFlashAbility(BlackFlashPlugin plugin, NamespacedKey axeKey) {
        this.plugin = plugin;
        this.cooldownManager = new CooldownManager();
        this.axeKey = axeKey;
    }

    /**
     * Creates the special Black Flash golden axe marked with a display name, lore, and a
     * persistent data flag so it can be distinguished from normal axes.
     */
    public ItemStack createBlackFlashAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "Black Flash Axe");
            meta.setLore(
                    java.util.List.of(
                            ChatColor.GRAY + "Channel cursed energy in a single moment.",
                            ChatColor.DARK_GRAY + "Right-click to arm Black Flash."));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(axeKey, PersistentDataType.INTEGER, 1);
            axe.setItemMeta(meta);
        }
        return axe;
    }

    /**
     * Attempts to activate Black Flash for the player when right-clicking with the special axe.
     */
    public void handleActivation(Player player) {
        UUID id = player.getUniqueId();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!isBlackFlashAxe(inHand)) {
            player.sendMessage(ChatColor.RED + "You must wield the Black Flash Axe to activate this ability.");
            return;
        }

        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(
                    ChatColor.YELLOW + "Black Flash is recharging. " + remaining + "s remaining.");
            return;
        }

        if (activeAttempts.containsKey(id)) {
            player.sendMessage(ChatColor.YELLOW + "Black Flash is already primed! Strike within 3 seconds.");
            return;
        }

        grantStrengthBuff(player);
        startAttemptWindow(player);
    }

    /**
     * Handles a hit attempt during the activation window.
     */
    public void handleStrike(Player player, LivingEntity target) {
        UUID id = player.getUniqueId();
        AttemptState state = activeAttempts.remove(id);
        if (state == null) {
            return; // No primed attempt
        }

        state.timeoutTask().cancel();
        removeStrengthBuff(player);

        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!isBlackFlashAxe(inHand)) {
            markMiss(player);
            player.sendMessage(
                    ChatColor.RED + "You must strike with the Black Flash Axe! 30s cooldown applied.");
            return;
        }

        applyEnemyEffects(target);
        applyPlayerEffects(player);
        spawnParticles(target.getLocation());
        spawnParticles(player.getLocation());
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);

        cooldownManager.setCooldown(id, SUCCESS_COOLDOWN_SECONDS);
        player.sendMessage(ChatColor.DARK_RED + "Black Flash connects! Cooldown: 5s.");
        if (target instanceof Player victim) {
            victim.sendMessage(ChatColor.RED + player.getName() + " struck you with Black Flash!");
        }
    }

    /**
     * Called when the window expires without a valid hit.
     */
    private void markMiss(Player player) {
        cooldownManager.setCooldown(player.getUniqueId(), MISS_COOLDOWN_SECONDS);
        player.sendMessage(ChatColor.RED + "Black Flash missed. 30s cooldown applied.");
    }

    private void startAttemptWindow(Player player) {
        UUID id = player.getUniqueId();
        player.sendMessage(ChatColor.GRAY + "Black Flash armed for 3 seconds! Land your strike.");

        BukkitTask task =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        AttemptState removed = activeAttempts.remove(id);
                        if (removed != null) {
                            removeStrengthBuff(player);
                            markMiss(player);
                        }
                    }
                }.runTaskLater(plugin, WINDOW_MILLIS / 50L);

        activeAttempts.put(id, new AttemptState(task));
    }

    private void grantStrengthBuff(Player player) {
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INCREASE_DAMAGE, (int) (WINDOW_MILLIS / 50L), 1, false, true, true));
    }

    private void removeStrengthBuff(Player player) {
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
    }

    private void applyEnemyEffects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, adjustedDuration(BASE_NAUSEA_TICKS), 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, adjustedDuration(BASE_WEAKNESS_TICKS), 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, adjustedDuration(BASE_SLOW_TICKS), 10, false, true, true));
    }

    private void applyPlayerEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0, false, true, true));
    }

    private int adjustedDuration(int baseTicks) {
        return Math.max(20, baseTicks - 20);
    }

    private void spawnParticles(Location location) {
        Location loc = location.clone().add(0, 1, 0);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(220, 25, 25), 1.2f);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.1f);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 30, 0.5, 0.5, 0.5, red);
        loc.getWorld().spawnParticle(Particle.REDSTONE, loc, 25, 0.4, 0.4, 0.4, black);
        loc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, loc, 12, 0.4, 0.4, 0.4, 0.01);
    }

    public boolean isBlackFlashAxe(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_AXE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(axeKey, PersistentDataType.INTEGER);
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    private record AttemptState(BukkitTask timeoutTask) {}
}
