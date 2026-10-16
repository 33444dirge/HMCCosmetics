package com.hibiscusmc.hmccosmetics;

import com.hibiscusmc.hmccosmetics.api.events.HMCCosmeticSetupEvent;
import com.hibiscusmc.hmccosmetics.command.CosmeticCommand;
import com.hibiscusmc.hmccosmetics.command.CosmeticCommandTabComplete;
import com.hibiscusmc.hmccosmetics.config.migration.WardrobeMigration;
import com.hibiscusmc.hmccosmetics.config.section.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenuProvider;
import com.hibiscusmc.hmccosmetics.gui.special.impl.HMCColorDyeMenu;
import com.hibiscusmc.hmccosmetics.gui.special.impl.InternalDyeMenu;
import com.hibiscusmc.hmccosmetics.hooks.items.HookHMCCosmetics;
import com.hibiscusmc.hmccosmetics.hooks.misc.HookBetterHud;
import com.hibiscusmc.hmccosmetics.hooks.misc.HookVulcan;
import com.hibiscusmc.hmccosmetics.hooks.placeholders.HMCPlaceholderExpansion;
import com.hibiscusmc.hmccosmetics.hooks.resourcepack.HookNexo;
import com.hibiscusmc.hmccosmetics.hooks.worldguard.WGHook;
import com.hibiscusmc.hmccosmetics.hooks.worldguard.WGListener;
import com.hibiscusmc.hmccosmetics.listener.*;
import com.hibiscusmc.hmccosmetics.packets.CosmeticPacketInterface;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCScheduler;
import com.hibiscusmc.hmccosmetics.util.ShutdownHandoff;
import com.hibiscusmc.hmccosmetics.util.search.OctreePlayerSearchEngine;
import com.hibiscusmc.hmccosmetics.util.search.PlayerSearchManager;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.TranslationUtil;
import lombok.Getter;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import me.lojosho.hibiscuscommons.HibiscusPlugin;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.config.serializer.LocationSerializer;
import me.lojosho.hibiscuscommons.hooks.Hook;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.shaded.configupdater.common.config.CommentedConfiguration;
import me.lojosho.shaded.configurate.CommentedConfigurationNode;
import me.lojosho.shaded.configurate.ConfigurateException;
import me.lojosho.shaded.configurate.ConfigurationOptions;
import me.lojosho.shaded.configurate.yaml.NodeStyle;
import me.lojosho.shaded.configurate.yaml.YamlConfigurationLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class HMCCosmeticsPlugin extends HibiscusPlugin {

    private static HMCCosmeticsPlugin instance;
    private static YamlConfigurationLoader configLoader;

    @Getter
    private PlayerSearchManager playerSearchManager;
    private HMCPlaceholderExpansion placeholderExpansion;
    private volatile boolean shuttingDown;
    private ShutdownMenuGuard shutdownMenuGuard;

    private final Hook hmcCosmeticsHook;
    private final Hook betterHudHook;
    private final Hook vulcanHook;

    public HMCCosmeticsPlugin() {
        super(13873, 1879);
        // Hooks.addHook replaces by id, so a PlugManX reload swaps these for the new classloader's copies
        hmcCosmeticsHook = new HookHMCCosmetics();
        betterHudHook = new HookBetterHud();
        vulcanHook = new HookVulcan();
    }

    @Override
    public void onStart() {
        // Plugin startup logic
        instance = this;

        // File setup
        saveDefaultConfig();
        if (!Path.of(getDataFolder().getPath(), "messages.yml").toFile().exists()) saveResource("messages.yml", false);
        if (!Path.of(getDataFolder().getPath(), "translations.yml").toFile().exists()) saveResource("translations.yml", false);
        if (!Path.of(getDataFolder().getPath() + "/cosmetics/").toFile().exists()) saveResource("cosmetics/defaultcosmetics.yml", false);
        if (!Path.of(getDataFolder().getPath() + "/menus/").toFile().exists()) {
            saveResource("menus/defaultmenu_hats.yml", false);
            saveResource("menus/defaultmenu_balloons.yml", false);
            saveResource("menus/defaultmenu_hands.yml", false);
            saveResource("menus/defaultmenu_backpacks.yml", false);
        }
        if (!Path.of(getDataFolder().getPath() + "/menus/functional/internal_dye_menu.yml").toFile().exists()) {
            saveResource("menus/functional/internal_dye_menu.yml", false);
        }
        if (!Path.of(getDataFolder().getPath() + "/wardrobes/").toFile().exists()) {
            saveResource("wardrobes/defaultwardrobe.yml", false);
            WardrobeMigration.migrate(this);
        }

        // Configuration Sync
        final File configFile = Path.of(getInstance().getDataFolder().getPath(), "config.yml").toFile();
        final File messageFile = Path.of(getInstance().getDataFolder().getPath(), "messages.yml").toFile();
        final File translationFile = Path.of(getInstance().getDataFolder().getPath(), "translations.yml").toFile();
        try {
            CommentedConfiguration.loadConfiguration(configFile).syncWithConfig(configFile, getInstance().getResource("config.yml"),
                    "database-settings", "wardrobe.wardrobes", "debug-mode", "wardrobe.viewer-location", "wardrobe.npc-location", "wardrobe.wardrobe-location", "wardrobe.leave-location");
            CommentedConfiguration.loadConfiguration(messageFile).syncWithConfig(messageFile, getInstance().getResource("messages.yml"));
            CommentedConfiguration.loadConfiguration(translationFile).syncWithConfig(translationFile, getInstance().getResource("translations.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Move this over to Hibiscus Commons later
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new HMCPlaceholderExpansion();
            placeholderExpansion.register();
        }

        // Setup
        setup();
        setPacketInterface(new CosmeticPacketInterface());

        // Search Service
        this.playerSearchManager = new PlayerSearchManager(Settings.getEngine(), this);

        // Commands
        PluginCommand cosmeticCommand = getServer().getPluginCommand("hmccosmetics");
        if (cosmeticCommand != null) {
            cosmeticCommand.setExecutor(new CosmeticCommand());
            cosmeticCommand.setTabCompleter(new CosmeticCommandTabComplete());
        } else {
            getLogger().severe("Unable to register commands! (Is another plugin interfering with HMCCosmetics commands?)");
        }

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerGameListener(), this);
        getServer().getPluginManager().registerEvents(new ServerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(), this);
        getServer().getPluginManager().registerEvents(this.playerSearchManager.getEngine(), this);

        // The listener is owned by Commons and stays active during Folia's cross-region cleanup.
        shutdownMenuGuard = new ShutdownMenuGuard(this);
        getServer().getPluginManager().registerEvents(shutdownMenuGuard, HibiscusCommonsPlugin.getInstance());

        if (HibiscusCommonsPlugin.isOnPaper()) {
            getServer().getPluginManager().registerEvents(new PaperPlayerGameListener(), this);
        }
        // Nexo resource-pack shading. Registered as a plain listener (not a HibiscusCommons Hook) so it
        // doesn't collide with HibiscusCommons' own "Nexo" item hook, and only when Nexo is present so
        // reflecting over its NexoPack event handlers doesn't hit missing classes.
        if (Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            getServer().getPluginManager().registerEvents(new HookNexo(), this);
        }
        // Database
        new Database();

        // HMCColor
        try {
            if (Settings.isPreferHMCColorDyeMenu() && Hooks.isActiveHook("HMCColor")) {
                DyeMenuProvider.setDyeMenuProvider(new HMCColorDyeMenu());
            } else {
                DyeMenuProvider.setDyeMenuProvider(new InternalDyeMenu());
            }
            // Reload method called in setup, do not need to call it here as all we do is set the provider.
        } catch (IllegalStateException e) {
            getLogger().warning("Unable to set a dye menu. There is likely another plugin registering another dye menu.");
        }

        // WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && Settings.isWorldGuardMoveCheck()) {
            if (WGHook.isHooked()) {
                getServer().getPluginManager().registerEvents(new WGListener(), this);
            } else {
                getLogger().warning("WorldGuard flags are unavailable (WorldGuard only accepts new flags while the server starts). Restart the server to enable the HMCCosmetics region flags.");
            }
        }

        // PlugManX (re)load: HibiscusCommons only registers hook listeners during its own startup, so hooks
        // created by this load were never picked up. Register them under this plugin so they go away on disable.
        for (Hook hook : ownHooks()) {
            if (hook.isDetected() || Bukkit.getPluginManager().getPlugin(hook.getId()) == null) continue;
            getServer().getPluginManager().registerEvents(hook, this);
            hook.setDetected(true);
            hook.load();
        }

        // PlayerJoinEvent has already passed for anyone online during a PlugManX (re)load.
        // On a normal startup nobody is online yet, so this does nothing.
        PlayerConnectionListener connectionListener = new PlayerConnectionListener();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerSearchManager.getEngine() instanceof OctreePlayerSearchEngine octree) octree.addPlayer(player);
            connectionListener.loadOnlinePlayer(player);
        }
    }

    private Hook[] ownHooks() {
        return new Hook[]{hmcCosmeticsHook, betterHudHook, vulcanHook};
    }

    public boolean isAcceptingWork() {
        return !shuttingDown && !isDisabled() && isEnabled();
    }

    @Override
    public void onLoad() {
        // WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            new WGHook();
        }
    }

    @Override
    public void onEnd() {
        List<CosmeticUser> users;
        synchronized (this) {
            shuttingDown = true;
            users = new ArrayList<>(CosmeticUsers.values());
        }
        ShutdownHandoff handoff = ShutdownHandoff.publish();

        Map<UUID, CosmeticUser> byId = new HashMap<>();
        Throwable saveFailure = null;
        for (CosmeticUser user : users) {
            byId.put(user.getUniqueId(), user);
            try {
                Database.save(user);
            } catch (RuntimeException ex) {
                getLogger().log(Level.SEVERE, "Unable to save cosmetics for " + user.getUniqueId() + " during shutdown", ex);
                if (saveFailure == null) saveFailure = ex;
                else saveFailure.addSuppressed(ex);
            } finally {
                CosmeticUsers.removeUserIfSame(user);
            }
        }
        CompletableFuture<Void> databaseClosed;
        try {
            databaseClosed = Database.close();
        } catch (RuntimeException ex) {
            getLogger().log(Level.SEVERE, "Unable to close the cosmetic database during shutdown", ex);
            databaseClosed = CompletableFuture.failedFuture(ex);
        }

        List<CompletableFuture<HMCCScheduler.CleanupResult>> cleanups = new ArrayList<>();
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            cleanups.add(cleanupPlayer(player, byId.remove(player.getUniqueId())));
        }
        for (CosmeticUser user : byId.values()) {
            Player player = user.getPlayer();
            if (player != null) {
                cleanups.add(cleanupPlayer(player, user));
            } else {
                try {
                    cleanups.add(observeCleanup(user.getUniqueId(), user.destroyForRetirement()
                        .thenApply(ignored -> HMCCScheduler.CleanupResult.RETIRED_CLEANED)));
                } catch (RuntimeException ex) {
                    getLogger().log(Level.SEVERE, "Unable to clean up disconnected cosmetic user " + user.getUniqueId(), ex);
                    cleanups.add(CompletableFuture.failedFuture(ex));
                }
            }
        }
        if (shutdownMenuGuard != null) shutdownMenuGuard.finishTracking();

        CompletableFuture<Void> cleanupsDone = CompletableFuture.allOf(cleanups.toArray(CompletableFuture[]::new))
            .thenRun(() -> {
                for (CompletableFuture<HMCCScheduler.CleanupResult> cleanup : cleanups) {
                    HMCCScheduler.CleanupResult result = cleanup.join();
                    if (result != HMCCScheduler.CleanupResult.COMPLETED
                        && result != HMCCScheduler.CleanupResult.RETIRED_CLEANED) {
                        throw new IllegalStateException("Shutdown cleanup did not finish: " + result);
                    }
                }
            });
        Throwable finalSaveFailure = saveFailure;
        CompletableFuture<Void> databaseWithinLimit = databaseClosed.thenRun(() -> {}).orTimeout(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(cleanupsDone, databaseWithinLimit).whenComplete((ignored, error) -> {
            Throwable failure = error;
            if (finalSaveFailure != null) {
                if (failure == null) failure = finalSaveFailure;
                else failure.addSuppressed(finalSaveFailure);
            }
            if (failure == null) handoff.succeed();
            else {
                handoff.fail(failure);
                getLogger().log(Level.SEVERE,
                    "Shutdown handoff failed; the next HMCCosmetics instance will not read stale cosmetic data", failure);
            }
        });

        if (placeholderExpansion != null && placeholderExpansion.isRegistered()) placeholderExpansion.unregister();
        placeholderExpansion = null;

        // On a normal startup HibiscusCommons registers these hooks under its own name, so disabling
        // HMCCosmetics doesn't remove them. Left behind, they'd keep running code from the unloaded jar.
        // Inactive also stops Hooks.getItem("HMCCosmetics:...") from calling into the unloaded jar until a reload replaces it
        for (Hook hook : ownHooks()) {
            HandlerList.unregisterAll(hook);
            hook.setDetected(false);
            hook.setActive(false);
            unregisterOwnHook(hook);
        }
    }

    private CompletableFuture<HMCCScheduler.CleanupResult> cleanupPlayer(Player player, CosmeticUser user) {
        UUID playerId = player.getUniqueId();
        if (shutdownMenuGuard != null) shutdownMenuGuard.track(playerId);
        CompletableFuture<Void> independentCleanup = new CompletableFuture<>();
        CompletableFuture<HMCCScheduler.CleanupResult> playerCleanup = HMCCScheduler.runEntityCleanup(player, () -> {
            if (player.isOnline() && shutdownMenuGuard != null
                && shutdownMenuGuard.isOldMenu(player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
            if (shutdownMenuGuard != null) shutdownMenuGuard.resolved(playerId);
            if (user == null) {
                independentCleanup.complete(null);
            } else {
                user.destroyForShutdown().whenComplete((ignored, error) -> {
                    if (error != null) independentCleanup.completeExceptionally(error);
                    else independentCleanup.complete(null);
                });
            }
        });
        playerCleanup.whenComplete((result, error) -> {
            if (user == null || (error == null && result != HMCCScheduler.CleanupResult.REJECTED)) return;
            try {
                user.destroyForRetirement().whenComplete((ignored, resourceError) -> {
                    if (resourceError != null) getLogger().log(Level.SEVERE,
                        "Unable to remove independent cosmetics for " + playerId, resourceError);
                });
            } catch (RuntimeException ex) {
                getLogger().log(Level.SEVERE, "Unable to schedule independent cosmetic cleanup for " + playerId, ex);
            }
        });
        CompletableFuture<HMCCScheduler.CleanupResult> cleanup = playerCleanup.thenCompose(result -> {
            if (result == HMCCScheduler.CleanupResult.COMPLETED) {
                return independentCleanup.thenApply(ignored -> HMCCScheduler.CleanupResult.COMPLETED);
            }
            if (result == HMCCScheduler.CleanupResult.RETIRED) {
                if (shutdownMenuGuard != null) shutdownMenuGuard.resolved(playerId);
                if (user == null) return CompletableFuture.completedFuture(HMCCScheduler.CleanupResult.RETIRED_CLEANED);
                return user.destroyForRetirement()
                    .thenApply(ignored -> HMCCScheduler.CleanupResult.RETIRED_CLEANED);
            }
            return CompletableFuture.completedFuture(result);
        });
        return observeCleanup(playerId, cleanup);
    }

    private CompletableFuture<HMCCScheduler.CleanupResult> observeCleanup(
        UUID playerId, CompletableFuture<HMCCScheduler.CleanupResult> cleanup) {
        CompletableFuture<HMCCScheduler.CleanupResult> observed = new CompletableFuture<>();
        cleanup.whenComplete((result, error) -> {
            if (error != null) observed.completeExceptionally(error);
            else observed.complete(result);
        });
        if (!observed.isDone()) {
            CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(
                () -> observed.complete(HMCCScheduler.CleanupResult.TIMED_OUT));
        }
        return observed.whenComplete((result, error) -> {
                if (error != null || result == HMCCScheduler.CleanupResult.REJECTED
                    || result == HMCCScheduler.CleanupResult.TIMED_OUT) {
                    getLogger().log(Level.SEVERE, "Unable to finish shutdown cleanup for " + playerId
                        + ": " + (error == null ? result : "exception"), error);
                }
            });
    }

    /** Commons 0.9.2 has no public removeHook API; remove only this exact registration. */
    private void unregisterOwnHook(Hook hook) {
        try {
            Field poolField = Hooks.class.getDeclaredField("HOOK_POOL");
            poolField.setAccessible(true);
            Object value = poolField.get(null);
            if (!(value instanceof Map<?, ?> pool)) throw new IllegalStateException("HOOK_POOL is not a Map");
            String key = hook.getId().toLowerCase(Locale.ROOT);
            synchronized (pool) {
                if (pool.get(key) == hook) pool.remove(key);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            getLogger().log(Level.WARNING, "Unable to unregister HibiscusCommons hook " + hook.getId(), ex);
        }
    }

    public static HMCCosmeticsPlugin getInstance() {
        return instance;
    }

    public static void setup() {
        getInstance().reloadConfig();

        // Configuration setup
        final File file = Path.of(getInstance().getDataFolder().getPath(), "config.yml").toFile();
        final YamlConfigurationLoader loader = YamlConfigurationLoader.
                builder().
                path(file.toPath()).
                defaultOptions(opts ->
                        opts.serializers(build -> {
                            build.register(Location.class, LocationSerializer.INSTANCE);
                            build.register(ItemStack.class, ItemSerializer.INSTANCE);
                        }))
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            Settings.load(loader.load(ConfigurationOptions.defaults()));
            WardrobeSettings.load(loader.load().node("wardrobe"));
            DatabaseSettings.load(loader.load().node("database-settings"));
            configLoader = loader;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Messages setup
        final File messagesFile = Path.of(getInstance().getDataFolder().getPath(), "messages.yml").toFile();
        final YamlConfigurationLoader messagesLoader = YamlConfigurationLoader.
                builder().
                path(messagesFile.toPath()).
                defaultOptions(opts ->
                        opts.serializers(build -> {
                            build.register(Location.class, LocationSerializer.INSTANCE);
                            build.register(ItemStack.class, ItemSerializer.INSTANCE);
                        }))
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            MessagesUtil.setup(messagesLoader.load());
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }

        // Translation setup
        final File translationFile = Path.of(getInstance().getDataFolder().getPath(), "translations.yml").toFile();
        final YamlConfigurationLoader translationLoader = YamlConfigurationLoader.
                builder().
                path(translationFile.toPath())
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            TranslationUtil.setup(translationLoader.load());
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }

        // Cosmetics setup
        Cosmetics.setup();

        // Menus setup
        Menus.setup();

        // Dye Menu Reload
        DyeMenuProvider.reload();

        // For reloads
        /*
        for (Player player : Bukkit.getOnlinePlayers()) {
            CosmeticUser user = CosmeticUsers.getUser(player.getUniqueId());
            if (user == null) continue;
            for (Cosmetic cosmetic : user.getCosmetic()) {
                Color color = user.getCosmeticColor(cosmetic.getSlot());
                Cosmetic newCosmetic = Cosmetics.getCosmetic(cosmetic.getId());
                user.removeCosmeticSlot(cosmetic);

                if (newCosmetic == null) continue;
                user.addPlayerCosmetic(newCosmetic, color);
            }
            user.updateCosmetic();
        }
         */

        getInstance().getLogger().info("Successfully Enabled HMCCosmetics");
        getInstance().getLogger().info(Cosmetics.values().size() + " Cosmetics Successfully Setup");
        getInstance().getLogger().info(Menus.getMenuNames().size() + " Menus Successfully Setup");
        getInstance().getLogger().info(WardrobeSettings.getWardrobes().size() + " Wardrobes Successfully Setup");
        getInstance().getLogger().info("Data storage is set to " + DatabaseSettings.getDatabaseType());

        Bukkit.getPluginManager().callEvent(new HMCCosmeticSetupEvent());
    }
}
