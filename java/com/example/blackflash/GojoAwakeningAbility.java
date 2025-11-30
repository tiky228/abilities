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
    private final Map<UUID, InventorySnapshot> savedInventories = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> trackedTasks = new HashMap<>();

    private BlackFlashAbility blackFlashAbility;
    private ReverseCursedTechniqueAbility reverseCursedTechniqueAbility;
    private LapseBlueAbility lapseBlueAbility;
    private ReverseRedAbility reverseRedAbility;

    public GojoAwakeningAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
    }

    public void setAbilityHooks(BlackFlashAbility blackFlashAbility,
            ReverseCursedTechniqueAbility reverseCursedTechniqueAbility, LapseBlueAbility lapseBlueAbility,
            ReverseRedAbility reverseRedAbility) {
        this.blackFlashAbility = blackFlashAbility;
        this.reverseCursedTechniqueAbility = reverseCursedTechniqueAbility;
        this.lapseBlueAbility = lapseBlueAbility;
        this.reverseRedAbility = reverseRedAbility;
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

        UUID id = caster.getUniqueId();
        BukkitTask particleTask = startCutsceneParticles(caster);
        trackTask(id, particleTask);
        BukkitTask endTask = new BukkitRunnable() {
            @Override
            public void run() {
                particleTask.cancel();
                endCutscene(caster);
            }
        }.runTaskLater(plugin, CUTSCENE_TICKS);
        trackTask(id, endTask);
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
        clearTrackedTasks(caster.getUniqueId());
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

        InventorySnapshot snapshot = captureInventory(caster);
        savedInventories.put(caster.getUniqueId(), snapshot);
        clearInventory(caster.getInventory());
        giveAwakeningItems(caster);

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
        restoreInventory(player);
        if (blackFlashAbility != null) {
            blackFlashAbility.onAwakeningEnd(player);
        }
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

    public int getAbilityPoints(Player player) {
        AwakeningState state = activeAwakenings.get(player.getUniqueId());
        return state != null ? state.getAbilityPoints() : 0;
    }

    public boolean hasAbilityPoint(Player player) {
        return getAbilityPoints(player) > 0;
    }

    public int addAbilityPoint(Player player) {
        AwakeningState state = activeAwakenings.get(player.getUniqueId());
        if (state == null) {
            return 0;
        }
        int updated = state.incrementAbilityPoints();
        player.sendMessage(ChatColor.AQUA + "You gained +1 Ability Point (Total: " + updated + ").");
        return updated;
    }

    public boolean consumeAbilityPoint(Player player) {
        AwakeningState state = activeAwakenings.get(player.getUniqueId());
        if (state == null) {
            return false;
        }
        return state.consumeAbilityPoint();
    }

    private InventorySnapshot captureInventory(Player player) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        return new InventorySnapshot(inventory.getContents().clone(), inventory.getArmorContents().clone(),
                inventory.getExtraContents().clone(), inventory.getItemInOffHand());
    }

    private void clearInventory(org.bukkit.inventory.PlayerInventory inventory) {
        inventory.clear();
        inventory.setArmorContents(new ItemStack[inventory.getArmorContents().length]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        inventory.setItemInOffHand(null);
    }

    private void giveAwakeningItems(Player player) {
        int slot = 0;
        if (blackFlashAbility != null) {
            player.getInventory().setItem(slot++, blackFlashAbility.createBlackFlashAxe());
        }
        if (reverseCursedTechniqueAbility != null) {
            player.getInventory().setItem(slot++, reverseCursedTechniqueAbility.createItem());
        }
        if (lapseBlueAbility != null) {
            player.getInventory().setItem(slot++, lapseBlueAbility.createItem());
        }
        if (reverseRedAbility != null) {
            player.getInventory().setItem(slot, reverseRedAbility.createItem());
        }
    }

    private void restoreInventory(Player player) {
        UUID id = player.getUniqueId();
        InventorySnapshot snapshot = savedInventories.remove(id);
        if (snapshot == null) {
            return;
        }
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        inventory.setContents(snapshot.contents.clone());
        inventory.setArmorContents(snapshot.armor.clone());
        inventory.setExtraContents(snapshot.extra.clone());
        inventory.setItemInOffHand(snapshot.offHand);
    }

    public void clearState(Player player) {
        UUID id = player.getUniqueId();
        clearTrackedTasks(id);
        restrictionManager.setFrozenByGojo(player, false);
        Set<UUID> frozen = pendingFreezes.remove(id);
        if (frozen != null) {
            for (UUID frozenId : frozen) {
                Player frozenPlayer = plugin.getServer().getPlayer(frozenId);
                if (frozenPlayer != null) {
                    restrictionManager.setFrozenByGojo(frozenPlayer, false);
                }
            }
        }
        endAwakening(player, true);
        restoreInventory(player);
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
                savedInventories.remove(id);
            }
        }
    }

    private void sendAwakeningTitle(Player player) {
        player.sendTitle(ChatColor.AQUA + "Since you're this strong… I won't hold back.", "", 10, 40, 10);
    }

    private static class InventorySnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final ItemStack offHand;

        private InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra, ItemStack offHand) {
            this.contents = contents;
            this.armor = armor;
            this.extra = extra;
            this.offHand = offHand;
        }
    }

    private static class AwakeningState {
        private final double baseHealth;
        private BukkitTask upkeepTask;
        private BukkitTask endTask;
        private long endTimeMillis;
        private int abilityPoints;

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

        private int getAbilityPoints() {
            return abilityPoints;
        }

        private int incrementAbilityPoints() {
            abilityPoints++;
            return abilityPoints;
        }

        private boolean consumeAbilityPoint() {
            if (abilityPoints <= 0) {
                return false;
            }
            abilityPoints--;
            return true;
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

    private void trackTask(UUID id, BukkitTask task) {
        trackedTasks.computeIfAbsent(id, ignored -> new ArrayList<>()).add(task);
    }

    private void clearTrackedTasks(UUID id) {
        List<BukkitTask> tasks = trackedTasks.remove(id);
        if (tasks == null) {
            return;
        }
        for (BukkitTask task : tasks) {
            task.cancel();
        }
    }
}
