package com.leaf.leafwe;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionVisualizer {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public SelectionVisualizer(LeafWE plugin, SelectionManager selManager, ConfigManager confManager) {
        this.plugin = plugin;
        this.selectionManager = selManager;
        this.configManager = confManager;
    }

    public void start(Player player) {
        if (!configManager.isVisualizerEnabled()) return;
        stop(player);

        activeTasks.put(player.getUniqueId(), new BukkitRunnable() {
            @Override
            public void run() {
                Location pos1 = selectionManager.getPosition1(player);
                Location pos2 = selectionManager.getPosition2(player);
                if (pos1 == null || pos2 == null || !player.isOnline()) {
                    this.cancel();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }
                drawBox(pos1, pos2, Color.AQUA);
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 20L));
    }

    public void playSuccessEffect(Player player) {
        if (!configManager.isVisualizerEnabled()) return;
        stop(player);

        Location pos1 = selectionManager.getPosition1(player);
        Location pos2 = selectionManager.getPosition2(player);
        if (pos1 == null || pos2 == null) return;

        if (configManager.isSuccessEffectEnabled()) {
            player.playSound(player.getLocation(), configManager.getSuccessSound(), 1.0f, 1.2f);
        }

        new BukkitRunnable() {
            private int duration = 40;
            @Override
            public void run() {
                if (duration <= 0 || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                drawBox(pos1, pos2, Color.LIME);
                duration--;
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);
    }

    public void stop(Player player) {
        if (activeTasks.containsKey(player.getUniqueId())) {
            activeTasks.remove(player.getUniqueId()).cancel();
        }
    }

    public void shutdown() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
    }

    private void drawBox(Location corner1, Location corner2, Color color) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0F);

        double minX = Math.min(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxX = Math.max(corner1.getX(), corner2.getX()) + 1;
        double maxY = Math.max(corner1.getY(), corner2.getY()) + 1;
        double maxZ = Math.max(corner1.getZ(), corner2.getZ()) + 1;

        for (double x = minX; x <= maxX; x += 0.5) {
            spawnParticle(new Location(corner1.getWorld(), x, minY, minZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), x, maxY, minZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), x, minY, maxZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), x, maxY, maxZ), dustOptions);
        }
        for (double y = minY; y <= maxY; y += 0.5) {
            spawnParticle(new Location(corner1.getWorld(), minX, y, minZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), maxX, y, minZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), minX, y, maxZ), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), maxX, y, maxZ), dustOptions);
        }
        for (double z = minZ; z <= maxZ; z += 0.5) {
            spawnParticle(new Location(corner1.getWorld(), minX, minY, z), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), maxX, minY, z), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), minX, maxY, z), dustOptions);
            spawnParticle(new Location(corner1.getWorld(), maxX, maxY, z), dustOptions);
        }
    }

    private void spawnParticle(Location location, Particle.DustOptions options) {
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.REDSTONE, location, 1, options);
        }
    }
}