package com.leaf.leafwe.commands.impl;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.commands.BaseCommand;
import com.leaf.leafwe.database.migration.MigrationManager;
import com.leaf.leafwe.registry.ManagerRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MigrationCommandImpl extends BaseCommand {

    public MigrationCommandImpl(LeafWE plugin) {
        super(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!performBasicChecks(sender)) {
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status":
                return handleStatus(sender);
            case "migrate":
                return handleMigrate(sender);
            case "rollback":
                return handleRollback(sender, args);
            case "history":
                return handleHistory(sender);
            case "help":
            default:
                showHelp(sender);
                return true;
        }
    }

    private boolean handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("Checking migration status...", NamedTextColor.YELLOW));

        MigrationManager migrationManager = ManagerRegistry.getInstance().get(MigrationManager.class);
        if (migrationManager == null) {
            sender.sendMessage(Component.text("Migration system not available!", NamedTextColor.RED));
            return true;
        }

        migrationManager.getStatus().thenAccept(status -> {
            sender.sendMessage(Component.text("=== Database Migration Status ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Current Version: " + status.currentVersion, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Latest Version: " + status.latestVersion, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Database Type: " + ManagerRegistry.database().getDatabaseType(), NamedTextColor.AQUA));

            if (status.upToDate) {
                sender.sendMessage(Component.text("✓ Database is up to date!", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("⚠ Database needs migration", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Pending versions: " + status.pendingVersions, NamedTextColor.YELLOW));
            }

            if (!status.appliedMigrations.isEmpty()) {
                sender.sendMessage(Component.text("\n--- Applied Migrations ---", NamedTextColor.GRAY));
                status.appliedMigrations.forEach(migration -> {
                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(migration.appliedAt),
                            ZoneId.systemDefault()
                    );
                    String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    sender.sendMessage(Component.text(
                            String.format("v%d: %s (%dms) - %s",
                                    migration.version,
                                    migration.name,
                                    migration.executionTime,
                                    formattedDate),
                            NamedTextColor.GRAY
                    ));
                });
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(Component.text("Error getting migration status: " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });

        return true;
    }

    private boolean handleMigrate(CommandSender sender) {
        sender.sendMessage(Component.text("Starting database migration...", NamedTextColor.YELLOW));

        MigrationManager migrationManager = ManagerRegistry.getInstance().get(MigrationManager.class);
        if (migrationManager == null) {
            sender.sendMessage(Component.text("Migration system not available!", NamedTextColor.RED));
            return true;
        }

        migrationManager.migrate().thenAccept(result -> {
            if (result.success) {
                sender.sendMessage(Component.text("✓ Migration completed successfully!", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Migrated from version " + result.fromVersion +
                        " to " + result.toVersion, NamedTextColor.GREEN));

                if (!result.executedMigrations.isEmpty()) {
                    sender.sendMessage(Component.text("\n--- Executed Migrations ---", NamedTextColor.GRAY));
                    result.executedMigrations.forEach(migration -> {
                        sender.sendMessage(Component.text(
                                String.format("v%d: %s (%dms)",
                                        migration.version,
                                        migration.success ? "SUCCESS" : "FAILED",
                                        migration.executionTime),
                                migration.success ? NamedTextColor.GREEN : NamedTextColor.RED
                        ));
                    });
                }
            } else {
                sender.sendMessage(Component.text("✗ Migration failed!", NamedTextColor.RED));
                sender.sendMessage(Component.text("Check server logs for details", NamedTextColor.YELLOW));
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(Component.text("Migration error: " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });

        return true;
    }

    private boolean handleRollback(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /lwe migration rollback <version>", NamedTextColor.RED));
            return true;
        }

        int targetVersion;
        try {
            targetVersion = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid version number: " + args[1], NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("⚠ WARNING: Rolling back database to version " + targetVersion, NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("This may result in data loss!", NamedTextColor.RED));
        sender.sendMessage(Component.text("Type '/lwe migration rollback " + targetVersion + " confirm' to proceed", NamedTextColor.YELLOW));

        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            return true;
        }

        sender.sendMessage(Component.text("Starting rollback to version " + targetVersion + "...", NamedTextColor.YELLOW));

        MigrationManager migrationManager = ManagerRegistry.getInstance().get(MigrationManager.class);
        if (migrationManager == null) {
            sender.sendMessage(Component.text("Migration system not available!", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Rollback functionality not fully implemented yet", NamedTextColor.YELLOW));
        return true;
    }

    private boolean handleHistory(CommandSender sender) {
        sender.sendMessage(Component.text("Fetching migration history...", NamedTextColor.YELLOW));

        MigrationManager migrationManager = ManagerRegistry.getInstance().get(MigrationManager.class);
        if (migrationManager == null) {
            sender.sendMessage(Component.text("Migration system not available!", NamedTextColor.RED));
            return true;
        }

        migrationManager.getStatus().thenAccept(status -> {
            sender.sendMessage(Component.text("=== Migration History ===", NamedTextColor.GOLD));

            if (status.appliedMigrations.isEmpty()) {
                sender.sendMessage(Component.text("No migrations have been applied yet", NamedTextColor.GRAY));
                return;
            }

            status.appliedMigrations.forEach(migration -> {
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(migration.appliedAt),
                        ZoneId.systemDefault()
                );
                String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                sender.sendMessage(Component.text("=".repeat(50), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Version: " + migration.version, NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Name: " + migration.name, NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Description: " + migration.description, NamedTextColor.WHITE));
                sender.sendMessage(Component.text("Applied: " + formattedDate, NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Execution Time: " + migration.executionTime + "ms", NamedTextColor.GRAY));
            });
        }).exceptionally(throwable -> {
            sender.sendMessage(Component.text("Error getting migration history: " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });

        return true;
    }

    @Override
    protected void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Migration Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lwe migration status", NamedTextColor.AQUA)
                .append(Component.text(" - Show migration status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe migration migrate", NamedTextColor.AQUA)
                .append(Component.text(" - Run pending migrations", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe migration rollback <version>", NamedTextColor.AQUA)
                .append(Component.text(" - Rollback to specific version", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/lwe migration history", NamedTextColor.AQUA)
                .append(Component.text(" - Show migration history", NamedTextColor.GRAY)));
    }

    @Override
    public String getDescription() {
        return "Manage database migrations";
    }

    @Override
    public String getUsage() {
        return "/lwe migration <status|migrate|rollback|history>";
    }

    @Override
    public String getPermission() {
        return "leafwe.admin.migration";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }
}