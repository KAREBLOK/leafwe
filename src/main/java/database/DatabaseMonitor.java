package com.leaf.leafwe.database;

import com.leaf.leafwe.LeafWE;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

/**
 * Database performance and health monitoring system
 */
public class DatabaseMonitor {

    private final LeafWE plugin;
    private final DatabaseManager databaseManager;

    // Performance metrics
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicInteger currentConnections = new AtomicInteger(0);
    private final AtomicLong slowQueries = new AtomicLong(0);
    private final AtomicLong failedQueries = new AtomicLong(0);

    // Health monitoring
    private final ConcurrentHashMap<String, Long> lastQueryTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QueryStats> queryStats = new ConcurrentHashMap<>();
    private final List<HealthCheckResult> healthHistory = new ArrayList<>();

    // Configuration
    private final long SLOW_QUERY_THRESHOLD;
    private final long HEALTH_CHECK_INTERVAL;
    private final int MAX_HEALTH_HISTORY;

    // Tasks
    private BukkitRunnable healthCheckTask;
    private BukkitRunnable metricsReportTask;

    public DatabaseMonitor(LeafWE plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        // Load configuration
        this.SLOW_QUERY_THRESHOLD = plugin.getConfig().getLong("database.performance.slow-query-threshold", 1000);
        this.HEALTH_CHECK_INTERVAL = plugin.getConfig().getLong("database.monitoring.health-check-interval", 300); // 5 minutes
        this.MAX_HEALTH_HISTORY = plugin.getConfig().getInt("database.monitoring.max-health-history", 100);

        startMonitoring();
    }

    /**
     * Start monitoring tasks
     */
    private void startMonitoring() {
        startHealthCheckTask();
        startMetricsReportTask();

        plugin.getLogger().info("Database monitoring started - Health checks every " +
                (HEALTH_CHECK_INTERVAL / 60) + " minutes");
    }

    /**
     * Record a query execution
     */
    public void recordQuery(String queryType, long executionTime, boolean success) {
        totalQueries.incrementAndGet();

        if (success) {
            totalQueryTime.addAndGet(executionTime);

            // Track slow queries
            if (executionTime > SLOW_QUERY_THRESHOLD) {
                slowQueries.incrementAndGet();

                if (plugin.getConfig().getBoolean("database.performance.log-slow-queries", true)) {
                    plugin.getLogger().warning("Slow query detected: " + queryType + " took " + executionTime + "ms");
                }
            }

            // Update query stats
            queryStats.compute(queryType, (key, existing) -> {
                if (existing == null) {
                    return new QueryStats(queryType, 1, executionTime, executionTime, executionTime);
                } else {
                    existing.totalExecutions++;
                    existing.totalTime += executionTime;
                    existing.minTime = Math.min(existing.minTime, executionTime);
                    existing.maxTime = Math.max(existing.maxTime, executionTime);
                    return existing;
                }
            });

        } else {
            failedQueries.incrementAndGet();
            plugin.getLogger().warning("Database query failed: " + queryType);
        }

        lastQueryTimes.put(queryType, System.currentTimeMillis());
    }

