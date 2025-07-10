package com.leaf.leafwe.commands.impl;

import com.leaf.leafwe.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReplaceCommandImpl implements CommandExecutor {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private final GuiManager guiManager;

    public ReplaceCommandImpl(LeafWE plugin, SelectionManager selManager, ConfigManager confManager,
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

        if (!player.hasPermission("leafwe.replace")) {
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

        if (!checkAreaPermissions(player, pos1, pos2)) {
            player.sendMessage(configManager.getMessage("protection-no-permission"));
            return true;
        }

        if (args.length == 0) {
            guiManager.openReplaceGui(player);
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(configManager.getMessage("invalid-usage-replace"));
            return true;
        }

        Material fromBlock;
        Material toBlock;
        try {
            fromBlock = Material.valueOf(args[0].trim().toUpperCase());
            toBlock = Material.valueOf(args[1].trim().toUpperCase());

            if (!fromBlock.isBlock()) {
                player.sendMessage(configManager.getMessage("invalid-block")
                        .replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
                return true;
            }

            if (!toBlock.isBlock()) {
                player.sendMessage(configManager.getMessage("invalid-block")
                        .replaceText(config -> config.matchLiteral("%block%").replacement(args[1])));
                return true;
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-block")
                    .replaceText(config -> config.matchLiteral("%block%").replacement("UNKNOWN")));
            return true;
        }

        if (configManager.getBlockedMaterials().contains(toBlock)) {
            player.sendMessage(configManager.getMessage("blacklisted-block"));
            return true;
        }

        if (!player.getInventory().contains(toBlock)) {
            player.sendMessage(configManager.getMessage("inventory-empty")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(toBlock.name())));
            return true;
        }

        List<Block> blocksToChange = new ArrayList<>();
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
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() == fromBlock) {
                            blocksToChange.add(block);
                            undoData.put(block.getLocation(), block.getBlockData());
                        }
                    }
                }
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("§cError while scanning blocks: " + e.getMessage()));
            return true;
        }

        if (blocksToChange.isEmpty()) {
            player.sendMessage(Component.text("§cNo '" + fromBlock.name() + "' blocks found in the selected area."));
            return true;
        }

        long volume = blocksToChange.size();

        // DailyLimitManager kontrolü - düzeltilmiş versiyon
        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();
        if (dailyLimitManager != null) {
            DailyLimitManager.LimitCheckResult limitResult = dailyLimitManager.canPerformOperationDetailed(player, (int) volume);

            if (!limitResult.canPerform) {
                DailyLimitManager.DailyUsageInfo usageInfo = dailyLimitManager.getUsageInfo(player);

                if (limitResult.limitType == DailyLimitManager.LimitType.BLOCKS) {
                    // Blok limiti aşıldı
                    player.sendMessage(configManager.getDailyLimitBlocksExceeded()
                            .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedBlocks)))
                            .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxBlocks)))
                            .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));
                } else if (limitResult.limitType == DailyLimitManager.LimitType.OPERATIONS) {
                    // İşlem limiti aşıldı
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

        final Material finalFromBlock = fromBlock;
        final Material finalToBlock = toBlock;

        Runnable executionTask = () -> {
            try {
                undoManager.addHistory(player, undoData);
                guiManager.setLastReplacedFrom(player, finalFromBlock);
                player.sendMessage(configManager.getMessage("process-starting"));

                ReplaceTask task = new ReplaceTask(player, blocksToChange, finalToBlock,
                        configManager, selectionVisualizer, taskManager, blockstateManager);
                task.runTaskTimer(plugin, 2L, configManager.getSpeed());
                taskManager.startTask(player, task);
            } catch (Exception e) {
                player.sendMessage(Component.text("§cError starting replace task: " + e.getMessage()));
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