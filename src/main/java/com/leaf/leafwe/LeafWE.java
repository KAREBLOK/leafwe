package com.leaf.leafwe;

import org.bukkit.plugin.java.JavaPlugin;

public final class LeafWE extends JavaPlugin {

    private ConfigManager configManager;
    private SelectionManager selectionManager;
    private UndoManager undoManager;
    private PendingCommandManager pendingCommandManager;
    private SelectionVisualizer selectionVisualizer;
    private TaskManager taskManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.selectionManager = new SelectionManager();
        this.undoManager = new UndoManager(this, configManager);
        this.pendingCommandManager = new PendingCommandManager(this);
        this.selectionVisualizer = new SelectionVisualizer(this, selectionManager, configManager);
        this.taskManager = new TaskManager();

        registerCommands();
        registerListeners();

        getLogger().info("LeafWE v2.4 başarıyla etkinleştirildi!");
    }

    private void registerCommands() {
        this.getCommand("set").setExecutor(new SetCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager));
        this.getCommand("wall").setExecutor(new WallCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager));
        this.getCommand("replace").setExecutor(new ReplaceCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager));

        this.getCommand("lwe").setExecutor(new LWECommand(this, configManager, undoManager, pendingCommandManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WandListener(selectionManager, configManager, selectionVisualizer), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(selectionManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager), this);
    }

    @Override
    public void onDisable() {
        if (selectionVisualizer != null) {
            selectionVisualizer.shutdown();
        }
        getLogger().info("LeafWE devre dışı bırakıldı.");
    }
}