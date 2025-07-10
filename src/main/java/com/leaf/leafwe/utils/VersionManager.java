package com.leaf.leafwe.utils;

import com.leaf.leafwe.LeafWE;

public class VersionManager {

    private final LeafWE plugin;
    private final String version;
    private final String name;
    private final String description;

    public VersionManager(LeafWE plugin) {
        this.plugin = plugin;
        this.version = plugin.getDescription().getVersion();
        this.name = plugin.getDescription().getName();
        this.description = plugin.getDescription().getDescription();
    }

    /**
     * Plugin versiyonunu al
     */
    public String getVersion() {
        return version;
    }

    /**
     * Plugin adını al
     */
    public String getName() {
        return name;
    }

    /**
     * Plugin açıklamasını al
     */
    public String getDescription() {
        return description;
    }

    /**
     * Tam plugin bilgisini al
     */
    public String getFullInfo() {
        return name + " v" + version;
    }

    /**
     * Enable mesajını al
     */
    public String getEnableMessage() {
        return name + " v" + version + " successfully enabled!";
    }

    /**
     * Disable mesajını al
     */
    public String getDisableMessage() {
        return name + " v" + version + " disabled successfully.";
    }

    /**
     * Plugin bilgilerini logla
     */
    public void logPluginInfo() {
        plugin.getLogger().info("=".repeat(50));
        plugin.getLogger().info(name + " v" + version);
        plugin.getLogger().info("Description: " + description);
        plugin.getLogger().info("Author(s): " + String.join(", ", plugin.getDescription().getAuthors()));
        plugin.getLogger().info("API Version: " + plugin.getDescription().getAPIVersion());
        plugin.getLogger().info("=".repeat(50));
    }

    /**
     * Version karşılaştırması yap
     */
    public int compareVersion(String otherVersion) {
        String[] currentParts = version.split("\\.");
        String[] otherParts = otherVersion.split("\\.");

        int maxLength = Math.max(currentParts.length, otherParts.length);

        for (int i = 0; i < maxLength; i++) {
            int current = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
            int other = i < otherParts.length ? parseVersionPart(otherParts[i]) : 0;

            if (current < other) return -1;
            if (current > other) return 1;
        }

        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Debug bilgilerini al
     */
    public String getDebugInfo() {
        return String.format(
                "Plugin: %s v%s | Server: %s %s | Java: %s",
                name,
                version,
                plugin.getServer().getName(),
                plugin.getServer().getVersion(),
                System.getProperty("java.version")
        );
    }
}