package com.cozy.advertisemod.command;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.AuditLog;
import com.cozy.advertisemod.model.RegisteredBarrel;
import com.cozy.advertisemod.model.ShopListing;
import com.cozy.advertisemod.model.ShulkerContent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Barrel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Main command handler for /advertise (/adv).
 * Enforces permissions, container validation, spatial boundaries, and concurrency rules.
 */
public class AdvertiseCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> VALID_PRICE_UNITS = Set.of("diamond", "diamond_block");
    private static final Set<String> VALID_SELL_UNITS = Set.of("item", "stack", "shulker");

    private final AdvertisePlugin plugin;
    private final AdminCommand adminCommand;

    public AdvertiseCommand(AdvertisePlugin plugin) {
        this.plugin = plugin;
        this.adminCommand = new AdminCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cozy.advertise.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use /advertise.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "admin" -> adminCommand.handleAdmin(sender, args);
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.YELLOW + "         ★ Cozy Shopping District ★");
        sender.sendMessage(ChatColor.GOLD + "========================================");
        sender.sendMessage(ChatColor.AQUA + "/advertise start <price> <unit> <sell_amount> <sell_unit> [shop_name]");
        sender.sendMessage(ChatColor.GRAY + "  Register an open barrel as a shop listing.");
        sender.sendMessage(ChatColor.DARK_GRAY + "  Price Units: " + ChatColor.WHITE + "diamond, diamond_block");
        sender.sendMessage(ChatColor.DARK_GRAY + "  Sell Units: " + ChatColor.WHITE + "item, stack, shulker");
        sender.sendMessage(ChatColor.AQUA + "/advertise stop");
        sender.sendMessage(ChatColor.GRAY + "  Unregister any advertised barrel you are looking into.");
        sender.sendMessage(ChatColor.AQUA + "/advertise status");
        sender.sendMessage(ChatColor.GRAY + "  View live stock, pricing, and audit logs of the open barrel.");
        sender.sendMessage(ChatColor.GOLD + "----------------------------------------");
        sender.sendMessage(ChatColor.GRAY + "• Currency exclusion: Diamonds & Diamond Blocks inside barrels are not listed.");
        sender.sendMessage(ChatColor.GRAY + "• Private items: Items whose name begins with '*' are hidden.");
        sender.sendMessage(ChatColor.GRAY + "• Shulker boxes are deep-scanned and indexed on the web feed.");
        if (plugin.getConfigManager().isEnforceAreaLimits()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "• Allowed dimensions: " + ChatColor.YELLOW + String.join(", ", plugin.getConfigManager().getAllowedDimensions()));
            sender.sendMessage(ChatColor.DARK_GRAY + "• Bounding area: " + ChatColor.YELLOW + "X[" + plugin.getConfigManager().getMinX() + " to " + plugin.getConfigManager().getMaxX()
                    + "] Z[" + plugin.getConfigManager().getMinZ() + " to " + plugin.getConfigManager().getMaxZ() + "]");
        }
        sender.sendMessage(ChatColor.GOLD + "========================================");
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can register shop barrels.");
            return;
        }

        if (args.length < 5) {
            player.sendMessage(ChatColor.RED + "Usage: /advertise start <price_amount> <price_unit> <sell_amount> <sell_unit> [shop_name]");
            return;
        }

        // 1. Verify player has an open Barrel
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getType() != InventoryType.BARREL) {
            player.sendMessage(ChatColor.RED + "You must have a Barrel open to advertise it!");
            return;
        }

        InventoryHolder holder = openInv.getHolder();
        if (!(holder instanceof Barrel barrel)) {
            player.sendMessage(ChatColor.RED + "Unable to resolve the open Barrel entity.");
            return;
        }

        Location loc = barrel.getLocation();
        if (loc.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Cannot determine barrel world.");
            return;
        }

        // 2. Parse arguments
        int priceAmount;
        try {
            priceAmount = Integer.parseInt(args[1]);
            if (priceAmount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Price amount must be a positive integer.");
            return;
        }

        String priceUnit = args[2].toLowerCase();
        if (!VALID_PRICE_UNITS.contains(priceUnit)) {
            player.sendMessage(ChatColor.RED + "Invalid price unit! Must be one of: " + VALID_PRICE_UNITS);
            return;
        }

        int sellAmount;
        try {
            sellAmount = Integer.parseInt(args[3]);
            if (sellAmount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Sell amount must be a positive integer.");
            return;
        }

        String sellUnit = args[4].toLowerCase();
        if (!VALID_SELL_UNITS.contains(sellUnit)) {
            player.sendMessage(ChatColor.RED + "Invalid sell unit! Must be one of: " + VALID_SELL_UNITS);
            return;
        }

        String shopName;
        if (args.length >= 6) {
            shopName = String.join(" ", Arrays.copyOfRange(args, 5, args.length)).trim();
        } else {
            shopName = player.getName() + "'s Shop";
        }

        // 3. Validate dimension and spatial boundaries
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        if (!plugin.getConfigManager().isLocationAllowed(worldName, x, y, z)) {
            player.sendMessage(ChatColor.RED + "Registration failed: This location (" + worldName + " " + x + ", " + y + ", " + z
                    + ") is outside the designated Cozy Shopping District!");
            return;
        }

        String barrelId = RegisteredBarrel.formatId(worldName, x, y, z);

        // 4. Concurrency-safe Database operations on Folia AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> existing = plugin.getDatabaseManager().getBarrel(barrelId);

            // Ownership lock check
            if (existing.isPresent() && !existing.get().getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "This barrel is already registered by " + existing.get().getOwnerName() + "!");
                return;
            }

            // Quota limit check for new registrations
            if (existing.isEmpty()) {
                int ownedCount = plugin.getDatabaseManager().getBarrelCountByPlayer(player.getUniqueId());
                int maxAllowed = plugin.getConfigManager().getMaxBarrelsPerPlayer();
                if (ownedCount >= maxAllowed) {
                    player.sendMessage(ChatColor.RED + "You have reached your limit of " + maxAllowed + " registered shop barrels!");
                    return;
                }
            }

            boolean isUpdate = existing.isPresent();
            RegisteredBarrel registeredBarrel = new RegisteredBarrel(
                    barrelId, worldName, x, y, z,
                    player.getUniqueId(), player.getName(), shopName,
                    priceAmount, priceUnit, sellAmount, sellUnit,
                    null
            );

            try {
                plugin.getDatabaseManager().saveOrUpdateBarrel(registeredBarrel);
                String action = isUpdate ? "PRICE_CHANGE" : "REGISTER";
                String details = priceAmount + " " + priceUnit + " for " + sellAmount + " " + sellUnit + " ('" + shopName + "')";
                plugin.getDatabaseManager().logAudit(barrelId, player.getUniqueId().toString(), player.getName(), action, details);

                // Trigger instant stock re-scan on RegionScheduler
                plugin.getStockAuditTask().triggerInstantScan(loc, barrelId);

                player.sendMessage(ChatColor.GREEN + "✔ Shop successfully " + (isUpdate ? "updated" : "registered") + "!");
                player.sendMessage(ChatColor.YELLOW + "Shop: " + ChatColor.WHITE + shopName);
                player.sendMessage(ChatColor.YELLOW + "Price: " + ChatColor.AQUA + priceAmount + " " + priceUnit
                        + ChatColor.YELLOW + " for " + ChatColor.AQUA + sellAmount + " " + sellUnit);
                player.sendMessage(ChatColor.GRAY + "Location: " + worldName + " (" + x + ", " + y + ", " + z + ")");
                player.sendMessage(ChatColor.GRAY + "View live on the server shopping web portal!");
            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Database error while saving shop: " + e.getMessage());
            }
        });
    }

    private void handleStop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can unregister shop barrels.");
            return;
        }

        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getType() != InventoryType.BARREL || !(openInv.getHolder() instanceof Barrel barrel)) {
            player.sendMessage(ChatColor.RED + "You must have an advertised Barrel open to unregister it!");
            return;
        }

        Location loc = barrel.getLocation();
        if (loc.getWorld() == null) {
            player.sendMessage(ChatColor.RED + "Cannot determine barrel world.");
            return;
        }

        String barrelId = RegisteredBarrel.formatId(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        // Universal unregistration on Folia AsyncScheduler
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> existing = plugin.getDatabaseManager().getBarrel(barrelId);
            if (existing.isEmpty()) {
                player.sendMessage(ChatColor.RED + "This barrel is not currently registered as an advertised shop.");
                return;
            }

            RegisteredBarrel shop = existing.get();
            try {
                plugin.getDatabaseManager().deleteBarrel(barrelId);
                plugin.getDatabaseManager().logAudit(
                        barrelId,
                        player.getUniqueId().toString(),
                        player.getName(),
                        "UNREGISTER_BY_PLAYER",
                        "Unregistered by " + player.getName() + " (Shop: " + shop.getShopName() + ")"
                );
                plugin.removeCachedListing(barrelId);

                player.sendMessage(ChatColor.GREEN + "✔ Successfully unregistered shop '" + shop.getShopName() + "'!");
            } catch (SQLException e) {
                player.sendMessage(ChatColor.RED + "Database error while unregistering shop: " + e.getMessage());
            }
        });
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can view shop status.");
            return;
        }

        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getType() != InventoryType.BARREL || !(openInv.getHolder() instanceof Barrel barrel)) {
            player.sendMessage(ChatColor.RED + "You must have an advertised Barrel open to check its status!");
            return;
        }

        Location loc = barrel.getLocation();
        if (loc.getWorld() == null) {
            return;
        }

        String barrelId = RegisteredBarrel.formatId(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> existing = plugin.getDatabaseManager().getBarrel(barrelId);
            if (existing.isEmpty()) {
                player.sendMessage(ChatColor.RED + "This barrel is not currently advertised.");
                return;
            }

            RegisteredBarrel shop = existing.get();
            List<AuditLog> recentLogs = plugin.getDatabaseManager().getRecentAuditLogs(barrelId, 4);
            List<ShopListing> listings = plugin.getCachedListings(barrelId);

            player.sendMessage(ChatColor.GOLD + "=== [ Shop Barrel Status ] ===");
            player.sendMessage(ChatColor.YELLOW + "Shop Name: " + ChatColor.WHITE + shop.getShopName());
            player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE + shop.getOwnerName());
            player.sendMessage(ChatColor.YELLOW + "Pricing: " + ChatColor.AQUA + shop.getPriceAmount() + " " + shop.getPriceUnit()
                    + ChatColor.YELLOW + " for " + ChatColor.AQUA + shop.getSellAmount() + " " + shop.getSellUnit());
            player.sendMessage(ChatColor.YELLOW + "Coordinates: " + ChatColor.GRAY + shop.getWorld() + " (" + shop.getX() + ", " + shop.getY() + ", " + shop.getZ() + ")");

            if (listings != null && !listings.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Current Stock Listings:");
                for (ShopListing listing : listings) {
                    if (listing.isShulker()) {
                        player.sendMessage(ChatColor.LIGHT_PURPLE + " • " + listing.getQuantityAvailable() + "x " + listing.getItemType()
                                + " (Contains " + listing.getShulkerContents().size() + " indexed item types)");
                        for (ShulkerContent sc : listing.getShulkerContents()) {
                            player.sendMessage(ChatColor.DARK_GRAY + "     - " + sc.getCount() + "x " + sc.getItem());
                        }
                    } else {
                        player.sendMessage(ChatColor.WHITE + " • " + listing.getQuantityAvailable() + "x " + listing.getItemType());
                    }
                }
            } else {
                player.sendMessage(ChatColor.GRAY + "Stock: No valid items found or awaiting audit scan.");
            }

            if (!recentLogs.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Recent Audit Logs:");
                for (AuditLog log : recentLogs) {
                    player.sendMessage(ChatColor.DARK_GRAY + " • [" + log.getTimestamp() + "] "
                            + ChatColor.GRAY + log.getAction() + " by " + log.getPlayerName() + " (" + log.getDetails() + ")");
                }
            }
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cozy.advertise.use")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "start", "stop", "status"));
            if (sender.hasPermission("cozy.advertise.admin")) {
                subs.add("admin");
            }
            return filterMatching(subs, args[0]);
        }

        if (args[0].equalsIgnoreCase("admin")) {
            return adminCommand.tabCompleteAdmin(sender, args);
        }

        if (args[0].equalsIgnoreCase("start")) {
            return switch (args.length) {
                case 2 -> filterMatching(List.of("1", "2", "5", "10", "32", "64"), args[1]);
                case 3 -> filterMatching(new ArrayList<>(VALID_PRICE_UNITS), args[2]);
                case 4 -> filterMatching(List.of("1", "16", "32", "64"), args[3]);
                case 5 -> filterMatching(new ArrayList<>(VALID_SELL_UNITS), args[4]);
                case 6 -> List.of("<shop_name>");
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private List<String> filterMatching(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                matches.add(opt);
            }
        }
        return matches;
    }
}

