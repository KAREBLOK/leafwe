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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UndoTask extends BukkitRunnable {

    private final Player player;
    private final List<Map.Entry<Location, BlockState>> changes;
    private final ConfigManager configManager;
    private int refundedItems = 0;
    private int blocksRestored = 0;
    private boolean isRunning = true;

    private final Map<Material, Integer> dropBuffer = new HashMap<>();
    private Location lastDropLocation = null;

    private static final double MAX_DISTANCE_SQUARED = 50 * 50;

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
        Location playerLoc = player.getLocation();

        while (blocksToRestore < maxBlocksPerTick && !changes.isEmpty()) {
            try {
                Map.Entry<Location, BlockState> entry = changes.remove(0);
                Location location = entry.getKey();
                BlockState oldState = entry.getValue();

                if (location == null || oldState == null) continue;

                Block currentBlock = location.getBlock();
                Material currentMaterial = currentBlock.getType();

                if (currentMaterial != oldState.getType() && currentMaterial != Material.AIR) {
                    try {
                        boolean isNear = location.getWorld().equals(playerLoc.getWorld()) &&
                                location.distanceSquared(playerLoc) <= MAX_DISTANCE_SQUARED;

                        if (isNear) {
                            // Yakınsa doğrudan envantere ekleg
                            HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(new ItemStack(currentMaterial, 1));

                            if (!leftOver.isEmpty()) {
                                player.getWorld().dropItem(playerLoc, leftOver.get(0));
                            }
                        } else {
                            // Uzaktaysa envantere koymadan o bölgeye düşür.
                            dropBuffer.merge(currentMaterial, 1, Integer::sum);
                            lastDropLocation = location;
                        }

                        refundedItems++;
                    } catch (Exception ignored) { }
                }

                try {
                    oldState.update(true, false);
                    blocksRestored++;
                } catch (Exception ignored) { }

                blocksToRestore++;

            } catch (Exception ignored) { }
        }

        flushDropBuffer();
    }

    /**
     * Tampon bellekte biriken eşyaları 64'lük paketler halinde düşürür.
     * Bu sayede 1000 entity yerine 16 entity oluşur ve TPS korunur.
     */
    private void flushDropBuffer() {
        if (dropBuffer.isEmpty() || lastDropLocation == null) return;

        for (Map.Entry<Material, Integer> entry : dropBuffer.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            while (amount > 0) {
                int stackSize = Math.min(amount, 64);
                ItemStack stack = new ItemStack(material, stackSize);

                lastDropLocation.getWorld().dropItem(lastDropLocation, stack);

                amount -= stackSize;
            }
        }

        dropBuffer.clear();
    }

    private void finishTask() {
        isRunning = false;

        flushDropBuffer();

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
        flushDropBuffer();
        super.cancel();
    }
}