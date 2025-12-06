package com.leaf.leafwe.managers;

import com.leaf.leafwe.LeafWE;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

public class ProtectionManager {

    private final LeafWE plugin;

    private boolean worldGuardEnabled = false;
    private boolean superiorSkyblockEnabled = false;
    private boolean townyEnabled = false;
    private boolean landsEnabled = false;
    private boolean griefPreventionEnabled = false;
    private boolean plotSquaredEnabled = false;

    private Object townyAPIInstance;
    private Method townyTestPermissionMethod;
    private Object townyBuildAction;

    private Object landsIntegrationInstance;
    private Method landsGetAreaMethod;
    private Object landsBlockPlaceFlag;

    public ProtectionManager(LeafWE plugin) {
        this.plugin = plugin;
    }

    public void initializeHooksDelayed() {
        initializeHooks();
    }

    private void initializeHooks() {
        PluginManager pm = plugin.getServer().getPluginManager();

        if (pm.getPlugin("GriefPrevention") != null && Objects.requireNonNull(pm.getPlugin("GriefPrevention")).isEnabled()) {
            this.griefPreventionEnabled = true;
            plugin.getLogger().info("GriefPrevention hook enabled.");
        }

        if (pm.getPlugin("PlotSquared") != null && Objects.requireNonNull(pm.getPlugin("PlotSquared")).isEnabled()) {
            this.plotSquaredEnabled = true;
            plugin.getLogger().info("PlotSquared hook enabled.");
        }

        if (pm.getPlugin("Lands") != null && Objects.requireNonNull(pm.getPlugin("Lands")).isEnabled()) {
            try {
                Class<?> integrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
                Constructor<?> constructor = integrationClass.getConstructor(Plugin.class);
                this.landsIntegrationInstance = constructor.newInstance(plugin);

                this.landsGetAreaMethod = integrationClass.getMethod("getArea", Location.class);

                Class<?> flagsClass = Class.forName("me.angeschossen.lands.api.flags.Flags");
                Field blockPlaceField = flagsClass.getField("BLOCK_PLACE");
                this.landsBlockPlaceFlag = blockPlaceField.get(null);

                this.landsEnabled = true;
                plugin.getLogger().info("Lands hook enabled (Optimized).");
            } catch (Exception e) {
                plugin.getLogger().warning("Lands found but failed to initialize hook: " + e.getMessage());
                this.landsEnabled = false;
            }
        }

        Plugin wgPlugin = pm.getPlugin("WorldGuard");
        if (wgPlugin != null && wgPlugin.isEnabled()) {
            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                this.worldGuardEnabled = true;
                plugin.getLogger().info("WorldGuard hook enabled.");
            } catch (Exception e) {
                plugin.getLogger().warning("WorldGuard integration error: " + e.getMessage());
                this.worldGuardEnabled = false;
            }
        }

        if (pm.getPlugin("SuperiorSkyblock2") != null && Objects.requireNonNull(pm.getPlugin("SuperiorSkyblock2")).isEnabled()) {
            try {
                Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                this.superiorSkyblockEnabled = true;
                plugin.getLogger().info("SuperiorSkyblock2 hook enabled.");
            } catch (ClassNotFoundException e) {
                this.superiorSkyblockEnabled = false;
            }
        }

        if (pm.getPlugin("Towny") != null && Objects.requireNonNull(pm.getPlugin("Towny")).isEnabled()) {
            try {
                Class<?> townyAPIClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                Method getInstanceMethod = townyAPIClass.getMethod("getInstance");
                this.townyAPIInstance = getInstanceMethod.invoke(null);

                Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
                Field buildField = actionTypeClass.getField("BUILD");
                this.townyBuildAction = buildField.get(null);

                this.townyTestPermissionMethod = townyAPIClass.getMethod("testPermission",
                        org.bukkit.entity.Player.class,
                        org.bukkit.Location.class,
                        actionTypeClass);

                this.townyEnabled = true;
                plugin.getLogger().info("Towny hook enabled (Optimized).");
            } catch (Exception e) {
                plugin.getLogger().warning("Towny found but failed to initialize hook: " + e.getMessage());
                this.townyEnabled = false;
            }
        }
    }

    public boolean canBuild(Player player, Location location) {
        if (player == null || location == null) return false;

        if (player.hasPermission("leafwe.bypass.protection")) return true;

        if (landsEnabled) {
            if (!checkLandsPermission(player, location)) {
                return false;
            }
        }

        if (griefPreventionEnabled) {
            if (!checkGriefPreventionPermission(player, location)) return false;
        }

        if (plotSquaredEnabled) {
            if (!checkPlotSquaredPermission(player, location)) return false;
        }

        if (worldGuardEnabled) {
            if (!checkWorldGuardPermission(player, location)) return false;
        }

        if (superiorSkyblockEnabled) {
            if (!checkSuperiorSkyblockPermission(player, location)) return false;
        }

        if (townyEnabled) {
            return checkTownyPermission(player, location);
        }

        return true;
    }

    private boolean checkTownyPermission(Player player, Location location) {
        try {
            return (boolean) townyTestPermissionMethod.invoke(townyAPIInstance, player, location, townyBuildAction);
        } catch (Exception e) {
            plugin.getLogger().warning("Towny check error: " + e.getMessage());
            return false;
        }
    }

    private boolean checkLandsPermission(Player player, Location location) {
        try {
            Object area = landsGetAreaMethod.invoke(landsIntegrationInstance, location);
            if (area != null) {
                Method hasFlag = area.getClass().getMethod("hasFlag", UUID.class, landsBlockPlaceFlag.getClass().getSuperclass());
                return (boolean) hasFlag.invoke(area, player.getUniqueId(), landsBlockPlaceFlag);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
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
                return !com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI.getGrid().isIslandsWorld(location.getWorld());
            }
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean checkGriefPreventionPermission(Player player, Location location) {
        try {
            me.ryanhamshire.GriefPrevention.DataStore dataStore = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore;
            me.ryanhamshire.GriefPrevention.Claim claim = dataStore.getClaimAt(location, true, null);

            if (claim != null) {
                return claim.allowBuild(player, org.bukkit.Material.STONE) == null;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkPlotSquaredPermission(Player player, Location location) {
        try {
            com.plotsquared.core.location.Location plotLoc = com.plotsquared.core.location.Location.at(
                    location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());

            com.plotsquared.core.plot.Plot plot = com.plotsquared.core.plot.Plot.getPlot(plotLoc);

            if (plot != null) {
                return plot.isAdded(player.getUniqueId()) || plot.isOwner(player.getUniqueId());
            }

            return new com.plotsquared.core.PlotAPI().getPlotSquared().getPlotAreaManager()
                    .getPlotAreaByString(location.getWorld().getName()) == null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isWorldGuardEnabled() { return worldGuardEnabled; }
    public boolean isSuperiorSkyblockEnabled() { return superiorSkyblockEnabled; }
    public boolean isTownyEnabled() { return townyEnabled; }
    public boolean isLandsEnabled() { return landsEnabled; }
    public boolean isGriefPreventionEnabled() { return griefPreventionEnabled; }
    public boolean isPlotSquaredEnabled() { return plotSquaredEnabled; }
}