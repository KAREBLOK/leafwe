package com.leaf.leafwe;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {

    private final Map<UUID, Location> pos1Selections;
    private final Map<UUID, Location> pos2Selections;

    public SelectionManager() {
        this.pos1Selections = new HashMap<>();
        this.pos2Selections = new HashMap<>();
    }

    public void setPosition1(Player player, Location location) {
        pos1Selections.put(player.getUniqueId(), location);
    }

    public void setPosition2(Player player, Location location) {
        pos2Selections.put(player.getUniqueId(), location);
    }

    public Location getPosition1(Player player) {
        return pos1Selections.get(player.getUniqueId());
    }

    public Location getPosition2(Player player) {
        return pos2Selections.get(player.getUniqueId());
    }

    public void clearSelection(Player player) {
        pos1Selections.remove(player.getUniqueId());
        pos2Selections.remove(player.getUniqueId());
    }
}