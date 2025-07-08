package com.leaf.leafwe;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DailyLimitManager {

    private final LeafWE plugin;
    private final ConfigManager configManager;
    private File dataFile;
    private FileConfiguration dataConfig;

    private final ConcurrentHashMap<UUID, DailyUsage> usageCache = new ConcurrentHashMap<>();

    public DailyLimitManager(LeafWE plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        initializeDataFile();
        loadUsageData();
    }

    private void initializeDataFile() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        dataFile = new File(dataFolder, "daily-limits.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create daily-limits.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadUsageData() {
        String today = getCurrentDate();

        if (dataConfig.getConfigurationSection("limits") != null) {
            for (String uuidString : dataConfig.getConfigurationSection("limits").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String playerPath = "limits." + uuidString;

                    String date = dataConfig.getString(playerPath + ".date", today);
                    int blocksUsed = dataConfig.getInt(playerPath + ".blocks-used", 0);
                    int operationsUsed = dataConfig.getInt(playerPath + ".operations-used", 0);
                    String group = dataConfig.getString(playerPath + ".group", "default");

                    if (!date.equals(today)) {
                        blocksUsed = 0;
                        operationsUsed = 0;
                    }

                    usageCache.put(uuid, new DailyUsage(date, blocksUsed, operationsUsed, group));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading daily limit data for UUID: " + uuidString);
                }
            }
        }
    }

    /**
     * Player'ın işlem yapıp yapamayacağını kontrol eder
     */
    public boolean canPerformOperation(Player player, int blockCount) {
        if (!isDailyLimitsEnabled()) {
            return true;
        }

        String playerGroup = getPlayerGroup(player);
        DailyUsage usage = getOrCreateUsage(player, playerGroup);

        int maxBlocks = getGroupMaxBlocks(playerGroup);
        int maxOperations = getGroupMaxOperations(playerGroup);

        if (maxBlocks == -1 && maxOperations == -1) {
            return true;
        }

        if (maxBlocks != -1 && (usage.blocksUsed + blockCount) > maxBlocks) {
            return false;
        }

        if (maxOperations != -1 && (usage.operationsUsed + 1) > maxOperations) {
            return false;
        }

        return true;
    }

    /**
     * İşlem gerçekleştikten sonra kullanımı günceller
     */
    public void recordUsage(Player player, int blockCount) {
        if (!isDailyLimitsEnabled()) {
            return;
        }

        String playerGroup = getPlayerGroup(player);
        DailyUsage usage = getOrCreateUsage(player, playerGroup);

        usage.blocksUsed += blockCount;
        usage.operationsUsed += 1;
        usage.date = getCurrentDate();
        usage.group = playerGroup;

        usageCache.put(player.getUniqueId(), usage);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveUsageData);
    }

    /**
     * Player'ın günlük kullanım bilgilerini getirir
     */
    public DailyUsageInfo getUsageInfo(Player player) {
        if (!isDailyLimitsEnabled()) {
            return new DailyUsageInfo(-1, -1, 0, 0, "unlimited");
        }

        String playerGroup = getPlayerGroup(player);
        DailyUsage usage = getOrCreateUsage(player, playerGroup);

        int maxBlocks = getGroupMaxBlocks(playerGroup);
        int maxOperations = getGroupMaxOperations(playerGroup);

        return new DailyUsageInfo(maxBlocks, maxOperations, usage.blocksUsed, usage.operationsUsed, playerGroup);
    }

    private DailyUsage getOrCreateUsage(Player player, String group) {
        UUID uuid = player.getUniqueId();
        DailyUsage usage = usageCache.get(uuid);

        if (usage == null || !usage.date.equals(getCurrentDate())) {
            usage = new DailyUsage(getCurrentDate(), 0, 0, group);
            usageCache.put(uuid, usage);
        }

        usage.group = group;

        return usage;
    }

    private String getPlayerGroup(Player player) {
        for (String group : getConfiguredGroups()) {
            if (player.hasPermission("leafwe.limit.group." + group)) {
                return group;
            }
        }
        return "default";
    }

    private String[] getConfiguredGroups() {
        if (plugin.getConfig().getConfigurationSection("daily-limits.groups") != null) {
            return plugin.getConfig().getConfigurationSection("daily-limits.groups").getKeys(false).toArray(new String[0]);
        }
        return new String[]{"default"};
    }

    private boolean isDailyLimitsEnabled() {
        return plugin.getConfig().getBoolean("daily-limits.enabled", false);
    }

    private int getGroupMaxBlocks(String group) {
        return plugin.getConfig().getInt("daily-limits.groups." + group + ".max-blocks-per-day", 1000);
    }

    private int getGroupMaxOperations(String group) {
        return plugin.getConfig().getInt("daily-limits.groups." + group + ".max-operations-per-day", 10);
    }

    private String getCurrentDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private void saveUsageData() {
        try {
            dataConfig.set("limits", null);

            for (Map.Entry<UUID, DailyUsage> entry : usageCache.entrySet()) {
                String uuidString = entry.getKey().toString();
                DailyUsage usage = entry.getValue();

                String path = "limits." + uuidString;
                dataConfig.set(path + ".date", usage.date);
                dataConfig.set(path + ".blocks-used", usage.blocksUsed);
                dataConfig.set(path + ".operations-used", usage.operationsUsed);
                dataConfig.set(path + ".group", usage.group);
            }

            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save daily limits data: " + e.getMessage());
        }
    }

    /**
     * Günlük kullanım verilerini temizler (yeni gün için)
     */
    public void resetDailyData() {
        String today = getCurrentDate();

        for (DailyUsage usage : usageCache.values()) {
            if (!usage.date.equals(today)) {
                usage.date = today;
                usage.blocksUsed = 0;
                usage.operationsUsed = 0;
            }
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveUsageData);
    }

    /**
     * Plugin kapatılırken verileri kaydet
     */
    public void shutdown() {
        saveUsageData();
    }

    /**
     * Player'ın limitlerini sıfırlar (Admin komutu)
     */
    public void resetPlayerLimits(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        String group = getPlayerGroup(player);
        DailyUsage usage = new DailyUsage(getCurrentDate(), 0, 0, group);
        usageCache.put(uuid, usage);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveUsageData);
    }

    /**
     * Player'a bonus limit verir (Admin komutu)
     */
    public void setPlayerBonusLimits(Player player, int bonusBlocks) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        String group = getPlayerGroup(player);
        DailyUsage usage = getOrCreateUsage(player, group);

        usage.blocksUsed = Math.max(0, usage.blocksUsed - bonusBlocks);
        usageCache.put(uuid, usage);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveUsageData);
    }

    private static class DailyUsage {
        String date;
        int blocksUsed;
        int operationsUsed;
        String group;

        DailyUsage(String date, int blocksUsed, int operationsUsed, String group) {
            this.date = date;
            this.blocksUsed = blocksUsed;
            this.operationsUsed = operationsUsed;
            this.group = group;
        }
    }

    public static class DailyUsageInfo {
        public final int maxBlocks;
        public final int maxOperations;
        public final int usedBlocks;
        public final int usedOperations;
        public final String group;

        public DailyUsageInfo(int maxBlocks, int maxOperations, int usedBlocks, int usedOperations, String group) {
            this.maxBlocks = maxBlocks;
            this.maxOperations = maxOperations;
            this.usedBlocks = usedBlocks;
            this.usedOperations = usedOperations;
            this.group = group;
        }

        public int getRemainingBlocks() {
            return maxBlocks == -1 ? -1 : Math.max(0, maxBlocks - usedBlocks);
        }

        public int getRemainingOperations() {
            return maxOperations == -1 ? -1 : Math.max(0, maxOperations - usedOperations);
        }
    }
}