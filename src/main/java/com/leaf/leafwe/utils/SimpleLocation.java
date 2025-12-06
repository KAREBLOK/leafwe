package com.leaf.leafwe.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;

/**
 * A lightweight location class that stores world name instead of World object
 * to prevent memory leaks when worlds are unloaded.
 */
public class SimpleLocation {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public SimpleLocation(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static SimpleLocation from(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return new SimpleLocation(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public String getWorldName() {
        return worldName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleLocation that = (SimpleLocation) o;
        return x == that.x &&
                y == that.y &&
                z == that.z &&
                Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        int result = worldName != null ? worldName.hashCode() : 0;
        result = 31 * result + x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "SimpleLocation{" +
                "world='" + worldName + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
}