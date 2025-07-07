package com.leaf.leafwe;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private final Player player;
    private final List<Location> locationsToFill;
    private final Material material;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private final BlockstateManager blockstateManager;
    private int blocksPlaced = 0;
    private ArmorStand worker = null;
    private boolean isRunning = true;

    public BlockPlacerTask(Player player, List<Location> locations, Material material, ConfigManager configManager, SelectionVisualizer visualizer, TaskManager taskManager, BlockstateManager blockstateManager) {
        this.player = player;
        this.locationsToFill = locations;
        this.material = material;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
        this.blockstateManager = blockstateManager;
    }

    @Override
    public void run() {
        if (!isRunning) return;

        if (locationsToFill.isEmpty() || !player.getInventory().contains(material)) {
            finishTask();
            return;
        }

        Location currentLocation = locationsToFill.remove(0);

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

            if (configManager.getPlacementParticle() != null) {
                player.getWorld().spawnParticle(configManager.getPlacementParticle(), currentLocation.clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            }
            player.getInventory().removeItem(new ItemStack(material, 1));
            blocksPlaced++;
        }
    }

    private void finishTask() {
        isRunning = false;
        cleanupWorker();
        taskManager.finishTask(player);
        selectionVisualizer.playSuccessEffect(player);

        if (!locationsToFill.isEmpty()) {
            player.sendMessage(configManager.getMessage("inventory-ran-out")
                    .replaceText(config -> config.matchLiteral("%block%").replacement(material.name())));
            player.sendMessage(configManager.getMessage("process-incomplete")
                    .replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(locationsToFill.size()))));
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
        } catch (Exception e) {
        }
    }
}