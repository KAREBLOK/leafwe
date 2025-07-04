package com.leaf.leafwe;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UndoTask extends BukkitRunnable {
    private final List<Map.Entry<Location, BlockData>> changes;

    public UndoTask(Map<Location, BlockData> changeMap) {
        this.changes = new ArrayList<>(changeMap.entrySet());
    }

    @Override
    public void run() {
        if (changes.isEmpty()) {
            this.cancel();
            return;
        }
        int blocksToRestore = 0;
        while (blocksToRestore < 2000 && !changes.isEmpty()) {
            Map.Entry<Location, BlockData> entry = changes.remove(0);
            entry.getKey().getBlock().setBlockData(entry.getValue(), false);
            blocksToRestore++;
        }
    }
}