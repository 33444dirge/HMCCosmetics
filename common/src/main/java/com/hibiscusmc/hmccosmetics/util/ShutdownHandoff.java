package com.hibiscusmc.hmccosmetics.util;

import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * A server-owned handoff between successive HMCCosmetics class loaders. Commons remains enabled
 * when PlugManX unloads only HMCCosmetics, so its service registration survives the old disable.
 */
public final class ShutdownHandoff extends CompletableFuture<Void> {
    private static final String SERVICE_NAME = ShutdownHandoff.class.getName();

    private ShutdownHandoff() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static ShutdownHandoff publish() {
        Plugin commons = HibiscusCommonsPlugin.getInstance();
        if (commons == null || !commons.isEnabled()) {
            throw new IllegalStateException("HibiscusCommons is unavailable for hot-reload handoff");
        }
        ShutdownHandoff handoff = new ShutdownHandoff();
        // CompletableFuture is loaded by the JVM, unlike this class in the old plugin class loader.
        Bukkit.getServicesManager().register((Class) CompletableFuture.class, handoff, commons, ServicePriority.Highest);
        return handoff;
    }

    /** Wait without blocking a region thread; inspect all previous registrations, including failures. */
    public static CompletableFuture<Void> awaitPrior() {
        List<CompletableFuture<?>> previous = new ArrayList<>();
        Plugin commons = HibiscusCommonsPlugin.getInstance();
        for (RegisteredServiceProvider<CompletableFuture> registration
            : Bukkit.getServicesManager().getRegistrations(CompletableFuture.class)) {
            CompletableFuture<?> provider = registration.getProvider();
            if (registration.getPlugin() == commons && provider.getClass().getName().equals(SERVICE_NAME)) {
                previous.add(provider);
            }
        }
        return CompletableFuture.allOf(previous.toArray(CompletableFuture[]::new));
    }

    public void succeed() {
        complete(null);
        try {
            Bukkit.getServicesManager().unregister(CompletableFuture.class, this);
        } catch (RuntimeException ex) {
            Bukkit.getLogger().log(Level.WARNING, "Unable to unregister completed HMCCosmetics handoff", ex);
        }
    }

    /** Keep a failed registration visible so a new class loader cannot silently read stale data. */
    public void fail(Throwable error) {
        completeExceptionally(error);
    }
}
