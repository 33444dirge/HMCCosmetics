package com.hibiscusmc.hmccosmetics.config.section;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public class WardrobeLocation {

    @Getter @Setter
    private Location npcLocation;
    @Getter @Setter
    private Location viewerLocation;
    @Getter @Setter
    private Location leaveLocation;

    /**
     * This creates a WardrobeLocation object with the locations required for a wardrobe to work
     * @param npcLocation The location of the NPC
     * @param viewerLocation The location of the viewer
     * @param leaveLocation The location that the player will be teleported to when they leave the wardrobe if return-last-location in the config is false
     */
    public WardrobeLocation(@Nullable Location npcLocation, @Nullable Location viewerLocation, @Nullable Location leaveLocation) {
        this.npcLocation = npcLocation;
        this.viewerLocation = viewerLocation;
        this.leaveLocation = leaveLocation;
    }

    /**
     * Checks if the required locations are set
     * @return true if the NPC and viewer locations are not null, else false
     */
    public boolean hasAllLocations() {
        return npcLocation != null && viewerLocation != null;
    }
}
