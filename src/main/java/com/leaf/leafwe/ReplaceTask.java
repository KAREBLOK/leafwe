package com.leaf.leafwe;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    private final List<Block> blocksToChange;
    private final Material toMaterial;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final TaskManager taskManager;
    private int blocksReplaced = 0;
    private ArmorStand worker = null;

    public ReplaceTask(Player player, List<Block> blocksToChange, Material toMaterial, ConfigManager configManager, SelectionVisualizer visualizer, TaskManager taskManager) {
        this.player = player;
        this.blocksToChange = blocksToChange;
        this.toMaterial = toMaterial;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.taskManager = taskManager;
    }

    @Override
    public void run() {
        if (blocksToChange.isEmpty() || !player.getInventory().contains(toMaterial)) {
            if (worker != null) {
                worker.remove();
            }
            taskManager.finishTask(player);
            selectionVisualizer.playSuccessEffect(player);
            if (!blocksToChange.isEmpty()) {
                player.sendMessage(configManager.getMessage("inventory-ran-out").replaceText(config -> config.matchLiteral("%block%").replacement(toMaterial.name())));
                player.sendMessage(configManager.getMessage("process-incomplete").replaceText(config -> config.matchLiteral("%remaining%").replacement(String.valueOf(blocksToChange.size()))));
            }
            player.sendMessage(configManager.getMessage("process-complete").replaceText(config -> config.matchLiteral("%placed%").replacement(String.valueOf(blocksReplaced))));
            this.cancel();
            return;
        }

        Block currentBlock = blocksToChange.getFirst();

        if (worker == null && configManager.isWorkerAnimationEnabled()) {
            spawnWorker(currentBlock.getLocation());
        }

        if (worker != null) {
            Location workerLocation = currentBlock.getLocation().clone().add(0.5, configManager.getWorkerYOffset(), 0.5);
            worker.teleport(workerLocation);
            worker.swingMainHand();
        }

        blocksToChange.removeFirst();
        currentBlock.setType(toMaterial);

        if (configManager.getPlacementParticle() != null) {
            player.getWorld().spawnParticle(configManager.getPlacementParticle(), currentBlock.getLocation().clone().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
        }
        player.getInventory().removeItem(new ItemStack(toMaterial, 1));
        blocksReplaced++;
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