package com.leaf.leafwe.tasks;

import com.leaf.leafwe.managers.ConfigManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UndoTask extends BukkitRunnable {

    private final Player player;
    private final List<Map.Entry<Location, BlockState>> changes;
    private final ConfigManager configManager;
    private int refundedItems = 0;
    private int blocksRestored = 0;
    private boolean isRunning = true;

    public UndoTask(Player player, Map<Location, BlockState> changeMap, ConfigManager configManager) {
        this.player = player;
        this.changes = new ArrayList<>(changeMap.entrySet());
        this.configManager = configManager;
    }

    @Override
    public void run() {
        if (!isRunning || !player.isOnline()) {
            finishTask();
            return;
        }

        if (changes.isEmpty()) {
            finishTask();
            return;
        }

        int blocksToRestore = 0;
        int maxBlocksPerTick = 2000;

        while (blocksToRestore < maxBlocksPerTick && !changes.isEmpty()) {
            try {
                Map.Entry<Location, BlockState> entry = changes.remove(0);
                Location location = entry.getKey();
                BlockState oldState = entry.getValue();

                if (location == null || oldState == null) {
                    continue;
                }

                Block currentBlock = location.getBlock();
                Material currentMaterial = currentBlock.getType();

                if (currentMaterial != oldState.getType() && currentMaterial != Material.AIR) {
                    try {
                        ItemStack refundItem = new ItemStack(currentMaterial, 1);
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(refundItem);
                        } else {
                            player.getWorld().dropItem(player.getLocation(), refundItem);
                        }
                        refundedItems++;
                    } catch (Exception ignored) {
                    }
                }

                try {
                    oldState.update(true, false);
                    blocksRestored++;
                } catch (Exception e) {
                    System.err.println("Failed to restore block at " + location + ": " + e.getMessage());
                }

                blocksToRestore++;

            } catch (Exception e) {
                System.err.println("Error processing undo entry: " + e.getMessage());
            }
        }
    }

    private void finishTask() {
        isRunning = false;

        try {
            if (player.isOnline()) {
                if (refundedItems > 0) {
                    player.sendMessage(configManager.getMessage("undo-successful-with-refund")
                            .replaceText(config -> config.matchLiteral("%count%").replacement(String.valueOf(refundedItems)))
                            .replaceText(config -> config.matchLiteral("%blocks%").replacement(String.valueOf(blocksRestored))));
                } else {
                    player.sendMessage(configManager.getMessage("undo-successful")
                            .replaceText(config -> config.matchLiteral("%blocks%").replacement(String.valueOf(blocksRestored))));
                }
            }
        } catch (Exception ignored) {
        }

        this.cancel();
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        isRunning = false;
        super.cancel();
    }
}