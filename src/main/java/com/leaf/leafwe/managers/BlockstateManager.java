package com.leaf.leafwe.managers;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockstateManager {

    private final ConcurrentHashMap<UUID, BlockData> copiedBlockstates = new ConcurrentHashMap<>();

    public void setCopiedBlockstate(Player player, BlockData blockData) {
        if (player == null || blockData == null) return;
        copiedBlockstates.put(player.getUniqueId(), blockData);
    }

    public BlockData getCopiedBlockstate(Player player) {
        if (player == null) return null;
        return copiedBlockstates.get(player.getUniqueId());
    }

    public void clearCopiedBlockstate(Player player) {
        if (player == null) return;
        copiedBlockstates.remove(player.getUniqueId());
    }

    public void cleanupOfflinePlayer(UUID playerUUID) {
        if (playerUUID == null) return;
        copiedBlockstates.remove(playerUUID);
    }

    public void cleanupAll() {
        copiedBlockstates.clear();
    }
}
