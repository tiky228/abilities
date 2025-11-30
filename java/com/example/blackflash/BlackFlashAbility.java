package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Core logic for the Black Flash ability.
 */
public class BlackFlashAbility {

    private static final long WINDOW_TICKS = 60L; // 3 seconds
    private static final long AWAKENING_WINDOW_TICKS = 40L; // 2 seconds
    private static final int SUCCESS_COOLDOWN_SECONDS = 5;
    private static final int AWAKENING_SUCCESS_COOLDOWN_SECONDS = 15;
    private static final int MISS_COOLDOWN_SECONDS = 30;
    private static final int AWAKENING_EXTENSION_SECONDS = 5;
    private static final int DURATION_REDUCTION_TICKS = 20; // Subtract 1 second from each base duration
    private static final int REGENERATION_AMPLIFIER = 1; // Regeneration II

    private static final int BASE_NAUSEA_TICKS = 120; // 6 seconds
    private static final int BASE_WEAKNESS_TICKS = 200; // 10 seconds
    private static final int BASE_SLOW_TICKS = 140; // 7 seconds

    private final BlackFlashPlugin plugin;
    private final CooldownManager cooldownManager;
    private final NamespacedKey axeKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility gojoAwakeningAbility;
    private final Map<UUID, Attempt> activeAttempts = new HashMap<>();
    private final Set<UUID> awakeningDisabled = new HashSet<>();

    public BlackFlashAbility(BlackFlashPlugin plugin, NamespacedKey axeKey, AbilityRestrictionManager restrictionManager,
            GojoAwakeningAbility gojoAwakeningAbility) {
        this.plugin = plugin;
        this.axeKey = axeKey;
        this.cooldownManager = new CooldownManager();
        this.restrictionManager = restrictionManager;
        this.gojoAwakeningAbility = gojoAwakeningAbility;
    }

