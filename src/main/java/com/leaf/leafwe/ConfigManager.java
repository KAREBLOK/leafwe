package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager {
    private final LeafWE plugin;
    private FileConfiguration config;
    private FileConfiguration messages;

    public ConfigManager(LeafWE plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadMessages();
    }

    private void loadMessages() {
        String lang = config.getString("settings.language", "en").toLowerCase();
        File langFile = new File(plugin.getDataFolder(), "messages_" + lang + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("messages_" + lang + ".yml", false);
        }

        if (!langFile.exists()) {
            plugin.getLogger().warning("Unsupported language '" + lang + "' selected. Falling back to English.");
            lang = "en";
            langFile = new File(plugin.getDataFolder(), "messages_en.yml");
            if (!langFile.exists()) plugin.saveResource("messages_en.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(langFile);
        InputStream defaultStream = plugin.getResource("messages_" + lang + ".yml");
        if (defaultStream == null) defaultStream = plugin.getResource("messages_en.yml");

        if (defaultStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        }
    }

    public Component getMessage(String path) {
        String message = messages.getString("messages." + path, "<red>Message not found: " + path);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public List<Component> getMessageAsComponentList(String path) {
        List<String> stringList = messages.getStringList("replace-gui." + path);
        if (stringList == null || stringList.isEmpty()) {
            return Collections.singletonList(Component.text(""));
        }
        return stringList.stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                .collect(Collectors.toList());
    }

    public int getSpeed() {
        int speed = config.getInt("settings.speed", 2);
        return Math.max(1, Math.min(20, speed)); // Limit between 1-20
    }

    public boolean isVisualizerEnabled() {
        return config.getBoolean("settings.selection-visualizer", true);
    }

    public int getConfirmationLimit() {
        return Math.max(1, config.getInt("settings.confirmation-limit", 5000));
    }

    public int getMaxUndo() {
        return Math.max(1, config.getInt("settings.max-undo", 10));
    }

    public int getMaxVolume() {
        return Math.max(1, config.getInt("settings.max-volume", 50000));
    }

    public Particle getPlacementParticle() {
        try {
            String particleName = config.getString("settings.placement-particle", "WAX_ON").toUpperCase();
            if (particleName.equalsIgnoreCase("NONE")) return null;
            return Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle name: " + config.getString("settings.placement-particle") + ". Using default WAX_ON.");
            return Particle.WAX_ON;
        }
    }

    public boolean isPipetteToolEnabled() {
        return config.getBoolean("settings.pipette-tool.enabled", true);
    }

    public boolean isSuccessEffectEnabled() {
        return config.getBoolean("settings.success-effect.enabled", true);
    }

    public Sound getSuccessSound() {
        try {
            String soundName = config.getString("settings.success-effect.sound", "ENTITY_PLAYER_LEVELUP").toUpperCase();
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid success sound: " + config.getString("settings.success-effect.sound") + ". Using default.");
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    public Sound getPipetteCopySound() {
        try {
            String soundName = config.getString("settings.pipette-tool.copy-sound", "ENTITY_ITEM_PICKUP").toUpperCase();
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid pipette copy sound: " + config.getString("settings.pipette-tool.copy-sound") + ". Using default.");
            return Sound.ENTITY_ITEM_PICKUP;
        }
    }

    public Sound getPipettePasteSound() {
        try {
            String soundName = config.getString("settings.pipette-tool.paste-sound", "BLOCK_NOTE_BLOCK_PLING").toUpperCase();
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid pipette paste sound: " + config.getString("settings.pipette-tool.paste-sound") + ". Using default.");
            return Sound.BLOCK_NOTE_BLOCK_PLING;
        }
    }

    public Material getWandMaterial() {
        try {
            String materialName = config.getString("wand-tool.material", "BLAZE_ROD").toUpperCase();
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid wand material: " + config.getString("wand-tool.material") + ". Using default BLAZE_ROD.");
            return Material.BLAZE_ROD;
        }
    }

    public Component getWandName() {
        String name = config.getString("wand-tool.name", "&6Construction Rod");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(name);
    }

    public List<Component> getWandLore() {
        List<String> loreStrings = config.getStringList("wand-tool.lore");
        return loreStrings.stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                .collect(Collectors.toList());
    }

    public boolean isWorkerAnimationEnabled() {
        return config.getBoolean("settings.worker-animation.enabled", true);
    }

    public boolean shouldShowWorkerName() {
        return config.getBoolean("settings.worker-animation.show-name", true);
    }

    public String getWorkerNameTemplate() {
        return config.getString("settings.worker-animation.name-template", "&a[Worker] %player%");
    }

    public double getWorkerYOffset() {
        return config.getDouble("settings.worker-animation.y-offset", 0.5);
    }

    public Color getWorkerArmorColor() {
        String colorString = config.getString("settings.worker-animation.armor-color", "0,255,255");
        String[] rgb = colorString.replace(" ", "").split(",");

        if (rgb.length != 3) {
            plugin.getLogger().warning("Invalid armor color format: " + colorString + ". Using default aqua.");
            return Color.AQUA;
        }

        try {
            int r = Integer.parseInt(rgb[0]);
            int g = Integer.parseInt(rgb[1]);
            int b = Integer.parseInt(rgb[2]);

            // RGB değerlerini kontrol et
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                plugin.getLogger().warning("RGB values must be between 0-255. Using default aqua color.");
                return Color.AQUA;
            }

            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid RGB values in armor color: " + colorString + ". Using default aqua.");
            return Color.AQUA;
        }
    }

    public Component getBlockPickerGuiTitle() {
        String title = config.getString("block-selector-gui.title", "&1Select a Block");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(title);
    }

    public Component getReplaceGuiTitle() {
        String title = config.getString("replace-gui.title", "&1Block Replace Menu");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(title);
    }

    public Component getReplaceGuiConfirmButtonName() {
        String name = config.getString("replace-gui.confirm-button-name", "&a&lCONFIRM & REPLACE");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(name);
    }

    public List<Component> getReplaceGuiConfirmButtonLore() {
        List<String> loreStrings = config.getStringList("replace-gui.confirm-button-lore");
        return loreStrings.stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                .collect(Collectors.toList());
    }

    public Set<String> getDisabledWorlds() {
        return new HashSet<>(config.getStringList("disabled-worlds"));
    }

    public Set<Material> getBlockedMaterials() {
        return config.getStringList("blocked-materials").stream()
                .map(Material::matchMaterial)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // Progress Bar Messages
    public Component getProgressOperationPlacing() {
        return getMessage("progress-operation-placing");
    }

    public Component getProgressOperationReplacing() {
        return getMessage("progress-operation-replacing");
    }

    public Component getProgressOperationBlockPlacement() {
        return getMessage("progress-operation-block-placement");
    }

    public Component getProgressOperationBlockReplacement() {
        return getMessage("progress-operation-block-replacement");
    }

    public Component getProgressErrorInventory() {
        return getMessage("progress-error-inventory");
    }

    public Component getProgressCompleted() {
        return getMessage("progress-completed");
    }

    public Component getProgressCancelled() {
        return getMessage("progress-cancelled");
    }

    // Daily Limits
    public boolean isDailyLimitsEnabled() {
        return config.getBoolean("daily-limits.enabled", false);
    }

    public String getDailyLimitResetTime() {
        return config.getString("daily-limits.reset-time", "00:00");
    }

    // Daily Limit Messages
    public Component getDailyLimitBlocksExceeded() {
        return getMessage("daily-limit-blocks-exceeded");
    }

    public Component getDailyLimitOperationsExceeded() {
        return getMessage("daily-limit-operations-exceeded");
    }

    public Component getDailyLimitsDisabled() {
        return getMessage("daily-limits-disabled");
    }

    public Component getDailyLimitsHeader() {
        return getMessage("daily-limits-header");
    }

    public Component getDailyLimitsGroup() {
        return getMessage("daily-limits-group");
    }

    public Component getDailyLimitsBlocks() {
        return getMessage("daily-limits-blocks");
    }

    public Component getDailyLimitsOperations() {
        return getMessage("daily-limits-operations");
    }

    public Component getDailyLimitsBlocksUnlimited() {
        return getMessage("daily-limits-blocks-unlimited");
    }

    public Component getDailyLimitsOperationsUnlimited() {
        return getMessage("daily-limits-operations-unlimited");
    }

    // Task cancellation message
    public Component getTaskCancelledForUndo() {
        return getMessage("task-cancelled-for-undo");
    }

    // Config access method
    public FileConfiguration getConfig() {
        return config;
    }
}