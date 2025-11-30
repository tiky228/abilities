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
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ReverseCursedTechniqueAbility {

    private static final int COOLDOWN_SECONDS = 60;
    private static final int PENALTY_SECONDS = 5;
    private static final int PARTICLE_INTERVAL_TICKS = 4;
    private static final int HEALTH_CHECK_TICKS = 10;
    private static final int REGENERATION_AMPLIFIER = 4; // Regeneration V

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Map<UUID, ChannelState> activeChannels = new HashMap<>();
    private final Map<UUID, Long> penaltyUntil = new HashMap<>();

    public ReverseCursedTechniqueAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Reverse Cursed Technique");
            meta.setLore(java.util.List.of(ChatColor.GRAY + "Channel cursed energy to heal.",
                    ChatColor.DARK_AQUA + "Right-click to begin regeneration."));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean canUseAbility(Player player) {
        return restrictionManager.canUseAbility(player);
    }

    public boolean isAbilityItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.INTEGER);
    }

    public void tryActivate(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            return;
        }

        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }

        if (activeChannels.containsKey(id)) {
            player.sendMessage(ChatColor.YELLOW + "Reverse Cursed Technique is already active.");
            return;
        }

        if (isPenalized(id)) {
            long remaining = getPenaltyRemainingSeconds(id);
            player.sendMessage(ChatColor.RED + "You are staggered for another " + remaining + "s.");
            return;
        }

        if (!cooldownManager.isReady(id)) {
            long remaining = cooldownManager.getRemainingSeconds(id);
            player.sendMessage(ChatColor.YELLOW + "Reverse Cursed Technique is recharging. " + remaining + "s remaining.");
            return;
        }

        cooldownManager.setCooldown(id, COOLDOWN_SECONDS);
        startChannel(player, id);
    }

    private void startChannel(Player player, UUID id) {
        applyImmobility(player, Integer.MAX_VALUE);
        applyRegeneration(player);

        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.0f);
        player.sendMessage(ChatColor.AQUA + "Reverse Cursed Technique activated! You are regenerating.");

        BukkitTask particleTask = startParticleTask(player, id);
        BukkitTask healthTask = startHealthMonitor(player, id);
        activeChannels.put(id, new ChannelState(particleTask, healthTask));
    }

    private void applyImmobility(Player player, int durationTicks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, durationTicks, 255, false, false, false));
    }

    private void applyRegeneration(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, REGENERATION_AMPLIFIER,
                false, false, true));
    }

    private BukkitTask startParticleTask(Player player, UUID id) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !activeChannels.containsKey(id)) {
                    cancel();
                    return;
                }

                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.REDSTONE, loc, 90, 0.9, 0.7, 0.9, 0.03,
                        new Particle.DustOptions(Color.fromRGB(80, 180, 255), 1.4f));
                player.getWorld().spawnParticle(Particle.SPELL_INSTANT, loc, 45, 0.6, 0.5, 0.6, 0.03);
                player.getWorld().spawnParticle(Particle.END_ROD, loc, 12, 0.35, 0.4, 0.35, 0.01);
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_INTERVAL_TICKS);
    }

    private BukkitTask startHealthMonitor(Player player, UUID id) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    endChannel(player, false, false);
                    cancel();
                    return;
                }

                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                if (player.getHealth() >= maxHealth) {
                    endChannel(player, false, true);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, HEALTH_CHECK_TICKS, HEALTH_CHECK_TICKS);
    }

    public void interrupt(Player player) {
        UUID id = player.getUniqueId();
        if (!activeChannels.containsKey(id)) {
            return;
        }

        endChannel(player, true, false);
        applyPenalty(player, id);
    }

    public void endChannel(Player player, boolean interrupted, boolean healed) {
        UUID id = player.getUniqueId();
        ChannelState state = activeChannels.remove(id);
        if (state != null) {
            state.cancel();
        }

        player.removePotionEffect(PotionEffectType.REGENERATION);
        if (!isPenalized(id)) {
            removeChannelImmobility(player);
        }

        if (interrupted) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            player.sendMessage(ChatColor.RED + "Your Reverse Cursed Technique was interrupted! You are staggered.");
        } else if (healed) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.3f);
            player.sendMessage(ChatColor.GREEN + "Your wounds are fully healed.");
        }
    }

    private void removeChannelImmobility(Player player) {
        PotionEffect slowness = player.getPotionEffect(PotionEffectType.SLOW);
        if (slowness != null && slowness.getAmplifier() >= 10) {
            player.removePotionEffect(PotionEffectType.SLOW);
        }
    }

    private void applyPenalty(Player player, UUID id) {
        long expiry = System.currentTimeMillis() + (PENALTY_SECONDS * 1000L);
        penaltyUntil.put(id, expiry);
        applyImmobility(player, PENALTY_SECONDS * 20 + 10);

        new BukkitRunnable() {
            @Override
            public void run() {
                penaltyUntil.remove(id);
                player.removePotionEffect(PotionEffectType.SLOW);
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.GRAY + "You regain your footing.");
                }
            }
        }.runTaskLater(plugin, PENALTY_SECONDS * 20L);
    }

    public boolean isChanneling(UUID id) {
        return activeChannels.containsKey(id);
    }

    public boolean isPenalized(UUID id) {
        Long until = penaltyUntil.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    public long getPenaltyRemainingSeconds(UUID id) {
        Long until = penaltyUntil.get(id);
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            penaltyUntil.remove(id);
            return 0;
        }
        return (remaining + 999) / 1000;
    }

    public boolean isOnCooldown(UUID id) {
        return !cooldownManager.isReady(id);
    }

    public long getCooldownRemainingSeconds(UUID id) {
        return cooldownManager.getRemainingSeconds(id);
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        ChannelState state = activeChannels.remove(id);
        if (state != null) {
            state.cancel();
        }
        penaltyUntil.remove(id);
        cooldownManager.clear(id);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.SLOW);
    }

    private static class ChannelState {
        private final BukkitTask particleTask;
        private final BukkitTask healthTask;

        private ChannelState(BukkitTask particleTask, BukkitTask healthTask) {
            this.particleTask = particleTask;
            this.healthTask = healthTask;
        }

        private void cancel() {
            particleTask.cancel();
            healthTask.cancel();
        }
    }
}
