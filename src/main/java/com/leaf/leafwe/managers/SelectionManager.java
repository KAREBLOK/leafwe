package com.leaf.leafwe.managers;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SelectionManager {

    private final ConcurrentHashMap<UUID, Location> pos1Selections;
    private final ConcurrentHashMap<UUID, Location> pos2Selections;

    public SelectionManager() {
        this.pos1Selections = new ConcurrentHashMap<>();
        this.pos2Selections = new ConcurrentHashMap<>();
    }

    public void setPosition1(Player player, Location location) {
        if (player == null || location == null) return;
        pos1Selections.put(player.getUniqueId(), location.clone());
    }

    public void setPosition2(Player player, Location location) {
        if (player == null || location == null) return;
        pos2Selections.put(player.getUniqueId(), location.clone());
    }

    public Location getPosition1(Player player) {
        if (player == null) return null;
        Location loc = pos1Selections.get(player.getUniqueId());
        return loc != null ? loc.clone() : null;
    }

    public Location getPosition2(Player player) {
        if (player == null) return null;
        Location loc = pos2Selections.get(player.getUniqueId());
        return loc != null ? loc.clone() : null;
    }

    public void clearSelection(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        pos1Selections.remove(uuid);
        pos2Selections.remove(uuid);
    }

    public boolean hasCompleteSelection(Player player) {
        return getPosition1(player) != null && getPosition2(player) != null;
    }

    public boolean hasSameWorld(Player player) {
        Location pos1 = getPosition1(player);
        Location pos2 = getPosition2(player);

        if (pos1 == null || pos2 == null) return false;

        return pos1.getWorld().equals(pos2.getWorld());
    }

    public long getVolume(Player player) {
        Location pos1 = getPosition1(player);
        Location pos2 = getPosition2(player);

        if (pos1 == null || pos2 == null) return 0;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public void clearAll() {
        pos1Selections.clear();
        pos2Selections.clear();
    }
}
