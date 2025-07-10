package com.leaf.leafwe.commands.impl;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.commands.BaseCommand;
import com.leaf.leafwe.registry.ManagerRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetCommandImpl extends BaseCommand {

    public SetCommandImpl(LeafWE plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!performBasicChecks(sender)) {
            return true;
        }

        Player player = getPlayer(sender);

        if (ManagerRegistry.task().hasActiveTask(player)) {
            player.sendMessage(ManagerRegistry.config().getMessage("task-already-running"));
            return true;
        }

        if (ManagerRegistry.config().getDisabledWorlds().contains(player.getWorld().getName().toLowerCase())) {
            player.sendMessage(ManagerRegistry.config().getMessage("world-disabled"));
            return true;
        }

        Location pos1 = ManagerRegistry.selection().getPosition1(player);
        if (pos1 == null) {
            player.sendMessage(ManagerRegistry.config().getMessage("select-pos1"));
            return true;
        }

        Location pos2 = ManagerRegistry.selection().getPosition2(player);
        if (pos2 == null) {
            player.sendMessage(ManagerRegistry.config().getMessage("select-pos2"));
            return true;
        }

        if (!ManagerRegistry.selection().hasSameWorld(player)) {
            player.sendMessage(Component.text("§cPositions must be in the same world!"));
            return true;
        }

        if (!checkAreaPermissions(player, pos1, pos2)) {
            player.sendMessage(ManagerRegistry.config().getMessage("protection-no-permission"));
            return true;
        }

        if (args.length == 0) {
            ManagerRegistry.gui().openBlockPickerGui(player, "set", null);
            return true;
        }

        Material blockType;
        try {
            String materialName = args[0].trim().toUpperCase();
            blockType = Material.valueOf(materialName);

            if (!blockType.isBlock()) {
                player.sendMessage(ManagerRegistry.config().getMessage("invalid-block")
                        .replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
                return true;
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(ManagerRegistry.config().getMessage("invalid-block")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
            return true;
        }

        if (ManagerRegistry.config().getBlockedMaterials().contains(blockType)) {
            player.sendMessage(ManagerRegistry.config().getMessage("blacklisted-block"));
            return true;
        }

        if (!player.getInventory().contains(blockType)) {
            player.sendMessage(ManagerRegistry.config().getMessage("inventory-empty")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(blockType.name())));
            return true;
        }

        long volume = ManagerRegistry.selection().getVolume(player);

        // Daily Limit kontrolü
        if (ManagerRegistry.dailyLimit() != null) {
            var limitResult = ManagerRegistry.dailyLimit().canPerformOperationDetailed(player, (int) volume);

            if (!limitResult.canPerform) {
                var usageInfo = ManagerRegistry.dailyLimit().getUsageInfo(player);

                if (limitResult.limitType == com.leaf.leafwe.DailyLimitManager.LimitType.BLOCKS) {
                    player.sendMessage(ManagerRegistry.config().getDailyLimitBlocksExceeded()
                            .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedBlocks)))
                            .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxBlocks)))
                            .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));
                } else if (limitResult.limitType == com.leaf.leafwe.DailyLimitManager.LimitType.OPERATIONS) {
                    player.sendMessage(ManagerRegistry.config().getDailyLimitOperationsExceeded()
                            .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedOperations)))
                            .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxOperations)))
                            .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));
                }
                return true;
            }
        }

        if (!player.hasPermission("leafwe.bypass.limit") && volume > ManagerRegistry.config().getMaxVolume()) {
            player.sendMessage(ManagerRegistry.config().getMessage("volume-limit-exceeded")
                    .replaceText(config -> config.matchLiteral("%limit%").replacement(String.valueOf(ManagerRegistry.config().getMaxVolume()))));
            return true;
        }

        List<Location> locationsToFill = new ArrayList<>();
        Map<Location, BlockData> undoData = new HashMap<>();

        try {
            World world = pos1.getWorld();
            int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
            int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
            int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
            int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
            int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
            int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Location loc = new Location(world, x, y, z);
                        locationsToFill.add(loc);
                        undoData.put(loc, loc.getBlock().getBlockData());
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("§cError while preparing locations: " + e.getMessage()));
            return true;
        }

        final Material finalBlockType = blockType;

        Runnable executionTask = () -> {
            try {
                ManagerRegistry.undo().addHistory(player, undoData);
                ManagerRegistry.gui().setLastReplacedFrom(player, finalBlockType);
                player.sendMessage(ManagerRegistry.config().getMessage("process-starting"));

                com.leaf.leafwe.BlockPlacerTask task = new com.leaf.leafwe.BlockPlacerTask(
                        plugin, player, locationsToFill, finalBlockType,
                        ManagerRegistry.config(), ManagerRegistry.visualizer(),
                        ManagerRegistry.task(), ManagerRegistry.blockstate()
                );
                task.runTaskTimer(plugin, 2L, ManagerRegistry.config().getSpeed());
                ManagerRegistry.task().startTask(player, task);
            } catch (Exception e) {
                player.sendMessage(Component.text("§cError starting set task: " + e.getMessage()));
            }
        };

        int confirmationLimit = ManagerRegistry.config().getConfirmationLimit();
        if (confirmationLimit > 0 && volume > confirmationLimit) {
            if (ManagerRegistry.pending().hasPending(player)) {
                player.sendMessage(ManagerRegistry.config().getMessage("confirmation-pending"));
                return true;
            }

            ManagerRegistry.pending().setPending(player, executionTask);
            player.sendMessage(ManagerRegistry.config().getMessage("confirmation-required")
                    .replaceText(config -> config.matchLiteral("%total%").replacement(String.valueOf(volume))));
        } else {
            executionTask.run();
        }

        return true;
    }

    private boolean checkAreaPermissions(Player player, Location pos1, Location pos2) {
        if (player.hasPermission("leafwe.bypass.protection")) {
            return true;
        }

        if (ManagerRegistry.protection() == null) {
            return true;
        }

        World world = pos1.getWorld();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        Location[] checkPoints = {
                new Location(world, minX, minY, minZ),
                new Location(world, maxX, maxY, maxZ),
                new Location(world, minX, maxY, minZ),
                new Location(world, maxX, minY, maxZ),
                new Location(world, (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
        };

        for (Location checkPoint : checkPoints) {
            if (!ManagerRegistry.protection().canBuild(player, checkPoint)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getDescription() {
        return "Fill the selected area with blocks";
    }

    @Override
    public String getUsage() {
        return "/set <material>";
    }

    @Override
    public String getPermission() {
        return "leafwe.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }
}