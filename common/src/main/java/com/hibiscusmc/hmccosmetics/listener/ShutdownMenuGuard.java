package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import dev.triumphteam.gui.guis.BaseGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HibiscusCommons owns this listener so it keeps protecting old menus while Folia's other
 * player regions finish closing them after HMCCosmetics has been disabled.
 */
public final class ShutdownMenuGuard implements Listener {
    private final HMCCosmeticsPlugin plugin;
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean trackingDone;

    public ShutdownMenuGuard(HMCCosmeticsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOldMenu(Inventory inventory) {
        return inventory.getHolder(false) instanceof BaseGui;
    }

    public void track(UUID playerId) {
        pendingPlayers.add(playerId);
    }

    public void resolved(UUID playerId) {
        pendingPlayers.remove(playerId);
        unregisterIfDone();
    }

    public void finishTracking() {
        trackingDone = true;
        unregisterIfDone();
    }

    private void unregisterIfDone() {
        if (trackingDone && pendingPlayers.isEmpty()) HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!plugin.isAcceptingWork() && isOldMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!plugin.isAcceptingWork() && isOldMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.isAcceptingWork() && isOldMenu(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!plugin.isAcceptingWork() && isOldMenu(event.getInventory())) {
            resolved(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.isAcceptingWork()) resolved(event.getPlayer().getUniqueId());
    }
}
