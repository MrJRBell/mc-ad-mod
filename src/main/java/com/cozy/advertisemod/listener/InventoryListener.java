package com.cozy.advertisemod.listener;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.fairmarket.FairMarketPriceCalculator;
import com.cozy.advertisemod.model.RegisteredBarrel;
import com.cozy.advertisemod.model.ShopListing;
import com.cozy.advertisemod.scanner.BarrelScanner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for inventory interactions. When an advertised barrel is closed,
 * triggers an asynchronous, non-blocking stock recount.
 */
public class InventoryListener implements Listener {

    private final AdvertisePlugin plugin;
    private final Map<UUID, Map<String, Long>> stockBeforeOpen = new ConcurrentHashMap<>();

    public InventoryListener(AdvertisePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.BARREL || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        List<ShopListing> listings = BarrelScanner.scan(event.getInventory());
        stockBeforeOpen.put(player.getUniqueId(), FairMarketPriceCalculator.materialQuantities(listings));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.BARREL || !(event.getPlayer() instanceof Player player)) {
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

        Map<String, Long> before = stockBeforeOpen.remove(player.getUniqueId());
        List<ShopListing> currentListings = BarrelScanner.scan(event.getInventory());
        Map<String, Long> after = FairMarketPriceCalculator.materialQuantities(currentListings);
        Set<String> addedMaterials = before == null
                ? Set.of()
                : FairMarketPriceCalculator.addedMaterials(before, after);

        // Run non-blocking check on Folia AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> registered = plugin.getDatabaseManager().getBarrel(barrelId);
            if (registered.isPresent()) {
                plugin.getStockAuditTask().triggerInstantScan(loc, barrelId);
                if (!addedMaterials.isEmpty()) {
                    RegisteredBarrel shop = registered.get();
                    var violations = FairMarketPriceCalculator.findViolations(
                            currentListings,
                            shop.getPriceAmount(), shop.getPriceUnit(), shop.getSellAmount(), shop.getSellUnit(),
                            plugin.getFairMarketPriceManager(), addedMaterials);
                    if (!violations.isEmpty()) {
                        StringBuilder warning = new StringBuilder("[AdvertiseMod] Added stock is below the fair market price for:");
                        for (FairMarketPriceCalculator.Violation violation : violations) {
                            warning.append(" ").append(violation.material().name())
                                    .append(" (floor ").append(violation.floor().amount()).append(' ')
                                    .append(violation.floor().currency()).append(" per ")
                                    .append(violation.floor().unit()).append(");");
                        }
                        player.getScheduler().run(plugin, scheduledTask ->
                                player.sendMessage(org.bukkit.ChatColor.RED + warning.toString()), null);
                    }
                }
            }
        });
    }
}

