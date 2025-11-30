package com.example.blackflash;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class TeleportStrikeAbility {

    private static final int COOLDOWN_SECONDS = 10;
    private static final double MAX_DASH_DISTANCE = 15.0;
    private static final double RAY_STEP = 0.5;
    private static final double TARGET_HIT_RADIUS = 1.25;
    private static final double STRIKE_DAMAGE = 12.0;
    private static final double STRIKE_KNOCKBACK = 1.8;

    private final BlackFlashPlugin plugin;
    private final NamespacedKey itemKey;
    private final AbilityRestrictionManager restrictionManager;
    private final GojoAwakeningAbility awakeningAbility;
    private final CooldownManager cooldownManager = new CooldownManager();

    public TeleportStrikeAbility(BlackFlashPlugin plugin, NamespacedKey itemKey,
            AbilityRestrictionManager restrictionManager, GojoAwakeningAbility awakeningAbility) {
        this.plugin = plugin;
        this.itemKey = itemKey;
        this.restrictionManager = restrictionManager;
        this.awakeningAbility = awakeningAbility;
    }

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.CHORUS_FRUIT);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "Teleport Strike");
            meta.setLore(java.util.Arrays.asList(ChatColor.GRAY + "Dash through space and strike.",
                    ChatColor.LIGHT_PURPLE + "Right-click to teleport and slam foes."));
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isAbilityItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.INTEGER);
    }

    public void tryActivate(Player player) {
        if (!awakeningAbility.isAwakening(player)) {
            player.sendMessage(ChatColor.RED + "Teleport Strike can only be used during Gojo's Awakening.");
            return;
        }
        if (!restrictionManager.canUseAbility(player)) {
            player.sendMessage(ChatColor.RED + "You cannot use abilities right now.");
            return;
        }

        if (!cooldownManager.isReady(player.getUniqueId())) {
            long remaining = cooldownManager.getRemainingSeconds(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Teleport Strike is recharging for " + remaining + "s.");
            return;
        }

        cooldownManager.setCooldown(player.getUniqueId(), COOLDOWN_SECONDS);
        performTeleportStrike(player);
    }

    private void performTeleportStrike(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location lastSafe = eye.clone();
        Player target = null;

        for (double travelled = RAY_STEP; travelled <= MAX_DASH_DISTANCE; travelled += RAY_STEP) {
            Location sample = eye.clone().add(direction.clone().multiply(travelled));
            Block block = sample.getBlock();
            if (block != null && block.getType().isSolid()) {
                break;
            }
            lastSafe = sample.clone();
            Player hit = findPlayerAt(sample, player);
            if (hit != null) {
                target = hit;
                break;
            }
        }

        if (target != null) {
            teleportToTarget(player, target);
        } else {
            teleportToLocation(player, lastSafe, direction);
        }
    }

    private Player findPlayerAt(Location sample, Player caster) {
        double radiusSq = TARGET_HIT_RADIUS * TARGET_HIT_RADIUS;
        for (Player candidate : caster.getWorld().getPlayers()) {
            if (candidate.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (!candidate.getWorld().equals(caster.getWorld())) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(sample) <= radiusSq) {
                return candidate;
            }
        }
        return null;
    }

    private void teleportToTarget(Player player, Player target) {
        Location targetLocation = target.getLocation();
        Vector toTarget = targetLocation.toVector().subtract(player.getLocation().toVector());
        Vector behind = toTarget.lengthSquared() > 1.0e-6 ? toTarget.normalize().multiply(-1.0) : new Vector(0, 0, 0);
        Location destination = targetLocation.clone().add(behind);
        destination.setDirection(targetLocation.toVector().subtract(destination.toVector()));
        if (destination.getBlock() != null && destination.getBlock().getType().isSolid()) {
            destination.add(0, 1, 0);
        }

        player.teleport(destination);
        player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.1f, 1.0f);
        strikeTarget(player, target);
    }

    private void teleportToLocation(Player player, Location destination, Vector direction) {
        if (destination == null) {
            destination = player.getLocation();
        }
        destination.setDirection(direction);
        player.teleport(destination);
        destination.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.3f);
        spawnTeleportParticles(destination);
    }

    private void strikeTarget(Player player, Player target) {
        target.damage(STRIKE_DAMAGE, player);
        Vector knockback = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (knockback.lengthSquared() > 1.0e-6) {
            Vector push = knockback.normalize().multiply(STRIKE_KNOCKBACK);
            push.setY(Math.max(0.6, push.getY() + 0.2));
            target.setVelocity(target.getVelocity().add(push));
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.85f);
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1.0, 0), 8, 0.2, 0.2, 0.2,
                0.01);
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 12, 0.35, 0.35, 0.35,
                0.04);
    }

    private void spawnTeleportParticles(Location center) {
        center.getWorld().spawnParticle(Particle.PORTAL, center, 28, 0.6, 0.6, 0.6, 0.35);
        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 18, 0.5, 0.5, 0.5, 0.18);
    }

    public void clearState(Player player) {
        cooldownManager.clear(player.getUniqueId());
    }

    public void clearAll() {
        // No active tasks to cancel; method present for symmetry.
    }
}
