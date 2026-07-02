package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.hooks.worldguard.WGListener;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCScheduler;
import lombok.extern.slf4j.Slf4j;
import me.lojosho.hibiscuscommons.packets.data.PlayerPositionWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PlayerMovementListener implements Listener {
    private static final List<CosmeticSlot> MOVEMENT_COSMETICS = List.of(
        CosmeticSlot.BACKPACK,
        CosmeticSlot.BALLOON
    );
    private static final double TELEPORT_DISTANCE = 8;

    // Player Id -> Small Location
    private final Map<UUID, SmallLocation> locations = new ConcurrentHashMap<>();
    private static final Map<UUID, SmallLocation> packetLocations = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent ev) {
        final Player player = ev.getPlayer();

        final CosmeticUser user = CosmeticUsers.getUser(player);
        if(user == null) {
            return;
        }

        if(!updateDirtyLocation(ev.getPlayer(), ev.getTo())) {
            return;
        }

        for(final CosmeticSlot slot : MOVEMENT_COSMETICS) {
            user.updateMovementCosmetic(slot, ev.getFrom(), ev.getTo());
        }
    }

    public static void handlePositionPacket(Player player, PlayerPositionWrapper wrapper) {
        if (!wrapper.isHasPosition()) return;

        HMCCScheduler.runEntity(player, () -> {
            World world = player.getWorld();
            SmallLocation next = SmallLocation.fromPacket(wrapper, world.getUID());
            SmallLocation previous = packetLocations.put(player.getUniqueId(), next);
            if (previous == null || !previous.isTeleportLike(next)) return;

            HMCCosmeticsPlugin.getInstance().getPlayerSearchManager().handlePlayerPosition(player);
            Location location = new Location(world, next.x(), next.y(), next.z(), next.yaw(), 0);
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && Settings.isWorldGuardMoveCheck()) {
                WGListener.handleTeleport(player, location);
            }

            boolean unloadedChunk = !world.isChunkLoaded(next.chunkX(), next.chunkZ());
            PlayerGameListener.handleTeleport(player, location, unloadedChunk, !previous.world().equals(next.world()));
        });
    }

    private boolean updateDirtyLocation(final Player player, final Location nextLoc) {
        final SmallLocation previous = locations.computeIfAbsent(
            player.getUniqueId(),
            $ -> SmallLocation.fromLocation(nextLoc)
        );
        final SmallLocation next = SmallLocation.fromLocation(nextLoc);

        if(next.distanceTo(previous) > 0.25) {
            this.locations.put(player.getUniqueId(), next);
            return true;
        }

        if(next.yawDistanceTo(previous) > 5) {
            this.locations.put(player.getUniqueId(), next);
            return true;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(final PlayerChangedWorldEvent ev) {
        this.locations.remove(ev.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent ev) {
        locations.remove(ev.getPlayer().getUniqueId());
        packetLocations.remove(ev.getPlayer().getUniqueId());
    }

    record SmallLocation(
        double x,
        double y,
        double z,
        float yaw,
        UUID world
    ) {
        public double distanceTo(SmallLocation other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public float yawDistanceTo(SmallLocation other) {
            float diff = Math.abs(this.yaw - other.yaw) % 360;
            return diff > 180 ? 360 - diff : diff;
        }

        public boolean isTeleportLike(SmallLocation other) {
            return !world.equals(other.world) || distanceTo(other) > TELEPORT_DISTANCE;
        }

        public int chunkX() {
            return ((int) Math.floor(x)) >> 4;
        }

        public int chunkZ() {
            return ((int) Math.floor(z)) >> 4;
        }

        public static SmallLocation fromLocation(final Location location) {
            return new SmallLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getWorld().getUID());
        }

        public static SmallLocation fromPacket(final PlayerPositionWrapper wrapper, final UUID world) {
            return new SmallLocation(wrapper.getX(), wrapper.getY(), wrapper.getZ(), wrapper.getYaw(), world);
        }
    }
}
