package com.leaf.leafwe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    private final SelectionManager selectionManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;

    public PlayerListener(SelectionManager selectionManager, UndoManager undoManager, PendingCommandManager pendingCommandManager, SelectionVisualizer selectionVisualizer, TaskManager taskManager, BlockstateManager blockstateManager) {
        this.selectionManager = selectionManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingCommandManager;
        this.selectionVisualizer = selectionVisualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        selectionManager.clearSelection(player);
        undoManager.clearHistory(player);
        pendingCommandManager.clear(player);
        selectionVisualizer.stop(player);
        taskManager.finishTask(player);
        blockstateManager.clearCopiedBlockstate(player);
    }
}