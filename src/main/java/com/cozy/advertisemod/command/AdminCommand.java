package com.cozy.advertisemod.command;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.RegisteredBarrel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Handles all administrative commands under /advertise admin.
 */
public class AdminCommand {

    private final AdvertisePlugin plugin;

    public AdminCommand(AdvertisePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cozy.advertise.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute admin commands.");
            return true;
        }

        if (args.length < 2) {
            sendAdminHelp(sender);
            return true;
        }

        String sub = args[1].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "remove" -> handleRemove(sender, args);
            case "setlimit" -> handleSetLimit(sender, args);
            case "setbounds" -> handleSetBounds(sender, args);
            default -> sendAdminHelp(sender);
        }
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== [ AdvertiseMod Admin Commands ] ===");
        sender.sendMessage(ChatColor.YELLOW + "/advertise admin reload " + ChatColor.GRAY + "- Reload config and restart microservice");
        sender.sendMessage(ChatColor.YELLOW + "/advertise admin remove <world> <x> <y> <z> " + ChatColor.GRAY + "- Force remove shop barrel");
        sender.sendMessage(ChatColor.YELLOW + "/advertise admin setlimit <player|default> <amount> " + ChatColor.GRAY + "- Update barrel limit");
        sender.sendMessage(ChatColor.YELLOW + "/advertise admin setbounds <minX> <maxX> <minY> <maxY> <minZ> <maxZ> " + ChatColor.GRAY + "- Set boundary box");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(ChatColor.GREEN + "[AdvertiseMod] Configuration and web service reloaded successfully.");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(ChatColor.RED + "Usage: /advertise admin remove <world> <x> <y> <z>");
            return;
        }

        String world = args[2];
        int x, y, z;
        try {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            z = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Coordinates x, y, z must be valid integers.");
            return;
        }

        String barrelId = RegisteredBarrel.formatId(world, x, y, z);
        String adminName = sender.getName();
        String adminUuid = (sender instanceof Player p) ? p.getUniqueId().toString() : "CONSOLE";

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Optional<RegisteredBarrel> existing = plugin.getDatabaseManager().getBarrel(barrelId);
            if (existing.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No registered shop barrel found at " + world + " (" + x + ", " + y + ", " + z + ").");
                return;
            }

            RegisteredBarrel barrel = existing.get();
            try {
                plugin.getDatabaseManager().deleteBarrel(barrelId);
                plugin.getDatabaseManager().logAudit(
                        barrelId,
                        adminUuid,
                        adminName,
                        "ADMIN_REMOVE",
                        "Removed by " + adminName + " (Shop: " + barrel.getShopName() + ", Owner: " + barrel.getOwnerName() + ")"
                );
                plugin.removeCachedListing(barrelId);
                sender.sendMessage(ChatColor.GREEN + "Successfully removed shop '" + barrel.getShopName() + "' at " + barrelId + ".");
            } catch (SQLException e) {
                sender.sendMessage(ChatColor.RED + "Database error while removing barrel: " + e.getMessage());
            }
        });
    }

    private void handleSetLimit(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /advertise admin setlimit <player_name|default> <amount>");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount <= 0) {
                sender.sendMessage(ChatColor.RED + "Limit amount must be greater than zero.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Amount must be a valid integer.");
            return;
        }

        // Updates default quota in config.yml
        plugin.getConfigManager().setMaxBarrelsPerPlayer(amount);
        sender.sendMessage(ChatColor.GREEN + "[AdvertiseMod] Barrel limit updated to " + amount + " per player.");
    }

    private void handleSetBounds(CommandSender sender, String[] args) {
        if (args.length < 8) {
            sender.sendMessage(ChatColor.RED + "Usage: /advertise admin setbounds <minX> <maxX> <minY> <maxY> <minZ> <maxZ>");
            return;
        }

        try {
            int minX = Integer.parseInt(args[2]);
            int maxX = Integer.parseInt(args[3]);
            int minY = Integer.parseInt(args[4]);
            int maxY = Integer.parseInt(args[5]);
            int minZ = Integer.parseInt(args[6]);
            int maxZ = Integer.parseInt(args[7]);

            plugin.getConfigManager().setBounds(minX, maxX, minY, maxY, minZ, maxZ);
            sender.sendMessage(ChatColor.GREEN + "[AdvertiseMod] Shopping district bounds updated: "
                    + "X[" + minX + " to " + maxX + "], Y[" + minY + " to " + maxY + "], Z[" + minZ + " to " + maxZ + "].");
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "All bounding box values must be valid integers.");
        }
    }

    public List<String> tabCompleteAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cozy.advertise.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return filterMatching(List.of("reload", "remove", "setlimit", "setbounds"), args[1]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                worlds.add(w.getName());
            }
            return filterMatching(worlds, args[2]);
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("setlimit")) {
            List<String> targets = new ArrayList<>();
            targets.add("default");
            for (Player p : Bukkit.getOnlinePlayers()) {
                targets.add(p.getName());
            }
            return filterMatching(targets, args[2]);
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("setlimit")) {
            return List.of("32", "64", "128");
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

