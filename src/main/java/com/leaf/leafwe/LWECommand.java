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
    private final BlockstateManager blockstateManager;

    public LWECommand(LeafWE plugin, ConfigManager configManager, UndoManager undoManager,
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
            sender.sendMessage(configManager.getMessage("help-message"));
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
                return handleLimits(sender, args);
            case "resetlimits":
                return handleResetLimits(sender, args);
            case "givelimits":
                return handleGiveLimits(sender, args);
            case "help":
            default:
                sender.sendMessage(configManager.getMessage("help-message"));
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("leafwe.reload")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        try {
            configManager.loadConfig();
            sender.sendMessage(configManager.getMessage("reload-successful"));
        } catch (Exception e) {
            sender.sendMessage(Component.text("§cReload failed: " + e.getMessage()));
        }
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leafwe.give")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(configManager.getMessage("invalid-usage-give"));
            return true;
        }

        String targetName = args[1].trim();
        if (targetName.isEmpty()) {
            sender.sendMessage(configManager.getMessage("invalid-usage-give"));
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

    private boolean handleLimits(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (!sender.hasPermission("leafwe.limits.others")) {
                sender.sendMessage(configManager.getMessage("no-permission"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(configManager.getMessage("player-not-found")
                        .replaceText(config -> config.matchLiteral("%player%").replacement(args[1])));
                return true;
            }

            showLimitsInfo(sender, target);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("players-only"));
            return true;
        }

        showLimitsInfo(sender, player);
        return true;
    }

    private void showLimitsInfo(CommandSender sender, Player targetPlayer) {
        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();

        if (dailyLimitManager == null || !configManager.isDailyLimitsEnabled()) {
            sender.sendMessage(configManager.getDailyLimitsDisabled());
            return;
        }

        DailyLimitManager.DailyUsageInfo usageInfo = dailyLimitManager.getUsageInfo(targetPlayer);

        if (sender.equals(targetPlayer)) {
            sender.sendMessage(configManager.getDailyLimitsHeader());
        } else {
            sender.sendMessage(Component.text("=== Daily Limits for " + targetPlayer.getName() + " ===", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }

        sender.sendMessage(configManager.getDailyLimitsGroup()
                .replaceText(config -> config.matchLiteral("%group%").replacement(usageInfo.group)));

        if (usageInfo.maxBlocks == -1) {
            sender.sendMessage(configManager.getDailyLimitsBlocksUnlimited());
        } else {
            sender.sendMessage(configManager.getDailyLimitsBlocks()
                    .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedBlocks)))
                    .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxBlocks)))
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(usageInfo.getRemainingBlocks()))));
        }

        if (usageInfo.maxOperations == -1) {
            sender.sendMessage(configManager.getDailyLimitsOperationsUnlimited());
        } else {
            sender.sendMessage(configManager.getDailyLimitsOperations()
                    .replaceText(config -> config.matchLiteral("%used%").replacement(String.valueOf(usageInfo.usedOperations)))
                    .replaceText(config -> config.matchLiteral("%max%").replacement(String.valueOf(usageInfo.maxOperations)))
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(usageInfo.getRemainingOperations()))));
        }
    }

    private boolean handleResetLimits(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leafwe.resetlimits")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("§cUsage: /lwe resetlimits <player>", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found")
                    .replaceText(config -> config.matchLiteral("%player%").replacement(args[1])));
            return true;
        }

        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();
        if (dailyLimitManager != null) {
            dailyLimitManager.resetPlayerLimits(target);
            sender.sendMessage(Component.text("§aDaily limits reset for " + target.getName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));

            if (target.isOnline()) {
                target.sendMessage(Component.text("§aYour daily limits have been reset by an admin.", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
        }

        return true;
    }

    private boolean handleGiveLimits(CommandSender sender, String[] args) {
        if (!sender.hasPermission("leafwe.givelimits")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(Component.text("§cUsage: /lwe givelimits <player> <amount>", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(configManager.getMessage("player-not-found")
                    .replaceText(config -> config.matchLiteral("%player%").replacement(args[1])));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) {
                sender.sendMessage(Component.text("§cAmount must be positive or 0 to reset.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("§cInvalid number: " + args[2], net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        DailyLimitManager dailyLimitManager = plugin.getDailyLimitManager();
        if (dailyLimitManager != null) {
            dailyLimitManager.setPlayerBonusLimits(target, amount);
            sender.sendMessage(Component.text("§aGave " + amount + " bonus blocks to " + target.getName(), net.kyori.adventure.text.format.NamedTextColor.GREEN));

            if (target.isOnline()) {
                target.sendMessage(Component.text("§aYou received " + amount + " bonus blocks from an admin!", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
        }

        return true;
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