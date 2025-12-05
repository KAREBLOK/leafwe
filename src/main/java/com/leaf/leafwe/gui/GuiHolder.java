package com.leaf.leafwe.gui;

import com.leaf.leafwe.managers.*;

import com.leaf.leafwe.LeafWE;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiHolder implements InventoryHolder {

    private final GuiType type;
    private final String command;
    private final String firstArg;

    public enum GuiType {
        BLOCK_PICKER,
        REPLACE
    }

    public GuiHolder(GuiType type, String command, String firstArg) {
        this.type = type;
        this.command = command;
        this.firstArg = firstArg;
    }

    public GuiHolder(GuiType type) {
        this.type = type;
        this.command = "replace";
        this.firstArg = null;
    }

    public GuiType getType() {
        return type;
    }

    public String getCommand() {
        return command;
    }

    public String getFirstArg() {
        return firstArg;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
