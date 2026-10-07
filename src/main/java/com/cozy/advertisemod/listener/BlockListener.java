package com.cozy.advertisemod.listener;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.RegisteredBarrel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Listens for block breaks. If an advertised barrel is broken by any player,
 * removes it from the database, logs BREAK_UNREGISTER, and clears cache.
 */
public class BlockListener implements Listener {

    private final AdvertisePlugin plugin;

    public BlockListener(AdvertisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARREL) {
            return;
        }

        Location loc = block.getLocation();
        if (loc.getWorld() == null) {
            return;
        }

        String barrelId = RegisteredBarrel.formatId(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );

        Player player = event.getPlayer();

        // Perform unregistration asynchronously on Folia AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> registered = plugin.getDatabaseManager().getBarrel(barrelId);
            if (registered.isEmpty()) {
                return;
            }

            RegisteredBarrel barrel = registered.get();
            try {
                plugin.getDatabaseManager().deleteBarrel(barrelId);
                plugin.getDatabaseManager().logAudit(
                        barrelId,
                        player.getUniqueId().toString(),
                        player.getName(),
                        "BREAK_UNREGISTER",
                        "Barrel broken by " + player.getName() + " (Shop: " + barrel.getShopName() + ")"
                );
                plugin.removeCachedListing(barrelId);

                player.sendMessage(ChatColor.YELLOW + "[AdvertiseMod] " + ChatColor.RED +
                        "Advertised shop '" + barrel.getShopName() + "' was unregistered because the barrel was broken.");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to unregister broken barrel: " + barrelId + ": " + e.getMessage());
            }
        });
    }
}

