package com.leaf.leafwe.managers;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {

    private final ConcurrentHashMap<UUID, BukkitRunnable> activeTasks = new ConcurrentHashMap<>();

    public boolean hasActiveTask(Player player) {
        if (player == null) return false;
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void startTask(Player player, BukkitRunnable task) {
        if (player == null || task == null) return;

        UUID playerUUID = player.getUniqueId();

        if (hasActiveTask(player)) {
            finishTask(player);
        }

        activeTasks.put(playerUUID, task);
    }

    public void finishTask(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        BukkitRunnable task = activeTasks.remove(playerUUID);

        if (task != null && !task.isCancelled()) {
            try {
                task.cancel();
            } catch (IllegalStateException e) {
            }
        }
    }

    public void cancelAllTasks() {
        for (BukkitRunnable task : activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                try {
                    task.cancel();
                } catch (IllegalStateException e) {
                }
            }
        }
        activeTasks.clear();
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public BukkitRunnable getActiveTask(Player player) {
        if (player == null) return null;
        return activeTasks.get(player.getUniqueId());
    }
}
