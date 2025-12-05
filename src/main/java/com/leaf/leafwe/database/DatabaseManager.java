package com.leaf.leafwe.database;

import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseManager {

    CompletableFuture<Boolean> initialize();

    CompletableFuture<Void> shutdown();

    CompletableFuture<Boolean> testConnection();

    CompletableFuture<DailyUsageData> getDailyUsage(UUID playerId, String date);
    CompletableFuture<Boolean> updateDailyUsage(UUID playerId, String date, int blocksUsed, int operationsUsed, String group);
    CompletableFuture<Boolean> resetDailyUsage(UUID playerId, String date);
    CompletableFuture<Boolean> cleanupOldData(int daysToKeep);

    CompletableFuture<PlayerStats> getPlayerStats(UUID playerId);
    CompletableFuture<Boolean> updatePlayerStats(UUID playerId, String statType, long value);
    CompletableFuture<Boolean> incrementPlayerStat(UUID playerId, String statType, long increment);

    CompletableFuture<Boolean> recordSession(UUID playerId, String sessionType, long duration);
    CompletableFuture<SessionData> getLastSession(UUID playerId);

    CompletableFuture<Boolean> batchUpdateDailyUsage(java.util.List<DailyUsageData> usageList);
    CompletableFuture<java.util.List<DailyUsageData>> getAllDailyUsage(String date);

    String getDatabaseType();
    String getConnectionInfo();
    CompletableFuture<DatabaseStats> getDatabaseStats();

    class DailyUsageData {
        public final UUID playerId;
        public final String date;
        public final int blocksUsed;
        public final int operationsUsed;
        public final String playerGroup;
        public final long lastUpdated;

        public DailyUsageData(UUID playerId, String date, int blocksUsed, int operationsUsed, String playerGroup, long lastUpdated) {
            this.playerId = playerId;
            this.date = date;
            this.blocksUsed = blocksUsed;
            this.operationsUsed = operationsUsed;
            this.playerGroup = playerGroup;
            this.lastUpdated = lastUpdated;
        }
    }

    class PlayerStats {
        public final UUID playerId;
        public final long totalBlocksPlaced;
        public final long totalOperations;
        public final long totalPlayTime;
        public final String favoriteBlock;
        public final long firstSeen;
        public final long lastSeen;

        public PlayerStats(UUID playerId, long totalBlocksPlaced, long totalOperations, long totalPlayTime,
                           String favoriteBlock, long firstSeen, long lastSeen) {
            this.playerId = playerId;
            this.totalBlocksPlaced = totalBlocksPlaced;
            this.totalOperations = totalOperations;
            this.totalPlayTime = totalPlayTime;
            this.favoriteBlock = favoriteBlock;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
        }
    }

    class SessionData {
        public final UUID playerId;
        public final String sessionType;
        public final long startTime;
        public final long endTime;
        public final long duration;

        public SessionData(UUID playerId, String sessionType, long startTime, long endTime, long duration) {
            this.playerId = playerId;
            this.sessionType = sessionType;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
        }
    }

    class DatabaseStats {
        public final String databaseType;
        public final long totalRecords;
        public final long dailyUsageRecords;
        public final long playerStatsRecords;
        public final long sessionRecords;
        public final double avgResponseTime;
        public final String status;

        public DatabaseStats(String databaseType, long totalRecords, long dailyUsageRecords,
                             long playerStatsRecords, long sessionRecords, double avgResponseTime, String status) {
            this.databaseType = databaseType;
            this.totalRecords = totalRecords;
            this.dailyUsageRecords = dailyUsageRecords;
            this.playerStatsRecords = playerStatsRecords;
            this.sessionRecords = sessionRecords;
            this.avgResponseTime = avgResponseTime;
            this.status = status;
        }
    }
}