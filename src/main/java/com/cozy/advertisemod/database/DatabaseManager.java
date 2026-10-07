package com.cozy.advertisemod.database;

import com.cozy.advertisemod.model.AuditLog;
import com.cozy.advertisemod.model.RegisteredBarrel;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the SQLite database (shops.db) connection, tables, indexes, and CRUD operations.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "shops.db");
    }

    /**
     * Initializes SQLite connection and tables.
     */
    public synchronized void initDatabase() throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING, "SQLite driver class not found in system loader, relying on driver manager.", e);
        }

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        this.connection = DriverManager.getConnection(url);

        try (Statement stmt = connection.createStatement()) {
            // WAL mode enables concurrent readers while writing
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS registered_barrels (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INT NOT NULL,
                    y INT NOT NULL,
                    z INT NOT NULL,
                    owner_uuid TEXT NOT NULL,
                    owner_name TEXT NOT NULL,
                    shop_name TEXT,
                    price_amount INT NOT NULL,
                    price_unit TEXT NOT NULL,
                    sell_amount INT NOT NULL,
                    sell_unit TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    barrel_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    action TEXT NOT NULL,
                    details TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_owner_uuid ON registered_barrels(owner_uuid);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_audit_barrel_id ON audit_logs(barrel_id);");
        }

        plugin.getLogger().info("SQLite database initialized successfully at: " + dbFile.getName());
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    /**
     * Registers or updates a shop barrel in the database.
     */
    public synchronized void saveOrUpdateBarrel(RegisteredBarrel barrel) throws SQLException {
        String sql = """
            INSERT INTO registered_barrels (
                id, world, x, y, z, owner_uuid, owner_name, shop_name,
                price_amount, price_unit, sell_amount, sell_unit
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                owner_name = excluded.owner_name,
                shop_name = excluded.shop_name,
                price_amount = excluded.price_amount,
                price_unit = excluded.price_unit,
                sell_amount = excluded.sell_amount,
                sell_unit = excluded.sell_unit;
        """;

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, barrel.getId());
            ps.setString(2, barrel.getWorld());
            ps.setInt(3, barrel.getX());
            ps.setInt(4, barrel.getY());
            ps.setInt(5, barrel.getZ());
            ps.setString(6, barrel.getOwnerUuid().toString());
            ps.setString(7, barrel.getOwnerName());
            ps.setString(8, barrel.getShopName());
            ps.setInt(9, barrel.getPriceAmount());
            ps.setString(10, barrel.getPriceUnit());
            ps.setInt(11, barrel.getSellAmount());
            ps.setString(12, barrel.getSellUnit());
            ps.executeUpdate();
        }
    }

    /**
     * Deletes a barrel from the database.
     */
    public synchronized boolean deleteBarrel(String barrelId) throws SQLException {
        String sql = "DELETE FROM registered_barrels WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, barrelId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retrieves a single barrel by ID.
     */
    public synchronized Optional<RegisteredBarrel> getBarrel(String barrelId) {
        String sql = "SELECT * FROM registered_barrels WHERE id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, barrelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBarrel(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch barrel: " + barrelId, e);
        }
        return Optional.empty();
    }

    /**
     * Fetches all registered barrels.
     */
    public synchronized List<RegisteredBarrel> getAllBarrels() {
        List<RegisteredBarrel> list = new ArrayList<>();
        String sql = "SELECT * FROM registered_barrels ORDER BY created_at DESC";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapBarrel(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch all barrels", e);
        }
        return list;
    }

    /**
     * Counts how many barrels a specific player currently has registered.
     */
    public synchronized int getBarrelCountByPlayer(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM registered_barrels WHERE owner_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count barrels for player: " + playerUuid, e);
        }
        return 0;
    }

    /**
     * Inserts an immutable audit log record.
     */
    public synchronized void logAudit(String barrelId, String playerUuid, String playerName, String action, String details) {
        String sql = "INSERT INTO audit_logs (barrel_id, player_uuid, player_name, action, details) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, barrelId);
            ps.setString(2, playerUuid);
            ps.setString(3, playerName);
            ps.setString(4, action);
            ps.setString(5, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to write audit log for barrel: " + barrelId, e);
        }
    }

    /**
     * Retrieves recent audit logs for a barrel.
     */
    public synchronized List<AuditLog> getRecentAuditLogs(String barrelId, int limit) {
        List<AuditLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM audit_logs WHERE barrel_id = ? ORDER BY timestamp DESC, id DESC LIMIT ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, barrelId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new AuditLog(
                            rs.getInt("id"),
                            rs.getString("barrel_id"),
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("action"),
                            rs.getString("details"),
                            rs.getString("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch audit logs for: " + barrelId, e);
        }
        return logs;
    }

    private RegisteredBarrel mapBarrel(ResultSet rs) throws SQLException {
        return new RegisteredBarrel(
                rs.getString("id"),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("shop_name"),
                rs.getInt("price_amount"),
                rs.getString("price_unit"),
                rs.getInt("sell_amount"),
                rs.getString("sell_unit"),
                rs.getString("created_at")
        );
    }

    /**
     * Safely closes the database connection.
     */
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection", e);
            }
        }
    }
}

