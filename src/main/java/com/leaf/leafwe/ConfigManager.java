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
        if (!langFile.exists()) plugin.saveResource("messages_" + lang + ".yml", false);
        if (!langFile.exists()) {
            lang = "en";
            langFile = new File(plugin.getDataFolder(), "messages_en.yml");
            if (!langFile.exists()) plugin.saveResource("messages_en.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(langFile);
        InputStream defaultStream = plugin.getResource("messages_" + lang + ".yml");
        if (defaultStream == null) defaultStream = plugin.getResource("messages_en.yml");
        if (defaultStream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        }
    }

    public Component getMessage(String path) {
        String message = messages.getString("messages." + path, "<red>Message not found: " + path);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public int getSpeed() { return config.getInt("settings.speed", 2); }
    public boolean isVisualizerEnabled() { return config.getBoolean("settings.selection-visualizer", true); }
    public int getConfirmationLimit() { return config.getInt("settings.confirmation-limit", 5000); }
    public int getMaxUndo() { return config.getInt("settings.max-undo", 10); }
    public int getMaxVolume() { return config.getInt("settings.max-volume", 50000); }
    public boolean isSuccessEffectEnabled() { return config.getBoolean("settings.success-effect.enabled", true); }
    public Set<String> getDisabledWorlds() { return new HashSet<>(config.getStringList("disabled-worlds")); }
    public Particle getPlacementParticle() {
        try {
            String particleName = config.getString("settings.placement-particle", "WAX_ON").toUpperCase();
            if (particleName.equalsIgnoreCase("NONE")) return null;
            return Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) { return Particle.WAX_ON; }
    }
    public Sound getSuccessSound() {
        try {
            return Sound.valueOf(config.getString("settings.success-effect.sound", "ENTITY_PLAYER_LEVELUP").toUpperCase());
        } catch (IllegalArgumentException e) { return Sound.ENTITY_PLAYER_LEVELUP; }
    }
    public boolean isWorkerAnimationEnabled() { return config.getBoolean("settings.worker-animation.enabled", true); }
    public boolean shouldShowWorkerName() { return config.getBoolean("settings.worker-animation.show-name", true); }
    public String getWorkerNameTemplate() { return config.getString("settings.worker-animation.name-template", "&a[Worker] %player%"); }
    public double getWorkerYOffset() { return config.getDouble("settings.worker-animation.y-offset", 0.5); }
    public Color getWorkerArmorColor() {
        String rgbString = config.getString("settings.worker-animation.armor-color", "0,255,255");
        String[] rgb = rgbString.replace(" ", "").split(",");
        try {
            int r = Integer.parseInt(rgb[0]);
            int g = Integer.parseInt(rgb[1]);
            int b = Integer.parseInt(rgb[2]);
            return Color.fromRGB(r, g, b);
        } catch (Exception e) { return Color.AQUA; }
    }
    public Material getWandMaterial() {
        try {
            return Material.valueOf(config.getString("wand-tool.material", "BLAZE_ROD").toUpperCase());
        } catch (IllegalArgumentException e) { return Material.BLAZE_ROD; }
    }
    public Component getWandName() {
        String name = config.getString("wand-tool.name", "&6Construction Rod");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(name);
    }
    public List<Component> getWandLore() {
        return config.getStringList("wand-tool.lore").stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                .collect(Collectors.toList());
    }
    public Set<Material> getBlockedMaterials() {
        return config.getStringList("blocked-materials").stream()
                .map(Material::matchMaterial)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }
}