package com.leaf.leafwe;

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

public class SetCommand implements CommandExecutor {
    private final LeafWE plugin;
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private final GuiManager guiManager;

    public SetCommand(LeafWE plugin, SelectionManager selManager, ConfigManager confManager, UndoManager undoManager, PendingCommandManager pendingManager, SelectionVisualizer visualizer, TaskManager taskManager, BlockstateManager blockstateManager, GuiManager guiManager) {
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
        if (!(sender instanceof Player player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
        if (taskManager.hasActiveTask(player)) { player.sendMessage(configManager.getMessage("task-already-running")); return true; }
        if (!player.hasPermission("leafwe.use")) { player.sendMessage(configManager.getMessage("no-permission")); return true; }
        if (configManager.getDisabledWorlds().contains(player.getWorld().getName().toLowerCase())) { player.sendMessage(configManager.getMessage("world-disabled")); return true; }

        Location pos1 = selectionManager.getPosition1(player);
        if (pos1 == null) { player.sendMessage(configManager.getMessage("select-pos1")); return true; }
        Location pos2 = selectionManager.getPosition2(player);
        if (pos2 == null) { player.sendMessage(configManager.getMessage("select-pos2")); return true; }

        if (args.length == 0) {
            guiManager.openBlockPickerGui(player, "set", null);
            return true;
        }

        Material blockType;
        try {
            blockType = Material.valueOf(args[0].toUpperCase());
            if (!blockType.isBlock()) {
                player.sendMessage(configManager.getMessage("invalid-block").replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
                return true;
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(configManager.getMessage("invalid-block").replaceText(config -> config.matchLiteral("%block%").replacement(args[0])));
            return true;
        }

        if (configManager.getBlockedMaterials().contains(blockType)) { player.sendMessage(configManager.getMessage("blacklisted-block")); return true; }
        if (!player.getInventory().contains(blockType)) {
            player.sendMessage(configManager.getMessage("inventory-empty").replaceText(config -> config.matchLiteral("%block%").replacement(blockType.name())));
            return true;
        }

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (!player.hasPermission("leafwe.bypass.limit") && volume > configManager.getMaxVolume()) {
            player.sendMessage(configManager.getMessage("volume-limit-exceeded").replaceText(config -> config.matchLiteral("%limit%").replacement(String.valueOf(configManager.getMaxVolume()))));
            return true;
        }

        List<Location> locationsToFill = new ArrayList<>();
        Map<Location, BlockData> undoData = new HashMap<>();
        World world = pos1.getWorld();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location loc = new Location(world, x, y, z);
                    locationsToFill.add(loc);
                    undoData.put(loc, loc.getBlock().getBlockData());
                }
            }
        }

        final Material finalBlockType = blockType;
        Runnable executionTask = () -> {
            undoManager.addHistory(player, undoData);
            guiManager.setLastReplacedFrom(player, finalBlockType);
            player.sendMessage(configManager.getMessage("process-starting"));
            BlockPlacerTask task = new BlockPlacerTask(player, locationsToFill, finalBlockType, configManager, selectionVisualizer, taskManager, blockstateManager);
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