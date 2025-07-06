package com.leaf.leafwe;

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

    public WandListener(SelectionManager manager, ConfigManager configManager, SelectionVisualizer visualizer, BlockstateManager blockstateManager) {
        this.selectionManager = manager;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.blockstateManager = blockstateManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isWand(event.getItem())) {
            Block clickedBlock = event.getClickedBlock();

            if (configManager.isPipetteToolEnabled() && player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
                event.setCancelled(true);
                handlePipetteAction(player, clickedBlock);
                return;
            }

            if (clickedBlock != null) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true);

                    Location loc = clickedBlock.getLocation();
                    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                        selectionManager.setPosition1(player, loc);
                        player.sendMessage(configManager.getMessage("pos1-set"));
                    } else {
                        selectionManager.setPosition2(player, loc);
                        player.sendMessage(configManager.getMessage("pos2-set"));
                    }
                    selectionVisualizer.start(player);
                }
            }
        }
    }

    private void handlePipetteAction(Player player, Block clickedBlock) {
        if (clickedBlock == null || clickedBlock.getType().isAir()) {
            if (blockstateManager.getCopiedBlockstate(player) != null) {
                blockstateManager.clearCopiedBlockstate(player);
                player.sendActionBar(configManager.getMessage("blockstate-cleared"));
            }
            return;
        }

        blockstateManager.setCopiedBlockstate(player, clickedBlock.getBlockData());
        player.playSound(player.getLocation(), configManager.getPipetteCopySound(), 1.0f, 1.2f);
        Component message = configManager.getMessage("blockstate-copied")
                .replaceText(config -> config.matchLiteral("%block%").replacement(clickedBlock.getType().name().toLowerCase().replace('_', ' ')));
        player.sendActionBar(message);
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != configManager.getWandMaterial()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return false;
        return Objects.equals(meta.displayName(), configManager.getWandName()) &&
                Objects.equals(meta.lore(), configManager.getWandLore());
    }
}