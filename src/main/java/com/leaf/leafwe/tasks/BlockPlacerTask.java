package com.leaf.leafwe.tasks;

import com.leaf.leafwe.LeafWE;
import com.leaf.leafwe.gui.SelectionVisualizer;
import com.leaf.leafwe.managers.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.List;

public class BlockPlacerTask extends BukkitRunnable {

    private final LeafWE plugin;
    private final Player player;
    private final List<Location> locationsToFill;
    private final Material material;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private final ProtectionManager protectionManager;
    private final int totalBlocks;
    private int blocksPlaced = 0;
    private int blocksSkipped = 0;
    private ArmorStand worker = null;
    private boolean isRunning = true;
    private boolean isCompleted = false;
    private boolean limitsRecorded = false;

    public BlockPlacerTask(LeafWE plugin, Player player, List<Location> locations, Material material,
                           ConfigManager configManager, SelectionVisualizer visualizer,
                           TaskManager taskManager, BlockstateManager blockstateManager,
                           ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.player = player;
        this.locationsToFill = locations;
        this.material = material;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
        this.protectionManager = protectionManager;
        this.totalBlocks = locations.size();
    }

    @Override
    public void run() {
        if (!isRunning) return;

        if (locationsToFill.isEmpty() || !hasSafeMaterial(player, material)) {
            finishTask();
            return;
        }

        Location currentLocation = locationsToFill.remove(0);

        if (protectionManager != null && !protectionManager.canBuild(player, currentLocation)) {
            blocksSkipped++;
            return;
        }

        if (worker == null && configManager.isWorkerAnimationEnabled()) {
            spawnWorker(currentLocation);
        }

        if (worker != null && !worker.isDead()) {
            Location workerLocation = currentLocation.clone().add(0.5, configManager.getWorkerYOffset(), 0.5);
            worker.teleport(workerLocation);
            worker.swingMainHand();
        }

        if (currentLocation.getBlock().getType() != material) {
            BlockData copiedData = blockstateManager.getCopiedBlockstate(player);
            if (copiedData != null && copiedData.getMaterial() == material) {
                currentLocation.getBlock().setBlockData(copiedData, false);
            } else {
                currentLocation.getBlock().setType(material);
            }
            currentLocation.getWorld().playSound(currentLocation, org.bukkit.Sound.BLOCK_STONE_PLACE, 0.5f, 1.0f);

            if (configManager.getPlacementParticle() != null) {
                player.getWorld().spawnParticle(configManager.getPlacementParticle(), currentLocation.clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            }

            removeSafeMaterial(player, material);
            blocksPlaced++;
        }

        Component operationComp = configManager.getProgressOperationPlacing()
                .append(Component.text(" "))
                .append(Component.translatable(material.translationKey()));
        ProgressBarManager.showProgress(player, blocksPlaced + blocksSkipped, totalBlocks, operationComp);
    }

    private void removeSafeMaterial(Player player, Material material) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (item.hasItemMeta()) continue;

                int amount = item.getAmount();
                if (amount > 1) {
                    item.setAmount(amount - 1);
                } else {
                    player.getInventory().removeItem(item);
                }
                return;
            }
        }
    }

    private boolean hasSafeMaterial(Player player, Material material) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (!item.hasItemMeta()) return true;
            }
        }
        return false;
    }

    private void finishTask() {
        isRunning = false;
        isCompleted = true;
        cleanupWorker();
        taskManager.finishTask(player);
        selectionVisualizer.playSuccessEffect(player);

        if (!limitsRecorded && blocksPlaced > 0) {
            DailyLimitManager dailyLimitManager = plugin.getRegistry().get(DailyLimitManager.class);
            if (dailyLimitManager != null) {
                dailyLimitManager.recordUsage(player, blocksPlaced);
                limitsRecorded = true;
            }
        }

        if (!locationsToFill.isEmpty()) {
            player.sendMessage(configManager.getMessage("inventory-ran-out")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(material.name())));
            player.sendMessage(configManager.getMessage("process-incomplete")
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(locationsToFill.size()))));

            String operationText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationPlacing());
            String errorText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressErrorInventory());
            ProgressBarManager.showError(player, operationText, errorText);
        } else {
            String completionText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationBlockPlacement());
            ProgressBarManager.showCompletion(player, blocksPlaced, completionText);

            if (blocksSkipped > 0) {
                player.sendMessage("§e" + blocksSkipped + " blok korumalı alanda olduğu için yerleştirilemedi.");
            }
        }

        player.sendMessage(configManager.getMessage("process-complete")
                .replaceText(config -> config.matchLiteral("%placed%").replacement(String.valueOf(blocksPlaced))));

        this.cancel();
    }

    private void cleanupWorker() {
        if (worker != null && !worker.isDead()) {
            worker.remove();
            worker = null;
        }
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        isRunning = false;
        cleanupWorker();

        if (player.isOnline() && !isCompleted) {
            String cancellationText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationBlockPlacement());
            ProgressBarManager.showCancellation(player, cancellationText);
        }

        super.cancel();
    }

    private void spawnWorker(Location location) {
        try {
            Location spawnLocation = location.clone().add(0.5, configManager.getWorkerYOffset(), 0.5);
            this.worker = (ArmorStand) location.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);

            worker.setGravity(false);
            worker.setCanPickupItems(false);
            worker.setInvulnerable(true);
            worker.setArms(true);
            worker.setBasePlate(false);
            worker.setSmall(true);
            worker.setHeadPose(new EulerAngle(Math.toRadians(25), 0, 0));

            if (configManager.shouldShowWorkerName()) {
                String name = configManager.getWorkerNameTemplate().replace("%player%", player.getName());
                worker.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(name));
                worker.setCustomNameVisible(true);
            }

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD, 1);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(player);
                playerHead.setItemMeta(skullMeta);
                worker.getEquipment().setHelmet(playerHead);
            }

            worker.getEquipment().setItemInMainHand(new ItemStack(this.material, 1));

            Color armorColor = configManager.getWorkerArmorColor();
            setColoredArmorPiece(worker, Material.LEATHER_CHESTPLATE, armorColor, "chestplate");
            setColoredArmorPiece(worker, Material.LEATHER_LEGGINGS, armorColor, "leggings");
            setColoredArmorPiece(worker, Material.LEATHER_BOOTS, armorColor, "boots");

        } catch (Exception e) {
            worker = null;
        }
    }

    private void setColoredArmorPiece(ArmorStand armorStand, Material material, Color color, String piece) {
        try {
            ItemStack armor = new ItemStack(material, 1);
            LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
            if (meta != null) {
                meta.setColor(color);
                armor.setItemMeta(meta);

                switch (piece) {
                    case "chestplate":
                        armorStand.getEquipment().setChestplate(armor);
                        break;
                    case "leggings":
                        armorStand.getEquipment().setLeggings(armor);
                        break;
                    case "boots":
                        armorStand.getEquipment().setBoots(armor);
                        break;
                }
            }
        } catch (Exception ignored) { }
    }
}