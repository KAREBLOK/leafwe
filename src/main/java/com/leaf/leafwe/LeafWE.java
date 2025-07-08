package com.leaf.leafwe;

import org.bukkit.plugin.java.JavaPlugin;

public final class LeafWE extends JavaPlugin {

    private ConfigManager configManager;
    private SelectionManager selectionManager;
    private UndoManager undoManager;
    private PendingCommandManager pendingCommandManager;
    private SelectionVisualizer selectionVisualizer;
    private TaskManager taskManager;
    private BlockstateManager blockstateManager;
    private GuiManager guiManager;
    private ProtectionManager protectionManager;
    private DailyLimitManager dailyLimitManager;

    @Override
    public void onEnable() {
        try {
            this.configManager = new ConfigManager(this);
            this.selectionManager = new SelectionManager();
            this.undoManager = new UndoManager(this, configManager);
            this.pendingCommandManager = new PendingCommandManager(this);
            this.selectionVisualizer = new SelectionVisualizer(this, selectionManager, configManager);
            this.taskManager = new TaskManager();
            this.blockstateManager = new BlockstateManager();
            this.guiManager = new GuiManager(this, configManager);
            this.protectionManager = new ProtectionManager(this);
            this.dailyLimitManager = new DailyLimitManager(this, configManager);

            registerCommands();
            registerListeners();

            getServer().getScheduler().runTaskLater(this, () -> {
                if (protectionManager != null) {
                    protectionManager.initializeHooksDelayed();
                }
            }, 1L);

            getLogger().info("LeafWE v4.0.3 successfully enabled!");

        } catch (Exception e) {
            getLogger().severe("Failed to enable LeafWE: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommands() {
        try {
            if (this.getCommand("set") != null) {
                this.getCommand("set").setExecutor(new SetCommand(this, selectionManager, configManager, undoManager,
                        pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
            }
            if (this.getCommand("wall") != null) {
                this.getCommand("wall").setExecutor(new WallCommand(this, selectionManager, configManager, undoManager,
                        pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
            }
            if (this.getCommand("replace") != null) {
                this.getCommand("replace").setExecutor(new ReplaceCommand(this, selectionManager, configManager, undoManager,
                        pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
            }
            if (this.getCommand("lwe") != null) {
                this.getCommand("lwe").setExecutor(new LWECommand(this, configManager, undoManager,
                        pendingCommandManager, blockstateManager));
            }

        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
        }
    }

    private void registerListeners() {
        try {
            getServer().getPluginManager().registerEvents(new WandListener(selectionManager, configManager,
                    selectionVisualizer, blockstateManager, protectionManager), this);
            getServer().getPluginManager().registerEvents(new PlayerListener(selectionManager, undoManager,
                    pendingCommandManager, selectionVisualizer, taskManager, blockstateManager), this);
            getServer().getPluginManager().registerEvents(new GuiListener(configManager, guiManager), this);

        } catch (Exception e) {
            getLogger().severe("Failed to register listeners: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        try {
            if (taskManager != null) {
                taskManager.cancelAllTasks();
            }

            if (selectionVisualizer != null) {
                selectionVisualizer.shutdown();
            }

            if (blockstateManager != null) {
                blockstateManager.cleanupAll();
            }

            if (dailyLimitManager != null) {
                dailyLimitManager.shutdown();
            }

            getLogger().info("LeafWE disabled successfully.");

        } catch (Exception e) {
            getLogger().severe("Error during plugin disable: " + e.getMessage());
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public PendingCommandManager getPendingCommandManager() {
        return pendingCommandManager;
    }

    public SelectionVisualizer getSelectionVisualizer() {
        return selectionVisualizer;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public BlockstateManager getBlockstateManager() {
        return blockstateManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public ProtectionManager getProtectionManager() {
        return protectionManager;
    }

    public DailyLimitManager getDailyLimitManager() {
        return dailyLimitManager;
    }
}