    /**
     * Start health check monitoring
     */
    private void startHealthCheckTask() {
        healthCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                performHealthCheck();
            }
        };

        // Convert seconds to ticks (20 ticks = 1 second)
        long intervalTicks = HEALTH_CHECK_INTERVAL * 20L;
        healthCheckTask.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    /**
     * Start metrics reporting task
     */
    private void startMetricsReportTask() {
        if (!plugin.getConfig().getBoolean("database.monitoring.enable-periodic-reports", false)) {
            return;
        }

        metricsReportTask = new BukkitRunnable() {
            @Override
            public void run() {
                generateMetricsReport();
            }
        };

        // Report every hour
        long reportInterval = plugin.getConfig().getLong("database.monitoring.report-interval", 3600) * 20L;
        metricsReportTask.runTaskTimerAsynchronously(plugin, reportInterval, reportInterval);
    }

    /**
     * Perform comprehensive health check
     */
    private void performHealthCheck() {
        long startTime = System.currentTimeMillis();

        databaseManager.testConnection().thenCompose(connectionOk -> {
            if (!connectionOk) {
                recordHealthCheck(false, "Connection test failed", System.currentTimeMillis() - startTime);
                return CompletableFuture.completedFuture(false);
            }

            // Test database stats query
            return databaseManager.getDatabaseStats().thenApply(stats -> {
                boolean healthy = stats != null && "Connected".equals(stats.status);
                String message = healthy ? "All systems operational" : "Database stats unavailable";

                long duration = System.currentTimeMillis() - startTime;
                recordHealthCheck(healthy, message, duration);

                return healthy;
            });
        }).exceptionally(throwable -> {
            long duration = System.currentTimeMillis() - startTime;
            recordHealthCheck(false, "Health check exception: " + throwable.getMessage(), duration);
            return false;
        });
    }

    /**
     * Record health check result
     */
    private void recordHealthCheck(boolean healthy, String message, long duration) {
        HealthCheckResult result = new HealthCheckResult(
                System.currentTimeMillis(),
                healthy,
                message,
                duration,
                getCurrentMetrics()
        );

        synchronized (healthHistory) {
            healthHistory.add(result);

            // Keep only the last N results
            while (healthHistory.size() > MAX_HEALTH_HISTORY) {
                healthHistory.remove(0);
            }
        }

        // Log if unhealthy
        if (!healthy) {
            plugin.getLogger().warning("Database health check failed: " + message + " (took " + duration + "ms)");
        } else if (plugin.getConfig().getBoolean("database.monitoring.log-healthy-checks", false)) {
            plugin.getLogger().info("Database health check passed: " + message + " (took " + duration + "ms)");
        }
    }

    /**
     * Generate detailed metrics report
     */
    private void generateMetricsReport() {
        DatabaseMetrics metrics = getCurrentMetrics();

        plugin.getLogger().info("=== Database Performance Report ===");
        plugin.getLogger().info("Database Type: " + databaseManager.getDatabaseType());
        plugin.getLogger().info("Total Queries: " + metrics.totalQueries);
        plugin.getLogger().info("Average Query Time: " + String.format("%.2f ms", metrics.averageQueryTime));
        plugin.getLogger().info("Slow Queries: " + metrics.slowQueries + " (" +
                String.format("%.2f%%", metrics.slowQueryPercentage) + ")");
        plugin.getLogger().info("Failed Queries: " + metrics.failedQueries + " (" +
                String.format("%.2f%%", metrics.failureRate) + ")");
        plugin.getLogger().info("Current Connections: " + metrics.currentConnections);

        // Query type breakdown
        if (!queryStats.isEmpty()) {
            plugin.getLogger().info("--- Query Breakdown ---");
            queryStats.forEach((type, stats) -> {
                plugin.getLogger().info(String.format("%s: %d executions, avg: %.2f ms, max: %d ms",
                        type, stats.totalExecutions, stats.getAverageTime(), stats.maxTime));
            });
        }

        plugin.getLogger().info("================================");
    }

    /**
     * Get current performance metrics
     */
    public DatabaseMetrics getCurrentMetrics() {
        long queries = totalQueries.get();
        long queryTime = totalQueryTime.get();
        long slow = slowQueries.get();
        long failed = failedQueries.get();

        double avgQueryTime = queries > 0 ? (double) queryTime / queries : 0.0;
        double slowPercentage = queries > 0 ? (double) slow / queries * 100 : 0.0;
        double failureRate = queries > 0 ? (double) failed / queries * 100 : 0.0;

        return new DatabaseMetrics(
                queries,
                avgQueryTime,
                slow,
                slowPercentage,
                failed,
                failureRate,
                currentConnections.get(),
                databaseManager.getDatabaseType(),
                System.currentTimeMillis()
        );
    }

    /**
     * Get health check history
     */
    public List<HealthCheckResult> getHealthHistory() {
        synchronized (healthHistory) {
            return new ArrayList<>(healthHistory);
        }
    }

    /**
     * Get query statistics
     */
    public ConcurrentHashMap<String, QueryStats> getQueryStats() {
        return new ConcurrentHashMap<>(queryStats);
    }

    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        totalQueries.set(0);
        totalQueryTime.set(0);
        slowQueries.set(0);
        failedQueries.set(0);
        queryStats.clear();
        lastQueryTimes.clear();

        synchronized (healthHistory) {
            healthHistory.clear();
        }

        plugin.getLogger().info("Database metrics reset");
    }

    /**
     * Check if database is currently healthy
     */
    public boolean isHealthy() {
        synchronized (healthHistory) {
            if (healthHistory.isEmpty()) {
                return true; // No data yet, assume healthy
            }

            // Check last few health checks
            int recentChecks = Math.min(3, healthHistory.size());
            long healthyCount = healthHistory.subList(healthHistory.size() - recentChecks, healthHistory.size())
                    .stream()
                    .mapToLong(result -> result.healthy ? 1 : 0)
                    .sum();

            return healthyCount >= (recentChecks / 2); // At least half should be healthy
        }
    }

    /**
     * Get database uptime percentage (last 24 hours)
     */
    public double getUptimePercentage() {
        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

        synchronized (healthHistory) {
            List<HealthCheckResult> recentResults = healthHistory.stream()
                    .filter(result -> result.timestamp > twentyFourHoursAgo)
                    .toList();

            if (recentResults.isEmpty()) {
                return 100.0; // No data, assume 100%
            }

            long healthyCount = recentResults.stream()
                    .mapToLong(result -> result.healthy ? 1 : 0)
                    .sum();

            return (double) healthyCount / recentResults.size() * 100.0;
        }
    }

    /**
     * Update connection count
     */
    public void setCurrentConnections(int connections) {
        currentConnections.set(connections);
    }

    /**
     * Shutdown monitoring
     */
    public void shutdown() {
        if (healthCheckTask != null) {
            healthCheckTask.cancel();
        }

        if (metricsReportTask != null) {
            metricsReportTask.cancel();
        }

        // Generate final report
        if (plugin.getConfig().getBoolean("database.monitoring.final-report-on-shutdown", true)) {
            generateMetricsReport();
        }

        plugin.getLogger().info("Database monitoring shutdown completed");
    }

    // Data classes
    public static class DatabaseMetrics {
        public final long totalQueries;
        public final double averageQueryTime;
        public final long slowQueries;
        public final double slowQueryPercentage;
        public final long failedQueries;
        public final double failureRate;
        public final int currentConnections;
        public final String databaseType;
        public final long timestamp;

        public DatabaseMetrics(long totalQueries, double averageQueryTime, long slowQueries,
                               double slowQueryPercentage, long failedQueries, double failureRate,
                               int currentConnections, String databaseType, long timestamp) {
            this.totalQueries = totalQueries;
            this.averageQueryTime = averageQueryTime;
            this.slowQueries = slowQueries;
            this.slowQueryPercentage = slowQueryPercentage;
            this.failedQueries = failedQueries;
            this.failureRate = failureRate;
            this.currentConnections = currentConnections;
            this.databaseType = databaseType;
            this.timestamp = timestamp;
        }
    }

    public static class HealthCheckResult {
        public final long timestamp;
        public final boolean healthy;
        public final String message;
        public final long duration;
        public final DatabaseMetrics metrics;

        public HealthCheckResult(long timestamp, boolean healthy, String message, long duration, DatabaseMetrics metrics) {
            this.timestamp = timestamp;
            this.healthy = healthy;
            this.message = message;
            this.duration = duration;
            this.metrics = metrics;
        }
    }

    public static class QueryStats {
        public final String queryType;
        public long totalExecutions;
        public long totalTime;
        public long minTime;
        public long maxTime;

        public QueryStats(String queryType, long totalExecutions, long totalTime, long minTime, long maxTime) {
            this.queryType = queryType;
            this.totalExecutions = totalExecutions;
            this.totalTime = totalTime;
            this.minTime = minTime;
            this.maxTime = maxTime;
        }

        public double getAverageTime() {
            return totalExecutions > 0 ? (double) totalTime / totalExecutions : 0.0;
        }
    }
}