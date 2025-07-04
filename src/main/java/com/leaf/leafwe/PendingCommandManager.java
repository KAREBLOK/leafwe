package com.leaf.leafwe;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PendingCommandManager {
    private final LeafWE plugin;
    private final Map<UUID, Runnable> pendingTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();

    public PendingCommandManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    public boolean hasPending(Player player) {
        return pendingTasks.containsKey(player.getUniqueId());
    }

    public void setPending(Player player, Runnable task) {
        UUID playerUUID = player.getUniqueId();
        pendingTasks.put(playerUUID, task);

        BukkitTask timeout = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingTasks.remove(playerUUID) != null) {
                    player.sendMessage("§cOnay süresi doldu, işlem iptal edildi.");
                }
            }
        }.runTaskLater(plugin, 20 * 30);
        timeoutTasks.put(playerUUID, timeout);
    }

    public boolean confirm(Player player) {
        UUID playerUUID = player.getUniqueId();
        Runnable task = pendingTasks.remove(playerUUID);
        if (task != null) {
            task.run();
            if (timeoutTasks.containsKey(playerUUID)) {
                timeoutTasks.remove(playerUUID).cancel();
            }
            return true;
        }
        return false;
    }

    public void clear(Player player) {
        pendingTasks.remove(player.getUniqueId());
        if (timeoutTasks.containsKey(player.getUniqueId())) {
            timeoutTasks.remove(player.getUniqueId()).cancel();
        }
    }
}