    /**
     * Creates the special Black Flash Golden Axe with custom name, lore, and a persistent data marker.
     */
    public ItemStack createBlackFlashAxe() {
        ItemStack axe = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "Black Flash Axe");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Channel cursed energy in a single moment.");
            lore.add(ChatColor.DARK_RED + "Right-click to arm Black Flash.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(axeKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            axe.setItemMeta(meta);
        }
        return axe;
    }

    public boolean canUseAbility(Player player) {
        return restrictionManager.canUseAbility(player);
    }

    /**
     * Checks if the provided item is the special Black Flash Golden Axe.
     */
    public boolean isBlackFlashAxe(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.GOLDEN_AXE) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte marker = data.get(axeKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    /**
     * Handles right-click activation to arm Black Flash for the next hit.
     */
    public void handleActivation(Player player) {
        UUID id = player.getUniqueId();
        if (!isBlackFlashAxe(player.getInventory().getItemInMainHand())) {
            return;
        }

        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }

        boolean awakeningActive = gojoAwakeningAbility != null && gojoAwakeningAbility.isAwakening(player);
        if (!awakeningActive) {
            awakeningDisabled.remove(id);
        } else if (awakeningDisabled.contains(id)) {
            player.sendMessage(ChatColor.RED + "Your failed flash silences the technique until awakening ends.");
            return;
        }

        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Black Flash is recharging. " + remaining + "s remaining.");
            return;
        }

        if (activeAttempts.containsKey(id)) {
            player.sendMessage(ChatColor.YELLOW + "Black Flash is already armed! Strike now.");
            return;
        }

        armBlackFlash(player, id, awakeningActive);
    }

    /**
     * Handles a potential Black Flash strike when the player damages an entity.
     */
    public void handleStrike(Player player, LivingEntity target) {
        UUID id = player.getUniqueId();
        Attempt attempt = activeAttempts.remove(id);
        if (attempt == null) {
            return;
        }

        if (!restrictionManager.canUseAbility(player)) {
            removeStrength(player);
            if (attempt.awakening) {
                handleAwakeningMiss(player);
            } else {
                cooldownManager.setCooldown(id, MISS_COOLDOWN_SECONDS);
            }
            return;
        }

        attempt.windowTask.cancel();
        removeStrength(player);
        if (attempt.awakening && gojoAwakeningAbility != null) {
            gojoAwakeningAbility.extendAwakening(player, AWAKENING_EXTENSION_SECONDS * 1000L);
        }

        int cooldownSeconds = attempt.awakening ? AWAKENING_SUCCESS_COOLDOWN_SECONDS : SUCCESS_COOLDOWN_SECONDS;
        cooldownManager.setCooldown(id, cooldownSeconds);

        applyNegativeEffects(target);
        applyPositiveEffects(player);
        spawnBlackFlashEffects(player, target);

        player.sendMessage(ChatColor.DARK_RED + "Black Flash connects!" + ChatColor.GRAY + " Cooldown: "
                + cooldownSeconds + "s");
        if (target instanceof Player otherPlayer) {
            otherPlayer.sendMessage(ChatColor.RED + player.getName() + " enveloped you in Black Flash!");
        }
    }

    private void armBlackFlash(Player player, UUID id, boolean awakening) {
        long windowTicks = awakening ? AWAKENING_WINDOW_TICKS : WINDOW_TICKS;
        applyStrength(player, windowTicks);
        player.sendMessage(ChatColor.DARK_GRAY + "Black Flash armed for " + (windowTicks / 20)
                + " seconds! Land your strike.");
        player.getWorld().spawnParticle(Particle.CRIT_MAGIC, player.getLocation().add(0, 1, 0), 12, 0.4, 0.2, 0.4, 0.01);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.4f);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeAttempts.remove(id) != null) {
                    removeStrength(player);
                    if (awakening) {
                        handleAwakeningMiss(player);
                    } else {
                        cooldownManager.setCooldown(id, MISS_COOLDOWN_SECONDS);
                        player.sendMessage(
                                ChatColor.RED + "Black Flash missed! Cooldown: " + MISS_COOLDOWN_SECONDS + "s");
                    }
                }
            }
        }.runTaskLater(plugin, windowTicks);

        activeAttempts.put(id, new Attempt(task, awakening));
    }

    private void applyStrength(Player player, long durationTicks) {
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.INCREASE_DAMAGE, (int) durationTicks, 1, false, true, true));
    }

    private void removeStrength(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.INCREASE_DAMAGE);
        long maxWindow = Math.max(WINDOW_TICKS, AWAKENING_WINDOW_TICKS);
        if (effect != null && effect.getAmplifier() == 1 && effect.getDuration() <= maxWindow + 20) {
            player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        }
    }

    private void applyNegativeEffects(LivingEntity target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION,
                Math.max(1, BASE_NAUSEA_TICKS - DURATION_REDUCTION_TICKS), 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                Math.max(1, BASE_WEAKNESS_TICKS - DURATION_REDUCTION_TICKS), 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                Math.max(1, BASE_SLOW_TICKS - DURATION_REDUCTION_TICKS), 4, false, true, true));
    }

    private void applyPositiveEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 0, false, true, true));
        player.addPotionEffect(
                new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, REGENERATION_AMPLIFIER, false, true, true));
    }

    private void spawnBlackFlashEffects(Player player, LivingEntity target) {
        Location targetLoc = target.getLocation().add(0, 1, 0);
        Location playerLoc = player.getLocation().add(0, 1, 0);

        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(200, 25, 25), 1.4f);
        Particle.DustOptions black = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 1.2f);

        target.getWorld().spawnParticle(Particle.REDSTONE, targetLoc, 35, 0.5, 0.5, 0.5, red);
        target.getWorld().spawnParticle(Particle.REDSTONE, targetLoc, 30, 0.4, 0.4, 0.4, black);
        target.getWorld().spawnParticle(Particle.SMOKE_LARGE, targetLoc, 15, 0.5, 0.4, 0.5, 0.02);
        player.getWorld().spawnParticle(Particle.CRIT, playerLoc, 20, 0.3, 0.2, 0.3, 0.02);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.2f);
    }

    private static class Attempt {
        private final BukkitTask windowTask;
        private final boolean awakening;

        private Attempt(BukkitTask windowTask, boolean awakening) {
            this.windowTask = windowTask;
            this.awakening = awakening;
        }
    }

    private void handleAwakeningMiss(Player player) {
        awakeningDisabled.add(player.getUniqueId());
        player.sendMessage(ChatColor.RED + "Black Flash slips away! It cannot be used again until awakening ends.");
    }

    public void onAwakeningEnd(Player player) {
        UUID id = player.getUniqueId();
        awakeningDisabled.remove(id);
        Attempt attempt = activeAttempts.remove(id);
        if (attempt != null) {
            attempt.windowTask.cancel();
            removeStrength(player);
        }
    }
}
