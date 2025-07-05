package com.leaf.leafwe;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UndoTask extends BukkitRunnable {

    private final Player player;
    private final List<Map.Entry<Location, BlockData>> changes;
    private final ConfigManager configManager;
    private int refundedItems = 0;

    public UndoTask(Player player, Map<Location, BlockData> changeMap, ConfigManager configManager) {
        this.player = player;
        this.changes = new ArrayList<>(changeMap.entrySet());
        this.configManager = configManager;
    }

    @Override
    public void run() {
        if (changes.isEmpty()) {
            if (refundedItems > 0) {
                player.sendMessage(configManager.getMessage("undo-successful-with-refund")
                        .replaceText(config -> config.matchLiteral("%count%").replacement(String.valueOf(refundedItems))));
            } else {
                player.sendMessage(configManager.getMessage("undo-successful"));
            }
            this.cancel();
            return;
        }

        int blocksToRestore = 0;
        while (blocksToRestore < 2000 && !changes.isEmpty()) {
            Map.Entry<Location, BlockData> entry = changes.remove(0);
            Location location = entry.getKey();
            BlockData oldBlockData = entry.getValue();

            Block currentBlock = location.getBlock();
            Material newMaterial = currentBlock.getType();

            if (newMaterial != oldBlockData.getMaterial() && newMaterial != Material.AIR) {
                player.getInventory().addItem(new ItemStack(newMaterial, 1));
                refundedItems++;
            }

            currentBlock.setBlockData(oldBlockData, false);
            blocksToRestore++;
        }
    }
}