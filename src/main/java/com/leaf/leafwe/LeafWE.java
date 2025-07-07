package com.leaf.leafwe;

import org.bukkit.plugin.java.JavaPlugin;

public final class LeafWE extends JavaPlugin {

    // Tüm yönetici sınıflarımız için alanlar
    private ConfigManager configManager;
    private SelectionManager selectionManager;
    private UndoManager undoManager;
    private PendingCommandManager pendingCommandManager;
    private SelectionVisualizer selectionVisualizer;
    private TaskManager taskManager;
    private BlockstateManager blockstateManager;
    private GuiManager guiManager;
    private ProtectionManager protectionManager; // WorldGuard yöneticimiz

    @Override
    public void onEnable() {
        // Plugin başladığında tüm yönetici sınıflarını başlat
        this.configManager = new ConfigManager(this);
        this.selectionManager = new SelectionManager();
        this.undoManager = new UndoManager(this, configManager);
        this.pendingCommandManager = new PendingCommandManager(this);
        this.selectionVisualizer = new SelectionVisualizer(this, selectionManager, configManager);
        this.taskManager = new TaskManager();
        this.blockstateManager = new BlockstateManager();
        this.guiManager = new GuiManager(this, configManager);
        this.protectionManager = new ProtectionManager(this); // Yeni yöneticiyi başlat

        // Komutları ve dinleyicileri kaydet
        registerCommands();
        registerListeners();

        getLogger().info("LeafWE v3.5 successfully enabled!");
    }

    private void registerCommands() {
        // Komut sınıflarına artık ProtectionManager da gönderiliyor
        this.getCommand("set").setExecutor(new SetCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
        this.getCommand("wall").setExecutor(new WallCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
        this.getCommand("replace").setExecutor(new ReplaceCommand(this, selectionManager, configManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager, blockstateManager, guiManager));
        this.getCommand("lwe").setExecutor(new LWECommand(this, configManager, undoManager, pendingCommandManager, blockstateManager));
    }

    private void registerListeners() {
        // Listener'ların constructor'ları bu değişiklikten etkilenmiyor
        getServer().getPluginManager().registerEvents(new WandListener(selectionManager, configManager, selectionVisualizer, blockstateManager, protectionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(selectionManager, undoManager, pendingCommandManager, selectionVisualizer, taskManager, blockstateManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(configManager, guiManager), this);
    }

    @Override
    public void onDisable() {
        if (selectionVisualizer != null) {
            selectionVisualizer.shutdown();
        }
        getLogger().info("LeafWE disabled.");
    }
}