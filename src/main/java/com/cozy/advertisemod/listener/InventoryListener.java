package com.cozy.advertisemod.listener;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.RegisteredBarrel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

/**
 * Listens for inventory interactions. When an advertised barrel is closed,
 * triggers an asynchronous, non-blocking stock recount.
 */
public class InventoryListener implements Listener {

    private final AdvertisePlugin plugin;

    public InventoryListener(AdvertisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.BARREL) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Barrel barrel)) {
            return;
        }

        Location loc = barrel.getLocation();
        if (loc.getWorld() == null) {
            return;
        }

        String barrelId = RegisteredBarrel.formatId(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
        );

        // Run non-blocking check on Folia AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> registered = plugin.getDatabaseManager().getBarrel(barrelId);
            if (registered.isPresent()) {
                plugin.getStockAuditTask().triggerInstantScan(loc, barrelId);
            }
        });
    }
}

