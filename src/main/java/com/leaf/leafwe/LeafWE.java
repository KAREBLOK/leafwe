package com.leaf.leafwe;

import com.leaf.leafwe.commands.CommandManager;
import com.leaf.leafwe.registry.ManagerRegistry;
import com.leaf.leafwe.utils.VersionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LeafWE extends JavaPlugin {

    private ManagerRegistry registry;
    private VersionManager versionManager;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        try {
            // Registry'yi başlat
            this.registry = ManagerRegistry.getInstance();

            // Version manager'ı başlat
            this.versionManager = new VersionManager(this);
            registry.register(VersionManager.class, versionManager);

            // Plugin bilgilerini logla
            versionManager.logPluginInfo();

            // Manager'ları başlat
            initializeManagers();

            // Command sistem'ini başlat
            this.commandManager = new CommandManager(this);

            // Listener'ları kaydet
            registerListeners();

            // WorldGuard hook'unu 1 tick sonra çalıştır
            getServer().getScheduler().runTaskLater(this, () -> {
                ProtectionManager protectionManager = registry.get(ProtectionManager.class);
                if (protectionManager != null) {
                    protectionManager.initializeHooksDelayed();
                }
            }, 1L);

            getLogger().info(versionManager.getEnableMessage());

        } catch (Exception e) {
            getLogger().severe("Failed to enable " + (versionManager != null ? versionManager.getFullInfo() : "LeafWE") + ": " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() {
        // Tüm manager'ları başlat ve registry'ye kaydet
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

        ProtectionManager protectionManager = new ProtectionManager(this);
        registry.register(ProtectionManager.class, protectionManager);

        DailyLimitManager dailyLimitManager = new DailyLimitManager(this, configManager);
        registry.register(DailyLimitManager.class, dailyLimitManager);

        // Database Manager'ı registry'ye ekle (DailyLimitManager içinde oluşturuluyor)
        registry.register(com.leaf.leafwe.database.DatabaseManager.class, dailyLimitManager.getDatabaseManager());
    }

    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(
                    new WandListener(
                            registry.get(SelectionManager.class),
                            registry.get(ConfigManager.class),
                            registry.get(SelectionVisualizer.class),
                            registry.get(BlockstateManager.class),
                            registry.get(ProtectionManager.class)
                    ), this);

            getServer().getPluginManager().registerEvents(
                    new PlayerListener(
                            registry.get(SelectionManager.class),
                            registry.get(UndoManager.class),
                            registry.get(PendingCommandManager.class),
                            registry.get(SelectionVisualizer.class),
                            registry.get(TaskManager.class),
                            registry.get(BlockstateManager.class)
                    ), this);

            getServer().getPluginManager().registerEvents(
                    new GuiListener(
                            registry.get(ConfigManager.class),
                            registry.get(GuiManager.class)
                    ), this);

        } catch (Exception e) {
            getLogger().severe("Failed to register listeners: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        try {
            // Cleanup tasks
            TaskManager taskManager = registry != null ? registry.get(TaskManager.class) : null;
            if (taskManager != null) {
                taskManager.cancelAllTasks();
            }

            SelectionVisualizer selectionVisualizer = registry != null ? registry.get(SelectionVisualizer.class) : null;
            if (selectionVisualizer != null) {
                selectionVisualizer.shutdown();
            }

            BlockstateManager blockstateManager = registry != null ? registry.get(BlockstateManager.class) : null;
            if (blockstateManager != null) {
                blockstateManager.cleanupAll();
            }

            DailyLimitManager dailyLimitManager = registry != null ? registry.get(DailyLimitManager.class) : null;
            if (dailyLimitManager != null) {
                dailyLimitManager.shutdown();
            }

            // Registry'yi temizle
            ManagerRegistry.reset();

            getLogger().info(versionManager != null ? versionManager.getDisableMessage() : "LeafWE disabled.");

        } catch (Exception e) {
            getLogger().severe("Error during plugin disable: " + e.getMessage());
        }
    }

    // Backward compatibility için getter methods (deprecated)
    @Deprecated
    public ConfigManager getConfigManager() {
        return registry != null ? registry.get(ConfigManager.class) : null;
    }

    @Deprecated
    public SelectionManager getSelectionManager() {
        return registry != null ? registry.get(SelectionManager.class) : null;
    }

    @Deprecated
    public UndoManager getUndoManager() {
        return registry != null ? registry.get(UndoManager.class) : null;
    }

    @Deprecated
    public PendingCommandManager getPendingCommandManager() {
        return registry != null ? registry.get(PendingCommandManager.class) : null;
    }

    @Deprecated
    public SelectionVisualizer getSelectionVisualizer() {
        return registry != null ? registry.get(SelectionVisualizer.class) : null;
    }

    @Deprecated
    public TaskManager getTaskManager() {
        return registry != null ? registry.get(TaskManager.class) : null;
    }

    @Deprecated
    public BlockstateManager getBlockstateManager() {
        return registry != null ? registry.get(BlockstateManager.class) : null;
    }

    @Deprecated
    public GuiManager getGuiManager() {
        return registry != null ? registry.get(GuiManager.class) : null;
    }

    @Deprecated
    public ProtectionManager getProtectionManager() {
        return registry != null ? registry.get(ProtectionManager.class) : null;
    }

    @Deprecated
    public DailyLimitManager getDailyLimitManager() {
        return registry != null ? registry.get(DailyLimitManager.class) : null;
    }

    // Yeni accessor methods
    public ManagerRegistry getRegistry() {
        return registry;
    }

    public VersionManager getVersionManager() {
        return versionManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }
}