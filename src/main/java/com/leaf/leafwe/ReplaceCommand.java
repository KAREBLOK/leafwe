package com.leaf.leafwe;

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

public class ReplaceCommand implements CommandExecutor {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;

    public ReplaceCommand(LeafWE plugin, SelectionManager selManager, ConfigManager confManager, UndoManager undoManager, PendingCommandManager pendingManager, SelectionVisualizer visualizer, TaskManager taskManager) {
        this.plugin = plugin;
        this.selectionManager = selManager;
        this.configManager = confManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
        Player player = (Player) sender;
        if (taskManager.hasActiveTask(player)) { player.sendMessage(configManager.getMessage("task-already-running")); return true; }
        if (!player.hasPermission("leafwe.replace")) { player.sendMessage(configManager.getMessage("no-permission")); return true; }
        if (configManager.getDisabledWorlds().contains(player.getWorld().getName().toLowerCase())) { player.sendMessage(configManager.getMessage("world-disabled")); return true; }

        Location pos1 = selectionManager.getPosition1(player);
        if (pos1 == null) { player.sendMessage(configManager.getMessage("select-pos1")); return true; }
        Location pos2 = selectionManager.getPosition2(player);
        if (pos2 == null) { player.sendMessage(configManager.getMessage("select-pos2")); return true; }

        if (args.length == 0) { player.sendMessage(configManager.getMessage("replace-first-block-specify")); return true; }
        if (args.length == 1) {
            try {
                Material.valueOf(args[0].toUpperCase());
                player.sendMessage(configManager.getMessage("replace-second-block-specify").replaceText(config -> config.matchLiteral("%old_block%").replacement(args[0])));
            } catch (IllegalArgumentException e) {
                player.sendMessage(configManager.getMessage("invalid-block").replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
            }
            return true;
        }
        if (args.length > 2) { player.sendMessage(configManager.getMessage("invalid-usage-replace")); return true; }

        Material fromBlock;
        Material toBlock;
        try {
            fromBlock = Material.valueOf(args[0].toUpperCase());
            toBlock = Material.valueOf(args[1].toUpperCase());
            if (!fromBlock.isBlock() || !toBlock.isBlock()) {
                player.sendMessage(configManager.getMessage("invalid-block").replaceText(config -> config.matchLiteral("%block%").replacement(!fromBlock.isBlock() ? args[0] : args[1])));
                return true;
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-block").replaceText(config -> config.matchLiteral("%block%").replacement("UNKNOWN")));
            return true;
        }

        if (configManager.getBlockedMaterials().contains(toBlock)) { player.sendMessage(configManager.getMessage("blacklisted-block")); return true; }
        if (!player.getInventory().contains(toBlock)) {
            player.sendMessage(configManager.getMessage("inventory-empty").replaceText(config -> config.matchLiteral("%block%").replacement(toBlock.name())));
            return true;
        }

        List<Block> blocksToChange = new ArrayList<>();
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
                    }
                }
            }
        }
        if (blocksToChange.isEmpty()){ player.sendMessage(Component.text("§cNo '" + fromBlock.name() + "' blocks found in the selected area.")); return true; }
        long volume = blocksToChange.size();
        if (!player.hasPermission("leafwe.bypass.limit") && volume > configManager.getMaxVolume()) {
            player.sendMessage(configManager.getMessage("volume-limit-exceeded").replaceText(config -> config.matchLiteral("%limit%").replacement(String.valueOf(configManager.getMaxVolume()))));
            return true;
        }

        Runnable executionTask = () -> {
            Map<Location, BlockData> undoData = new HashMap<>();
            for (Block block : blocksToChange) { undoData.put(block.getLocation(), block.getBlockData()); }
            undoManager.addHistory(player, undoData);
            player.sendMessage(configManager.getMessage("process-starting"));
            ReplaceTask task = new ReplaceTask(player, blocksToChange, toBlock, configManager, selectionVisualizer, taskManager);
            task.runTaskTimer(plugin, 2L, configManager.getSpeed());
            taskManager.startTask(player, task);
        };

        int confirmationLimit = configManager.getConfirmationLimit();
        if (confirmationLimit > 0 && volume > confirmationLimit) {
            if (pendingCommandManager.hasPending(player)) { player.sendMessage(configManager.getMessage("confirmation-pending")); return true; }
            pendingCommandManager.setPending(player, executionTask);
            player.sendMessage(configManager.getMessage("confirmation-required").replaceText(config -> config.matchLiteral("%total%").replacement(String.valueOf(volume))));
        } else {
            executionTask.run();
        }
        return true;
    }
}