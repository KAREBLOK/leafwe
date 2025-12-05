package com.leaf.leafwe.utils; // Change this to your package name

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public class Metrics {

    private final JavaPlugin plugin;
    private final int pluginId;
    private final ScheduledExecutorService scheduler;
    private final static int BSTATS_VERSION = 1;
    private final static String URL = "https://bStats.org/api/send/v1/";
    private boolean enabled;
    private boolean firstSend = true;

    // We use an atomic integer to make sure that the monitor is only started once
    private final Object started = new Object();

    public Metrics(JavaPlugin plugin, int pluginId) {
        this.plugin = plugin;
        this.pluginId = pluginId;

        // Check if bStats is enabled and the server owner allows it
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = config.getBoolean("enabled", true);
        config.addDefault("enabled", true);
        config.addDefault("uuid", UUID.randomUUID().toString());
        config.addDefault("logFailedRequests", false);

        try {
            config.save(configFile);
        } catch (IOException e) {
            // Ignored
        }

        if (!enabled) {
            scheduler = null; // No need to create a scheduler if bStats is disabled
            return;
        }

        String pluginVersion = plugin.getDescription().getVersion();

        // Check if the server is in offline mode
        if (Bukkit.getServer().getOnlineMode()) {
            // Check if the server's version is at least 1.7
            if (VersionManager.compareVersion(Bukkit.getVersion(), "1.7.0") < 0) {
                enabled = false; // bStats doesn't support versions older than 1.7
            }
        }

        if (!enabled) {
            scheduler = null; // No need to create a scheduler if bStats is disabled
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1);
        // We start the monitor a bit later to give the server a chance to warm up
        scheduler.scheduleAtFixedRate(this::sendData, 5, 10, TimeUnit.MINUTES);
    }

    private void sendData() {
        if (!enabled) {
            return;
        }
        
        synchronized (started) {
            // We want to make sure that the monitor is only started once
            // This is especially important if you add metrics to your plugin through a custom class
            // that you didn't define in your plugin.yml.
            // If you did define it in your plugin.yml, you might need to call this only once.
            if (!firstSend) {
                return; // We already sent data once
            }
            firstSend = false;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // We use a separate UUID for each plugin,
                // so we can track multiple plugins from the same server
                UUID serverUUID = UUID.fromString(YamlConfiguration.loadConfiguration(new File(new File(plugin.getDataFolder().getParentFile(), "bStats"), "config.yml")).getString("uuid"));

                // We collect all the data and send it to bStats
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream dataWriter = new DataOutputStream(new GZIPOutputStream(byteStream));
                dataWriter.writeUTF(serverUUID.toString());
                dataWriter.writeInt(pluginId);
                dataWriter.writeUTF(plugin.getDescription().getVersion());
                dataWriter.writeInt(BSTATS_VERSION);

                // OS
                dataWriter.writeUTF(System.getProperty("os.name"));
                dataWriter.writeUTF(System.getProperty("os.arch"));
                dataWriter.writeUTF(System.getProperty("os.version"));
                dataWriter.writeInt(Runtime.getRuntime().availableProcessors());

                // Java
                dataWriter.writeUTF(System.getProperty("java.version"));
                dataWriter.writeUTF(System.getProperty("java.vendor"));

                // Server
                dataWriter.writeUTF(Bukkit.getVersion());
                dataWriter.writeUTF(Bukkit.getBukkitVersion());

                // Players
                dataWriter.writeInt(Bukkit.getOnlinePlayers().size());

                // Ping (only available for 1.8+)
                if (VersionManager.compareVersion(Bukkit.getVersion(), "1.8.0") >= 0) {
                    try {
                        Method getPing = Class.forName("org.bukkit.entity.Player").getDeclaredMethod("getPing");
                        int totalPing = 0;
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            totalPing += (int) getPing.invoke(player);
                        }
                        dataWriter.writeInt(totalPing / Bukkit.getOnlinePlayers().size());
                    } catch (ReflectiveOperationException e) {
                        dataWriter.writeInt(0); // If ping isn't available, set to 0
                    }
                } else {
                    dataWriter.writeInt(0); // For versions older than 1.8, set to 0
                }

                dataWriter.close();

                // Send the data to bStats
                sendPostRequest(byteStream.toByteArray());
            } catch (Exception e) {
                if (YamlConfiguration.loadConfiguration(new File(new File(plugin.getDataFolder().getParentFile(), "bStats"), "config.yml")).getBoolean("logFailedRequests", false)) {
                    plugin.getLogger().warning("Failed to send plugin metrics to bStats: " + e.getMessage());
                }
            }
        });
    }

    private void sendPostRequest(byte[] data) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-Encoding", "gzip"); // We compress the data
        connection.addRequestProperty("Content-Type", "application/json"); // We send data in json format
        connection.addRequestProperty("Accept", "application/json"); // We accept json response
        connection.addRequestProperty("Connection", "Keep-Alive"); // We want to keep the connection alive
        connection.setDoOutput(true);

        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();

        connection.getInputStream().close(); // We don't care about the response, just send the data
    }
    
    // A simple version manager helper for bStats
    private static class VersionManager {
        public static int compareVersion(String version1, String version2) {
            String[] parts1 = version1.split("\\.");
            String[] parts2 = version2.split("\\.");

            int length = Math.max(parts1.length, parts2.length);

            for (int i = 0; i < length; i++) {
                int v1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int v2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

                if (v1 < v2) {
                    return -1;
                }
                if (v1 > v2) {
                    return 1;
                }
            }
            return 0;
        }
    }
}
