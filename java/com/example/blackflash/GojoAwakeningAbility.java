package com.example.blackflash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GojoAwakeningAbility {

    private static final double FREEZE_RADIUS = 20.0;
    private static final long CUTSCENE_TICKS = 60L;
    private static final long AWAKENING_TICKS = 120 * 20L;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final Map<UUID, AwakeningState> activeAwakenings = new HashMap<>();
    private final Map<UUID, Set<UUID>> pendingFreezes = new HashMap<>();

    public GojoAwakeningAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Gojo Awakening");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Unleash limitless power.");
            lore.add(ChatColor.DARK_AQUA + "Right-click to awaken Gojo.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAbilityItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.INTEGER);
    }

    public boolean canUseAbility(Player player) {
        return restrictionManager.canUseAbility(player);
    }

    public void tryActivate(Player player) {
        if (!canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }

        if (restrictionManager.isTimeStopped(player)) {
            player.sendMessage(ChatColor.RED + "Time itself is stopped. You cannot awaken.");
            return;
        }

        if (activeAwakenings.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your awakening is already active.");
            return;
        }

        if (pendingFreezes.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your awakening cutscene is already underway.");
            return;
        }

        startCutscene(player);
    }

    private void startCutscene(Player caster) {
        List<Player> frozen = findTargets(caster);
        Set<UUID> frozenIds = new HashSet<>();
        for (Player target : frozen) {
            restrictionManager.setFrozenByGojo(target, true);
            frozenIds.add(target.getUniqueId());
            sendAwakeningTitle(target);
        }
        if (!frozenIds.isEmpty()) {
            pendingFreezes.put(caster.getUniqueId(), frozenIds);
        }

        sendAwakeningTitle(caster);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.4f, 0.8f);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);

        BukkitTask particleTask = startCutsceneParticles(caster);
        new BukkitRunnable() {
            @Override
            public void run() {
                particleTask.cancel();
                endCutscene(caster);
            }
        }.runTaskLater(plugin, CUTSCENE_TICKS);
    }

    private BukkitTask startCutsceneParticles(Player caster) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline()) {
                    cancel();
                    return;
                }
                Location center = caster.getLocation().add(0, 1, 0);
                spawnAwakeningParticles(center);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawnAwakeningParticles(Location center) {
        center.getWorld().spawnParticle(Particle.END_ROD, center, 22, 1.2, 0.9, 1.2, 0.02);
        center.getWorld().spawnParticle(Particle.CLOUD, center, 16, 0.8, 0.6, 0.8, 0.03);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 10, 0.7, 0.4, 0.7, 0.02);
        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 30, 1.5, 1.2, 1.5, 0.02);
        center.getWorld().spawnParticle(Particle.REDSTONE, center, 20, 0.8, 0.5, 0.8, 0.01,
                new Particle.DustOptions(Color.fromRGB(130, 205, 255), 1.4f));
        center.getWorld().spawnParticle(Particle.REDSTONE, center, 18, 0.8, 0.5, 0.8, 0.01,
                new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.2f));
    }

    private List<Player> findTargets(Player caster) {
        List<Player> targets = new ArrayList<>();
        Location center = caster.getLocation();
        for (Player player : caster.getWorld().getPlayers()) {
            if (player.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (player.getLocation().distance(center) <= FREEZE_RADIUS) {
                targets.add(player);
            }
        }
        return targets;
    }

    private void endCutscene(Player caster) {
        Set<UUID> frozen = pendingFreezes.remove(caster.getUniqueId());
        if (frozen != null) {
            for (UUID id : frozen) {
                Player target = plugin.getServer().getPlayer(id);
                if (target != null) {
                    restrictionManager.setFrozenByGojo(target, false);
                }
            }
        }
        beginAwakening(caster);
    }

    private void beginAwakening(Player caster) {
        if (!caster.isOnline()) {
            return;
        }
        AttributeInstance healthAttribute = caster.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute == null) {
            caster.sendMessage(ChatColor.RED + "Your body resists the awakening.");
            return;
        }

        double baseHealth = healthAttribute.getBaseValue();
        healthAttribute.setBaseValue(baseHealth + 10.0);
        caster.setHealth(Math.min(caster.getHealth() + 10.0, healthAttribute.getValue()));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, (int) AWAKENING_TICKS, 0, false, false,
                true));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, (int) AWAKENING_TICKS, 4, false, false,
                false));

        long startTime = System.currentTimeMillis();
        AwakeningState state = new AwakeningState(baseHealth, startTime + (AWAKENING_TICKS * 50));
        BukkitTask upkeepTask = startUpkeepTask(caster, state);
        state.setUpkeepTask(upkeepTask);
        state.setEndTask(scheduleEndTask(caster, state));

        activeAwakenings.put(caster.getUniqueId(), state);
        caster.sendMessage(ChatColor.AQUA + "You unveil limitless technique. Awakening lasts 120s.");
    }

    private BukkitTask startUpkeepTask(Player caster, AwakeningState state) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                if (!caster.isOnline()) {
                    endAwakening(caster, true);
                    cancel();
                    return;
                }
                caster.setFoodLevel(20);
                caster.setSaturation(20f);
                caster.setExhaustion(0f);

                long remainingMs = Math.max(0, state.getEndTimeMillis() - System.currentTimeMillis());
                long secondsLeft = (remainingMs + 999) / 1000;
                caster.sendActionBar(Component.text("Awakening: " + secondsLeft + "s", NamedTextColor.AQUA));
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private BukkitTask scheduleEndTask(Player caster, AwakeningState state) {
        long remainingMs = Math.max(1L, state.getEndTimeMillis() - System.currentTimeMillis());
        long delayTicks = Math.max(1L, (remainingMs + 49) / 50);
        return new BukkitRunnable() {
            @Override
            public void run() {
                endAwakening(caster, false);
            }
        }.runTaskLater(plugin, delayTicks);
    }

    public void endAwakening(Player player, boolean silent) {
        UUID id = player.getUniqueId();
        AwakeningState state = activeAwakenings.remove(id);
        if (state == null) {
            return;
        }
        state.cancel();

        AttributeInstance healthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (healthAttribute != null) {
            healthAttribute.setBaseValue(state.baseHealth);
            if (player.getHealth() > healthAttribute.getValue()) {
                player.setHealth(healthAttribute.getValue());
            }
        }
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.SATURATION);
        if (!silent && player.isOnline()) {
            player.sendMessage(ChatColor.GRAY + "Your limitless focus fades.");
        }
    }

    public boolean isAwakening(Player player) {
        return activeAwakenings.containsKey(player.getUniqueId());
    }

    public boolean extendAwakening(Player player, long extraMillis) {
        AwakeningState state = activeAwakenings.get(player.getUniqueId());
        if (state == null) {
            return false;
        }

        state.setEndTimeMillis(state.getEndTimeMillis() + extraMillis);
        if (state.getEndTask() != null) {
            state.getEndTask().cancel();
        }
        state.setEndTask(scheduleEndTask(player, state));
        return true;
    }

    public void clearState(Player player) {
        restrictionManager.setFrozenByGojo(player, false);
        Set<UUID> frozen = pendingFreezes.remove(player.getUniqueId());
        if (frozen != null) {
            for (UUID id : frozen) {
                Player frozenPlayer = plugin.getServer().getPlayer(id);
                if (frozenPlayer != null) {
                    restrictionManager.setFrozenByGojo(frozenPlayer, false);
                }
            }
        }
        endAwakening(player, true);
    }

    public void clearAll() {
        for (Set<UUID> frozenSet : new HashSet<>(pendingFreezes.values())) {
            for (UUID frozenId : frozenSet) {
                Player frozen = plugin.getServer().getPlayer(frozenId);
                if (frozen != null) {
                    restrictionManager.setFrozenByGojo(frozen, false);
                }
            }
        }
        pendingFreezes.clear();
        for (UUID id : new HashSet<>(activeAwakenings.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                endAwakening(player, true);
            } else {
                AwakeningState state = activeAwakenings.remove(id);
                if (state != null) {
                    state.cancel();
                }
            }
        }
    }

    private void sendAwakeningTitle(Player player) {
        player.sendTitle(ChatColor.AQUA + "Since you're this strong…", ChatColor.WHITE + "I won't hold back.", 10, 40, 10);
    }

    private static class AwakeningState {
        private final double baseHealth;
        private BukkitTask upkeepTask;
        private BukkitTask endTask;
        private long endTimeMillis;

        private AwakeningState(double baseHealth, long endTimeMillis) {
            this.baseHealth = baseHealth;
            this.endTimeMillis = endTimeMillis;
        }

        private void setUpkeepTask(BukkitTask upkeepTask) {
            this.upkeepTask = upkeepTask;
        }

        private void setEndTask(BukkitTask endTask) {
            this.endTask = endTask;
        }

        private BukkitTask getEndTask() {
            return endTask;
        }

        private long getEndTimeMillis() {
            return endTimeMillis;
        }

        private void setEndTimeMillis(long endTimeMillis) {
            this.endTimeMillis = endTimeMillis;
        }

        private void cancel() {
            if (upkeepTask != null) {
                upkeepTask.cancel();
            }
            if (endTask != null) {
                endTask.cancel();
            }
        }
    }
}
