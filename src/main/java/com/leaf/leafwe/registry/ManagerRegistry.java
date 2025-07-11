package com.leaf.leafwe.registry;

import com.leaf.leafwe.*;
import com.leaf.leafwe.database.AsyncDatabaseManager;
import com.leaf.leafwe.database.DatabaseManager;
import com.leaf.leafwe.database.migration.MigrationManager;
import com.leaf.leafwe.utils.VersionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ManagerRegistry {

    private static ManagerRegistry instance;
    private final Map<Class<?>, Object> managers;
    private final Map<Class<?>, Supplier<?>> lazySuppliers;

    private ManagerRegistry() {
        this.managers = new HashMap<>();
        this.lazySuppliers = new HashMap<>();
    }

    public static ManagerRegistry getInstance() {
        if (instance == null) {
            instance = new ManagerRegistry();
        }
        return instance;
    }

    public <T> void register(Class<T> clazz, T manager) {
        managers.put(clazz, manager);
        lazySuppliers.remove(clazz);
    }

    public <T> void registerLazy(Class<T> clazz, Supplier<T> supplier) {
        lazySuppliers.put(clazz, supplier);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        Object manager = managers.get(clazz);
        if (manager != null) {
            return (T) manager;
        }

        Supplier<?> supplier = lazySuppliers.get(clazz);
        if (supplier != null) {
            manager = supplier.get();
            if (manager != null) {
                managers.put(clazz, manager);
                lazySuppliers.remove(clazz);
            }
            return (T) manager;
        }

        return null;
    }

    public boolean has(Class<?> clazz) {
        return managers.containsKey(clazz) || lazySuppliers.containsKey(clazz);
    }

    public void unregister(Class<?> clazz) {
        managers.remove(clazz);
        lazySuppliers.remove(clazz);
    }

    public void clear() {
        managers.clear();
        lazySuppliers.clear();
    }

    public void shutdown() {
        for (Object manager : managers.values()) {
            if (manager instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) manager).close();
                } catch (Exception e) {
                    // Log but continue shutdown
                    System.err.println("Error shutting down manager: " + manager.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
        }
        clear();
    }

    public static void reset() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public static ConfigManager config() {
        ConfigManager manager = getInstance().get(ConfigManager.class);
        if (manager == null) {
            throw new IllegalStateException("ConfigManager not registered!");
        }
        return manager;
    }

    public static SelectionManager selection() {
        SelectionManager manager = getInstance().get(SelectionManager.class);
        if (manager == null) {
            throw new IllegalStateException("SelectionManager not registered!");
        }
        return manager;
    }

    public static UndoManager undo() {
        UndoManager manager = getInstance().get(UndoManager.class);
        if (manager == null) {
            throw new IllegalStateException("UndoManager not registered!");
        }
        return manager;
    }

    public static TaskManager task() {
        TaskManager manager = getInstance().get(TaskManager.class);
        if (manager == null) {
            throw new IllegalStateException("TaskManager not registered!");
        }
        return manager;
    }

    public static GuiManager gui() {
        GuiManager manager = getInstance().get(GuiManager.class);
        if (manager == null) {
            throw new IllegalStateException("GuiManager not registered!");
        }
        return manager;
    }

    public static ProtectionManager protection() {
        return getInstance().get(ProtectionManager.class); // Nullable - optional dependency
    }

    public static DailyLimitManager dailyLimit() {
        return getInstance().get(DailyLimitManager.class); // Nullable - optional feature
    }

    public static SelectionVisualizer visualizer() {
        SelectionVisualizer manager = getInstance().get(SelectionVisualizer.class);
        if (manager == null) {
            throw new IllegalStateException("SelectionVisualizer not registered!");
        }
        return manager;
    }

    public static BlockstateManager blockstate() {
        BlockstateManager manager = getInstance().get(BlockstateManager.class);
        if (manager == null) {
            throw new IllegalStateException("BlockstateManager not registered!");
        }
        return manager;
    }

    public static PendingCommandManager pending() {
        PendingCommandManager manager = getInstance().get(PendingCommandManager.class);
        if (manager == null) {
            throw new IllegalStateException("PendingCommandManager not registered!");
        }
        return manager;
    }

    public static VersionManager version() {
        VersionManager manager = getInstance().get(VersionManager.class);
        if (manager == null) {
            throw new IllegalStateException("VersionManager not registered!");
        }
        return manager;
    }

    public static DatabaseManager database() {
        return getInstance().get(DatabaseManager.class); // Nullable - optional feature
    }

    public static AsyncDatabaseManager asyncDatabase() {
        return getInstance().get(AsyncDatabaseManager.class); // Nullable - optional feature
    }

    public static MigrationManager migration() {
        return getInstance().get(MigrationManager.class); // Nullable - optional feature
    }

    public boolean isHealthy() {
        Class<?>[] criticalManagers = {
                ConfigManager.class,
                SelectionManager.class,
                UndoManager.class,
                TaskManager.class,
                GuiManager.class,
                SelectionVisualizer.class,
                BlockstateManager.class,
                PendingCommandManager.class,
                VersionManager.class
        };

        for (Class<?> clazz : criticalManagers) {
            if (!has(clazz)) {
                return false;
            }
        }

        return true;
    }

    public boolean isDatabaseHealthy() {
        DatabaseManager dbManager = get(DatabaseManager.class);
        if (dbManager == null) {
            return false;
        }

        try {
            return dbManager.testConnection().join();
        } catch (Exception e) {
            return false;
        }
    }

    public String getSystemStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LeafWE System Status ===\n");
        sb.append("Core Health: ").append(isHealthy() ? "✅ HEALTHY" : "❌ UNHEALTHY").append("\n");
        sb.append("Database Health: ").append(isDatabaseHealthy() ? "✅ CONNECTED" : "❌ DISCONNECTED").append("\n");
        sb.append("Managers: ").append(managers.size()).append(" loaded\n");

        return sb.toString();
    }

    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered Managers (").append(managers.size()).append("):\n");

        for (Class<?> clazz : managers.keySet()) {
            sb.append("  - ").append(clazz.getSimpleName()).append("\n");
        }

        if (!lazySuppliers.isEmpty()) {
            sb.append("Lazy Suppliers (").append(lazySuppliers.size()).append("):\n");
            for (Class<?> clazz : lazySuppliers.keySet()) {
                sb.append("  - ").append(clazz.getSimpleName()).append(" [LAZY]\n");
            }
        }

        return sb.toString();
    }
}