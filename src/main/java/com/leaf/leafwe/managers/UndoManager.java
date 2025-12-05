package com.leaf.leafwe.managers;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UndoManager {
    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, LinkedList<Map<Location, BlockData>>> history = new ConcurrentHashMap<>();

    public UndoManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void addHistory(Player player, Map<Location, BlockData> change) {
        if (player == null || change == null || change.isEmpty()) return;

        UUID playerUUID = player.getUniqueId();
        history.computeIfAbsent(playerUUID, k -> new LinkedList<>());
        LinkedList<Map<Location, BlockData>> playerHistory = history.get(playerUUID);

        synchronized (playerHistory) {
            playerHistory.push(change);

            while (playerHistory.size() > configManager.getMaxUndo()) {
                playerHistory.removeLast();
            }
        }
    }

    public boolean undoLastChange(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();
        LinkedList<Map<Location, BlockData>> playerHistory = history.get(playerUUID);

        if (playerHistory == null || playerHistory.isEmpty()) {
            return false;
        }

        Map<Location, BlockData> lastChange;
        synchronized (playerHistory) {
            if (playerHistory.isEmpty()) return false;
            lastChange = playerHistory.pop();
        }

        if (lastChange == null || lastChange.isEmpty()) {
            return false;
        }

        try {
            UndoTask undoTask = new UndoTask(player, lastChange, configManager);
            undoTask.runTaskTimer(plugin, 1L, 1L);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error starting undo task for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public void clearHistory(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        LinkedList<Map<Location, BlockData>> playerHistory = history.remove(playerUUID);

        if (playerHistory != null) {
            synchronized (playerHistory) {
                playerHistory.clear();
            }
        }
    }

    public int getHistorySize(Player player) {
        if (player == null) return 0;

        LinkedList<Map<Location, BlockData>> playerHistory = history.get(player.getUniqueId());
        if (playerHistory == null) return 0;

        synchronized (playerHistory) {
            return playerHistory.size();
        }
    }

    public boolean hasHistory(Player player) {
        return getHistorySize(player) > 0;
    }

    public void clearAllHistory() {
        for (LinkedList<Map<Location, BlockData>> playerHistory : history.values()) {
            synchronized (playerHistory) {
                playerHistory.clear();
            }
        }
        history.clear();
    }
}
