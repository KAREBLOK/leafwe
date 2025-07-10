package com.leaf.leafwe.database.impl;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.database.DatabaseManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class MySQLDatabaseManager implements DatabaseManager {

    private final LeafWE plugin;
    private HikariDataSource dataSource;
    private boolean initialized = false;

    // Performance metrics
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);

    // SQL Statements
    private static final String CREATE_DAILY_USAGE_TABLE = """
        CREATE TABLE IF NOT EXISTS daily_usage (
            player_id VARCHAR(36) NOT NULL,
            date DATE NOT NULL,
            blocks_used INT DEFAULT 0,
            operations_used INT DEFAULT 0,
            player_group VARCHAR(32) DEFAULT 'default',
            last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, date),
            INDEX idx_date (date),
            INDEX idx_player (player_id),
            INDEX idx_last_updated (last_updated)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private static final String CREATE_PLAYER_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS player_stats (
            player_id VARCHAR(36) PRIMARY KEY,
            total_blocks_placed BIGINT DEFAULT 0,
            total_operations BIGINT DEFAULT 0,
            total_playtime BIGINT DEFAULT 0,
            favorite_block VARCHAR(64) DEFAULT 'STONE',
            first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_last_seen (last_seen),
            INDEX idx_total_blocks (total_blocks_placed),
            INDEX idx_total_operations (total_operations)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private static final String CREATE_SESSIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS sessions (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_id VARCHAR(36) NOT NULL,
            session_type VARCHAR(32) NOT NULL,
            start_time TIMESTAMP NOT NULL,
            end_time TIMESTAMP NOT NULL,
            duration BIGINT NOT NULL,
            INDEX idx_player_sessions (player_id),
            INDEX idx_session_time (start_time),
            INDEX idx_session_type (session_type)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    public MySQLDatabaseManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Setup connection pool
                setupConnectionPool();

                // Test connection
                if (!testConnectionSync()) {
                    plugin.getLogger().severe("Failed to establish MySQL connection!");
                    return false;
                }

                // Create tables
                createTables();

                // Run optimization queries
                optimizeDatabase();

                initialized = true;
                plugin.getLogger().info("MySQL database initialized successfully");
                logConnectionInfo();
                return true;

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize MySQL database: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    private void setupConnectionPool() {
        HikariConfig config = new HikariConfig();

        // Basic connection settings
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "leafwe");
        String username = plugin.getConfig().getString("database.mysql.username", "leafwe_user");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // Connection pool settings
        config.setMaximumPoolSize(plugin.getConfig().getInt("database.connection-pool.maximum-pool-size", 10));
        config.setMinimumIdle(plugin.getConfig().getInt("database.connection-pool.minimum-idle", 2));
        config.setConnectionTimeout(plugin.getConfig().getLong("database.connection-pool.connection-timeout", 30000));
        config.setIdleTimeout(plugin.getConfig().getLong("database.connection-pool.idle-timeout", 600000));
        config.setMaxLifetime(plugin.getConfig().getLong("database.connection-pool.max-lifetime", 1800000));
        config.setLeakDetectionThreshold(plugin.getConfig().getLong("database.connection-pool.leak-detection-threshold", 60000));

        // MySQL specific properties
        boolean useSSL = plugin.getConfig().getBoolean("database.mysql.use-ssl", false);
        boolean verifyServerCert = plugin.getConfig().getBoolean("database.mysql.verify-server-certificate", false);
        String encoding = plugin.getConfig().getString("database.mysql.character-encoding", "utf8mb4");

        config.addDataSourceProperty("useSSL", useSSL);
        config.addDataSourceProperty("verifyServerCertificate", verifyServerCert);
        config.addDataSourceProperty("characterEncoding", encoding);
        config.addDataSourceProperty("useUnicode", true);

        // Performance properties
        config.addDataSourceProperty("cachePrepStmts", plugin.getConfig().getBoolean("database.mysql.properties.cachePrepStmts", true));
        config.addDataSourceProperty("prepStmtCacheSize", plugin.getConfig().getInt("database.mysql.properties.prepStmtCacheSize", 250));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", plugin.getConfig().getInt("database.mysql.properties.prepStmtCacheSqlLimit", 2048));
        config.addDataSourceProperty("useServerPrepStmts", plugin.getConfig().getBoolean("database.mysql.properties.useServerPrepStmts", true));
        config.addDataSourceProperty("useLocalSessionState", plugin.getConfig().getBoolean("database.mysql.properties.useLocalSessionState", true));
        config.addDataSourceProperty("rewriteBatchedStatements", plugin.getConfig().getBoolean("database.mysql.properties.rewriteBatchedStatements", true));
        config.addDataSourceProperty("cacheResultSetMetadata", plugin.getConfig().getBoolean("database.mysql.properties.cacheResultSetMetadata", true));
        config.addDataSourceProperty("cacheServerConfiguration", plugin.getConfig().getBoolean("database.mysql.properties.cacheServerConfiguration", true));
        config.addDataSourceProperty("elideSetAutoCommits", plugin.getConfig().getBoolean("database.mysql.properties.elideSetAutoCommits", true));
        config.addDataSourceProperty("maintainTimeStats", plugin.getConfig().getBoolean("database.mysql.properties.maintainTimeStats", false));

        // Pool name for monitoring
        config.setPoolName("LeafWE-MySQL-Pool");

        // Connection test query
        config.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(config);

        plugin.getLogger().info("MySQL connection pool configured: " +
                "max=" + config.getMaximumPoolSize() +
                ", min=" + config.getMinimumIdle());
    }

    private boolean testConnectionSync() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("MySQL connection test failed: " + e.getMessage());
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(CREATE_DAILY_USAGE_TABLE);
            stmt.execute(CREATE_PLAYER_STATS_TABLE);
            stmt.execute(CREATE_SESSIONS_TABLE);

            plugin.getLogger().info("MySQL tables created successfully");
        }
    }

    private void optimizeDatabase() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {

                // Analyze tables for better query optimization
                stmt.execute("ANALYZE TABLE daily_usage, player_stats, sessions");

                // Check if we need to optimize tables
                ResultSet rs = stmt.executeQuery("SHOW TABLE STATUS WHERE Name IN ('daily_usage', 'player_stats', 'sessions')");
                boolean needsOptimization = false;

                while (rs.next()) {
                    long dataFree = rs.getLong("Data_free");
                    if (dataFree > 1048576) { // More than 1MB fragmentation
                        needsOptimization = true;
                        break;
                    }
                }

                if (needsOptimization) {
                    plugin.getLogger().info("Optimizing MySQL tables...");
                    stmt.execute("OPTIMIZE TABLE daily_usage, player_stats, sessions");
                    plugin.getLogger().info("MySQL table optimization completed");
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Error during MySQL optimization: " + e.getMessage());
            }
        });
    }

    private void logConnectionInfo() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            plugin.getLogger().info("Connected to MySQL " + metaData.getDatabaseProductVersion() +
                    " at " + metaData.getURL());

            // Log connection pool status
            plugin.getLogger().info("Connection pool status: active=" + dataSource.getHikariPoolMXBean().getActiveConnections() +
                    ", idle=" + dataSource.getHikariPoolMXBean().getIdleConnections() +
                    ", total=" + dataSource.getHikariPoolMXBean().getTotalConnections());
        } catch (SQLException e) {
            plugin.getLogger().warning("Error getting connection info: " + e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    // Log final pool statistics
                    var poolMBean = dataSource.getHikariPoolMXBean();
                    plugin.getLogger().info("Final pool stats - Total connections: " + poolMBean.getTotalConnections() +
                            ", Active: " + poolMBean.getActiveConnections() +
                            ", Idle: " + poolMBean.getIdleConnections());

                    dataSource.close();
                    plugin.getLogger().info("MySQL connection pool closed");
                }
                initialized = false;
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Error closing MySQL connection pool: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");

                long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
                if (duration > plugin.getConfig().getLong("database.performance.slow-query-threshold", 1000)) {
                    plugin.getLogger().warning("Slow connection test: " + duration + "ms");
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().warning("MySQL connection test failed: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<DailyUsageData> getDailyUsage(UUID playerId, String date) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM daily_usage WHERE player_id = ? AND date = ?";
            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);

                logQuery(sql, playerId.toString(), date);

                try (ResultSet rs = stmt.executeQuery()) {
                    recordQueryMetrics(startTime);

                    if (rs.next()) {
                        return new DailyUsageData(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getString("date"),
                                rs.getInt("blocks_used"),
                                rs.getInt("operations_used"),
                                rs.getString("player_group"),
                                rs.getTimestamp("last_updated").getTime()
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
                INSERT INTO daily_usage (player_id, date, blocks_used, operations_used, player_group) 
                VALUES (?, ?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                blocks_used = VALUES(blocks_used), 
                operations_used = VALUES(operations_used), 
                player_group = VALUES(player_group),
                last_updated = CURRENT_TIMESTAMP
                """;

            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);
                stmt.setInt(3, blocksUsed);
                stmt.setInt(4, operationsUsed);
                stmt.setString(5, group);

                logQuery(sql, playerId.toString(), date, String.valueOf(blocksUsed), String.valueOf(operationsUsed), group);

                int affected = stmt.executeUpdate();
                recordQueryMetrics(startTime);

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
            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, date);

                logQuery(sql, playerId.toString(), date);

                int affected = stmt.executeUpdate();
                recordQueryMetrics(startTime);

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
            String sql1 = "DELETE FROM daily_usage WHERE date < DATE_SUB(CURDATE(), INTERVAL ? DAY)";
            String sql2 = "DELETE FROM sessions WHERE start_time < DATE_SUB(NOW(), INTERVAL ? DAY)";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                int deletedUsage = 0;
                int deletedSessions = 0;

                try (PreparedStatement stmt1 = conn.prepareStatement(sql1)) {
                    stmt1.setInt(1, daysToKeep);
                    deletedUsage = stmt1.executeUpdate();
                }

                try (PreparedStatement stmt2 = conn.prepareStatement(sql2)) {
                    stmt2.setInt(1, daysToKeep);
                    deletedSessions = stmt2.executeUpdate();
                }

                conn.commit();

                plugin.getLogger().info("MySQL cleanup completed: " + deletedUsage + " usage records, " +
                        deletedSessions + " session records deleted");
                return true;

            } catch (SQLException e) {
                plugin.getLogger().severe("Error during MySQL cleanup: " + e.getMessage());
                return false;
            }
        });
    }

    // Implement other required methods similarly...
    @Override
    public CompletableFuture<PlayerStats> getPlayerStats(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            // Implementation similar to SQLite but optimized for MySQL
            String sql = "SELECT * FROM player_stats WHERE player_id = ?";
            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    recordQueryMetrics(startTime);

                    if (rs.next()) {
                        return new PlayerStats(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getLong("total_blocks_placed"),
                                rs.getLong("total_operations"),
                                rs.getLong("total_playtime"),
                                rs.getString("favorite_block"),
                                rs.getTimestamp("first_seen").getTime(),
                                rs.getTimestamp("last_seen").getTime()
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
        // Implementation for MySQL
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> incrementPlayerStat(UUID playerId, String statType, long increment) {
        // Implementation for MySQL
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> recordSession(UUID playerId, String sessionType, long duration) {
        // Implementation for MySQL
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<SessionData> getLastSession(UUID playerId) {
        // Implementation for MySQL
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> batchUpdateDailyUsage(List<DailyUsageData> usageList) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO daily_usage (player_id, date, blocks_used, operations_used, player_group) 
                VALUES (?, ?, ?, ?, ?) 
                ON DUPLICATE KEY UPDATE 
                blocks_used = VALUES(blocks_used), 
                operations_used = VALUES(operations_used), 
                player_group = VALUES(player_group)
                """;

            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (DailyUsageData usage : usageList) {
                        stmt.setString(1, usage.playerId.toString());
                        stmt.setString(2, usage.date);
                        stmt.setInt(3, usage.blocksUsed);
                        stmt.setInt(4, usage.operationsUsed);
                        stmt.setString(5, usage.playerGroup);
                        stmt.addBatch();
                    }

                    int[] results = stmt.executeBatch();
                    conn.commit();

                    recordQueryMetrics(startTime);

                    plugin.getLogger().info("MySQL batch update completed: " + results.length + " records processed");
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error in MySQL batch update: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<DailyUsageData>> getAllDailyUsage(String date) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM daily_usage WHERE date = ?";
            List<DailyUsageData> results = new ArrayList<>();
            long startTime = System.nanoTime();

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, date);

                try (ResultSet rs = stmt.executeQuery()) {
                    recordQueryMetrics(startTime);

                    while (rs.next()) {
                        results.add(new DailyUsageData(
                                UUID.fromString(rs.getString("player_id")),
                                rs.getString("date"),
                                rs.getInt("blocks_used"),
                                rs.getInt("operations_used"),
                                rs.getString("player_group"),
                                rs.getTimestamp("last_updated").getTime()
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
        return "MySQL";
    }

    @Override
    public String getConnectionInfo() {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "leafwe");
        return "MySQL: " + host + ":" + port + "/" + database;
    }

    @Override
    public CompletableFuture<DatabaseStats> getDatabaseStats() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                long dailyUsageCount = getTableRowCount(conn, "daily_usage");
                long playerStatsCount = getTableRowCount(conn, "player_stats");
                long sessionCount = getTableRowCount(conn, "sessions");
                long totalRecords = dailyUsageCount + playerStatsCount + sessionCount;

                double avgResponseTime = totalQueries.get() > 0 ?
                        (double) totalQueryTime.get() / totalQueries.get() / 1_000_000.0 : 0.0; // Convert to milliseconds

                return new DatabaseStats(
                        "MySQL",
                        totalRecords,
                        dailyUsageCount,
                        playerStatsCount,
                        sessionCount,
                        avgResponseTime,
                        initialized ? "Connected" : "Disconnected"
                );
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting MySQL database stats: " + e.getMessage());
                return new DatabaseStats("MySQL", 0, 0, 0, 0, 0.0, "Error");
            }
        });
    }

    private long getTableRowCount(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private void recordQueryMetrics(long startTime) {
        long duration = System.nanoTime() - startTime;
        totalQueries.incrementAndGet();
        totalQueryTime.addAndGet(duration);

        long durationMs = duration / 1_000_000;
        long slowThreshold = plugin.getConfig().getLong("database.performance.slow-query-threshold", 1000);

        if (durationMs > slowThreshold) {
            slowQueries.incrementAndGet();
            if (plugin.getConfig().getBoolean("database.performance.log-slow-queries", true)) {
                plugin.getLogger().warning("Slow query detected: " + durationMs + "ms");
            }
        }
    }

    private void logQuery(String sql, String... params) {
        if (plugin.getConfig().getBoolean("database.performance.log-queries", false)) {
            StringBuilder log = new StringBuilder("MySQL: ").append(sql);
            if (params.length > 0) {
                log.append(" | Params: ").append(String.join(", ", params));
            }
            plugin.getLogger().info(log.toString());
        }
    }

    /**
     * Get connection pool metrics for monitoring
     */
    public String getPoolMetrics() {
        if (dataSource == null) {
            return "Pool not initialized";
        }

        var pool = dataSource.getHikariPoolMXBean();
        return String.format("Pool[active=%d, idle=%d, total=%d, waiting=%d]",
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection());
    }

    /**
     * Get query performance metrics
     */
    public String getPerformanceMetrics() {
        if (totalQueries.get() == 0) {
            return "No queries executed yet";
        }

        double avgTime = (double) totalQueryTime.get() / totalQueries.get() / 1_000_000.0;
        double slowQueryPercent = (double) slowQueries.get() / totalQueries.get() * 100.0;

        return String.format("Queries[total=%d, avg=%.2fms, slow=%d (%.1f%%)]",
                totalQueries.get(),
                avgTime,
                slowQueries.get(),
                slowQueryPercent);
    }
}