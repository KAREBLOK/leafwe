package com.leaf.leafwe;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class WandListener implements Listener {
    private final SelectionManager selectionManager;
    private final ConfigManager configManager;
    private final SelectionVisualizer selectionVisualizer;
    private final BlockstateManager blockstateManager;
    private final ProtectionManager protectionManager;

    public WandListener(SelectionManager manager, ConfigManager configManager, SelectionVisualizer visualizer, BlockstateManager blockstateManager, ProtectionManager protectionManager) {
        this.selectionManager = manager;
        this.configManager = configManager;
        this.selectionVisualizer = visualizer;
        this.blockstateManager = blockstateManager;
        this.protectionManager = protectionManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Oyuncunun elindeki eşyanın bizim özel seçim çubuğumuz olup olmadığını kontrol et
        if (isWand(event.getItem())) {
            // Seçim çubuğu ile yapılan tüm varsayılan eylemleri iptal et (blok kırma, sandık açma vb.)
            event.setCancelled(true);

            Block clickedBlock = event.getClickedBlock();

            // Önce Pipet Aracı özelliğini kontrol et (eğilip sağ tıklama)
            if (configManager.isPipetteToolEnabled() && player.isSneaking() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) {
                handlePipetteAction(player, clickedBlock);
                return; // Pipet işlemi yapıldıysa, normal seçim işlemine devam etme
            }

            // Normal seçim işlemi (sol veya sağ tık)
            if (clickedBlock != null) {
                Location loc = clickedBlock.getLocation();

                // WorldGuard KORUMA KONTROLÜ
                // Seçim yapmadan hemen önce, oyuncunun bu bloğa dokunma izni var mı diye kontrol et
                if (!protectionManager.canBuild(player, loc)) {
                    player.sendMessage(configManager.getMessage("protection-no-permission"));
                    return; // İzin yoksa, seçim yaptırma ve işlemi durdur
                }

                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    selectionManager.setPosition1(player, loc);
                    player.sendMessage(configManager.getMessage("pos1-set"));
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    selectionManager.setPosition2(player, loc);
                    player.sendMessage(configManager.getMessage("pos2-set"));
                }

                // Her seçimden sonra partikül görselleştirmesini başlat/güncelle
                selectionVisualizer.start(player);
            }
        }
    }

    private void handlePipetteAction(Player player, Block clickedBlock) {
        // Eğilip boşluğa sağ tıklarsa kopyalanmış veriyi temizle
        if (clickedBlock == null || clickedBlock.getType().isAir()) {
            if (blockstateManager.getCopiedBlockstate(player) != null) {
                blockstateManager.clearCopiedBlockstate(player);
                player.sendActionBar(configManager.getMessage("blockstate-cleared"));
            }
            return;
        }

        // Bir bloğa tıklarsa, verisini kopyala
        blockstateManager.setCopiedBlockstate(player, clickedBlock.getBlockData());
        player.playSound(player.getLocation(), configManager.getPipetteCopySound(), 1.0f, 1.2f);
        Component message = configManager.getMessage("blockstate-copied")
                .replaceText(config -> config.matchLiteral("%block%").replacement(clickedBlock.getType().name().toLowerCase().replace('_', ' ')));
        player.sendActionBar(message);
    }

    private boolean isWand(ItemStack item) {
        if (item == null || item.getType() != configManager.getWandMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || !meta.hasLore()) {
            return false;
        }
        return Objects.equals(meta.displayName(), configManager.getWandName()) &&
                Objects.equals(meta.lore(), configManager.getWandLore());
    }
}