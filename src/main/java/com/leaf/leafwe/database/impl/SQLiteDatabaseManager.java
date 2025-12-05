package com.leaf.leafwe.database.impl;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.database.DatabaseManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SQLiteDatabaseManager implements DatabaseManager {

    private final LeafWE plugin;
    private Connection connection;
    private final String databaseFile;
    private boolean initialized = false;
    private final ConcurrentLinkedQueue<String> queryLog = new ConcurrentLinkedQueue<>();

    private static final String CREATE_DAILY_USAGE_TABLE = """
        CREATE TABLE IF NOT EXISTS daily_usage (
            player_id TEXT NOT NULL,
            date TEXT NOT NULL,
            blocks_used INTEGER DEFAULT 0,
            operations_used INTEGER DEFAULT 0,
            player_group TEXT DEFAULT 'default',
            last_updated INTEGER DEFAULT 0,
            PRIMARY KEY (player_id, date)
        )
        """;

    private static final String CREATE_PLAYER_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS player_stats (
            player_id TEXT PRIMARY KEY,
            total_blocks_placed INTEGER DEFAULT 0,
            total_operations INTEGER DEFAULT 0,
            total_playtime INTEGER DEFAULT 0,
            favorite_block TEXT DEFAULT 'STONE',
            first_seen INTEGER DEFAULT 0,
            last_seen INTEGER DEFAULT 0
        )
        """;

    private static final String CREATE_SESSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_id TEXT NOT NULL,
            session_type TEXT NOT NULL,
            start_time INTEGER NOT NULL,
            end_time INTEGER NOT NULL,
            duration INTEGER NOT NULL
        )
        """;

    public SQLiteDatabaseManager(LeafWE plugin) {
        this.plugin = plugin;
        this.databaseFile = plugin.getConfig().getString("database.sqlite.file", "data/leafwe.db");
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Force load the driver class
                Class.forName("org.sqlite.JDBC");

                File dbFile = new File(plugin.getDataFolder(), databaseFile);
                dbFile.getParentFile().mkdirs();

                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);

                applySQLiteOptimizations();

                createTables();

                initialized = true;
                plugin.getLogger().info("SQLite database initialized successfully: " + dbFile.getAbsolutePath());
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    private void applySQLiteOptimizations() throws SQLException {
        String journalMode = plugin.getConfig().getString("database.sqlite.journal-mode", "WAL");
        String synchronous = plugin.getConfig().getString("database.sqlite.synchronous", "NORMAL");
        int cacheSize = plugin.getConfig().getInt("database.sqlite.cache-size", 2000);
        String tempStore = plugin.getConfig().getString("database.sqlite.temp-store", "MEMORY");

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = " + journalMode);
            stmt.execute("PRAGMA synchronous = " + synchronous);
            stmt.execute("PRAGMA cache_size = " + cacheSize);
            stmt.execute("PRAGMA temp_store = " + tempStore);
            stmt.execute("PRAGMA foreign_keys = ON");

            plugin.getLogger().info("SQLite optimizations applied: " +
                    "journal_mode=" + journalMode + ", synchronous=" + synchronous +
                    ", cache_size=" + cacheSize + ", temp_store=" + tempStore);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_DAILY_USAGE_TABLE);
            stmt.execute(CREATE_PLAYER_STATS_TABLE);
            stmt.execute(CREATE_SESSIONS_TABLE);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_usage_date ON daily_usage(date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_daily_usage_player ON daily_usage(player_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sessions_time ON sessions(start_time)");

            plugin.getLogger().info("Database tables and indexes created successfully");
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("SQLite database connection closed");
                }
                initialized = false;
            } catch (SQLException e) {
                plugin.getLogger().warning("Error closing SQLite database: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    return false;
                }

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SELECT 1");
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Database connection test failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<DailyUsageData> getDailyUsage(UUID playerId, String date) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM daily_usage WHERE player_id = ? AND date = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);

                logQuery(sql, playerId.toString(), date);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new DailyUsageData(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getString("date"),
                                rs.getInt("blocks_used"),
                                rs.getInt("operations_used"),
                                rs.getString("player_group"),
                                rs.getLong("last_updated")
                        );
                    } else {
                        return new DailyUsageData(playerId, date, 0, 0, "default", System.currentTimeMillis());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting daily usage: " + e.getMessage());
                return new DailyUsageData(playerId, date, 0, 0, "default", System.currentTimeMillis());
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> updateDailyUsage(UUID playerId, String date, int blocksUsed, int operationsUsed, String group) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO daily_usage 
                (player_id, date, blocks_used, operations_used, player_group, last_updated) 
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);
                stmt.setInt(3, blocksUsed);
                stmt.setInt(4, operationsUsed);
                stmt.setString(5, group);
                stmt.setLong(6, System.currentTimeMillis());

                logQuery(sql, playerId.toString(), date, String.valueOf(blocksUsed), String.valueOf(operationsUsed), group);

                int affected = stmt.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating daily usage: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> resetDailyUsage(UUID playerId, String date) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM daily_usage WHERE player_id = ? AND date = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);

                logQuery(sql, playerId.toString(), date);

                int affected = stmt.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error resetting daily usage: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> cleanupOldData(int daysToKeep) {
        return CompletableFuture.supplyAsync(() -> {
            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60L * 60L * 1000L);

            String sql1 = "DELETE FROM daily_usage WHERE last_updated < ?";
            String sql2 = "DELETE FROM sessions WHERE start_time < ?";

            try {
                int deletedUsage = 0;
                int deletedSessions = 0;

                try (PreparedStatement stmt = connection.prepareStatement(sql1)) {
                    stmt.setLong(1, cutoffTime);
                    deletedUsage = stmt.executeUpdate();
                }

                try (PreparedStatement stmt = connection.prepareStatement(sql2)) {
                    stmt.setLong(1, cutoffTime);
                    deletedSessions = stmt.executeUpdate();
                }

                plugin.getLogger().info("Cleanup completed: " + deletedUsage + " usage records, " +
                        deletedSessions + " session records deleted");
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error during cleanup: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<PlayerStats> getPlayerStats(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_stats WHERE player_id = ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerStats(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getLong("total_blocks_placed"),
                                rs.getLong("total_operations"),
                                rs.getLong("total_playtime"),
                                rs.getString("favorite_block"),
                                rs.getLong("first_seen"),
                                rs.getLong("last_seen")
                        );
                    } else {
                        long now = System.currentTimeMillis();
                        return new PlayerStats(playerId, 0, 0, 0, "STONE", now, now);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting player stats: " + e.getMessage());
                long now = System.currentTimeMillis();
                return new PlayerStats(playerId, 0, 0, 0, "STONE", now, now);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> updatePlayerStats(UUID playerId, String statType, long value) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT OR REPLACE INTO player_stats (player_id, " + statType + ", last_seen) VALUES (?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());
                stmt.setLong(2, value);
                stmt.setLong(3, System.currentTimeMillis());

                int affected = stmt.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error updating player stats: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> incrementPlayerStat(UUID playerId, String statType, long increment) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO player_stats (player_id, %s, last_seen, first_seen) 
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET 
                %s = %s + ?, 
                last_seen = ?
                """.formatted(statType, statType, statType);

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                stmt.setString(1, playerId.toString());
                stmt.setLong(2, increment);
                stmt.setLong(3, now);
                stmt.setLong(4, now);
                stmt.setLong(5, increment);
                stmt.setLong(6, now);

                int affected = stmt.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error incrementing player stat: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> recordSession(UUID playerId, String sessionType, long duration) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO sessions (player_id, session_type, start_time, end_time, duration) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                stmt.setString(1, playerId.toString());
                stmt.setString(2, sessionType);
                stmt.setLong(3, now - duration);
                stmt.setLong(4, now);
                stmt.setLong(5, duration);

                int affected = stmt.executeUpdate();
                return affected > 0;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error recording session: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<SessionData> getLastSession(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM sessions WHERE player_id = ? ORDER BY start_time DESC LIMIT 1";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SessionData(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getString("session_type"),
                                rs.getLong("start_time"),
                                rs.getLong("end_time"),
                                rs.getLong("duration")
                        );
                    }
                    return null;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting last session: " + e.getMessage());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> batchUpdateDailyUsage(List<DailyUsageData> usageList) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO daily_usage 
                (player_id, date, blocks_used, operations_used, player_group, last_updated) 
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try {
                connection.setAutoCommit(false);

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    for (DailyUsageData usage : usageList) {
                        stmt.setString(1, usage.playerId.toString());
                        stmt.setString(2, usage.date);
                        stmt.setInt(3, usage.blocksUsed);
                        stmt.setInt(4, usage.operationsUsed);
                        stmt.setString(5, usage.playerGroup);
                        stmt.setLong(6, usage.lastUpdated);
                        stmt.addBatch();
                    }

                    int[] results = stmt.executeBatch();
                    connection.commit();

                    plugin.getLogger().info("Batch update completed: " + results.length + " records processed");
                    return true;
                }
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Error during rollback: " + rollbackEx.getMessage());
                }
                plugin.getLogger().severe("Error in batch update: " + e.getMessage());
                return false;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    plugin.getLogger().severe("Error restoring auto-commit: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<DailyUsageData>> getAllDailyUsage(String date) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM daily_usage WHERE date = ?";
            List<DailyUsageData> results = new ArrayList<>();

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, date);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new DailyUsageData(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getString("date"),
                                rs.getInt("blocks_used"),
                                rs.getInt("operations_used"),
                                rs.getString("player_group"),
                                rs.getLong("last_updated")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting all daily usage: " + e.getMessage());
            }

            return results;
        });
    }

    @Override
    public String getDatabaseType() {
        return "SQLite";
    }

    @Override
    public String getConnectionInfo() {
        return "SQLite: " + databaseFile;
    }

    @Override
    public CompletableFuture<DatabaseStats> getDatabaseStats() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long dailyUsageCount = getTableRowCount("daily_usage");
                long playerStatsCount = getTableRowCount("player_stats");
                long sessionCount = getTableRowCount("sessions");
                long totalRecords = dailyUsageCount + playerStatsCount + sessionCount;

                return new DatabaseStats(
                        "SQLite",
                        totalRecords,
                        dailyUsageCount,
                        playerStatsCount,
                        sessionCount,
                        0.0,
                        initialized ? "Connected" : "Disconnected"
                );
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting database stats: " + e.getMessage());
                return new DatabaseStats("SQLite", 0, 0, 0, 0, 0.0, "Error");
            }
        });
    }

    private long getTableRowCount(String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private void logQuery(String sql, String... params) {
        if (plugin.getConfig().getBoolean("database.performance.log-queries", false)) {
            StringBuilder log = new StringBuilder("SQL: ").append(sql);
            if (params.length > 0) {
                log.append(" | Params: ").append(String.join(", ", params));
            }
            plugin.getLogger().info(log.toString());
        }
    }
}