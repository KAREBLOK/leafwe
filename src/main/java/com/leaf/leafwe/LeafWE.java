package com.leaf.leafwe;

import com.leaf.leafwe.commands.CommandManager;
import com.leaf.leafwe.database.AsyncDatabaseManager;
import com.leaf.leafwe.database.DatabaseFactory;
import com.leaf.leafwe.database.DatabaseManager;
import com.leaf.leafwe.database.migration.MigrationManager;
import com.leaf.leafwe.registry.ManagerRegistry;
import com.leaf.leafwe.utils.VersionManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class LeafWE extends JavaPlugin {

    private ManagerRegistry registry;
    private VersionManager versionManager;
    private CommandManager commandManager;
    private boolean databaseEnabled = false;

    @Override
    public void onEnable() {
        try {
            this.registry = ManagerRegistry.getInstance();

            this.versionManager = new VersionManager(this);
            registry.register(VersionManager.class, versionManager);

            versionManager.logPluginInfo();

            if (!versionManager.isServerSupported()) {
                getLogger().warning("⚠️ Running on unsupported server version! Some features may not work correctly.");
                getLogger().warning("Supported versions: 1.19.x, 1.20.x, 1.21.x");
            }

            initializeCriticalManagers();

            initializeDatabaseSystemAsync();

            initializeOptionalManagers();

            this.commandManager = new CommandManager(this);

            registerListeners();

            getServer().getScheduler().runTaskLater(this, this::initializeDelayedHooks, 1L);

            performHealthCheck();

            getLogger().info(versionManager.getEnableMessage());

        } catch (Exception e) {
            getLogger().severe("Failed to enable " + (versionManager != null ? versionManager.getFullInfo() : "LeafWE") + ": " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeCriticalManagers() {
        getLogger().info("Initializing critical managers...");

        ConfigManager configManager = new ConfigManager(this);
        registry.register(ConfigManager.class, configManager);

        SelectionManager selectionManager = new SelectionManager();
        registry.register(SelectionManager.class, selectionManager);

        UndoManager undoManager = new UndoManager(this, configManager);
        registry.register(UndoManager.class, undoManager);

        PendingCommandManager pendingCommandManager = new PendingCommandManager(this);
        registry.register(PendingCommandManager.class, pendingCommandManager);

        SelectionVisualizer selectionVisualizer = new SelectionVisualizer(this, selectionManager, configManager);
        registry.register(SelectionVisualizer.class, selectionVisualizer);

        TaskManager taskManager = new TaskManager();
        registry.register(TaskManager.class, taskManager);

        BlockstateManager blockstateManager = new BlockstateManager();
        registry.register(BlockstateManager.class, blockstateManager);

        GuiManager guiManager = new GuiManager(this, configManager);
        registry.register(GuiManager.class, guiManager);

        getLogger().info("✅ Critical managers initialized successfully");
    }

    private void initializeDatabaseSystemAsync() {
        getLogger().info("Initializing database system...");

        CompletableFuture.runAsync(() -> {
            try {
                DatabaseManager databaseManager = DatabaseFactory.createFromConfig(this);
                registry.register(DatabaseManager.class, databaseManager);

                databaseManager.initialize().thenCompose(success -> {
                    if (success) {
                        getLogger().info("✅ Database initialized successfully");
                        databaseEnabled = true;

                        return initializeMigrationSystem(databaseManager);
                    } else {
                        getLogger().severe("❌ Failed to initialize database! Some features will be disabled.");
                        return CompletableFuture.completedFuture(false);
                    }
                }).thenCompose(migrationSuccess -> {
                    if (migrationSuccess && databaseEnabled) {
                        AsyncDatabaseManager asyncDbManager = new AsyncDatabaseManager(this, databaseManager);
                        registry.register(AsyncDatabaseManager.class, asyncDbManager);

                        DailyLimitManager dailyLimitManager = new DailyLimitManager(this, ManagerRegistry.config());
                        registry.register(DailyLimitManager.class, dailyLimitManager);

                        getLogger().info("✅ Database system fully initialized");
                        return CompletableFuture.completedFuture(true);
                    } else {
                        getLogger().warning("⚠️ Database system partially initialized");
                        return CompletableFuture.completedFuture(false);
                    }
                }).exceptionally(throwable -> {
                    getLogger().severe("❌ Database system initialization error: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return false;
                });

            } catch (Exception e) {
                getLogger().severe("❌ Error setting up database system: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private CompletableFuture<Boolean> initializeMigrationSystem(DatabaseManager databaseManager) {
        try {
            MigrationManager migrationManager = new MigrationManager(this, databaseManager);
            registry.register(MigrationManager.class, migrationManager);

            return migrationManager.initialize().thenCompose(success -> {
                if (success) {
                    getLogger().info("✅ Migration system initialized");

                    if (getConfig().getBoolean("database.auto-migrate", true)) {
                        getLogger().info("Running automatic migrations...");
                        return migrationManager.migrate().thenApply(result -> {
                            if (result.success) {
                                getLogger().info("✅ Migrations completed successfully");
                                return true;
                            } else {
                                getLogger().warning("⚠️ Some migrations failed - check logs");
                                return false;
                            }
                        });
                    } else {
                        getLogger().info("Auto-migration disabled");
                        return CompletableFuture.completedFuture(true);
                    }
                } else {
                    getLogger().severe("❌ Failed to initialize migration system");
                    return CompletableFuture.completedFuture(false);
                }
            });

        } catch (Exception e) {
            getLogger().severe("❌ Error initializing migration system: " + e.getMessage());
            e.printStackTrace();
            return CompletableFuture.completedFuture(false);
        }
    }

    private void initializeOptionalManagers() {
        getLogger().info("Initializing optional managers...");

        registry.registerLazy(ProtectionManager.class, () -> new ProtectionManager(this));

        getLogger().info("✅ Optional managers registered");
    }

    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(
                    new WandListener(
                            ManagerRegistry.selection(),
                            ManagerRegistry.config(),
                            ManagerRegistry.visualizer(),
                            ManagerRegistry.blockstate(),
                            ManagerRegistry.protection()
                    ), this);

            getServer().getPluginManager().registerEvents(
                    new PlayerListener(
                            ManagerRegistry.selection(),
                            ManagerRegistry.undo(),
                            ManagerRegistry.pending(),
                            ManagerRegistry.visualizer(),
                            ManagerRegistry.task(),
                            ManagerRegistry.blockstate()
                    ), this);

            getServer().getPluginManager().registerEvents(
                    new GuiListener(
                            ManagerRegistry.config(),
                            ManagerRegistry.gui()
                    ), this);

            getLogger().info("✅ Event listeners registered successfully");

        } catch (Exception e) {
            getLogger().severe("❌ Failed to register listeners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDelayedHooks() {
        try {
            ProtectionManager protectionManager = ManagerRegistry.protection();
            if (protectionManager != null) {
                protectionManager.initializeHooksDelayed();
            }
        } catch (Exception e) {
            getLogger().warning("Error initializing delayed hooks: " + e.getMessage());
        }
    }

    private void performHealthCheck() {
        if (!registry.isHealthy()) {
            getLogger().warning("⚠️ Some critical managers are missing! Plugin may not work correctly.");
            getLogger().info(registry.getDebugInfo());
        } else {
            getLogger().info("✅ All critical managers initialized successfully");
        }

        getLogger().info("\n" + registry.getSystemStatus());

        getServer().getScheduler().runTaskTimerAsynchronously(this, this::performPeriodicHealthCheck, 20L * 60L * 5L, 20L * 60L * 5L);
    }

    private void performPeriodicHealthCheck() {
        try {
            boolean coreHealthy = registry.isHealthy();
            boolean dbHealthy = registry.isDatabaseHealthy();

            if (!coreHealthy) {
                getLogger().warning("⚠️ Core system health check failed!");
            }

            if (databaseEnabled && !dbHealthy) {
                getLogger().warning("⚠️ Database connection lost! Attempting to reconnect...");

                DatabaseManager dbManager = ManagerRegistry.database();
                if (dbManager != null) {
                    dbManager.testConnection().thenAccept(connected -> {
                        if (connected) {
                            getLogger().info("✅ Database reconnected successfully");
                        } else {
                            getLogger().severe("❌ Database reconnection failed");
                        }
                    });
                }
            }

            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            double memoryPercent = (double) usedMemory / maxMemory * 100;

            if (memoryPercent > 80) {
                getLogger().warning("⚠️ High memory usage detected: " + String.format("%.1f%%", memoryPercent));
            }

        } catch (Exception e) {
            getLogger().warning("Error during periodic health check: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Shutting down LeafWE...");

            TaskManager taskManager = ManagerRegistry.task();
            if (taskManager != null) {
                taskManager.cancelAllTasks();
                getLogger().info("✅ All tasks cancelled");
            }

            SelectionVisualizer selectionVisualizer = ManagerRegistry.visualizer();
            if (selectionVisualizer != null) {
                selectionVisualizer.shutdown();
                getLogger().info("✅ Selection visualizer stopped");
            }

            BlockstateManager blockstateManager = ManagerRegistry.blockstate();
            if (blockstateManager != null) {
                blockstateManager.cleanupAll();
                getLogger().info("✅ Blockstate manager cleaned up");
            }

            AsyncDatabaseManager asyncDbManager = ManagerRegistry.asyncDatabase();
            if (asyncDbManager != null) {
                asyncDbManager.shutdown().join();
                getLogger().info("✅ Async database manager shutdown");
            }

            DailyLimitManager dailyLimitManager = ManagerRegistry.dailyLimit();
            if (dailyLimitManager != null) {
                dailyLimitManager.shutdown();
                getLogger().info("✅ Daily limit manager shutdown");
            }

            DatabaseManager databaseManager = ManagerRegistry.database();
            if (databaseManager != null) {
                databaseManager.shutdown().join();
                getLogger().info("✅ Database shutdown");
            }

            registry.shutdown();
            ManagerRegistry.reset();

            getLogger().info(versionManager != null ? versionManager.getDisableMessage() : "LeafWE disabled successfully.");

        } catch (Exception e) {
            getLogger().severe("❌ Error during plugin disable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Deprecated
    public ConfigManager getConfigManager() {
        return ManagerRegistry.config();
    }

    @Deprecated
    public SelectionManager getSelectionManager() {
        return ManagerRegistry.selection();
    }

    @Deprecated
    public UndoManager getUndoManager() {
        return ManagerRegistry.undo();
    }

    @Deprecated
    public PendingCommandManager getPendingCommandManager() {
        return ManagerRegistry.pending();
    }

    @Deprecated
    public SelectionVisualizer getSelectionVisualizer() {
        return ManagerRegistry.visualizer();
    }

    @Deprecated
    public TaskManager getTaskManager() {
        return ManagerRegistry.task();
    }

    @Deprecated
    public BlockstateManager getBlockstateManager() {
        return ManagerRegistry.blockstate();
    }

    @Deprecated
    public GuiManager getGuiManager() {
        return ManagerRegistry.gui();
    }

    @Deprecated
    public ProtectionManager getProtectionManager() {
        return ManagerRegistry.protection();
    }

    @Deprecated
    public DailyLimitManager getDailyLimitManager() {
        return ManagerRegistry.dailyLimit();
    }

    public ManagerRegistry getRegistry() {
        return registry;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public String getSystemStatus() {
        return registry.getSystemStatus();
    }

    public void forceHealthCheck() {
        performHealthCheck();
    }
}