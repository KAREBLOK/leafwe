package com.leaf.leafwe;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionManager {

    private boolean worldGuardEnabled = false;
    private boolean superiorSkyblockEnabled = false;

    public ProtectionManager(LeafWE plugin) {
        Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin != null) {
            this.worldGuardEnabled = true;
            plugin.getLogger().info("WorldGuard hook enabled.");
        } else {
            plugin.getLogger().info("WorldGuard not found. Protection hook disabled.");
        }

        Plugin ssbPlugin = plugin.getServer().getPluginManager().getPlugin("SuperiorSkyblock2");
        if (ssbPlugin != null) {
            this.superiorSkyblockEnabled = true;
            plugin.getLogger().info("SuperiorSkyblock2 hook enabled.");
        } else {
            plugin.getLogger().info("SuperiorSkyblock2 not found. Protection hook disabled.");
        }
    }

    public boolean canBuild(Player player, Location location) {
        if (player.hasPermission("leafwe.bypass.protection")) {
            return true;
        }

        if (worldGuardEnabled) {
            LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            if (!query.testState(BukkitAdapter.adapt(location), localPlayer, Flags.BUILD)) {
                return false;
            }
        }

        if (superiorSkyblockEnabled) {
            Island island = SuperiorSkyblockAPI.getIslandAt(location);
            if (island != null) {
                SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
                if (!island.isMember(superiorPlayer)) {
                    return false;
                }
            } else {
                // DÜZELTME: Doğru metod adı 'isSkyblockWorld' değil, 'getGrid' olmalı.
                // Eğer o dünyada bir ada ızgarası (grid) varsa, o bir ada dünyasıdır.
                if (SuperiorSkyblockAPI.getGrid().isIslandsWorld(location.getWorld())) {
                    return false;
                }
            }
        }

        return true;
    }
}