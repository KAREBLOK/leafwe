package com.leaf.leafwe.commands;

import com.leaf.leafwe.LeafWE;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final LeafWE plugin;
    private final LeafWETabCompleter tabCompleter;
    private final CommandExecutor setCommand;
    private final CommandExecutor wallCommand;
    private final CommandExecutor replaceCommand;
    private final CommandExecutor lweCommand;

    public CommandManager(LeafWE plugin) {
        this.plugin = plugin;
        this.tabCompleter = new LeafWETabCompleter(plugin.getConfigManager());

        this.setCommand = new CommandExecutorWrapper(new com.leaf.leafwe.commands.impl.SetCommandImpl(plugin));
        this.wallCommand = new com.leaf.leafwe.commands.impl.WallCommandImpl(
                plugin,
                plugin.getSelectionManager(),
                plugin.getConfigManager(),
                plugin.getUndoManager(),
                plugin.getPendingCommandManager(),
                plugin.getSelectionVisualizer(),
                plugin.getTaskManager(),
                plugin.getBlockstateManager(),
                plugin.getGuiManager()
        );
        this.replaceCommand = new com.leaf.leafwe.commands.impl.ReplaceCommandImpl(
                plugin,
                plugin.getSelectionManager(),
                plugin.getConfigManager(),
                plugin.getUndoManager(),
                plugin.getPendingCommandManager(),
                plugin.getSelectionVisualizer(),
                plugin.getTaskManager(),
                plugin.getBlockstateManager(),
                plugin.getGuiManager()
        );
        this.lweCommand = new com.leaf.leafwe.commands.impl.LWECommandImpl(
                plugin,
                plugin.getConfigManager(),
                plugin.getUndoManager(),
                plugin.getPendingCommandManager(),
                plugin.getBlockstateManager()
        );

        registerBukkitCommands();
    }

    private void registerBukkitCommands() {
        String[] commandNames = {"set", "wall", "replace", "lwe"};

        for (String commandName : commandNames) {
            org.bukkit.command.PluginCommand bukkitCommand = plugin.getCommand(commandName);
            if (bukkitCommand != null) {
                bukkitCommand.setExecutor(this);
                bukkitCommand.setTabCompleter(this);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase();

        try {
            switch (commandName) {
                case "set":
                    return setCommand.onCommand(sender, command, label, args);
                case "wall":
                    return wallCommand.onCommand(sender, command, label, args);
                case "replace":
                    return replaceCommand.onCommand(sender, command, label, args);
                case "lwe":
                    return lweCommand.onCommand(sender, command, label, args);
                default:
                    sender.sendMessage("§cUnknown command: " + commandName);
                    return true;
            }
        } catch (Exception e) {
            sender.sendMessage("§cAn error occurred while executing the command.");
            plugin.getLogger().severe("Error executing command '" + commandName + "': " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabCompleter.onTabComplete(sender, command, alias, args);
    }

    private static class CommandExecutorWrapper implements CommandExecutor {
        private final com.leaf.leafwe.commands.BaseCommand baseCommand;

        public CommandExecutorWrapper(com.leaf.leafwe.commands.BaseCommand baseCommand) {
            this.baseCommand = baseCommand;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return baseCommand.execute(sender, command, label, args);
        }
    }
}