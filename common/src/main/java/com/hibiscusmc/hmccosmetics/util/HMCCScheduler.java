package com.hibiscusmc.hmccosmetics.util;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

public final class HMCCScheduler {

    private static final TaskHandle NOOP_TASK = () -> {};

    private HMCCScheduler() {
    }

    public static TaskHandle run(@NotNull Runnable task) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run()));
    }

    public static TaskHandle runLater(@NotNull Runnable task, long delayTicks) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks));
    }

    public static TaskHandle runTimer(@NotNull Runnable task, long delayTicks, long periodTicks) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
        return new PaperTaskHandle(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delayTicks, periodTicks));
    }

    public static TaskHandle runAsync(@NotNull Runnable task) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run()));
    }

    public static TaskHandle runEntity(@NotNull Entity entity, @NotNull Runnable task) {
        return runEntityLater(entity, task, 1);
    }

    public static TaskHandle runEntityLater(@NotNull Entity entity, @NotNull Runnable task, long delayTicks) {
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (plugin.isDisabled()) return runEntityWhileDisabled(entity, task);
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
        }
        ScheduledTask scheduledTask = entity.getScheduler().runDelayed(plugin, ignored -> task.run(), null, delayTicks);
        if (scheduledTask == null) return NOOP_TASK;
        return new PaperTaskHandle(scheduledTask);
    }

    /**
     * Bukkit and Folia refuse new tasks from a disabled plugin, but entity cleanup (balloon removal,
     * closing menus) still runs while disabling, both on server stop and on a PlugManX unload/reload.
     * Run it right away when this thread owns the entity, otherwise hand it to HibiscusCommons, which stays enabled.
     */
    private static TaskHandle runEntityWhileDisabled(@NotNull Entity entity, @NotNull Runnable task) {
        if (ownsEntity(entity)) {
            task.run();
            return NOOP_TASK;
        }
        Plugin commons = HibiscusCommonsPlugin.getInstance();
        if (commons == null || !commons.isEnabled()) return NOOP_TASK;
        ScheduledTask scheduledTask = entity.getScheduler().run(commons, ignored -> task.run(), null);
        if (scheduledTask == null) return NOOP_TASK;
        return new PaperTaskHandle(scheduledTask);
    }

    /**
     * @return true if the current thread may touch the entity directly. Always true outside Folia.
     */
    public static boolean ownsEntity(@NotNull Entity entity) {
        return !HibiscusCommonsPlugin.isOnFolia() || Bukkit.isOwnedByCurrentRegion(entity);
    }

    public static TaskHandle runEntityTimer(@NotNull Entity entity, @NotNull Runnable task, long delayTicks, long periodTicks) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
        }
        ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate(plugin, ignored -> task.run(), null, delayTicks, periodTicks);
        if (scheduledTask == null) return NOOP_TASK;
        return new PaperTaskHandle(scheduledTask);
    }

    public static TaskHandle runAsyncLater(@NotNull Runnable task, long delayTicks) {
        Plugin plugin = HMCCosmeticsPlugin.getInstance();
        if (!HibiscusCommonsPlugin.isOnFolia()) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks));
        }
        return new PaperTaskHandle(Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks * 50, TimeUnit.MILLISECONDS));
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
