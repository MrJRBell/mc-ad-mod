package com.cozy.advertisemod;

import com.cozy.advertisemod.command.AdvertiseCommand;
import com.cozy.advertisemod.config.ConfigManager;
import com.cozy.advertisemod.database.DatabaseManager;
import com.cozy.advertisemod.listener.BlockListener;
import com.cozy.advertisemod.listener.InventoryListener;
import com.cozy.advertisemod.model.ShopListing;
import com.cozy.advertisemod.task.StockAuditTask;
import com.cozy.advertisemod.web.WebServer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Main plugin class for AdvertiseMod.
 * Coordinates database storage, config, event listeners, background stock audits, and the web microservice.
 */
public class AdvertisePlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private StockAuditTask stockAuditTask;
    private WebServer webServer;

    private final ConcurrentMap<String, List<ShopListing>> cachedListings = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Initializing AdvertiseMod for Paper/Folia...");

        // 1. Load configuration
        this.configManager = new ConfigManager(this);

        // 2. Extract default web assets if not present
        extractWebAssets();

        // 3. Initialize SQLite database
        this.databaseManager = new DatabaseManager(this);
        try {
            this.databaseManager.initDatabase();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize SQLite database! Disabling plugin...", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4. Register Event Listeners
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        // 5. Register Commands
        AdvertiseCommand advertiseCmd = new AdvertiseCommand(this);
        PluginCommand command = getCommand("advertise");
        if (command != null) {
            command.setExecutor(advertiseCmd);
            command.setTabCompleter(advertiseCmd);
        } else {
            getLogger().warning("Could not register '/advertise' command. Check plugin.yml.");
        }

        // 6. Start Stock Audit Scheduler
        this.stockAuditTask = new StockAuditTask(this);
        this.stockAuditTask.start();

        // 7. Start Embedded Web Microservice
        this.webServer = new WebServer(this);
        this.webServer.start();

        getLogger().info("AdvertiseMod enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down AdvertiseMod...");

        if (this.webServer != null) {
            this.webServer.stop();
        }

        if (this.stockAuditTask != null) {
            this.stockAuditTask.stop();
        }

        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        cachedListings.clear();
        getLogger().info("AdvertiseMod disabled cleanly.");
    }

    private void extractWebAssets() {
        File webDir = new File(getDataFolder(), "web");
        if (!webDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            webDir.mkdirs();
        }

        File indexFile = new File(webDir, "index.html");
        if (!indexFile.exists()) {
            try {
                saveResource("web/index.html", false);
            } catch (Throwable ignored) {
                // Ignore if resource extraction throws or is already present
            }
        }
    }

    public void updateBarrelListing(String barrelId, List<ShopListing> listings) {
        if (barrelId != null && listings != null) {
            cachedListings.put(barrelId, listings);
        }
    }

    public List<ShopListing> getCachedListings(String barrelId) {
        return cachedListings.get(barrelId);
    }

    public void removeCachedListing(String barrelId) {
        if (barrelId != null) {
            cachedListings.remove(barrelId);
        }
    }

    public void reloadPlugin() {
        configManager.loadConfig();

        if (webServer != null) {
            webServer.stop();
            webServer.start();
        }

        if (stockAuditTask != null) {
            stockAuditTask.start();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public StockAuditTask getStockAuditTask() {
        return stockAuditTask;
    }

    public WebServer getWebServer() {
        return webServer;
    }
}

