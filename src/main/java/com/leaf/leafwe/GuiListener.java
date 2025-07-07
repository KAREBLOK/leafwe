package com.leaf.leafwe;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder guiHolder)) return;

        Inventory topInventory = event.getView().getTopInventory();

        if (event.getClickedInventory() != topInventory) {
            if (guiHolder.getType() == GuiHolder.GuiType.REPLACE) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType().isBlock() &&
                        !configManager.getBlockedMaterials().contains(clickedItem.getType())) {
                    topInventory.setItem(15, new ItemStack(clickedItem.getType(), 1));
                }
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        try {
            switch (guiHolder.getType()) {
                case BLOCK_PICKER:
                    handleBlockPickerClick(player, event.getCurrentItem(), guiHolder);
                    break;
                case REPLACE:
                    handleReplaceGuiClick(player, event.getCurrentItem(), event.getSlot(), topInventory);
                    break;
            }
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("gui-error"));
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder)) return;

    }

    private void handleBlockPickerClick(Player player, ItemStack clickedItem, GuiHolder guiHolder) {
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        Material selectedMaterial = clickedItem.getType();

        if (configManager.getBlockedMaterials().contains(selectedMaterial)) {
            player.sendMessage(configManager.getMessage("blacklisted-block"));
            return;
        }

        String command = guiHolder.getCommand();
        String firstArg = guiHolder.getFirstArg();

        if (command == null || command.isEmpty()) {
            player.sendMessage(configManager.getMessage("gui-error"));
            player.closeInventory();
            return;
        }

        player.closeInventory();

        try {
            String fullCommand;
            if (firstArg != null && !firstArg.isEmpty()) {
                fullCommand = command + " " + firstArg + " " + selectedMaterial.name().toLowerCase();
            } else {
                fullCommand = command + " " + selectedMaterial.name().toLowerCase();
            }

            player.performCommand(fullCommand);
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("command-execution-error"));
        }
    }

    private void handleReplaceGuiClick(Player player, ItemStack clickedItem, int slot, Inventory gui) {
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        try {
            if (slot == 22 && clickedItem.getType() == Material.LIME_WOOL) {
                ItemStack fromItem = gui.getItem(11);
                ItemStack toItem = gui.getItem(15);

                if (fromItem == null || toItem == null ||
                        fromItem.getType().isAir() || toItem.getType().isAir()) {
                    player.sendMessage(configManager.getMessage("replace-gui-missing-blocks"));
                    return;
                }

                if (configManager.getBlockedMaterials().contains(toItem.getType())) {
                    player.sendMessage(configManager.getMessage("blacklisted-block"));
                    return;
                }

                if (!player.getInventory().contains(toItem.getType())) {
                    player.sendMessage(configManager.getMessage("inventory-empty")
                            .replaceText(config -> config.matchLiteral("%block%").replacement(toItem.getType().name())));
                    return;
                }

                player.closeInventory();

                String command = "replace " + fromItem.getType().name().toLowerCase() +
                        " " + toItem.getType().name().toLowerCase();
                player.performCommand(command);

            }
            else if (slot == 11 || slot == 15) {
                gui.setItem(slot, null);
            }
            else {
            }
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("gui-error"));
            player.closeInventory();
        }
    }
}