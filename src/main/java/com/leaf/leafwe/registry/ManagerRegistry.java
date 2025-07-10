package com.leaf.leafwe.registry;

import com.leaf.leafwe.*;
import com.leaf.leafwe.utils.VersionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Tüm manager'ları merkezi olarak tutan singleton registry
 */
public class ManagerRegistry {

    private static ManagerRegistry instance;
    private final Map<Class<?>, Object> managers;

    private ManagerRegistry() {
        this.managers = new HashMap<>();
    }

    public static ManagerRegistry getInstance() {
        if (instance == null) {
            instance = new ManagerRegistry();
        }
        return instance;
    }

    /**
     * Manager kaydet
     */
    public <T> void register(Class<T> clazz, T manager) {
        managers.put(clazz, manager);
    }

    /**
     * Manager al
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        return (T) managers.get(clazz);
    }

    /**
     * Manager var mı kontrol et
     */
    public boolean has(Class<?> clazz) {
        return managers.containsKey(clazz);
    }

    /**
     * Manager kaldır
     */
    public void unregister(Class<?> clazz) {
        managers.remove(clazz);
    }

    /**
     * Tüm manager'ları temizle
     */
    public void clear() {
        managers.clear();
    }

    /**
     * Registry'yi yeniden başlat
     */
    public static void reset() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    // Convenience methods
    public static ConfigManager config() {
        return getInstance().get(ConfigManager.class);
    }

    public static SelectionManager selection() {
        return getInstance().get(SelectionManager.class);
    }

    public static UndoManager undo() {
        return getInstance().get(UndoManager.class);
    }

    public static TaskManager task() {
        return getInstance().get(TaskManager.class);
    }

    public static GuiManager gui() {
        return getInstance().get(GuiManager.class);
    }

    public static ProtectionManager protection() {
        return getInstance().get(ProtectionManager.class);
    }

    public static DailyLimitManager dailyLimit() {
        return getInstance().get(DailyLimitManager.class);
    }

    public static SelectionVisualizer visualizer() {
        return getInstance().get(SelectionVisualizer.class);
    }

    public static BlockstateManager blockstate() {
        return getInstance().get(BlockstateManager.class);
    }

    public static PendingCommandManager pending() {
        return getInstance().get(PendingCommandManager.class);
    }

    public static VersionManager version() {
        return getInstance().get(VersionManager.class);
    }

    public static com.leaf.leafwe.database.DatabaseManager database() {
        return getInstance().get(com.leaf.leafwe.database.DatabaseManager.class);
    }

    public static com.leaf.leafwe.database.DatabaseMonitor databaseMonitor() {
        return getInstance().get(com.leaf.leafwe.database.DatabaseMonitor.class);
    }

    /**
     * Debug bilgisi
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered Managers (").append(managers.size()).append("):\n");

        for (Class<?> clazz : managers.keySet()) {
            sb.append("  - ").append(clazz.getSimpleName()).append("\n");
        }

        return sb.toString();
    }
}