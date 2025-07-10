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

    /**
     * Command'ı çalıştır
     */
    public abstract boolean execute(CommandSender sender, Command command, String label, String[] args);

    /**
     * Command'ın açıklaması
     */
    public abstract String getDescription();

    /**
     * Command'ın kullanımı
     */
    public abstract String getUsage();

    /**
     * Gerekli permission
     */
    public abstract String getPermission();

    /**
     * Sadece player'lar kullanabilir mi?
     */
    public abstract boolean isPlayerOnly();

    /**
     * Player kontrolü yap
     */
    protected boolean checkPlayer(CommandSender sender) {
        if (isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("players-only"));
            return false;
        }
        return true;
    }

    /**
     * Permission kontrolü yap
     */
    protected boolean checkPermission(CommandSender sender) {
        String permission = getPermission();
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
            return false;
        }
        return true;
    }

    /**
     * Temel kontrolleri yap
     */
    protected boolean performBasicChecks(CommandSender sender) {
        return checkPlayer(sender) && checkPermission(sender);
    }

    /**
     * Player cast yap (güvenli)
     */
    protected Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }

    /**
     * Help mesajı göster
     */
    protected void showHelp(CommandSender sender) {
        sender.sendMessage("§7Usage: §f" + getUsage());
        sender.sendMessage("§7Description: §f" + getDescription());
        if (getPermission() != null && !getPermission().isEmpty()) {
            sender.sendMessage("§7Permission: §f" + getPermission());
        }
    }
}