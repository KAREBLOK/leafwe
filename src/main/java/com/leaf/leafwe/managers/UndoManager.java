package com.leaf.leafwe.managers;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.tasks.UndoTask;
import com.leaf.leafwe.utils.SimpleLocation;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UndoManager {
    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, LinkedList<Map<SimpleLocation, BlockState>>> history = new ConcurrentHashMap<>();

    public UndoManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void addHistory(Player player, Map<Location, BlockState> change) {
        if (player == null || change == null || change.isEmpty()) return;

        Map<SimpleLocation, BlockState> optimizedChange = new HashMap<>();
        for (Map.Entry<Location, BlockState> entry : change.entrySet()) {
            optimizedChange.put(SimpleLocation.from(entry.getKey()), entry.getValue());
        }

        UUID playerUUID = player.getUniqueId();
        history.computeIfAbsent(playerUUID, k -> new LinkedList<>());
        LinkedList<Map<SimpleLocation, BlockState>> playerHistory = history.get(playerUUID);

        synchronized (playerHistory) {
            playerHistory.push(optimizedChange);

            while (playerHistory.size() > configManager.getMaxUndo()) {
                playerHistory.removeLast();
            }
        }
    }

    public boolean undoLastChange(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();
        LinkedList<Map<SimpleLocation, BlockState>> playerHistory = history.get(playerUUID);

        if (playerHistory == null || playerHistory.isEmpty()) {
            return false;
        }

        Map<SimpleLocation, BlockState> lastChange;
        synchronized (playerHistory) {
            if (playerHistory.isEmpty()) return false;
            lastChange = playerHistory.pop();
        }

        if (lastChange == null || lastChange.isEmpty()) {
            return false;
        }

        Map<Location, BlockState> taskData = new HashMap<>();
        boolean worldMissing = false;

        for (Map.Entry<SimpleLocation, BlockState> entry : lastChange.entrySet()) {
            Location loc = entry.getKey().toLocation();
            if (loc != null) {
                taskData.put(loc, entry.getValue());
            } else {
                worldMissing = true;
            }
        }

        if (worldMissing) {
            player.sendMessage("§cBazı bloklar geri alınamadı çünkü dünya artık yüklü değil.");
        }

        if (taskData.isEmpty()) {
            return false;
        }

        try {
            UndoTask undoTask = new UndoTask(player, taskData, configManager);
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
        LinkedList<Map<SimpleLocation, BlockState>> playerHistory = history.remove(playerUUID);

        if (playerHistory != null) {
            synchronized (playerHistory) {
                playerHistory.clear();
            }
        }
    }

    public int getHistorySize(Player player) {
        if (player == null) return 0;

        LinkedList<Map<SimpleLocation, BlockState>> playerHistory = history.get(player.getUniqueId());
        if (playerHistory == null) return 0;

        synchronized (playerHistory) {
            return playerHistory.size();
        }
    }

    public boolean hasHistory(Player player) {
        return getHistorySize(player) > 0;
    }

    /**
     * Belirli bir dünyaya ait tüm undo geçmişini temizler.
     * WorldListener tarafından WorldUnloadEvent sırasında çağrılır.
     */
    public void cleanupWorldHistory(String worldName) {
        for (LinkedList<Map<SimpleLocation, BlockState>> playerHistory : history.values()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (playerHistory) {
                playerHistory.removeIf(changeMap -> {
                    if (changeMap.isEmpty()) return true;
                    return changeMap.keySet().iterator().next().getWorldName().equals(worldName);
                });
            }
        }
    }

    public void clearAllHistory() {
        for (LinkedList<Map<SimpleLocation, BlockState>> playerHistory : history.values()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (playerHistory) {
                playerHistory.clear();
            }
        }
        history.clear();
    }
}