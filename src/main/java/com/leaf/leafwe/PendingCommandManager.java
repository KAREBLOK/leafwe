package com.leaf.leafwe;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingCommandManager {
    private final LeafWE plugin;
    private final ConcurrentHashMap<UUID, Runnable> pendingTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> timeoutTasks = new ConcurrentHashMap<>();
    private final int timeoutSeconds = 30;

    public PendingCommandManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    public boolean hasPending(Player player) {
        if (player == null) return false;
        return pendingTasks.containsKey(player.getUniqueId());
    }

    public void setPending(Player player, Runnable task) {
        if (player == null || task == null) return;

        UUID playerUUID = player.getUniqueId();

        clear(player);

        pendingTasks.put(playerUUID, task);

        BukkitTask timeout = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingTasks.remove(playerUUID) != null) {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("confirmation-expired"));
                    }
                }
                timeoutTasks.remove(playerUUID);
            }
        }.runTaskLater(plugin, 20L * timeoutSeconds);

        timeoutTasks.put(playerUUID, timeout);
    }

    public boolean confirm(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();
        Runnable task = pendingTasks.remove(playerUUID);

        if (task != null) {
            BukkitTask timeoutTask = timeoutTasks.remove(playerUUID);
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }

            try {
                task.run();
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Error executing pending command for player " + player.getName() + ": " + e.getMessage());
                player.sendMessage(plugin.getConfigManager().getMessage("command-execution-error"));
                return false;
            }
        }
        return false;
    }

    public void clear(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        pendingTasks.remove(playerUUID);

        BukkitTask timeoutTask = timeoutTasks.remove(playerUUID);
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
    }

    public void clearAll() {
        timeoutTasks.values().forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });

        pendingTasks.clear();
        timeoutTasks.clear();
    }

    public int getPendingCount() {
        return pendingTasks.size();
    }
}