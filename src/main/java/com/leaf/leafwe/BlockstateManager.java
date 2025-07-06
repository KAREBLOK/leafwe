package com.leaf.leafwe;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlockstateManager {

    private final Map<UUID, BlockData> copiedBlockstates = new HashMap<>();

    public void setCopiedBlockstate(Player player, BlockData blockData) {
        copiedBlockstates.put(player.getUniqueId(), blockData);
    }

    public BlockData getCopiedBlockstate(Player player) {
        return copiedBlockstates.get(player.getUniqueId());
    }

    public void clearCopiedBlockstate(Player player) {
        copiedBlockstates.remove(player.getUniqueId());
    }
}