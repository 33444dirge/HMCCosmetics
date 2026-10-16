package com.hibiscusmc.hmccosmetics.util;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

public final class HMCCScheduler {

    private static final TaskHandle NOOP_TASK = () -> {};

    private HMCCScheduler() {
    }

    public static TaskHandle run(@NotNull Runnable task) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, () -> runIfActive(plugin, task)));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> runIfActive(plugin, task)));
    }

    public static TaskHandle runLater(@NotNull Runnable task, long delayTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, () -> runIfActive(plugin, task), delayTicks));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> runIfActive(plugin, task), delayTicks));
    }

    public static TaskHandle runTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, () -> runIfActive(plugin, task), delayTicks, periodTicks));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> runIfActive(plugin, task), delayTicks, periodTicks));
    }

    public static TaskHandle runAsync(@NotNull Runnable task) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> runIfActive(plugin, task)));
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> runIfActive(plugin, task)));
    }

    public static TaskHandle runEntity(@NotNull Entity entity, @NotNull Runnable task) {
        return runEntityLater(entity, task, 1);
    }

    public static TaskHandle runEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, () -> runIfActive(plugin, task), delayTicks));
        }
        ScheduledTask scheduledTask;
        try {
            scheduledTask = entity.getScheduler().runDelayed(plugin, ignored -> runIfActive(plugin, task), null, delayTicks);
        } catch (IllegalPluginAccessException ex) {
            if (!plugin.isAcceptingWork()) return NOOP_TASK;
            throw ex;
        }
        if (scheduledTask == null) return NOOP_TASK;
        return new PaperTaskHandle(scheduledTask);
    }

    /**
     * Only shutdown cleanup may use HibiscusCommons as a task owner. Ordinary work must never be
     * rescheduled under another plugin after HMCCosmetics has stopped accepting it.
     */
    public static CompletableFuture<CleanupResult> runEntityCleanup(@NotNull Entity entity, @NotNull Runnable task) {
        CompletableFuture<CleanupResult> result = new CompletableFuture<>();
        if (ownsEntity(entity)) {
            completeCleanup(task, result);
            return result;
        }
        Plugin commons = HibiscusCommonsPlugin.getInstance();
        if (commons == null || !commons.isEnabled()) {
            result.complete(CleanupResult.REJECTED);
            return result;
        }
        try {
            ScheduledTask scheduledTask = entity.getScheduler().run(commons,
                ignored -> completeCleanup(task, result),
                () -> result.complete(CleanupResult.RETIRED));
            if (scheduledTask == null) result.complete(CleanupResult.RETIRED);
        } catch (RuntimeException ex) {
            result.completeExceptionally(ex);
        }
        return result;
    }

    /** A retired target entity is gone; retirement of some other entity says nothing about it. */
    public static CompletableFuture<Void> requireEntityCleanup(@NotNull Entity entity, @NotNull Runnable task) {
        return runEntityCleanup(entity, task).thenAccept(result -> {
            if (result != CleanupResult.COMPLETED && result != CleanupResult.RETIRED) {
                throw new IllegalStateException("Entity cleanup was not completed: " + result);
            }
        });
    }

    private static void completeCleanup(Runnable task, CompletableFuture<CleanupResult> result) {
        try {
            task.run();
            result.complete(CleanupResult.COMPLETED);
        } catch (Throwable ex) {
            result.completeExceptionally(ex);
        }
    }

    private static void runIfActive(HMCCosmeticsPlugin plugin, Runnable task) {
        if (plugin.isAcceptingWork()) task.run();
    }

    public enum CleanupResult {
        COMPLETED, RETIRED, RETIRED_CLEANED, REJECTED, TIMED_OUT
    }

    /**
     * @return true if the current thread may touch the entity directly. Always true outside Folia.
     */
    public static boolean ownsEntity(@NotNull Entity entity) {
        return !HibiscusCommonsPlugin.isOnFolia() || Bukkit.isOwnedByCurrentRegion(entity);
    }

    public static TaskHandle runEntityTimer(@NotNull Entity entity, @NotNull Runnable task, long delayTicks, long periodTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, () -> runIfActive(plugin, task), delayTicks, periodTicks));
        }
        ScheduledTask scheduledTask;
        try {
            scheduledTask = entity.getScheduler().runAtFixedRate(plugin, ignored -> runIfActive(plugin, task), null, delayTicks, periodTicks);
        } catch (IllegalPluginAccessException ex) {
            if (!plugin.isAcceptingWork()) return NOOP_TASK;
            throw ex;
        }
        if (scheduledTask == null) return NOOP_TASK;
        return new PaperTaskHandle(scheduledTask);
    }

    public static TaskHandle runAsyncLater(@NotNull Runnable task, long delayTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!plugin.isAcceptingWork()) return NOOP_TASK;
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> runIfActive(plugin, task), delayTicks));
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> runIfActive(plugin, task), delayTicks * 50, TimeUnit.MILLISECONDS));
    }

    public static CompletableFuture<Boolean> teleportAsync(@NotNull Entity entity, @NotNull Location location) {
        return entity.teleportAsync(location);
    }

    public interface TaskHandle {
        void cancel();
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }
    }

    private record PaperTaskHandle(ScheduledTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }
    }
}
