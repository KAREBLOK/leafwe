package com.leaf.leafwe.commands;

import com.leaf.leafwe.LeafWE;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class BaseCommand {

    protected final LeafWE plugin;

    public BaseCommand(LeafWE plugin) {
        this.plugin = plugin;
    }

    public abstract boolean execute(CommandSender sender, Command command, String label, String[] args);

    public abstract String getDescription();

    public abstract String getUsage();

    public abstract String getPermission();

    public abstract boolean isPlayerOnly();

    protected boolean checkPlayer(CommandSender sender) {
        if (isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("players-only"));
            return false;
        }
        return true;
    }

    protected boolean checkPermission(CommandSender sender) {
        String permission = getPermission();
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return false;
        }
        return true;
    }

    protected boolean performBasicChecks(CommandSender sender) {
        return checkPlayer(sender) && checkPermission(sender);
    }

    protected Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }

    protected void showHelp(CommandSender sender) {
        sender.sendMessage("§7Usage: §f" + getUsage());
        sender.sendMessage("§7Description: §f" + getDescription());
        if (getPermission() != null && !getPermission().isEmpty()) {
            sender.sendMessage("§7Permission: §f" + getPermission());
        }
    }
}