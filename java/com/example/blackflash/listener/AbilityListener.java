package com.example.blackflash.listener;

import com.example.blackflash.BlackFlashPlugin;
import com.example.blackflash.util.AbilityItems;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralised listener that reacts to players using the custom ability items.
 */
public class AbilityListener implements Listener {
    private final BlackFlashPlugin plugin;
    private final AbilityItems abilityItems;
    private final Map<UUID, Long> reverseCursedTechniqueCooldowns = new HashMap<>();
    private final Map<UUID, Long> hadoCooldowns = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeBankai = new HashMap<>();

    private static final long RCT_COOLDOWN_MS = 15_000;
    private static final long HADO_COOLDOWN_MS = 25_000;
    private static final long BANKAI_DURATION_MS = 12_000;

    public AbilityListener(BlackFlashPlugin plugin, AbilityItems abilityItems) {
        this.plugin = plugin;
        this.abilityItems = abilityItems;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(ChatColor.GRAY + "Cursed energy trembles... abilities are ready.");
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!hasFlag(held, abilityItems.getBlackFlashKey())) {
            return;
        }

        // Amplify the damage and add a dramatic lightning strike effect.
        event.setDamage(event.getDamage() + 6.0);
        Location targetLocation = event.getEntity().getLocation();
        player.getWorld().strikeLightningEffect(targetLocation);
        player.getWorld().spawnParticle(Particle.CRIT_MAGIC, targetLocation, 30, 0.5, 1, 0.5, 0.15);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.1f);
        player.sendMessage(ChatColor.DARK_RED + "Black Flash activated! Extra damage applied.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Prevent double firing for off-hand.
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        if (hasFlag(item, abilityItems.getReverseCursedTechniqueKey())) {
            handleReverseCursedTechnique(event.getPlayer());
        } else if (hasFlag(item, abilityItems.getHadoNinetyKey())) {
            handleHadoNinety(event.getPlayer());
        } else if (hasFlag(item, abilityItems.getBankaiKey())) {
            handleBankai(event.getPlayer());
        }
    }

    public void resetBankai(Player player) {
        BukkitRunnable task = activeBankai.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            clearBankaiEffects(player);
            player.sendMessage(ChatColor.RED + "Your Bankai has been dispelled.");
        }
    }

    private void handleReverseCursedTechnique(Player player) {
        long now = System.currentTimeMillis();
        long lastUse = reverseCursedTechniqueCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUse < RCT_COOLDOWN_MS) {
            long remaining = (RCT_COOLDOWN_MS - (now - lastUse)) / 1000;
            player.sendMessage(ChatColor.AQUA + "Reverse Cursed Technique recharging: " + remaining + "s");
            return;
        }

        reverseCursedTechniqueCooldowns.put(player.getUniqueId(), now);

        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(Math.min(player.getHealth() + 8.0, maxHealth));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.sendMessage(ChatColor.AQUA + "Reverse Cursed Technique mends your wounds.");
    }

    private void handleHadoNinety(Player player) {
        long now = System.currentTimeMillis();
        long lastUse = hadoCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUse < HADO_COOLDOWN_MS) {
            long remaining = (HADO_COOLDOWN_MS - (now - lastUse)) / 1000;
            player.sendMessage(ChatColor.DARK_PURPLE + "Hado #90 is stabilising: " + remaining + "s");
            return;
        }

        hadoCooldowns.put(player.getUniqueId(), now);

        Location center = player.getLocation();
        player.getWorld().spawnParticle(Particle.SMOKE_LARGE, center, 40, 0.8, 1.2, 0.8, 0.01);
        player.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.6f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Kurohitsugi engulfs nearby foes!");

        for (Entity entity : player.getNearbyEntities(5, 4, 5)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                living.damage(4.0, player);
            }
        }
    }

    private void handleBankai(Player player) {
        if (activeBankai.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already in Bankai.");
            return;
        }

        player.sendMessage(ChatColor.RED + "Bankai: Tensa Zangetsu!");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, (int) (BANKAI_DURATION_MS / 50), 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int) (BANKAI_DURATION_MS / 50), 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, (int) (BANKAI_DURATION_MS / 50), 0));

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                clearBankaiEffects(player);
                activeBankai.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GRAY + "Your Bankai fades.");
            }
        };

        task.runTaskLater(plugin, BANKAI_DURATION_MS / 50);
        activeBankai.put(player.getUniqueId(), task);
    }

    private void clearBankaiEffects(Player player) {
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    private boolean hasFlag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        return container.has(key, PersistentDataType.INTEGER);
    }
}
