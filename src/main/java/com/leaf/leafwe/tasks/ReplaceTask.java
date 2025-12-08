package com.leaf.leafwe.tasks;

import com.leaf.leafwe.gui.SelectionVisualizer;
import com.leaf.leafwe.managers.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

public class ReplaceTask extends BukkitRunnable {
    private final Player player;
    private final List<Location> locationsToChange;
    private final Material toMaterial;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private final ProtectionManager protectionManager;
    private final int totalBlocks;
    private int blocksReplaced = 0;
    private int blocksSkipped = 0;
    private ArmorStand worker = null;
    private boolean isRunning = true;
    private boolean isCompleted = false;

    public ReplaceTask(Player player, List<Location> locationsToChange, Material toMaterial,
                       ConfigManager configManager, SelectionVisualizer visualizer,
                       TaskManager taskManager, BlockstateManager blockstateManager,
                       ProtectionManager protectionManager) {
        this.player = player;
        this.locationsToChange = locationsToChange;
        this.toMaterial = toMaterial;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
        this.protectionManager = protectionManager;
        this.totalBlocks = locationsToChange.size();
    }

    @Override
    public void run() {
        if (!isRunning) return;

        // Kontrol: Envanterde güvenli materyal var mı?
        if (locationsToChange.isEmpty() || !hasSafeMaterial(player, toMaterial)) {
            finishTask();
            return;
        }

        Location currentLocation = locationsToChange.remove(0);

        if (protectionManager != null && !protectionManager.canBuild(player, currentLocation)) {
            blocksSkipped++;
            return;
        }

        Block currentBlock = currentLocation.getBlock();

        if (worker == null && configManager.isWorkerAnimationEnabled()) {
            spawnWorker(currentLocation);
        }

        if (worker != null && !worker.isDead()) {
            Location workerLocation = currentLocation.clone().add(0.5, configManager.getWorkerYOffset(), 0.5);
            worker.teleport(workerLocation);
            worker.swingMainHand();
        }

        BlockData copiedData = blockstateManager.getCopiedBlockstate(player);
        if (copiedData != null && copiedData.getMaterial() == toMaterial) {
            currentBlock.setBlockData(copiedData, false);
        } else {
            currentBlock.setType(toMaterial);
        }
        currentLocation.getWorld().playSound(currentLocation, org.bukkit.Sound.BLOCK_STONE_PLACE, 0.5f, 1.0f);

        if (configManager.getPlacementParticle() != null) {
            player.getWorld().spawnParticle(configManager.getPlacementParticle(),
                    currentLocation.clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
        }

        // YENİ: Güvenli silme
        removeSafeMaterial(player, toMaterial);
        blocksReplaced++;

        Component operationComp = configManager.getProgressOperationReplacing()
                .append(Component.text(" "))
                .append(Component.translatable(toMaterial.translationKey()));
        ProgressBarManager.showProgress(player, blocksReplaced + blocksSkipped, totalBlocks, operationComp);
    }

    // YENİ METOD
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

    // YENİ METOD
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

        if (!locationsToChange.isEmpty()) {
            player.sendMessage(configManager.getMessage("inventory-ran-out")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(toMaterial.name())));
            player.sendMessage(configManager.getMessage("process-incomplete")
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(locationsToChange.size()))));

            String operationText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationReplacing());
            String errorText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressErrorInventory());
            ProgressBarManager.showError(player, operationText, errorText);
        } else {
            String completionText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationBlockReplacement());
            ProgressBarManager.showCompletion(player, blocksReplaced, completionText);

            if (blocksSkipped > 0) {
                player.sendMessage("§e" + blocksSkipped + " blok korumalı alanda olduğu için değiştirilemedi.");
            }
        }

        player.sendMessage(configManager.getMessage("process-complete")
                .replaceText(config -> config.matchLiteral("%placed%").replacement(String.valueOf(blocksReplaced))));

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
            String cancellationText = PlainTextComponentSerializer.plainText().serialize(configManager.getProgressOperationBlockReplacement());
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

            worker.getEquipment().setItemInMainHand(new ItemStack(this.toMaterial, 1));

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
        } catch (Exception ignored) {
        }
    }
}