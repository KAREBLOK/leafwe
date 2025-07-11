package com.leaf.leafwe.commands.impl;

import com.leaf.leafwe.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WallCommandImpl implements CommandExecutor {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private final GuiManager guiManager;

    public WallCommandImpl(LeafWE plugin, SelectionManager selManager, ConfigManager confManager,
                           UndoManager undoManager, PendingCommandManager pendingManager,
                           SelectionVisualizer visualizer, TaskManager taskManager,
                           BlockstateManager blockstateManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.selectionManager = selManager;
        this.configManager = confManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("players-only"));
            return true;
        }

        if (taskManager.hasActiveTask(player)) {
            player.sendMessage(configManager.getMessage("task-already-running"));
            return true;
        }

        if (!player.hasPermission("leafwe.wall")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (configManager.getDisabledWorlds().contains(player.getWorld().getName().toLowerCase())) {
            player.sendMessage(configManager.getMessage("world-disabled"));
            return true;
        }

        Location pos1 = selectionManager.getPosition1(player);
        if (pos1 == null) {
            player.sendMessage(configManager.getMessage("select-pos1"));
            return true;
        }

        Location pos2 = selectionManager.getPosition2(player);
        if (pos2 == null) {
            player.sendMessage(configManager.getMessage("select-pos2"));
            return true;
        }

        if (!selectionManager.hasSameWorld(player)) {
            player.sendMessage(Component.text("§cPositions must be in the same world!"));
            return true;
        }

        if (!checkAreaPermissions(player, pos1, pos2)) {
            player.sendMessage(configManager.getMessage("protection-no-permission"));
            return true;
        }

        if (args.length == 0) {
            guiManager.openBlockPickerGui(player, "wall", null);
            return true;
        }

        Material blockType;
        try {
            String materialName = args[0].trim().toUpperCase();
            blockType = Material.valueOf(materialName);

            if (!blockType.isBlock()) {
                player.sendMessage(configManager.getMessage("invalid-block")
                        .replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
                return true;
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-block")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
            return true;
        }

        if (configManager.getBlockedMaterials().contains(blockType)) {
            player.sendMessage(configManager.getMessage("blacklisted-block"));
            return true;
        }

        if (!player.getInventory().contains(blockType)) {
            player.sendMessage(configManager.getMessage("inventory-empty")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(blockType.name())));
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
                        if (x == minX || x == maxX || z == minZ || z == maxZ) {
                            Location loc = new Location(world, x, y, z);
                            locationsToFill.add(loc);
                            undoData.put(loc, loc.getBlock().getBlockData());
                        }
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("§cError while calculating wall locations: " + e.getMessage()));
            return true;
        }

        if (locationsToFill.isEmpty()) {
            player.sendMessage(Component.text("§cYou must select an area of at least 3x3 to build walls."));
            return true;
        }

        long volume = locationsToFill.size();

        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();
        if (dailyLimitManager != null) {
            DailyLimitManager.LimitCheckResult limitResult = dailyLimitManager.canPerformOperationDetailed(player, (int) volume);

            if (!limitResult.canPerform) {
                DailyLimitManager.DailyUsageInfo usageInfo = dailyLimitManager.getUsageInfo(player);

                if (limitResult.limitType == DailyLimitManager.LimitType.BLOCKS) {
                    player.sendMessage(configManager.getDailyLimitBlocksExceeded()
                            .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedBlocks)))
                            .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxBlocks)))
                            .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));
                } else if (limitResult.limitType == DailyLimitManager.LimitType.OPERATIONS) {
                    player.sendMessage(configManager.getDailyLimitOperationsExceeded()
                            .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedOperations)))
                            .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxOperations)))
                            .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));
                }
                return true;
            }
        }

        if (!player.hasPermission("leafwe.bypass.limit") && volume > configManager.getMaxVolume()) {
            player.sendMessage(configManager.getMessage("volume-limit-exceeded")
                    .replaceText(config -> config.matchLiteral("%limit%").replacement(String.valueOf(configManager.getMaxVolume()))));
            return true;
        }

        final Material finalBlockType = blockType;

        Runnable executionTask = () -> {
            try {
                undoManager.addHistory(player, undoData);
                guiManager.setLastReplacedFrom(player, finalBlockType);
                player.sendMessage(configManager.getMessage("process-starting"));

                BlockPlacerTask task = new BlockPlacerTask(plugin, player, locationsToFill, finalBlockType,
                        configManager, selectionVisualizer, taskManager, blockstateManager);
                task.runTaskTimer(plugin, 2L, configManager.getSpeed());
                taskManager.startTask(player, task);
            } catch (Exception e) {
                player.sendMessage(Component.text("§cError starting wall task: " + e.getMessage()));
            }
        };

        int confirmationLimit = configManager.getConfirmationLimit();
        if (confirmationLimit > 0 && volume > confirmationLimit) {
            if (pendingCommandManager.hasPending(player)) {
                player.sendMessage(configManager.getMessage("confirmation-pending"));
                return true;
            }

            pendingCommandManager.setPending(player, executionTask);
            player.sendMessage(configManager.getMessage("confirmation-required")
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

        ProtectionManager protectionManager = plugin.getProtectionManager();
        if (protectionManager == null) {
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
            if (!protectionManager.canBuild(player, checkPoint)) {
                return false;
            }
        }

        return true;
    }
}