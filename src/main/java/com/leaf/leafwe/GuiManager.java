package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuiManager {

    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final Map<UUID, Material> lastReplacedFrom = new HashMap<>();

    public GuiManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void openBlockPickerGui(Player player, String command, String firstArg) {
        Component title = configManager.getBlockPickerGuiTitle();
        Inventory gui = Bukkit.createInventory(new GuiHolder(GuiHolder.GuiType.BLOCK_PICKER, command, firstArg), 36, title);
        Set<Material> blocked = configManager.getBlockedMaterials();
        Set<Material> addedMaterials = new HashSet<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().isBlock() &&
                    !blocked.contains(item.getType()) &&
                    !addedMaterials.contains(item.getType())) {
                gui.addItem(new ItemStack(item.getType(), 1));
                addedMaterials.add(item.getType());
            }
        }
        player.openInventory(gui);
        player.sendMessage(configManager.getMessage("gui-opened"));
    }

    public void openReplaceGui(Player player) {
        Component title = configManager.getReplaceGuiTitle();
        Inventory gui = Bukkit.createInventory(new GuiHolder(GuiHolder.GuiType.REPLACE), 27, title);

        ItemStack background = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = background.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            background.setItemMeta(meta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (i != 11 && i != 15 && i != 22) {
                gui.setItem(i, background);
            }
        }

        Material lastFrom = getLastReplacedFrom(player);
        if (lastFrom != null) {
            gui.setItem(11, new ItemStack(lastFrom));
        }

        ItemStack confirmButton = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmButton.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.displayName(configManager.getReplaceGuiConfirmButtonName());
            confirmMeta.lore(configManager.getReplaceGuiConfirmButtonLore());
            confirmButton.setItemMeta(confirmMeta);
        }
        gui.setItem(22, confirmButton);

        player.openInventory(gui);
        player.sendMessage(configManager.getMessage("replace-gui-opened"));
    }

    public void setLastReplacedFrom(Player player, Material material) {
        lastReplacedFrom.put(player.getUniqueId(), material);
    }

    public Material getLastReplacedFrom(Player player) {
        return lastReplacedFrom.get(player.getUniqueId());
    }

    public void cleanupPlayer(Player player) {
        lastReplacedFrom.remove(player.getUniqueId());
    }
}