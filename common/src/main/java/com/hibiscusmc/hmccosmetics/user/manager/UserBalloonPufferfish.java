package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class UserBalloonPufferfish extends UserEntity {

    @Getter
    private final int pufferFishEntityId;
    @Getter
    private final UUID uuid;
    private boolean destroyed = false;

    public UserBalloonPufferfish(UUID owner, int pufferFishEntityId, UUID uuid) {
        super(owner);
        this.pufferFishEntityId = pufferFishEntityId;
        setIds(List.of(pufferFishEntityId));
        this.uuid = uuid;
    }

    public void hidePufferfish() {
        HMCCPacketManager.sendEntityDestroyPacket(pufferFishEntityId, getViewers());
        getViewers().clear();
    }

    public void spawnPufferfish(Location location, List<Player> sendTo) {
        HMCCPacketManager.spawnInvisibleEntity(pufferFishEntityId, EntityType.PUFFERFISH, location, uuid, sendTo);
    }

    public void destroyPufferfish() {
        HMCCPacketManager.sendEntityDestroyPacket(pufferFishEntityId, getViewers());
        getViewers().clear();
        destroyed = true;
    }

    public void destroyPufferfish(List<Player> viewers) {
        HMCCPacketManager.sendEntityDestroyPacket(pufferFishEntityId, viewers);
        //getViewers().clear();
        destroyed = true;
    }

    /** Snapshot packet viewers without consulting a player that may already be retired. */
    public List<Player> detachViewersForShutdown() {
        List<Player> previous = List.copyOf(getViewers());
        getViewers().clear();
        destroyed = true;
        return previous;
    }

    @Override
    public List<Player> refreshViewers(Location location) {
        if (destroyed) return List.of(); //Prevents refreshing a destroyed entity
        return super.refreshViewers(location);
    }
}
