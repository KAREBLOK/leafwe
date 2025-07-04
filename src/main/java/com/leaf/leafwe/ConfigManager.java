package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    // YENİ: Modern metin işlemleri için
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

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

    // DÜZENLENDİ: Artık Component döndürüyor
    public Component getMessage(String path) {
        String message = messages.getString("messages." + path, "<red>Message not found: " + path);
        // Eski '&' kodlarını modern MiniMessage formatına çeviriyoruz.
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    // ... (getSpeed, isVisualizerEnabled ve diğer basit get'ler aynı) ...
    public int getSpeed() { return config.getInt("settings.speed", 2); }
    public boolean isVisualizerEnabled() { return config.getBoolean("settings.selection-visualizer", true); }
    public int getConfirmationLimit() { return config.getInt("settings.confirmation-limit", 5000); }
    public int getMaxUndo() { return config.getInt("settings.max-undo", 10); }
    public int getMaxVolume() { return config.getInt("settings.max-volume", 50000); }
    public boolean isSuccessEffectEnabled() { return config.getBoolean("settings.success-effect.enabled", true); }
    public Set<String> getDisabledWorlds() { return new HashSet<>(config.getStringList("disabled-worlds")); }
    public Particle getPlacementParticle() { /*...*/ return null; }
    public Sound getSuccessSound() { /*...*/ return null; }
    public Material getWandMaterial() { /*...*/ return null; }

    // DÜZENLENDİ: Component döndüren yeni metodlar
    public Component getWandName() {
        String name = config.getString("wand-tool.name", "&6Construction Rod");
        return LegacyComponentSerializer.legacyAmpersand().deserialize(name);
    }

    public List<Component> getWandLore() {
        return config.getStringList("wand-tool.lore").stream()
                .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                .collect(Collectors.toList());
    }

    // ... (Diğer metodlar aynı kalabilir)
    public boolean isWorkerAnimationEnabled() { /*...*/ return true; }
    public boolean shouldShowWorkerName() { /*...*/ return true; }
    public String getWorkerNameTemplate() { /*...*/ return null; }
    public double getWorkerYOffset() { /*...*/ return 0.5; }
    public Color getWorkerArmorColor() { /*...*/ return null; }
    public Set<Material> getBlockedMaterials() { /*...*/ return null; }
}