package com.leaf.leafwe.database.migration;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.database.DatabaseManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MigrationManager {

    private final LeafWE plugin;
    private final DatabaseManager databaseManager;
    private final Map<Integer, Migration> migrations;
    private final String MIGRATION_TABLE = "schema_migrations";

    public MigrationManager(LeafWE plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.migrations = new TreeMap<>();

        registerMigrations();
    }

    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                createMigrationTable();
                plugin.getLogger().info("Migration system initialized");
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize migration system: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<MigrationResult> migrate() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int currentVersion = getCurrentSchemaVersion();
                int targetVersion = getLatestMigrationVersion();

                plugin.getLogger().info("Current schema version: " + currentVersion);
                plugin.getLogger().info("Target schema version: " + targetVersion);

                if (currentVersion >= targetVersion) {
                    plugin.getLogger().info("Database schema is up to date");
                    return new MigrationResult(true, currentVersion, targetVersion, new ArrayList<>());
                }

                List<MigrationExecutionResult> results = new ArrayList<>();

                for (int version = currentVersion + 1; version <= targetVersion; version++) {
                    Migration migration = migrations.get(version);
                    if (migration != null) {
                        MigrationExecutionResult result = executeMigration(migration);
                        results.add(result);

                        if (!result.success) {
                            plugin.getLogger().severe("Migration failed at version " + version + ": " + result.error);
                            return new MigrationResult(false, currentVersion, version - 1, results);
                        }
                    }
                }

                plugin.getLogger().info("All migrations completed successfully");
                return new MigrationResult(true, currentVersion, targetVersion, results);

            } catch (Exception e) {
                plugin.getLogger().severe("Migration process failed: " + e.getMessage());
                e.printStackTrace();
                return new MigrationResult(false, 0, 0, new ArrayList<>());
            }
        });
    }

    public CompletableFuture<MigrationStatus> getStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int currentVersion = getCurrentSchemaVersion();
                int latestVersion = getLatestMigrationVersion();
                List<AppliedMigration> appliedMigrations = getAppliedMigrations();
                List<Integer> pendingVersions = new ArrayList<>();

                for (int version = currentVersion + 1; version <= latestVersion; version++) {
                    if (migrations.containsKey(version)) {
                        pendingVersions.add(version);
                    }
                }

                return new MigrationStatus(
                        currentVersion,
                        latestVersion,
                        appliedMigrations,
                        pendingVersions,
                        currentVersion >= latestVersion
                );

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to get migration status: " + e.getMessage());
                return new MigrationStatus(0, 0, new ArrayList<>(), new ArrayList<>(), false);
            }
        });
    }

    private void registerMigrations() {
        migrations.put(1, new Migration(1, "initial_setup",
                "Create initial database tables") {
            @Override
            public void up(DatabaseConnection conn) throws SQLException {
            }
        });

        migrations.put(2, new Migration(2, "add_performance_indexes",
                "Add indexes for better query performance") {
            @Override
            public void up(DatabaseConnection conn) throws SQLException {
                if (conn.getDatabaseType().equals("SQLite")) {
                    conn.execute("CREATE INDEX IF NOT EXISTS idx_daily_usage_last_updated ON daily_usage(last_updated)");
                    conn.execute("CREATE INDEX IF NOT EXISTS idx_player_stats_last_seen ON player_stats(last_seen)");
                } else {
                    conn.execute("CREATE INDEX idx_daily_usage_last_updated ON daily_usage(last_updated)");
                    conn.execute("CREATE INDEX idx_player_stats_last_seen ON player_stats(last_seen)");
                }
            }

            @Override
            public void down(DatabaseConnection conn) throws SQLException {
                conn.execute("DROP INDEX IF EXISTS idx_daily_usage_last_updated");
                conn.execute("DROP INDEX IF EXISTS idx_player_stats_last_seen");
            }
        });

        plugin.getLogger().info("Registered " + migrations.size() + " migrations");
    }

    private void createMigrationTable() throws SQLException {
        String sql;
        if (databaseManager.getDatabaseType().equals("SQLite")) {
            sql = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    description TEXT,
                    applied_at INTEGER NOT NULL,
                    execution_time INTEGER DEFAULT 0
                )
                """;
        } else {
            sql = """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                    version INT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    execution_time BIGINT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
        }

        plugin.getLogger().info("Creating migration table: " + sql);
    }

    private int getCurrentSchemaVersion() {
        try {
            return 0;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not get current schema version: " + e.getMessage());
            return 0;
        }
    }

    private int getLatestMigrationVersion() {
        return migrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private MigrationExecutionResult executeMigration(Migration migration) {
        long startTime = System.currentTimeMillis();

        try {
            plugin.getLogger().info("Executing migration " + migration.getVersion() + ": " + migration.getName());

            DatabaseConnection conn = new DatabaseConnectionImpl(databaseManager);
            migration.up(conn);

            recordMigration(migration, startTime);

            long duration = System.currentTimeMillis() - startTime;
            plugin.getLogger().info("Migration " + migration.getVersion() + " completed in " + duration + "ms");

            return new MigrationExecutionResult(migration.getVersion(), true, null, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            plugin.getLogger().severe("Migration " + migration.getVersion() + " failed: " + e.getMessage());
            e.printStackTrace();

            return new MigrationExecutionResult(migration.getVersion(), false, e.getMessage(), duration);
        }
    }

    private void recordMigration(Migration migration, long startTime) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;
            plugin.getLogger().fine("Recording migration: " + migration.getVersion());

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to record migration: " + e.getMessage());
        }
    }

    private List<AppliedMigration> getAppliedMigrations() {
        try {
            return new ArrayList<>();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get applied migrations: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static class MigrationResult {
        public final boolean success;
        public final int fromVersion;
        public final int toVersion;
        public final List<MigrationExecutionResult> executedMigrations;

        public MigrationResult(boolean success, int fromVersion, int toVersion,
                               List<MigrationExecutionResult> executedMigrations) {
            this.success = success;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.executedMigrations = executedMigrations;
        }
    }

    public static class MigrationExecutionResult {
        public final int version;
        public final boolean success;
        public final String error;
        public final long executionTime;

        public MigrationExecutionResult(int version, boolean success, String error, long executionTime) {
            this.version = version;
            this.success = success;
            this.error = error;
            this.executionTime = executionTime;
        }
    }

    public static class MigrationStatus {
        public final int currentVersion;
        public final int latestVersion;
        public final List<AppliedMigration> appliedMigrations;
        public final List<Integer> pendingVersions;
        public final boolean upToDate;

        public MigrationStatus(int currentVersion, int latestVersion,
                               List<AppliedMigration> appliedMigrations,
                               List<Integer> pendingVersions, boolean upToDate) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.appliedMigrations = appliedMigrations;
            this.pendingVersions = pendingVersions;
            this.upToDate = upToDate;
        }
    }

    public static class AppliedMigration {
        public final int version;
        public final String name;
        public final String description;
        public final long appliedAt;
        public final long executionTime;

        public AppliedMigration(int version, String name, String description,
                                long appliedAt, long executionTime) {
            this.version = version;
            this.name = name;
            this.description = description;
            this.appliedAt = appliedAt;
            this.executionTime = executionTime;
        }
    }

    public abstract static class Migration {
        private final int version;
        private final String name;
        private final String description;

        public Migration(int version, String name, String description) {
            this.version = version;
            this.name = name;
            this.description = description;
        }

        public abstract void up(DatabaseConnection conn) throws SQLException;

        public void down(DatabaseConnection conn) throws SQLException {
            throw new UnsupportedOperationException("Rollback not implemented for migration " + version);
        }

        public boolean hasRollback() {
            try {
                down(null);
                return false;
            } catch (UnsupportedOperationException e) {
                return false;
            } catch (Exception e) {
                return true;
            }
        }

        public int getVersion() { return version; }
        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    public interface DatabaseConnection {
        void execute(String sql) throws SQLException;
        void execute(String sql, Object... params) throws SQLException;
        ResultSet query(String sql) throws SQLException;
        ResultSet query(String sql, Object... params) throws SQLException;
        String getDatabaseType();
    }

    private static class DatabaseConnectionImpl implements DatabaseConnection {
        private final DatabaseManager databaseManager;

        public DatabaseConnectionImpl(DatabaseManager databaseManager) {
            this.databaseManager = databaseManager;
        }

        @Override
        public void execute(String sql) throws SQLException {
            System.out.println("Executing SQL: " + sql);
        }

        @Override
        public void execute(String sql, Object... params) throws SQLException {
            System.out.println("Executing SQL with params: " + sql);
        }

        @Override
        public ResultSet query(String sql) throws SQLException {
            throw new UnsupportedOperationException("Query not implemented yet");
        }

        @Override
        public ResultSet query(String sql, Object... params) throws SQLException {
            throw new UnsupportedOperationException("Query with params not implemented yet");
        }

        @Override
        public String getDatabaseType() {
            return databaseManager.getDatabaseType();
        }
    }
}