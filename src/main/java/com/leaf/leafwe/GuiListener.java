package com.leaf.leafwe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class GuiListener implements Listener {

    private final ConfigManager configManager;
    private final GuiManager guiManager;

    public GuiListener(ConfigManager configManager, GuiManager guiManager) {
        this.configManager = configManager;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder guiHolder)) return;

        Inventory topInventory = event.getView().getTopInventory();

        if (event.getClickedInventory() != topInventory) {
            if (guiHolder.getType() == GuiHolder.GuiType.REPLACE) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType().isBlock()) {
                    topInventory.setItem(15, new ItemStack(clickedItem.getType(), 1));
                }
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        switch (guiHolder.getType()) {
            case BLOCK_PICKER:
                handleBlockPickerClick(player, event.getCurrentItem(), guiHolder);
                break;
            case REPLACE:
                handleReplaceGuiClick(player, event.getCurrentItem(), event.getSlot(), topInventory);
                break;
        }
    }

    private void handleBlockPickerClick(Player player, ItemStack clickedItem, GuiHolder guiHolder) {
        if (clickedItem == null || clickedItem.getType().isAir()) return;
        Material selectedMaterial = clickedItem.getType();
        String command = guiHolder.getCommand();
        String firstArg = guiHolder.getFirstArg();
        player.closeInventory();
        String fullCommand = (firstArg != null) ? command + " " + firstArg + " " + selectedMaterial.name() : command + " " + selectedMaterial.name();
        player.performCommand(fullCommand);
    }

    private void handleReplaceGuiClick(Player player, ItemStack clickedItem, int slot, Inventory gui) {
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (slot == 22 && clickedItem.getType() == Material.LIME_WOOL) {
            ItemStack fromItem = gui.getItem(11);
            ItemStack toItem = gui.getItem(15);
            if (fromItem == null || toItem == null) {
                player.sendMessage(configManager.getMessage("replace-gui-missing-blocks"));
                return;
            }
            player.closeInventory();
            player.performCommand("replace " + fromItem.getType().name() + " " + toItem.getType().name());
        } else if (slot == 11 || slot == 15) {
            gui.setItem(slot, null);
        }
    }
}