package com.leaf.leafwe.commands.impl;

import com.leaf.leafwe.*;
import com.leaf.leafwe.registry.ManagerRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LWECommandImpl implements CommandExecutor {
    private final LeafWE plugin;
    private final ConfigManager configManager;
    private final UndoManager undoManager;
    private final PendingCommandManager pendingCommandManager;
    private final BlockstateManager blockstateManager;

    public LWECommandImpl(LeafWE plugin, ConfigManager configManager, UndoManager undoManager,
                          PendingCommandManager pendingManager, BlockstateManager blockstateManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.undoManager = undoManager;
        this.pendingCommandManager = pendingManager;
        this.blockstateManager = blockstateManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMainHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);
            case "give":
                return handleGive(sender, args);
            case "undo":
                return handleUndo(sender);
            case "confirm":
                return handleConfirm(sender);
            case "limits":
                return handleLimits(sender);
            case "status":
                return handleStatus(sender);
            case "migration":
                return handleMigration(sender, args);
            case "debug":
                return handleDebug(sender);
            case "help":
            default:
                showMainHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("leafwe.reload")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        try {
            sender.sendMessage(Component.text("Reloading LeafWE configuration...", NamedTextColor.YELLOW));

            configManager.loadConfig();
            sender.sendMessage(Component.text("✅ Configuration reloaded successfully!", NamedTextColor.GREEN));

        } catch (Exception e) {
            sender.sendMessage(Component.text("❌ Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Config reload error: " + e.getMessage());
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leafwe.give")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /lwe give <player>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[1].trim();
        if (targetName.isEmpty()) {
            sender.sendMessage(Component.text("Invalid player name!", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(configManager.getMessage("player-not-found")
                    .replaceText(config -> config.matchLiteral("%player%").replacement(targetName)));
            return true;
        }

        ItemStack wand = createWand();
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), wand);
        } else {
            target.getInventory().addItem(wand);
        }

        target.sendMessage(configManager.getMessage("wand-given-receiver"));
        if (!sender.equals(target)) {
            sender.sendMessage(configManager.getMessage("wand-given-sender")
                    .replaceText(config -> config.matchLiteral("%player%").replacement(target.getName())));
        }
        return true;
    }

    private boolean handleUndo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("leafwe.undo")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        TaskManager taskManager = plugin.getTaskManager();
        if (taskManager != null && taskManager.hasActiveTask(player)) {
            taskManager.finishTask(player);
            player.sendMessage(configManager.getTaskCancelledForUndo());
            ProgressBarManager.showCancellation(player, "Operation cancelled for undo");
        }

        if (!undoManager.undoLastChange(player)) {
            player.sendMessage(configManager.getMessage("no-undo"));
        }
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("leafwe.confirm")) {
            player.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (pendingCommandManager.confirm(player)) {
            player.sendMessage(configManager.getMessage("confirmation-successful"));
        } else {
            player.sendMessage(configManager.getMessage("no-pending-confirmation"));
        }
        return true;
    }

    private boolean handleLimits(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("players-only"));
            return true;
        }

        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();

        if (dailyLimitManager == null || !configManager.isDailyLimitsEnabled()) {
            player.sendMessage(configManager.getDailyLimitsDisabled());
            return true;
        }

        DailyLimitManager.DailyUsageInfo usageInfo = dailyLimitManager.getUsageInfo(player);

        player.sendMessage(configManager.getDailyLimitsHeader());
        player.sendMessage(configManager.getDailyLimitsGroup()
                .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));

        if (usageInfo.maxBlocks == -1) {
            player.sendMessage(configManager.getDailyLimitsBlocksUnlimited());
        } else {
            player.sendMessage(configManager.getDailyLimitsBlocks()
                    .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedBlocks)))
                    .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxBlocks)))
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(usageInfo.getRemainingBlocks()))));
        }

        if (usageInfo.maxOperations == -1) {
            player.sendMessage(configManager.getDailyLimitsOperationsUnlimited());
        } else {
            player.sendMessage(configManager.getDailyLimitsOperations()
                    .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedOperations)))
                    .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxOperations)))
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(usageInfo.getRemainingOperations()))));
        }

        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("leafwe.admin.status")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        sender.sendMessage(Component.text("=== LeafWE System Status ===", NamedTextColor.GOLD));

        sender.sendMessage(Component.text("Version: " + plugin.getVersionManager().getFullInfo(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Server Support: " +
                        (plugin.getVersionManager().isServerSupported() ? "✅ SUPPORTED" : "⚠️ UNSUPPORTED"),
                plugin.getVersionManager().isServerSupported() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));


        ManagerRegistry registry = ManagerRegistry.getInstance();
        sender.sendMessage(Component.text("Core System: " +
                        (registry.isHealthy() ? "✅ HEALTHY" : "❌ UNHEALTHY"),
                registry.isHealthy() ? NamedTextColor.GREEN : NamedTextColor.RED));

        boolean dbHealthy = registry.isDatabaseHealthy();
        sender.sendMessage(Component.text("Database: " +
                        (dbHealthy ? "✅ CONNECTED" : "❌ DISCONNECTED"),
                dbHealthy ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (dbHealthy && ManagerRegistry.database() != null) {
            sender.sendMessage(Component.text("Database Type: " + ManagerRegistry.database().getDatabaseType(), NamedTextColor.GRAY));
        }

        TaskManager taskManager = plugin.getTaskManager();
        if (taskManager != null) {
            sender.sendMessage(Component.text("Active Tasks: " + taskManager.getActiveTaskCount(), NamedTextColor.GRAY));
        }

        return true;
    }

    private boolean handleMigration(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leafwe.admin.migration")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            showMigrationHelp(sender);
            return true;
        }

        sender.sendMessage(Component.text("Migration system available! Use:", NamedTextColor.YELLOW));
        showMigrationHelp(sender);
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("leafwe.admin.debug")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        sender.sendMessage(Component.text("=== LeafWE Debug Information ===", NamedTextColor.GOLD));

        sender.sendMessage(Component.text(ManagerRegistry.getInstance().getDebugInfo(), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("System Info:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(plugin.getVersionManager().getDebugInfo(), NamedTextColor.GRAY));

        return true;
    }

    private void showMainHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== LeafWE Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lwe give <player>", NamedTextColor.AQUA)
                .append(Component.text(" - Give construction wand", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe undo", NamedTextColor.AQUA)
                .append(Component.text(" - Undo last operation", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe confirm", NamedTextColor.AQUA)
                .append(Component.text(" - Confirm pending operation", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe limits", NamedTextColor.AQUA)
                .append(Component.text(" - Show daily usage limits", NamedTextColor.GRAY)));

        if (sender.hasPermission("leafwe.admin")) {
            sender.sendMessage(Component.text("", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("=== Admin Commands ===", NamedTextColor.RED));
            sender.sendMessage(Component.text("/lwe reload", NamedTextColor.AQUA)
                    .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/lwe status", NamedTextColor.AQUA)
                    .append(Component.text(" - Show system status", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/lwe migration <cmd>", NamedTextColor.AQUA)
                    .append(Component.text(" - Database migration commands", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("/lwe debug", NamedTextColor.AQUA)
                    .append(Component.text(" - Show debug information", NamedTextColor.GRAY)));
        }
    }

    private void showMigrationHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Migration Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lwe migration status", NamedTextColor.AQUA)
                .append(Component.text(" - Show migration status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe migration migrate", NamedTextColor.AQUA)
                .append(Component.text(" - Run pending migrations", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe migration history", NamedTextColor.AQUA)
                .append(Component.text(" - Show migration history", NamedTextColor.GRAY)));
    }

    private ItemStack createWand() {
        ItemStack wand = new ItemStack(configManager.getWandMaterial(), 1);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(configManager.getWandName());
            meta.lore(configManager.getWandLore());
            wand.setItemMeta(meta);
        }
        return wand;
    }
}