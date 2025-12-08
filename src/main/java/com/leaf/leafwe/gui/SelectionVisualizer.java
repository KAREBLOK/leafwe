package com.leaf.leafwe.gui;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.managers.ConfigManager;
import com.leaf.leafwe.managers.SelectionManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionVisualizer {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private volatile boolean isShuttingDown = false;

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final long ACTIVITY_CHECK_INTERVAL = 60;

    public SelectionVisualizer(LeafWE plugin, SelectionManager selManager, ConfigManager confManager) {
        this.plugin = plugin;
        this.selectionManager = selManager;
        this.configManager = confManager;
    }

    public void start(Player player) {
        if (!configManager.isVisualizerEnabled() || player == null || isShuttingDown) return;

        stop(player);
        updateLastActivity(player);

        final UUID playerUUID = player.getUniqueId();

        try {
            BukkitTask task = new BukkitRunnable() {
                private int tickCount = 0;

                @Override
                public void run() {
                    if (isShuttingDown) {
                        this.cancel();
                        return;
                    }

                    Player currentPlayer = Bukkit.getPlayer(playerUUID);

                    if (currentPlayer == null || !currentPlayer.isOnline()) {
                        this.cancel();
                        activeTasks.remove(playerUUID);
                        lastActivity.remove(playerUUID);
                        return;
                    }

                    if (tickCount % ACTIVITY_CHECK_INTERVAL == 0) {
                        if (isTimedOut(playerUUID)) {
                            currentPlayer.sendActionBar(configManager.getMessage("selection-visualizer-timeout"));
                            this.cancel();
                            activeTasks.remove(playerUUID);
                            lastActivity.remove(playerUUID);
                            return;
                        }
                    }

                    Location pos1 = selectionManager.getPosition1(currentPlayer);
                    Location pos2 = selectionManager.getPosition2(currentPlayer);

                    if (pos1 == null || pos2 == null) {
                        this.cancel();
                        activeTasks.remove(playerUUID);
                        lastActivity.remove(playerUUID);
                        return;
                    }

                    if (!pos1.getWorld().equals(pos2.getWorld())) {
                        this.cancel();
                        activeTasks.remove(playerUUID);
                        lastActivity.remove(playerUUID);
                        return;
                    }

                    drawBox(pos1, pos2, Color.AQUA, currentPlayer);
                    tickCount++;
                }
            }.runTaskTimerAsynchronously(plugin, 0L, 20L);

            activeTasks.put(playerUUID, task);
        } catch (Exception e) {
            plugin.getLogger().warning("Error starting selection visualizer for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void playSuccessEffect(Player player) {
        if (!configManager.isVisualizerEnabled() || player == null || isShuttingDown) return;

        stop(player);

        Location pos1 = selectionManager.getPosition1(player);
        Location pos2 = selectionManager.getPosition2(player);
        if (pos1 == null || pos2 == null || !pos1.getWorld().equals(pos2.getWorld())) return;

        final UUID playerUUID = player.getUniqueId();

        try {
            if (configManager.isSuccessEffectEnabled() && player.isOnline()) {
                player.playSound(player.getLocation(), configManager.getSuccessSound(), 1.0f, 1.2f);
            }

            new BukkitRunnable() {
                private int duration = 40;

                @Override
                public void run() {
                    if (isShuttingDown || duration <= 0) {
                        this.cancel();
                        return;
                    }

                    Player currentPlayer = Bukkit.getPlayer(playerUUID);
                    if (currentPlayer == null || !currentPlayer.isOnline()) {
                        this.cancel();
                        return;
                    }

                    try {
                        drawBox(pos1, pos2, Color.LIME, currentPlayer);
                    } catch (Exception e) {
                        this.cancel();
                    }

                    duration--;
                }
            }.runTaskTimerAsynchronously(plugin, 0L, 1L);
        } catch (Exception e) {
            plugin.getLogger().warning("Error playing success effect for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void stop(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();

        BukkitTask task = activeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            try {
                task.cancel();
            } catch (IllegalStateException ignored) {
            }
        }

        lastActivity.remove(uuid);
    }

    public void shutdown() {
        isShuttingDown = true;

        for (BukkitTask task : activeTasks.values()) {
            if (task != null && !task.isCancelled()) {
                try {
                    task.cancel();
                } catch (IllegalStateException ignored) {
                }
            }
        }
        activeTasks.clear();
        lastActivity.clear();
    }

    public void updateLastActivity(Player player) {
        if (player != null) {
            lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private boolean isTimedOut(UUID playerUUID) {
        Long lastTime = lastActivity.get(playerUUID);
        if (lastTime == null) return true;

        long timeoutMillis = getTimeoutSeconds() * 1000;
        return (System.currentTimeMillis() - lastTime) > timeoutMillis;
    }

    private long getTimeoutSeconds() {
        return configManager.getConfig().getLong("settings.selection-timeout", DEFAULT_TIMEOUT_SECONDS);
    }

    public int getActiveVisualizationCount() {
        return activeTasks.size();
    }

    public boolean hasActiveVisualization(Player player) {
        return player != null && activeTasks.containsKey(player.getUniqueId());
    }

    private void drawBox(Location corner1, Location corner2, Color color, Player player) {
        if (corner1 == null || corner2 == null || player == null) return;

        if (!player.getWorld().getUID().equals(corner1.getWorld().getUID())) return;

        try {
            Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0F);

            double minX = Math.min(corner1.getX(), corner2.getX());
            double minY = Math.min(corner1.getY(), corner2.getY());
            double minZ = Math.min(corner1.getZ(), corner2.getZ());
            double maxX = Math.max(corner1.getX(), corner2.getX()) + 1;
            double maxY = Math.max(corner1.getY(), corner2.getY()) + 1;
            double maxZ = Math.max(corner1.getZ(), corner2.getZ()) + 1;

            double step = calculateStep(minX, minY, minZ, maxX, maxY, maxZ);

            for (double x = minX; x <= maxX; x += step) {
                spawnParticleForPlayer(new Location(corner1.getWorld(), x, minY, minZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), x, maxY, minZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), x, minY, maxZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), x, maxY, maxZ), dustOptions, player);
            }

            for (double y = minY; y <= maxY; y += step) {
                spawnParticleForPlayer(new Location(corner1.getWorld(), minX, y, minZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), maxX, y, minZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), minX, y, maxZ), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), maxX, y, maxZ), dustOptions, player);
            }

            for (double z = minZ; z <= maxZ; z += step) {
                spawnParticleForPlayer(new Location(corner1.getWorld(), minX, minY, z), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), maxX, minY, z), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), minX, maxY, z), dustOptions, player);
                spawnParticleForPlayer(new Location(corner1.getWorld(), maxX, maxY, z), dustOptions, player);
            }
        } catch (Exception ignored) { }
    }

    private double calculateStep(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double volume = (maxX - minX) * (maxY - minY) * (maxZ - minZ);

        if (volume > 10000) {
            return 2.0;
        } else if (volume > 1000) {
            return 1.0;
        } else {
            return 0.5;
        }
    }

    private void spawnParticleForPlayer(Location location, Particle.DustOptions options, Player player) {
        if (location == null || player == null) return;

        try {
            if (player.isOnline()) {
                player.spawnParticle(Particle.REDSTONE, location, 1, options);
            }
        } catch (Exception ignored) { }
    }
}