package com.leaf.leafwe.database;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.database.impl.SQLiteDatabaseManager;
import com.leaf.leafwe.database.impl.MySQLDatabaseManager;

public class DatabaseFactory {

    public enum DatabaseType {
        SQLITE,
        MYSQL
    }

    public static DatabaseManager createDatabaseManager(LeafWE plugin, DatabaseType type) {
        switch (type) {
            case SQLITE:
                return new SQLiteDatabaseManager(plugin);
            case MYSQL:
                return new MySQLDatabaseManager(plugin);
            default:
                throw new IllegalArgumentException("Unsupported database type: " + type);
        }
    }

    public static DatabaseManager createFromConfig(LeafWE plugin) {
        String databaseType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();

        switch (databaseType) {
            case "sqlite":
                plugin.getLogger().info("Using SQLite database");
                return new SQLiteDatabaseManager(plugin);
            case "mysql":
                plugin.getLogger().info("Using MySQL database");
                return new MySQLDatabaseManager(plugin);
            default:
                plugin.getLogger().warning("Unknown database type '" + databaseType + "', falling back to SQLite");
                return new SQLiteDatabaseManager(plugin);
        }
    }

    public static DatabaseType getRecommendedType(LeafWE plugin) {
        boolean isProduction = plugin.getConfig().getBoolean("database.production-mode", false);

        if (isProduction) {
            return DatabaseType.MYSQL;
        } else {
            return DatabaseType.SQLITE;
        }
    }

    public static boolean validateConfig(LeafWE plugin, DatabaseType type) {
        switch (type) {
            case SQLITE:
                return validateSQLiteConfig(plugin);
            case MYSQL:
                return validateMySQLConfig(plugin);
            default:
                return false;
        }
    }

    private static boolean validateSQLiteConfig(LeafWE plugin) {
        String dbPath = plugin.getConfig().getString("database.sqlite.file", "data/leafwe.db");
        return dbPath != null && !dbPath.trim().isEmpty();
    }

    private static boolean validateMySQLConfig(LeafWE plugin) {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database");
        String username = plugin.getConfig().getString("database.mysql.username");
        String password = plugin.getConfig().getString("database.mysql.password");

        if (host == null || host.trim().isEmpty()) {
            plugin.getLogger().severe("MySQL host is not configured!");
            return false;
        }

        if (database == null || database.trim().isEmpty()) {
            plugin.getLogger().severe("MySQL database name is not configured!");
            return false;
        }

        if (username == null || username.trim().isEmpty()) {
            plugin.getLogger().severe("MySQL username is not configured!");
            return false;
        }

        if (port <= 0 || port > 65535) {
            plugin.getLogger().severe("MySQL port is invalid: " + port);
            return false;
        }

        return true;
    }

    public static String getConnectionInfo(LeafWE plugin, DatabaseType type) {
        switch (type) {
            case SQLITE:
                String dbPath = plugin.getConfig().getString("database.sqlite.file", "data/leafwe.db");
                return "SQLite: " + dbPath;
            case MYSQL:
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String database = plugin.getConfig().getString("database.mysql.database", "leafwe");
                return "MySQL: " + host + ":" + port + "/" + database;
            default:
                return "Unknown database type";
        }
    }
}