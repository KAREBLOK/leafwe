package com.leaf.leafwe;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionManager {

    private boolean worldGuardEnabled = false;
    private boolean superiorSkyblockEnabled = false;
    private final LeafWE plugin;

    public ProtectionManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    public void initializeHooksDelayed() {
        initializeHooks();
    }

    private void initializeHooks() {
        Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        plugin.getLogger().info("WorldGuard plugin check: " + (wgPlugin != null ? "Found" : "Not Found"));

        if (wgPlugin != null) {
            plugin.getLogger().info("WorldGuard enabled status: " + wgPlugin.isEnabled());
            plugin.getLogger().info("WorldGuard version: " + wgPlugin.getName() + " v" + wgPlugin.getDescription().getName());

            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                plugin.getLogger().info("WorldGuard main class loaded successfully");

                Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                plugin.getLogger().info("WorldGuardPlugin class loaded successfully");

                com.sk89q.worldguard.WorldGuard wgInstance = com.sk89q.worldguard.WorldGuard.getInstance();
                plugin.getLogger().info("WorldGuard instance obtained: " + (wgInstance != null));

                this.worldGuardEnabled = true;
                plugin.getLogger().info("WorldGuard hook enabled successfully.");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("WorldGuard found but incompatible version detected: " + e.getMessage());
                this.worldGuardEnabled = false;
            } catch (Exception e) {
                plugin.getLogger().warning("WorldGuard integration error: " + e.getMessage());
                e.printStackTrace();
                this.worldGuardEnabled = false;
            }

        } else {
            plugin.getLogger().info("WorldGuard not found. Protection hook disabled.");
        }

        Plugin ssbPlugin = plugin.getServer().getPluginManager().getPlugin("SuperiorSkyblock2");
        if (ssbPlugin != null && ssbPlugin.isEnabled()) {
            try {
                Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                this.superiorSkyblockEnabled = true;
                plugin.getLogger().info("SuperiorSkyblock2 hook enabled successfully.");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("SuperiorSkyblock2 found but API not available: " + e.getMessage());
                this.superiorSkyblockEnabled = false;
            }
        } else {
            plugin.getLogger().info("SuperiorSkyblock2 not found. Protection hook disabled.");
        }

        plugin.getLogger().info("Protection Manager Status: WG=" + worldGuardEnabled + ", SSB=" + superiorSkyblockEnabled);
    }

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) {
            return false;
        }

        plugin.getLogger().info("Checking build permission for " + player.getName() +
                " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

        if (player.hasPermission("leafwe.bypass.protection")) {
            plugin.getLogger().info("Player has bypass permission - allowing");
            return true;
        }

        if (worldGuardEnabled) {
            plugin.getLogger().info("Checking WorldGuard permissions...");
            try {
                boolean canBuild = checkWorldGuardPermission(player, location);
                plugin.getLogger().info("WorldGuard result: " + canBuild);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] WorldGuard: Build permission denied in this region!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking WorldGuard permissions: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            plugin.getLogger().info("WorldGuard disabled - allowing");
        }

        if (superiorSkyblockEnabled) {
            try {
                boolean canBuild = checkSuperiorSkyblockPermission(player, location);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] SuperiorSkyblock: Build permission denied!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking SuperiorSkyblock permissions: " + e.getMessage());
                return false;
            }
        }

        plugin.getLogger().info("All checks passed - allowing build");
        return true;
    }

    private boolean checkWorldGuardPermission(Player player, Location location) {
        try {
            com.sk89q.worldguard.LocalPlayer localPlayer =
                    com.sk89q.worldguard.bukkit.WorldGuardPlugin.inst().wrapPlayer(player);
            com.sk89q.worldguard.protection.regions.RegionContainer container =
                    com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.regions.RegionQuery query = container.createQuery();

            return query.testState(
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location),
                    localPlayer,
                    com.sk89q.worldguard.protection.flags.Flags.BUILD
            );
        } catch (Exception e) {
            plugin.getLogger().warning("WorldGuard permission check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkSuperiorSkyblockPermission(Player player, Location location) {
        try {
            com.bgsoftware.superiorskyblock.api.island.Island island =
                    com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getIslandAt(location);

            if (island != null) {
                com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer superiorPlayer =
                        com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getPlayer(player);
                return island.isMember(superiorPlayer);
            } else {
                if (com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getGrid()
                        .isIslandsWorld(location.getWorld())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("SuperiorSkyblock permission check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isSuperiorSkyblockEnabled() {
        return superiorSkyblockEnabled;
    }
}