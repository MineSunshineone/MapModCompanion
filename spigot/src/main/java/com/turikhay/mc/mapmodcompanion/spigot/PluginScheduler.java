package com.turikhay.mc.mapmodcompanion.spigot;

import com.turikhay.mc.mapmodcompanion.Disposable;
import org.bukkit.entity.Entity;

public interface PluginScheduler extends Disposable {
    void schedule(Runnable r);

    /**
     * Schedule a task to run for a specific entity.
     * On Folia, this ensures the task runs on the entity's region thread.
     * On Bukkit, this is equivalent to scheduling on the main thread.
     */
    void scheduleForEntity(Entity entity, Runnable r);

    /**
     * Schedule a delayed task for a specific entity.
     * On Folia, this ensures the task runs on the entity's region thread after the
     * delay.
     * On Bukkit, this schedules a delayed task on the main thread.
     */
    void scheduleForEntityDelayed(Entity entity, Runnable r, long delayTicks);
}
