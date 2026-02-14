package com.pvptoggle.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.pvptoggle.PvPTogglePlugin;
import com.pvptoggle.util.MessageUtil;

public class ZoneListener implements Listener {

    private final PvPTogglePlugin plugin;
    private final Map<UUID, Long> exitMessageCooldowns = new HashMap<>();
    private long cooldownMillis;

    public ZoneListener(PvPTogglePlugin plugin) {
        this.plugin = plugin;
        updateCooldownFromConfig();
    }
    
    /**
     * Updates the cached cooldown value from the config.
     * Should be called on initialization and when config is reloaded.
     */
    public void updateCooldownFromConfig() {
        int cooldownSeconds = plugin.getConfig().getInt("zone-message-cooldown", 5);
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("pvptoggle.admin")) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isZoneWand(item)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getZoneManager().setPosition(player.getUniqueId(), 0, block.getLocation());
            MessageUtil.send(player,
                    "&ePosition 1 &7set to &f("
                            + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getZoneManager().setPosition(player.getUniqueId(), 1, block.getLocation());
            MessageUtil.send(player,
                    "&ePosition 2 &7set to &f("
                            + block.getX() + ", " + block.getY() + ", " + block.getZ() + ")");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();

        // Only check when the player crosses a block boundary
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        boolean wasInZone = plugin.getZoneManager().isInForcedPvPZone(from);
        boolean isInZone  = plugin.getZoneManager().isInForcedPvPZone(to);

        if (!wasInZone && isInZone) {
            MessageUtil.send(event.getPlayer(), "&c&l⚔ You entered a forced PvP zone!");
            MessageUtil.sendActionBar(event.getPlayer(), "&c&l⚔ FORCED PVP ZONE ⚔");
        } else if (wasInZone && !isInZone) {
            Player player = event.getPlayer();
            UUID playerId = player.getUniqueId();
            long currentTime = System.currentTimeMillis();
            
            // Check if the player is on cooldown
            Long lastMessageTime = exitMessageCooldowns.get(playerId);
            if (lastMessageTime == null || (currentTime - lastMessageTime) >= cooldownMillis) {
                MessageUtil.send(player, "&a&l✓ You left the forced PvP zone.");
                exitMessageCooldowns.put(playerId, currentTime);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up cooldown data when player leaves to prevent memory leak
        exitMessageCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean isZoneWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        try {
            String matName = plugin.getConfig().getString("zone-wand-material");
            if (matName == null) matName = "BLAZE_ROD";
            if (item.getType() != Material.valueOf(matName.toUpperCase())) return false;
        } catch (IllegalArgumentException e) {
            // Bad material in config — fall back to BLAZE_ROD
            if (item.getType() != Material.BLAZE_ROD) return false;
        }
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().equals(ChatColor.YELLOW + "PvP Zone Selector");
    }
}
