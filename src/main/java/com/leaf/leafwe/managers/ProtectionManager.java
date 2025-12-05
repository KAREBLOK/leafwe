package com.leaf.leafwe.managers;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionManager {

    private boolean worldGuardEnabled = false;
    private boolean superiorSkyblockEnabled = false;
    private boolean townyEnabled = false;
    private boolean landsEnabled = false;
    private boolean griefPreventionEnabled = false;
    private boolean plotSquaredEnabled = false;
    private final LeafWE plugin;

    public ProtectionManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    public void initializeHooksDelayed() {
        initializeHooks();
    }

    private void initializeHooks() {
        Plugin gpPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gpPlugin != null && gpPlugin.isEnabled()) {
            this.griefPreventionEnabled = true;
            plugin.getLogger().info("GriefPrevention hook enabled successfully.");
        } else {
            plugin.getLogger().info("GriefPrevention not found. Protection hook disabled.");
        }

        Plugin plotPlugin = plugin.getServer().getPluginManager().getPlugin("PlotSquared");
        if (plotPlugin != null && plotPlugin.isEnabled()) {
            this.plotSquaredEnabled = true;
            plugin.getLogger().info("PlotSquared hook enabled successfully.");
        } else {
            plugin.getLogger().info("PlotSquared not found. Protection hook disabled.");
        }

        Plugin landsPlugin = plugin.getServer().getPluginManager().getPlugin("Lands");
        if (landsPlugin != null && landsPlugin.isEnabled()) {
            this.landsEnabled = true;
            plugin.getLogger().info("Lands hook enabled successfully.");
        } else {
            plugin.getLogger().info("Lands not found. Protection hook disabled.");
        }

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

        Plugin townyPlugin = plugin.getServer().getPluginManager().getPlugin("Towny");
        if (townyPlugin != null && townyPlugin.isEnabled()) {
            try {
                Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                this.townyEnabled = true;
                plugin.getLogger().info("Towny hook enabled successfully.");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("Towny found but API not available: " + e.getMessage());
                this.townyEnabled = false;
            }
        } else {
            plugin.getLogger().info("Towny not found. Protection hook disabled.");
        }

        plugin.getLogger().info("Protection Manager Status: WG=" + worldGuardEnabled + ", SSB=" + superiorSkyblockEnabled + 
                ", Towny=" + townyEnabled + ", Lands=" + landsEnabled + ", GP=" + griefPreventionEnabled + ", P2=" + plotSquaredEnabled);
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

        if (landsEnabled) {
            try {
                boolean canBuild = checkLandsPermission(player, location);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] Lands: Build permission denied in this land!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking Lands permissions: " + e.getMessage());
                return false;
            }
        }

        if (griefPreventionEnabled) {
            try {
                boolean canBuild = checkGriefPreventionPermission(player, location);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] GriefPrevention: You don't have permission here!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking GriefPrevention permissions: " + e.getMessage());
                return false;
            }
        }

        if (plotSquaredEnabled) {
            try {
                boolean canBuild = checkPlotSquaredPermission(player, location);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] PlotSquared: You cannot build in this plot!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking PlotSquared permissions: " + e.getMessage());
                return false;
            }
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

        if (townyEnabled) {
            try {
                boolean canBuild = checkTownyPermission(player, location);
                if (!canBuild) {
                    player.sendMessage("§c[LeafWE] Towny: Build permission denied in this town!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking Towny permissions: " + e.getMessage());
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

    private boolean checkTownyPermission(Player player, Location location) {
        try {
            Class<?> townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object townyAPI = townyAPIClass.getMethod("getInstance").invoke(null);

            Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
            Object buildAction = actionTypeClass.getField("BUILD").get(null);

            boolean canBuild = (boolean) townyAPIClass.getMethod("testPermission",
                            org.bukkit.entity.Player.class,
                            org.bukkit.Location.class,
                            actionTypeClass)
                    .invoke(townyAPI, player, location, buildAction);

            return canBuild;

        } catch (Exception e) {
            plugin.getLogger().warning("Towny permission check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkLandsPermission(Player player, Location location) {
        try {
            me.angeschossen.lands.api.integration.LandsIntegration api = new me.angeschossen.lands.api.integration.LandsIntegration(plugin);
            me.angeschossen.lands.api.land.Area area = api.getArea(location);
            
            if (area != null) {
                // Check if player can place blocks (BLOCK_PLACE flag)
                return area.hasFlag(player.getUniqueId(), me.angeschossen.lands.api.flags.Flags.BLOCK_PLACE);
            } else {
                // Check wilderness settings
                // Usually wilderness allows everything unless restricted, but for safety we can check wilderness flags if needed.
                // For now, if it's wilderness, we assume Lands doesn't block it (handled by other plugins or default server rules).
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Lands permission check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkGriefPreventionPermission(Player player, Location location) {
        try {
            me.ryanhamshire.GriefPrevention.DataStore dataStore = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore;
            me.ryanhamshire.GriefPrevention.Claim claim = dataStore.getClaimAt(location, true, null);

            if (claim != null) {
                String failureReason = claim.allowBuild(player, org.bukkit.Material.STONE); // Checking dummy build permission
                return failureReason == null;
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("GriefPrevention permission check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean checkPlotSquaredPermission(Player player, Location location) {
        try {
            com.plotsquared.core.location.Location plotLoc = com.plotsquared.core.location.Location.at(
                    location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
            
            com.plotsquared.core.plot.Plot plot = com.plotsquared.core.plot.Plot.getPlot(plotLoc);

            if (plot != null) {
                // Check if player is added to the plot or is the owner
                return plot.isAdded(player.getUniqueId()) || plot.isOwner(player.getUniqueId());
            }
            
            // If not in a plot, we need to check if building on roads/unclaimed areas is allowed.
            // Usually P2 prevents building on roads. LeafWE should probably respect that.
            // However, getting "Road" permission is complex. For safety, if it's a plot world but no plot, return false.
            
            com.plotsquared.core.PlotAPI plotAPI = new com.plotsquared.core.PlotAPI();
            if (plotAPI.getPlotSquared().getPlotAreaManager().getPlotAreaByString(location.getWorld().getName()) != null) {
                // It is a plot world, but no plot found at location -> Road or Unclaimed.
                // Block editing on roads/unclaimed areas unless player has admin bypass (handled in canBuild main check)
                return false;
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("PlotSquared permission check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isSuperiorSkyblockEnabled() {
        return superiorSkyblockEnabled;
    }

    public boolean isTownyEnabled() {
        return townyEnabled;
    }

    public boolean isLandsEnabled() {
        return landsEnabled;
    }

    public boolean isGriefPreventionEnabled() {
        return griefPreventionEnabled;
    }

    public boolean isPlotSquaredEnabled() {
        return plotSquaredEnabled;
    }
}
