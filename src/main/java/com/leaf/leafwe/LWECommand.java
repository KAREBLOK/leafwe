package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LWECommand implements CommandExecutor {

    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;

    public LWECommand(LeafWE plugin, ConfigManager configManager, UndoManager undoManager, PendingCommandManager pendingCommandManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingCommandManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(configManager.getMessage("help-message"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("leafwe.reload")) { sender.sendMessage(configManager.getMessage("no-permission")); return true; }
                configManager.loadConfig();
                sender.sendMessage(configManager.getMessage("reload-successful"));
                break;

            case "give":
                if (!sender.hasPermission("leafwe.give")) { sender.sendMessage(configManager.getMessage("no-permission")); return true; }
                if (args.length != 2) { sender.sendMessage(configManager.getMessage("invalid-usage-give")); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(configManager.getMessage("player-not-found").replaceText(config -> config.matchLiteral("%player%").replacement(args[1])));
                    return true;
                }
                ItemStack wand = new ItemStack(configManager.getWandMaterial(), 1);
                ItemMeta meta = wand.getItemMeta();
                if (meta != null) {
                    meta.displayName(configManager.getWandName());
                    meta.lore(configManager.getWandLore());
                    wand.setItemMeta(meta);
                }
                target.getInventory().addItem(wand);
                target.sendMessage(configManager.getMessage("wand-given-receiver"));
                if (!sender.equals(target)) {
                    sender.sendMessage(configManager.getMessage("wand-given-sender").replaceText(config -> config.matchLiteral("%player%").replacement(target.getName())));
                }
                break;

            case "undo":
                if (!(sender instanceof Player player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
                if (!player.hasPermission("leafwe.undo")) { player.sendMessage(configManager.getMessage("no-permission")); return true; }
                if (undoManager.undoLastChange(player)) { player.sendMessage(configManager.getMessage("undo-successful")); }
                else { player.sendMessage(configManager.getMessage("no-undo")); }
                break;

            case "confirm":
                if (!(sender instanceof Player player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
                if (!player.hasPermission("leafwe.confirm")) { player.sendMessage(configManager.getMessage("no-permission")); return true; }
                if (pendingCommandManager.confirm(player)) { player.sendMessage(configManager.getMessage("confirmation-successful")); }
                else { player.sendMessage(configManager.getMessage("no-pending-confirmation")); }
                break;

            case "help":
            default:
                sender.sendMessage(configManager.getMessage("help-message"));
                break;
        }
        return true;
    }
}