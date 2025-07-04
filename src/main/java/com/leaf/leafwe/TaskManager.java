package com.leaf.leafwe;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TaskManager {

    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();

    public boolean hasActiveTask(Player player) {
        return activeTasks.containsKey(player.getUniqueId());
    }

    public void startTask(Player player, BukkitRunnable task) {
        if (hasActiveTask(player)) {
            return;
        }
        activeTasks.put(player.getUniqueId(), task);
    }

    public void finishTask(Player player) {
        activeTasks.remove(player.getUniqueId());
    }
}