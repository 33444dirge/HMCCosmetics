package com.hibiscusmc.hmccosmetics.util.search;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerSearchManager {

    @Getter
    private final HMCCosmeticsPlugin plugin;
    @Getter
    private final PlayerSearchEngine engine;

    public PlayerSearchManager(@NotNull SearchEngine engine, @NotNull HMCCosmeticsPlugin plugin) {
        this.plugin = plugin;

        // Choose Octree if set, otherwise just default to Bukkit
        switch (engine) {
            case OCTREE -> this.engine = new OctreePlayerSearchEngine(plugin);
            default -> this.engine = new BukkitPlayerSearchEngine(plugin);
        }
    }

    public @NotNull List<Player> getPlayersInRange(@NotNull Location location, double range) {
        return engine.getPlayersInRange(location, range);
    }

    public void handlePlayerPosition(@NotNull Player player) {
        engine.handlePlayerPosition(player);
    }

    public enum SearchEngine {
        BUKKIT,
        OCTREE
    }
}
