package com.donutauth.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Scheduler wrapper that works on both Paper and Folia.
 * Detects Folia at runtime and uses the correct scheduler.
 */
public final class Scheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private Scheduler() {}

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Run a task on the main thread (Paper) or the entity's region thread (Folia)
     */
    public static void runTask(JavaPlugin plugin, Player player, Runnable task) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a task later
     */
    public static void runTaskLater(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            player.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a global task (not tied to a player)
     */
    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a global task later
     */
    public static void runGlobalLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a repeating task for a player
     */
    public static void runTaskTimer(JavaPlugin plugin, Player player, Runnable task,
                                     long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            player.getScheduler().runAtFixedRate(plugin, scheduledTask -> {
                if (player.isOnline()) task.run();
            }, null, delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline()) task.run();
            }, delayTicks, periodTicks);
        }
    }

    /**
     * Run a global repeating task
     */
    public static void runGlobalTimer(JavaPlugin plugin, Runnable task,
                                       long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                scheduledTask -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Run async task (same on both)
     */
    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
}