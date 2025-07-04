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
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;

    public LWECommand(LeafWE plugin, ConfigManager configManager, UndoManager undoManager, PendingCommandManager pendingCommandManager) {
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
                if (args.length != 2) { sender.sendMessage("§cUsage: /lwe give <player_name>"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return true; }
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
                    Component message = configManager.getMessage("wand-given-sender")
                            .replaceText(config -> config.matchLiteral("%player%").replacement(target.getName()));
                    sender.sendMessage(message);
                }
                break;
            case "undo":
                if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
                Player undoPlayer = (Player) sender;
                if (!undoPlayer.hasPermission("leafwe.undo")) { undoPlayer.sendMessage(configManager.getMessage("no-permission")); return true; }
                if (undoManager.undoLastChange(undoPlayer)) { undoPlayer.sendMessage(configManager.getMessage("undo-successful")); }
                else { undoPlayer.sendMessage(configManager.getMessage("no-undo")); }
                break;
            case "confirm":
                if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("players-only")); return true; }
                Player confirmPlayer = (Player) sender;
                if (!confirmPlayer.hasPermission("leafwe.confirm")) { confirmPlayer.sendMessage(configManager.getMessage("no-permission")); return true; }
                if (pendingCommandManager.confirm(confirmPlayer)) { confirmPlayer.sendMessage(configManager.getMessage("confirmation-successful")); }
                else { confirmPlayer.sendMessage(configManager.getMessage("no-pending-confirmation")); }
                break;
            default:
                sender.sendMessage(configManager.getMessage("help-message"));
                break;
        }
        return true;
    }
}