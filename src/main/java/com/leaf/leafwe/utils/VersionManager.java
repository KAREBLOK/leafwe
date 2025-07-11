package com.leaf.leafwe.utils;

import com.leaf.leafwe.LeafWE;

public class VersionManager {

    private final LeafWE plugin;
    private final String version;
    private final String name;
    private final String description;
    private final boolean isSupported;

    public VersionManager(LeafWE plugin) {
        this.plugin = plugin;
        this.version = plugin.getDescription().getVersion();
        this.name = plugin.getDescription().getName();
        this.description = plugin.getDescription().getDescription();
        this.isSupported = checkServerCompatibility();
    }

    public String getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isServerSupported() {
        return isSupported;
    }

    public String getFullInfo() {
        return name + " v" + version;
    }

    public String getEnableMessage() {
        return name + " v" + version + " successfully enabled!";
    }

    public String getDisableMessage() {
        return name + " v" + version + " disabled successfully.";
    }

    public void logPluginInfo() {
        plugin.getLogger().info("=".repeat(50));
        plugin.getLogger().info(name + " v" + version);
        plugin.getLogger().info("Description: " + description);
        plugin.getLogger().info("Author(s): " + String.join(", ", plugin.getDescription().getAuthors()));
        plugin.getLogger().info("API Version: " + plugin.getDescription().getAPIVersion());
        plugin.getLogger().info("Server Support: " + (isSupported ? "✅ SUPPORTED" : "⚠️ UNSUPPORTED"));
        plugin.getLogger().info("=".repeat(50));
    }

    private boolean checkServerCompatibility() {
        try {
            String apiVersion = plugin.getDescription().getAPIVersion();
            if (apiVersion != null) {
                return apiVersion.startsWith("1.19") ||
                        apiVersion.startsWith("1.20") ||
                        apiVersion.startsWith("1.21");
            }

            String serverVersion = plugin.getServer().getBukkitVersion();
            return serverVersion.contains("1.19") ||
                    serverVersion.contains("1.20") ||
                    serverVersion.contains("1.21");
        } catch (Exception e) {
            return false;
        }
    }

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

    public String getSystemInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== System Information ===\n");
        sb.append("Plugin: ").append(getFullInfo()).append("\n");
        sb.append("Authors: ").append(String.join(", ", plugin.getDescription().getAuthors())).append("\n");
        sb.append("API Version: ").append(plugin.getDescription().getAPIVersion()).append("\n");
        sb.append("Server: ").append(plugin.getServer().getName()).append(" ").append(plugin.getServer().getVersion()).append("\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append("\n");
        sb.append("OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Memory: ").append(formatMemory(Runtime.getRuntime().maxMemory())).append(" max, ");
        sb.append(formatMemory(Runtime.getRuntime().totalMemory())).append(" allocated, ");
        sb.append(formatMemory(Runtime.getRuntime().freeMemory())).append(" free\n");
        sb.append("Compatibility: ").append(isSupported ? "✅ SUPPORTED" : "❌ UNSUPPORTED").append("\n");

        return sb.toString();
    }

    private String formatMemory(long bytes) {
        long mb = bytes / (1024 * 1024);
        return mb + "MB";
    }
}