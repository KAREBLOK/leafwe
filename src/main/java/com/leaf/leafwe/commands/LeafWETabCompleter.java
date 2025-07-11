package com.leaf.leafwe.commands;

import com.leaf.leafwe.ConfigManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LeafWETabCompleter implements TabCompleter {

    private final ConfigManager configManager;
    private final List<String> blockMaterials;

    public LeafWETabCompleter(ConfigManager configManager) {
        this.configManager = configManager;
        this.blockMaterials = initializeBlockMaterials();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "set":
            case "wall":
                return handleSingleBlockCommand(args, player);

            case "replace":
                return handleReplaceCommand(args, player);

            case "lwe":
                return handleLWECommand(args, player);

            default:
                return new ArrayList<>();
        }
    }

    private List<String> handleSingleBlockCommand(String[] args, Player player) {
        if (args.length == 1) {
            return filterSuggestions(getAvailableBlocks(player), args[0]);
        }
        return new ArrayList<>();
    }

    private List<String> handleReplaceCommand(String[] args, Player player) {
        if (args.length == 1) {
            return filterSuggestions(blockMaterials, args[0]);
        } else if (args.length == 2) {
            return filterSuggestions(getAvailableBlocks(player), args[1]);
        }
        return new ArrayList<>();
    }

    private List<String> handleLWECommand(String[] args, Player player) {
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "give", "undo", "confirm", "limits", "help");
            return filterSuggestions(subCommands, args[0]);
        } else if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return filterSuggestions(
                    player.getServer().getOnlinePlayers().stream()
                            .map(p -> p.getName())
                            .collect(Collectors.toList()),
                    args[1]
            );
        }
        return new ArrayList<>();
    }

    private List<String> getAvailableBlocks(Player player) {
        List<String> availableBlocks = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().isBlock() &&
                    !configManager.getBlockedMaterials().contains(item.getType())) {
                String materialName = item.getType().name().toLowerCase();
                if (!availableBlocks.contains(materialName)) {
                    availableBlocks.add(materialName);
                }
            }
        }

        if (availableBlocks.isEmpty()) {
            return blockMaterials.stream()
                    .filter(material -> {
                        try {
                            Material mat = Material.valueOf(material.toUpperCase());
                            return !configManager.getBlockedMaterials().contains(mat);
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }

        return availableBlocks;
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String lowerInput = input.toLowerCase();
        return suggestions.stream()
                .filter(suggestion -> suggestion.toLowerCase().startsWith(lowerInput))
                .sorted()
                .limit(20)
                .collect(Collectors.toList());
    }

    private List<String> initializeBlockMaterials() {
        return Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .filter(material -> !material.isAir())
                .map(material -> material.name().toLowerCase())
                .sorted()
                .collect(Collectors.toList());
    }
}