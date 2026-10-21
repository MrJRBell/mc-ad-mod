package com.cozy.advertisemod.fairmarket;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Loads and persists material-specific fair market prices. */
public class FairMarketPriceManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Material, FairMarketPrice> prices = new ConcurrentHashMap<>();

    public FairMarketPriceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fair-market-prices.yml");
        reload();
    }

    public synchronized void reload() {
        if (!file.exists()) {
            try {
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    throw new IOException("Could not create plugin data directory");
                }
                plugin.saveResource("fair-market-prices.yml", false);
            } catch (IllegalArgumentException missingResource) {
                try {
                    new YamlConfiguration().save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create fair-market-prices.yml", e);
                    return;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create fair-market-prices.yml", e);
                return;
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("prices");
        Map<Material, FairMarketPrice> loaded = new ConcurrentHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                String path = "prices." + key + ".";
                String currency = yaml.getString(path + "currency", "diamond").toLowerCase();
                String unit = yaml.getString(path + "unit", "stack").toLowerCase();
                Object rawAmount = yaml.get(path + "amount");
                int amount = rawAmount instanceof Number number
                        && number.doubleValue() == number.intValue() ? number.intValue() : 0;

                if (material == null || material.isAir() || material == Material.DIAMOND
                        || material == Material.DIAMOND_BLOCK || amount <= 0
                        || !(currency.equals("diamond") || currency.equals("diamond_block"))
                        || !(unit.equals("stack") || unit.equals("shulker"))) {
                    plugin.getLogger().warning("Ignoring invalid fair market price entry for material '" + key + "'.");
                    continue;
                }
                loaded.put(material, new FairMarketPrice(amount, currency, unit));
            }
        }

        prices.clear();
        prices.putAll(loaded);
        plugin.getLogger().info("Loaded " + prices.size() + " fair market price entries.");
    }

    public FairMarketPrice getPrice(Material material) {
        return prices.get(material);
    }

    public Map<Material, FairMarketPrice> getPrices() {
        return Collections.unmodifiableMap(new TreeMap<>(prices));
    }

    public synchronized void setPrice(Material material, FairMarketPrice price) throws IOException {
        Map<Material, FairMarketPrice> updated = new TreeMap<>(prices);
        updated.put(material, price);

        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<Material, FairMarketPrice> entry : updated.entrySet()) {
            String path = "prices." + entry.getKey().name() + ".";
            yaml.set(path + "amount", entry.getValue().amount());
            yaml.set(path + "currency", entry.getValue().currency());
            yaml.set(path + "unit", entry.getValue().unit());
        }
        yaml.save(file);

        prices.clear();
        prices.putAll(updated);
    }
}
