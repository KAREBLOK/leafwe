package com.leaf.leafwe;

import org.bukkit.Location;
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

    public WandListener(SelectionManager manager, ConfigManager configManager, SelectionVisualizer visualizer) {
        this.selectionManager = manager;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isWand(event.getItem())) {
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
                Location loc = event.getClickedBlock().getLocation();
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    selectionManager.setPosition1(event.getPlayer(), loc);
                    event.getPlayer().sendMessage(configManager.getMessage("pos1-set"));
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    selectionManager.setPosition2(event.getPlayer(), loc);
                    event.getPlayer().sendMessage(configManager.getMessage("pos2-set"));
                }
                selectionVisualizer.start(event.getPlayer());
            }
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != configManager.getWandMaterial()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) return false;

        return Objects.equals(meta.displayName(), configManager.getWandName()) &&
                Objects.equals(meta.lore(), configManager.getWandLore());
    }
}