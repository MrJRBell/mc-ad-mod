package com.cozy.advertisemod.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages configuration loading, validation, dynamic runtime updates, and persistence.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    private int webServerPort;
    private boolean webServerEnabled;
    private int maxBarrelsPerPlayer;
    private boolean enforceAreaLimits;
    private List<String> allowedDimensions;
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private int auditIntervalSeconds;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Loads or reloads all configuration values from config.yml.
     */
    public synchronized void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.webServerPort = config.getInt("web_server.port", 8080);
        this.webServerEnabled = config.getBoolean("web_server.enabled", true);

        this.maxBarrelsPerPlayer = config.getInt("limits.max_barrels_per_player", 64);

        this.enforceAreaLimits = config.getBoolean("shopping_district.enforce_area_limits", true);
        List<String> dims = config.getStringList("shopping_district.allowed_dimensions");
        if (dims.isEmpty()) {
            dims = List.of("world_the_end", "the_sift");
        }
        this.allowedDimensions = new ArrayList<>(dims);

        this.minX = config.getInt("shopping_district.bounds.min_x", -1000);
        this.maxX = config.getInt("shopping_district.bounds.max_x", 1000);
        this.minY = config.getInt("shopping_district.bounds.min_y", 0);
        this.maxY = config.getInt("shopping_district.bounds.max_y", 256);
        this.minZ = config.getInt("shopping_district.bounds.min_z", -1000);
        this.maxZ = config.getInt("shopping_district.bounds.max_z", 1000);

        this.auditIntervalSeconds = Math.max(5, config.getInt("scanning.audit_interval_seconds", 30));
    }

    /**
     * Checks if a given location falls within allowed dimensions and bounding box.
     */
    public synchronized boolean isLocationAllowed(String worldName, int x, int y, int z) {
        if (!enforceAreaLimits) {
            return true;
        }

        if (worldName == null || !allowedDimensions.contains(worldName)) {
            return false;
        }

        int lowerX = Math.min(minX, maxX);
        int upperX = Math.max(minX, maxX);
        int lowerY = Math.min(minY, maxY);
        int upperY = Math.max(minY, maxY);
        int lowerZ = Math.min(minZ, maxZ);
        int upperZ = Math.max(minZ, maxZ);

        return x >= lowerX && x <= upperX &&
               y >= lowerY && y <= upperY &&
               z >= lowerZ && z <= upperZ;
    }

    public synchronized void setMaxBarrelsPerPlayer(int limit) {
        this.maxBarrelsPerPlayer = limit;
        plugin.getConfig().set("limits.max_barrels_per_player", limit);
        plugin.saveConfig();
    }

    public synchronized void setBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;

        FileConfiguration config = plugin.getConfig();
        config.set("shopping_district.bounds.min_x", minX);
        config.set("shopping_district.bounds.max_x", maxX);
        config.set("shopping_district.bounds.min_y", minY);
        config.set("shopping_district.bounds.max_y", maxY);
        config.set("shopping_district.bounds.min_z", minZ);
        config.set("shopping_district.bounds.max_z", maxZ);
        plugin.saveConfig();
    }

    public synchronized int getWebServerPort() {
        return webServerPort;
    }

    public synchronized boolean isWebServerEnabled() {
        return webServerEnabled;
    }

    public synchronized int getMaxBarrelsPerPlayer() {
        return maxBarrelsPerPlayer;
    }

    public synchronized boolean isEnforceAreaLimits() {
        return enforceAreaLimits;
    }

    public synchronized List<String> getAllowedDimensions() {
        return Collections.unmodifiableList(allowedDimensions);
    }

    public synchronized int getMinX() {
        return minX;
    }

    public synchronized int getMaxX() {
        return maxX;
    }

    public synchronized int getMinY() {
        return minY;
    }

    public synchronized int getMaxY() {
        return maxY;
    }

    public synchronized int getMinZ() {
        return minZ;
    }

    public synchronized int getMaxZ() {
        return maxZ;
    }

    public synchronized int getAuditIntervalSeconds() {
        return auditIntervalSeconds;
    }
}

