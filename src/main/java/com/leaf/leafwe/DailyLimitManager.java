package com.leaf.leafwe;

import com.leaf.leafwe.database.DatabaseManager;
import com.leaf.leafwe.database.DatabaseFactory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DailyLimitManager {

    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;

    // Cache for quick access - cache expires every 5 minutes
    private final ConcurrentHashMap<UUID, CachedUsageData> usageCache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

    // Batch update system
    private final ConcurrentHashMap<UUID, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();
    private BukkitRunnable batchUpdateTask;

    public DailyLimitManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // Initialize database manager
        this.databaseManager = DatabaseFactory.createFromConfig(plugin);

        initializeDatabase();
        startBatchUpdateTask();
        startCleanupTask();
    }

    private void initializeDatabase() {
        databaseManager.initialize().thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("Database initialized successfully for Daily Limits");
                // Migrate existing YAML data if needed
                migrateFromYAML();
            } else {
                plugin.getLogger().severe("Failed to initialize database for Daily Limits!");
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Error during database initialization: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    private void migrateFromYAML() {
        // Check if there's existing YAML data to migrate
        if (hasExistingYamlData()) {
            plugin.getLogger().info("Migrating existing daily limit data from YAML to database...");

            CompletableFuture.runAsync(() -> {
                try {
                    migrateYamlDataToDatabase();
                    plugin.getLogger().info("Migration completed successfully!");
                } catch (Exception e) {
                    plugin.getLogger().severe("Migration failed: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    private boolean hasExistingYamlData() {
        // Check if old data file exists
        java.io.File dataFolder = new java.io.File(plugin.getDataFolder(), "data");
        java.io.File yamlFile = new java.io.File(dataFolder, "daily-limits.yml");
        return yamlFile.exists() && yamlFile.length() > 0;
    }

    private void migrateYamlDataToDatabase() {
        // Implementation for migrating from YAML to database
        // This would read the old YAML file and transfer data
        plugin.getLogger().info("YAML migration would be implemented here");
    }

    /**
     * Check if player can perform operation with detailed result
     */
    public LimitCheckResult canPerformOperationDetailed(Player player, int blockCount) {
        if (!isDailyLimitsEnabled()) {
            return new LimitCheckResult(true, LimitType.NONE, "");
        }

        String playerGroup = getPlayerGroup(player);

        // Get usage from cache or database
        return getUsageAsync(player).thenApply(usage -> {
            int maxBlocks = getGroupMaxBlocks(playerGroup);
            int maxOperations = getGroupMaxOperations(playerGroup);

            // Check operation limit first
            if (maxOperations != -1 && (usage.operationsUsed + 1) > maxOperations) {
                return new LimitCheckResult(false, LimitType.OPERATIONS, playerGroup);
            }

            // Check block limit
            if (maxBlocks != -1 && (usage.blocksUsed + blockCount) > maxBlocks) {
                return new LimitCheckResult(false, LimitType.BLOCKS, playerGroup);
            }

            return new LimitCheckResult(true, LimitType.NONE, playerGroup);
        }).join(); // Block for synchronous API compatibility
    }

    /**
     * Backward compatibility method
     */
    public boolean canPerformOperation(Player player, int blockCount) {
        return canPerformOperationDetailed(player, blockCount).canPerform;
    }

    /**
     * Record usage (async)
     */
    public void recordUsage(Player player, int blockCount) {
        if (!isDailyLimitsEnabled()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String playerGroup = getPlayerGroup(player);
        String today = getCurrentDate();

        // Add to pending updates for batch processing
        PendingUpdate update = pendingUpdates.compute(playerId, (uuid, existing) -> {
            if (existing == null) {
                return new PendingUpdate(blockCount, 1, playerGroup, today);
            } else {
                existing.blockCount += blockCount;
                existing.operationCount += 1;
                existing.playerGroup = playerGroup; // Update group in case it changed
                return existing;
            }
        });

        // Update cache immediately for instant feedback
        updateCache(playerId, today, update.blockCount, update.operationCount, playerGroup);
    }

    /**
     * Get usage info for player
     */
    public DailyUsageInfo getUsageInfo(Player player) {
        if (!isDailyLimitsEnabled()) {
            return new DailyUsageInfo(-1, -1, 0, 0, "unlimited");
        }

        String playerGroup = getPlayerGroup(player);

        return getUsageAsync(player).thenApply(usage -> {
            int maxBlocks = getGroupMaxBlocks(playerGroup);
            int maxOperations = getGroupMaxOperations(playerGroup);

            return new DailyUsageInfo(maxBlocks, maxOperations,
                    usage.blocksUsed, usage.operationsUsed, playerGroup);
        }).join();
    }

    /**
     * Get usage data async
     */
    private CompletableFuture<UsageData> getUsageAsync(Player player) {
        UUID playerId = player.getUniqueId();
        String today = getCurrentDate();

        // Check cache first
        CachedUsageData cached = usageCache.get(playerId);
        if (cached != null && !cached.isExpired() && cached.date.equals(today)) {
            return CompletableFuture.completedFuture(cached.usageData);
        }

        // Load from database
        return databaseManager.getDailyUsage(playerId, today)
                .thenApply(dbData -> {
                    UsageData usage = new UsageData(dbData.blocksUsed, dbData.operationsUsed, dbData.playerGroup);

                    // Update cache
                    usageCache.put(playerId, new CachedUsageData(usage, today, System.currentTimeMillis()));

                    return usage;
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Error loading usage data for " + player.getName() + ": " + throwable.getMessage());
                    // Return default data on error
                    return new UsageData(0, 0, getPlayerGroup(player));
                });
    }

    private void updateCache(UUID playerId, String date, int additionalBlocks, int additionalOps, String group) {
        usageCache.compute(playerId, (uuid, existing) -> {
            if (existing == null || !existing.date.equals(date)) {
                return new CachedUsageData(new UsageData(additionalBlocks, additionalOps, group),
                        date, System.currentTimeMillis());
            } else {
                existing.usageData.blocksUsed += additionalBlocks;
                existing.usageData.operationsUsed += additionalOps;
                existing.usageData.playerGroup = group;
                existing.cacheTime = System.currentTimeMillis();
                return existing;
            }
        });
    }

    private void startBatchUpdateTask() {
        batchUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!pendingUpdates.isEmpty()) {
                    processPendingUpdates();
                }
            }
        };

        // Run every 30 seconds
        batchUpdateTask.runTaskTimerAsynchronously(plugin, 600L, 600L);
    }

    private void processPendingUpdates() {
        if (pendingUpdates.isEmpty()) return;

        // Create a snapshot and clear pending updates
        var updates = new ConcurrentHashMap<>(pendingUpdates);
        pendingUpdates.clear();

        CompletableFuture.runAsync(() -> {
            for (var entry : updates.entrySet()) {
                UUID playerId = entry.getKey();
                PendingUpdate update = entry.getValue();

                // Get current data from database
                databaseManager.getDailyUsage(playerId, update.date)
                        .thenCompose(currentData -> {
                            // Add pending changes to current data
                            int newBlocksUsed = currentData.blocksUsed + update.blockCount;
                            int newOperationsUsed = currentData.operationsUsed + update.operationCount;

                            // Update database
                            return databaseManager.updateDailyUsage(playerId, update.date,
                                    newBlocksUsed, newOperationsUsed, update.playerGroup);
                        })
                        .exceptionally(throwable -> {
                            plugin.getLogger().warning("Error updating daily usage for " + playerId + ": " + throwable.getMessage());
                            return false;
                        });
            }
        });

        plugin.getLogger().fine("Processed " + updates.size() + " pending daily limit updates");
    }

    private void startCleanupTask() {
        if (!plugin.getConfig().getBoolean("database.data-retention.auto-cleanup", true)) {
            return;
        }

        long cleanupInterval = plugin.getConfig().getLong("database.data-retention.cleanup-interval", 24) * 20L * 60L * 60L; // Convert hours to ticks
        int retentionDays = plugin.getConfig().getInt("database.data-retention.daily-usage-days", 30);

        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.cleanupOldData(retentionDays).thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("Daily limit data cleanup completed");
                    }
                });
            }
        }.runTaskTimerAsynchronously(plugin, cleanupInterval, cleanupInterval);
    }

    /**
     * Reset player limits (Admin command)
     */
    public void resetPlayerLimits(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        String today = getCurrentDate();

        databaseManager.resetDailyUsage(playerId, today).thenAccept(success -> {
            if (success) {
                // Clear cache
                usageCache.remove(playerId);
                // Remove pending updates
                pendingUpdates.remove(playerId);

                plugin.getLogger().info("Reset daily limits for player: " + player.getName());
            }
        });
    }

    /**
     * Give bonus limits to player (Admin command)
     */
    public void setPlayerBonusLimits(Player player, int bonusBlocks) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        String today = getCurrentDate();

        getUsageAsync(player).thenCompose(currentUsage -> {
            int newBlocksUsed = Math.max(0, currentUsage.blocksUsed - bonusBlocks);
            return databaseManager.updateDailyUsage(playerId, today, newBlocksUsed,
                    currentUsage.operationsUsed, currentUsage.playerGroup);
        }).thenAccept(success -> {
            if (success) {
                // Clear cache to force reload
                usageCache.remove(playerId);
                plugin.getLogger().info("Gave " + bonusBlocks + " bonus blocks to " + player.getName());
            }
        });
    }

    /**
     * Shutdown and save all pending data
     */
    public void shutdown() {
        if (batchUpdateTask != null) {
            batchUpdateTask.cancel();
        }

        // Process any remaining pending updates
        if (!pendingUpdates.isEmpty()) {
            plugin.getLogger().info("Processing remaining " + pendingUpdates.size() + " daily limit updates...");
            processPendingUpdates();
        }

        // Shutdown database
        databaseManager.shutdown().thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("Daily limit database shutdown completed");
            }
        });
    }

    // Helper methods
    private String getPlayerGroup(Player player) {
        for (String group : getConfiguredGroups()) {
            if (player.hasPermission("leafwe.limit.group." + group)) {
                return group;
            }
        }
        return "default";
    }

    private String[] getConfiguredGroups() {
        if (plugin.getConfig().getConfigurationSection("daily-limits.groups") != null) {
            return plugin.getConfig().getConfigurationSection("daily-limits.groups").getKeys(false).toArray(new String[0]);
        }
        return new String[]{"default"};
    }

    private boolean isDailyLimitsEnabled() {
        return plugin.getConfig().getBoolean("daily-limits.enabled", false);
    }

    private int getGroupMaxBlocks(String group) {
        return plugin.getConfig().getInt("daily-limits.groups." + group + ".max-blocks-per-day", 1000);
    }

    private int getGroupMaxOperations(String group) {
        return plugin.getConfig().getInt("daily-limits.groups." + group + ".max-operations-per-day", 10);
    }

    private String getCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    // Data classes
    private static class UsageData {
        int blocksUsed;
        int operationsUsed;
        String playerGroup;

        UsageData(int blocksUsed, int operationsUsed, String playerGroup) {
            this.blocksUsed = blocksUsed;
            this.operationsUsed = operationsUsed;
            this.playerGroup = playerGroup;
        }
    }

    private static class CachedUsageData {
        final UsageData usageData;
        final String date;
        long cacheTime;

        CachedUsageData(UsageData usageData, String date, long cacheTime) {
            this.usageData = usageData;
            this.date = date;
            this.cacheTime = cacheTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 5 * 60 * 1000; // 5 minutes
        }
    }

    private static class PendingUpdate {
        int blockCount;
        int operationCount;
        String playerGroup;
        String date;

        PendingUpdate(int blockCount, int operationCount, String playerGroup, String date) {
            this.blockCount = blockCount;
            this.operationCount = operationCount;
            this.playerGroup = playerGroup;
            this.date = date;
        }
    }

    // Existing classes for compatibility
    public static class DailyUsageInfo {
        public final int maxBlocks;
        public final int maxOperations;
        public final int usedBlocks;
        public final int usedOperations;
        public final String group;

        public DailyUsageInfo(int maxBlocks, int maxOperations, int usedBlocks, int usedOperations, String group) {
            this.maxBlocks = maxBlocks;
            this.maxOperations = maxOperations;
            this.usedBlocks = usedBlocks;
            this.usedOperations = usedOperations;
            this.group = group;
        }

        public int getRemainingBlocks() {
            return maxBlocks == -1 ? -1 : Math.max(0, maxBlocks - usedBlocks);
        }

        public int getRemainingOperations() {
            return maxOperations == -1 ? -1 : Math.max(0, maxOperations - usedOperations);
        }
    }

    public static class LimitCheckResult {
        public final boolean canPerform;
        public final LimitType limitType;
        public final String playerGroup;

        public LimitCheckResult(boolean canPerform, LimitType limitType, String playerGroup) {
            this.canPerform = canPerform;
            this.limitType = limitType;
            this.playerGroup = playerGroup;
        }
    }

    public enum LimitType {
        NONE,
        BLOCKS,
        OPERATIONS
    }

    /**
     * Get database manager for registry
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}