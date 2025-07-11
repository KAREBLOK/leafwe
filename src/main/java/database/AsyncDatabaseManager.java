package com.leaf.leafwe.database;

import com.leaf.leafwe.LeafWE;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncDatabaseManager {

    private final LeafWE plugin;
    private final DatabaseManager databaseManager;
    private final DatabaseMonitor monitor;

    private final ExecutorService readExecutor;
    private final ExecutorService writeExecutor;
    private final ExecutorService maintenanceExecutor;

    private final Semaphore connectionSemaphore;
    private final Map<String, CompletableFuture<?>> activeOperations;
    private final AtomicLong operationCounter;

    private volatile boolean circuitOpen = false;
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final long CIRCUIT_BREAKER_THRESHOLD;
    private final long CIRCUIT_BREAKER_TIMEOUT;

    public AsyncDatabaseManager(LeafWE plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.monitor = new DatabaseMonitor(plugin, databaseManager);

        int maxConnections = plugin.getConfig().getInt("database.async.max-connections", 20);
        int readThreads = plugin.getConfig().getInt("database.async.read-threads", 4);
        int writeThreads = plugin.getConfig().getInt("database.async.write-threads", 2);
        this.CIRCUIT_BREAKER_THRESHOLD = plugin.getConfig().getLong("database.async.circuit-breaker-threshold", 10);
        this.CIRCUIT_BREAKER_TIMEOUT = plugin.getConfig().getLong("database.async.circuit-breaker-timeout", 30000);

        this.readExecutor = createThreadPool("LeafWE-DB-Read", readThreads);
        this.writeExecutor = createThreadPool("LeafWE-DB-Write", writeThreads);
        this.maintenanceExecutor = Executors.newScheduledThreadPool(1,
                r -> new Thread(r, "LeafWE-DB-Maintenance"));

        this.connectionSemaphore = new Semaphore(maxConnections);
        this.activeOperations = new ConcurrentHashMap<>();
        this.operationCounter = new AtomicLong(0);

        startMaintenanceTasks();
        plugin.getLogger().info("Async Database Manager initialized - " +
                "Read threads: " + readThreads + ", Write threads: " + writeThreads +
                ", Max connections: " + maxConnections);
    }

    public <T> CompletableFuture<T> executeRead(String operationType, Supplier<T> operation) {
        if (circuitOpen) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Database circuit breaker is open"));
        }

        String operationId = generateOperationId(operationType);

        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            boolean acquired = false;

            try {
                acquired = connectionSemaphore.tryAcquire(
                        plugin.getConfig().getLong("database.async.connection-timeout", 10000),
                        TimeUnit.MILLISECONDS
                );

                if (!acquired) {
                    throw new RuntimeException("Connection pool exhausted for read operation: " + operationType);
                }

                T result = operation.get();

                long duration = (System.nanoTime() - startTime) / 1_000_000;
                monitor.recordQuery(operationType, duration, true);
                resetCircuitBreaker();

                return result;

            } catch (Exception e) {
                long duration = (System.nanoTime() - startTime) / 1_000_000;
                monitor.recordQuery(operationType, duration, false);
                handleOperationFailure(e);
                throw new RuntimeException("Read operation failed: " + operationType, e);

            } finally {
                if (acquired) {
                    connectionSemaphore.release();
                }
                activeOperations.remove(operationId);
            }
        }, readExecutor);

        activeOperations.put(operationId, future);
        return future;
    }

    public <T> CompletableFuture<T> executeWrite(String operationType, Supplier<T> operation) {
        if (circuitOpen) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("Database circuit breaker is open"));
        }

        String operationId = generateOperationId(operationType);

        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            boolean acquired = false;

            try {
                acquired = connectionSemaphore.tryAcquire(
                        plugin.getConfig().getLong("database.async.connection-timeout", 10000),
                        TimeUnit.MILLISECONDS
                );

                if (!acquired) {
                    throw new RuntimeException("Connection pool exhausted for write operation: " + operationType);
                }

                T result = operation.get();

                long duration = (System.nanoTime() - startTime) / 1_000_000;
                monitor.recordQuery(operationType, duration, true);
                resetCircuitBreaker();

                return result;

            } catch (Exception e) {
                long duration = (System.nanoTime() - startTime) / 1_000_000;
                monitor.recordQuery(operationType, duration, false);
                handleOperationFailure(e);
                throw new RuntimeException("Write operation failed: " + operationType, e);

            } finally {
                if (acquired) {
                    connectionSemaphore.release();
                }
                activeOperations.remove(operationId);
            }
        }, writeExecutor);

        activeOperations.put(operationId, future);
        return future;
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            plugin.getLogger().info("Shutting down Async Database Manager...");

            circuitOpen = true;

            waitForActiveOperations();

            shutdownExecutor(readExecutor, "Read");
            shutdownExecutor(writeExecutor, "Write");
            shutdownExecutor(maintenanceExecutor, "Maintenance");

            monitor.shutdown();

            plugin.getLogger().info("Async Database Manager shutdown completed");
        });
    }

    private ExecutorService createThreadPool(String name, int threads) {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, name + "-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        };

        return new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(plugin.getConfig().getInt("database.async.queue-size", 1000)),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private void startMaintenanceTasks() {
        ((ScheduledExecutorService) maintenanceExecutor).scheduleAtFixedRate(
                this::monitorConnectionPool,
                30000,
                30000,
                TimeUnit.MILLISECONDS
        );

        ((ScheduledExecutorService) maintenanceExecutor).scheduleAtFixedRate(
                this::checkCircuitBreaker,
                10000,
                10000,
                TimeUnit.MILLISECONDS
        );
    }

    private String generateOperationId(String operationType) {
        return operationType + "-" + operationCounter.incrementAndGet();
    }

    private void handleOperationFailure(Exception e) {
        long failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpen = true;
            plugin.getLogger().warning("Database circuit breaker opened after " + failures + " failures");
        }
    }

    private void resetCircuitBreaker() {
        if (failureCount.get() > 0) {
            failureCount.set(0);
        }
    }

    private void checkCircuitBreaker() {
        if (circuitOpen &&
                (System.currentTimeMillis() - lastFailureTime.get()) > CIRCUIT_BREAKER_TIMEOUT) {
            circuitOpen = false;
            failureCount.set(0);
            plugin.getLogger().info("Database circuit breaker reset - operations resumed");
        }
    }

    private void waitForActiveOperations() {
        int timeout = plugin.getConfig().getInt("database.async.shutdown-timeout", 30);
        long deadline = System.currentTimeMillis() + (timeout * 1000);

        while (!activeOperations.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (!activeOperations.isEmpty()) {
            plugin.getLogger().warning("Forcing shutdown with " + activeOperations.size() + " active operations");
            activeOperations.values().forEach(future -> future.cancel(true));
        }
    }

    private void monitorConnectionPool() {
        int available = connectionSemaphore.availablePermits();
        int total = connectionSemaphore.availablePermits() + activeOperations.size();

        if (available < total * 0.2) {
            plugin.getLogger().warning("Database connection pool running low: " +
                    available + "/" + total + " available");
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().warning(name + " executor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveOperationCount() {
        return activeOperations.size();
    }

    public int getAvailableConnections() {
        return connectionSemaphore.availablePermits();
    }

    public boolean isCircuitOpen() {
        return circuitOpen;
    }

    public DatabaseMonitor getMonitor() {
        return monitor;
    }
}