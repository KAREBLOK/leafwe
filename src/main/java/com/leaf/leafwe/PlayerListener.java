package com.leaf.leafwe;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;

public class PlayerListener implements Listener {
    private final SelectionManager selectionManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;

    public PlayerListener(SelectionManager selectionManager, UndoManager undoManager,
                          PendingCommandManager pendingCommandManager, SelectionVisualizer selectionVisualizer,
                          TaskManager taskManager, BlockstateManager blockstateManager) {
        this.selectionManager = selectionManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingCommandManager;
        this.selectionVisualizer = selectionVisualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    private void cleanupPlayer(Player player) {
        if (player == null) return;

        try {
            selectionManager.clearSelection(player);

            undoManager.clearHistory(player);

            pendingCommandManager.clear(player);

            selectionVisualizer.stop(player);

            taskManager.finishTask(player);

            blockstateManager.clearCopiedBlockstate(player);

        } catch (Exception e) {
            System.err.println("Error cleaning up player " + player.getName() + ": " + e.getMessage());
        }
    }
}