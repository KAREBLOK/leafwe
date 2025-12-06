package com.leaf.leafwe.listeners;

import com.leaf.leafwe.registry.ManagerRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldListener implements Listener {

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        String worldName = event.getWorld().getName();

        if (ManagerRegistry.getInstance().has(com.leaf.leafwe.managers.UndoManager.class)) {
            ManagerRegistry.undo().cleanupWorldHistory(worldName);
        }
    }
}