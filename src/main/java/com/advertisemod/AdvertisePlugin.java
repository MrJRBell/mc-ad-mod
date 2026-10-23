package com.advertisemod;

import com.advertisemod.command.AdvertiseCommand;
import com.advertisemod.config.ConfigManager;
import com.advertisemod.database.DatabaseManager;
import com.advertisemod.fairmarket.FairMarketPriceManager;
import com.advertisemod.listener.BlockListener;
import com.advertisemod.listener.InventoryListener;
import com.advertisemod.model.ShopListing;
import com.advertisemod.task.StockAuditTask;
import com.advertisemod.web.WebServer;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    private FairMarketPriceManager fairMarketPriceManager;
    private StockAuditTask stockAuditTask;
    private WebServer webServer;

    private final ConcurrentMap<String, List<ShopListing>> cachedListings = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("Initializing AdvertiseMod for Paper/Folia...");

        // 1. Load configuration
        this.configManager = new ConfigManager(this);
        this.fairMarketPriceManager = new FairMarketPriceManager(this);

        // 2. Remove the old extracted default page; the bundled resource is served directly.
        removeLegacyWebCache();

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

    private void removeLegacyWebCache() {
        File legacyIndex = new File(getDataFolder(), "web/index.html");
        try {
            if (Files.deleteIfExists(legacyIndex.toPath())) {
                getLogger().info("Removed legacy web/index.html cache; bundled web page will be used. "
                        + "Use web/override.html for a custom page.");
            }
        } catch (IOException e) {
            getLogger().warning("Could not remove legacy web/index.html cache: " + e.getMessage());
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
        fairMarketPriceManager.reload();

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

    public FairMarketPriceManager getFairMarketPriceManager() {
        return fairMarketPriceManager;
    }

    public StockAuditTask getStockAuditTask() {
        return stockAuditTask;
    }

    public WebServer getWebServer() {
        return webServer;
    }
}

