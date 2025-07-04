package com.leaf.leafwe;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private int blocksPlaced = 0;
    private ArmorStand worker = null;

    public BlockPlacerTask(Player player, List<Location> locations, Material material, ConfigManager configManager, SelectionVisualizer visualizer, TaskManager taskManager) {
        this.player = player;
        this.locationsToFill = locations;
        this.material = material;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
    }

    @Override
    public void run() {
        if (locationsToFill.isEmpty() || !player.getInventory().contains(material)) {
            if (worker != null) {
                worker.remove();
            }
            taskManager.finishTask(player);
            selectionVisualizer.playSuccessEffect(player);
            if (!locationsToFill.isEmpty()) {
                player.sendMessage(configManager.getMessage("inventory-ran-out").replaceText(config -> config.matchLiteral("%block%").replacement(material.name())));
                player.sendMessage(configManager.getMessage("process-incomplete").replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(locationsToFill.size()))));
            }
            player.sendMessage(configManager.getMessage("process-complete").replaceText(config -> config.matchLiteral("%placed%").replacement(String.valueOf(blocksPlaced))));
            this.cancel();
            return;
        }

        // İYİLEŞTİRME: get(0) yerine getFirst() kullanıldı.
        Location currentLocation = locationsToFill.getFirst();

        if (worker == null && configManager.isWorkerAnimationEnabled()) {
            spawnWorker(currentLocation);
        }

        if (worker != null) {
            Location workerLocation = currentLocation.clone().add(0.5, configManager.getWorkerYOffset(), 0.5);
            worker.teleport(workerLocation);
            worker.swingMainHand();
        }

        // İYİLEŞTİRME: remove(0) yerine removeFirst() kullanıldı.
        locationsToFill.removeFirst();
        if (currentLocation.getBlock().getType() != material) {
            currentLocation.getBlock().setType(material);
            if (configManager.getPlacementParticle() != null) {
                player.getWorld().spawnParticle(configManager.getPlacementParticle(), currentLocation.clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            }
            player.getInventory().removeItem(new ItemStack(material, 1));
            blocksPlaced++;
        }
    }

    private void spawnWorker(Location location) {
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
            // DÜZELTME: Eski setCustomName yerine modern Component kullanımı
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

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        chestMeta.setColor(armorColor);
        chestplate.setItemMeta(chestMeta);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        LeatherArmorMeta legMeta = (LeatherArmorMeta) leggings.getItemMeta();
        legMeta.setColor(armorColor);
        leggings.setItemMeta(legMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
        LeatherArmorMeta bootMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootMeta.setColor(armorColor);
        boots.setItemMeta(bootMeta);

        worker.getEquipment().setChestplate(chestplate);
        worker.getEquipment().setLeggings(leggings);
        worker.getEquipment().setBoots(boots);
    }
}