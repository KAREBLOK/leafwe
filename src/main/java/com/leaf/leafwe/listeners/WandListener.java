package com.leaf.leafwe.listeners;

import com.leaf.leafwe.tasks.*;

import com.leaf.leafwe.gui.*;

import com.leaf.leafwe.managers.*;

import com.leaf.leafwe.LeafWE;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class WandListener implements Listener {
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final BlockstateManager blockstateManager;
    private final ProtectionManager protectionManager;

    public WandListener(SelectionManager manager, ConfigManager configManager, SelectionVisualizer visualizer,
                        BlockstateManager blockstateManager, ProtectionManager protectionManager) {
        this.selectionManager = manager;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.blockstateManager = blockstateManager;
        this.protectionManager = protectionManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!isWand(item)) {
            return;
        }

        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        Action action = event.getAction();

        if (configManager.isPipetteToolEnabled() && player.isSneaking() &&
                (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)) {
            handlePipetteAction(player, clickedBlock);
            return;
        }

        if (clickedBlock != null && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
            Location loc = clickedBlock.getLocation();

            if (!protectionManager.canBuild(player, loc)) {
                player.sendMessage(configManager.getMessage("protection-no-permission"));
                return;
            }

            if (action == Action.LEFT_CLICK_BLOCK) {
                selectionManager.setPosition1(player, loc);
                player.sendMessage(configManager.getMessage("pos1-set")
                        .replaceText(config -> config.matchLiteral("%x%").replacement(String.valueOf(loc.getBlockX())))
                        .replaceText(config -> config.matchLiteral("%y%").replacement(String.valueOf(loc.getBlockY())))
                        .replaceText(config -> config.matchLiteral("%z%").replacement(String.valueOf(loc.getBlockZ()))));
            } else {
                selectionManager.setPosition2(player, loc);
                player.sendMessage(configManager.getMessage("pos2-set")
                        .replaceText(config -> config.matchLiteral("%x%").replacement(String.valueOf(loc.getBlockX())))
                        .replaceText(config -> config.matchLiteral("%y%").replacement(String.valueOf(loc.getBlockY())))
                        .replaceText(config -> config.matchLiteral("%z%").replacement(String.valueOf(loc.getBlockZ()))));
            }

            selectionVisualizer.start(player);
        }
    }

    private void handlePipetteAction(Player player, Block clickedBlock) {
        if (clickedBlock == null || clickedBlock.getType().isAir()) {
            if (blockstateManager.getCopiedBlockstate(player) != null) {
                blockstateManager.clearCopiedBlockstate(player);
                player.sendActionBar(configManager.getMessage("blockstate-cleared"));
                player.playSound(player.getLocation(), configManager.getPipetteCopySound(), 0.5f, 0.8f);
            }
            return;
        }

        try {
            blockstateManager.setCopiedBlockstate(player, clickedBlock.getBlockData());
            player.playSound(player.getLocation(), configManager.getPipetteCopySound(), 1.0f, 1.2f);

            String blockName = clickedBlock.getType().name().toLowerCase().replace('_', ' ');
            Component message = configManager.getMessage("blockstate-copied")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(blockName));
            player.sendActionBar(message);
        } catch (Exception e) {
            player.sendActionBar(Component.text("§cFailed to copy block data."));
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != configManager.getWandMaterial()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return false;
        }

        try {
            return Objects.equals(meta.displayName(), configManager.getWandName()) &&
                    Objects.equals(meta.lore(), configManager.getWandLore());
        } catch (Exception e) {
            return false;
        }
    }
}
