package com.cozy.advertisemod.task;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.RegisteredBarrel;
import com.cozy.advertisemod.model.ShopListing;
import com.cozy.advertisemod.scanner.BarrelScanner;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Periodically scans registered barrels in loaded chunks via Folia's AsyncScheduler
 * and RegionScheduler to keep stock levels synchronized without causing tick lag.
 */
public class StockAuditTask {

    private final AdvertisePlugin plugin;
    private ScheduledTask scheduledTask;

    public StockAuditTask(AdvertisePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts the periodic audit task on Folia's AsyncScheduler.
     */
    public synchronized void start() {
        stop();
        int interval = plugin.getConfigManager().getAuditIntervalSeconds();
        this.scheduledTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                task -> runAudit(),
                5,
                interval,
                TimeUnit.SECONDS
        );
        plugin.getLogger().info("StockAuditTask scheduled every " + interval + " seconds.");
    }

    /**
     * Cancels the scheduled audit task.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * Executes the periodic audit scan across all registered barrels.
     */
    public void runAudit() {
        try {
            List<RegisteredBarrel> barrels = plugin.getDatabaseManager().getAllBarrels();
            for (RegisteredBarrel barrel : barrels) {
                auditBarrel(barrel);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Unexpected error during background stock audit", t);
        }
    }

    /**
     * Schedules a region-safe scan for a specific barrel if its chunk is loaded.
     */
    public void auditBarrel(RegisteredBarrel barrel) {
        World world = Bukkit.getWorld(barrel.getWorld());
        if (world == null) {
            return;
        }

        int chunkX = barrel.getX() >> 4;
        int chunkZ = barrel.getZ() >> 4;

        // Skip unloaded chunks to prevent blocking chunk loads
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        Location loc = new Location(world, barrel.getX(), barrel.getY(), barrel.getZ());
        Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
            try {
                Block block = loc.getBlock();
                if (block.getState() instanceof Barrel barrelBlock) {
                    List<ShopListing> listings = BarrelScanner.scan(barrelBlock.getInventory());
                    plugin.updateBarrelListing(barrel.getId(), listings);
                } else {
                    // Barrel block is no longer present
                    plugin.updateBarrelListing(barrel.getId(), Collections.emptyList());
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error scanning barrel at " + loc, t);
            }
        });
    }

    /**
     * Directly triggers an immediate scan for a barrel at a given location.
     */
    public void triggerInstantScan(Location loc, String barrelId) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
            try {
                Block block = loc.getBlock();
                if (block.getState() instanceof Barrel barrelBlock) {
                    List<ShopListing> listings = BarrelScanner.scan(barrelBlock.getInventory());
                    plugin.updateBarrelListing(barrelId, listings);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error performing instant scan for barrel " + barrelId, t);
            }
        });
    }
}

