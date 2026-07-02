package com.hibiscusmc.hmccosmetics.util.search;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PlayerSearchEngine implements Listener {

    @Getter
    private final HMCCosmeticsPlugin instance;

    public PlayerSearchEngine(@NotNull HMCCosmeticsPlugin plugin) {
        this.instance = plugin;
    }

    public List<Player> getPlayersInRange(Location location, double range) {
        return List.of();
    }

    public void handlePlayerPosition(Player player) {
        // Override
    }
}
