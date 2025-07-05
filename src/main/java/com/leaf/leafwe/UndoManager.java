package com.leaf.leafwe;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

public class UndoManager {
    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final Map<UUID, LinkedList<Map<Location, BlockData>>> history = new HashMap<>();

    public UndoManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void addHistory(Player player, Map<Location, BlockData> change) {
        history.computeIfAbsent(player.getUniqueId(), k -> new LinkedList<>());
        LinkedList<Map<Location, BlockData>> playerHistory = history.get(player.getUniqueId());
        playerHistory.push(change);

        while (playerHistory.size() > configManager.getMaxUndo()) {
            playerHistory.removeLast();
        }
    }

    public boolean undoLastChange(Player player) {
        LinkedList<Map<Location, BlockData>> playerHistory = history.get(player.getUniqueId());
        if (playerHistory == null || playerHistory.isEmpty()) {
            return false;
        }
        Map<Location, BlockData> lastChange = playerHistory.pop();

        new UndoTask(player, lastChange, configManager).runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    public void clearHistory(Player player) {
        history.remove(player.getUniqueId());
    }
}