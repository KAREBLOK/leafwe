package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class ProgressBarManager {

    private static final int BAR_LENGTH = 20;
    private static final char COMPLETED_CHAR = '█';
    private static final char REMAINING_CHAR = '░';

    public static void showProgress(Player player, int current, int total, String operation) {
        if (player == null || !player.isOnline()) return;

        double percentage = total > 0 ? (double) current / total * 100 : 0;
        String progressBar = createProgressBar(percentage);

        String timeRemaining = calculateTimeRemaining(current, total);

        Component actionBarMessage = Component.text()
                .append(Component.text(progressBar, NamedTextColor.GREEN))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text(String.format("%.1f%%", percentage), NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(current + "/" + total, NamedTextColor.WHITE))
                .append(Component.text(") ", NamedTextColor.GRAY))
                .append(Component.text(operation, NamedTextColor.AQUA))
                .append(Component.text(" " + timeRemaining, NamedTextColor.DARK_GRAY))
                .build();

        player.sendActionBar(actionBarMessage);
    }

    private static String createProgressBar(double percentage) {
        int completed = (int) (BAR_LENGTH * percentage / 100);
        int remaining = BAR_LENGTH - completed;

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < completed; i++) {
            bar.append(COMPLETED_CHAR);
        }

        for (int i = 0; i < remaining; i++) {
            bar.append(REMAINING_CHAR);
        }

        return bar.toString();
    }

    private static String calculateTimeRemaining(int current, int total) {
        if (current <= 0 || total <= current) {
            return "";
        }

        int remainingBlocks = total - current;
        int estimatedMs = remainingBlocks * 50;

        if (estimatedMs < 1000) {
            return "(<1s)";
        } else if (estimatedMs < 60000) {
            int seconds = estimatedMs / 1000;
            return "(" + seconds + "s)";
        } else {
            int minutes = estimatedMs / 60000;
            int seconds = (estimatedMs % 60000) / 1000;
            if (seconds == 0) {
                return "(" + minutes + "m)";
            } else {
                return "(" + minutes + "m " + seconds + "s)";
            }
        }
    }

    public static void showCompletion(Player player, int totalBlocks, String operation) {
        if (player == null || !player.isOnline()) return;

        Component completionMessage = Component.text()
                .append(Component.text("✓ ", NamedTextColor.GREEN))
                .append(Component.text(operation + " completed! ", NamedTextColor.AQUA))
                .append(Component.text("(" + totalBlocks + " blocks)", NamedTextColor.GRAY))
                .build();

        player.sendActionBar(completionMessage);
    }

    public static void showCancellation(Player player, String operation) {
        if (player == null || !player.isOnline()) return;

        Component cancellationMessage = Component.text()
                .append(Component.text("✗ ", NamedTextColor.RED))
                .append(Component.text(operation + " cancelled", NamedTextColor.RED))
                .build();

        player.sendActionBar(cancellationMessage);
    }

    public static void showError(Player player, String operation, String error) {
        if (player == null || !player.isOnline()) return;

        Component errorMessage = Component.text()
                .append(Component.text("⚠ ", NamedTextColor.RED))
                .append(Component.text(operation + " error: ", NamedTextColor.RED))
                .append(Component.text(error, NamedTextColor.GRAY))
                .build();

        player.sendActionBar(errorMessage);
    }
}