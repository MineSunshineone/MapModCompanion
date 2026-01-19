package com.turikhay.mc.mapmodcompanion.spigot;

import com.turikhay.mc.mapmodcompanion.ILogger;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Folia-compatible scheduler that uses reflection to access Folia's
 * EntityScheduler and GlobalRegionScheduler APIs.
 */
public class FoliaScheduler implements PluginScheduler {
    private final Plugin plugin;
    private final ILogger logger;

    // Cached reflection objects for Folia API
    private final Method getSchedulerMethod;
    private final Method executeMethod;
    private final Method runDelayedMethod;
    private final Method getGlobalRegionSchedulerMethod;
    private final Method globalRunMethod;

    public FoliaScheduler(Plugin plugin, ILogger logger) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.logger = logger;

        // Entity.getScheduler() -> EntityScheduler
        getSchedulerMethod = Entity.class.getMethod("getScheduler");

        // EntityScheduler.execute(Plugin, Runnable, Runnable, long)
        Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        executeMethod = entitySchedulerClass.getMethod("execute", Plugin.class, Runnable.class, Runnable.class,
                long.class);

        // EntityScheduler.runDelayed(Plugin, Consumer<ScheduledTask>, Runnable, long)
        Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        Class<?> consumerClass = java.util.function.Consumer.class;
        runDelayedMethod = entitySchedulerClass.getMethod("runDelayed", Plugin.class, consumerClass, Runnable.class,
                long.class);

        // Server.getGlobalRegionScheduler() -> GlobalRegionScheduler
        getGlobalRegionSchedulerMethod = plugin.getServer().getClass().getMethod("getGlobalRegionScheduler");

        // GlobalRegionScheduler.run(Plugin, Consumer<ScheduledTask>)
        Class<?> globalSchedulerClass = Class
                .forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        globalRunMethod = globalSchedulerClass.getMethod("run", Plugin.class, consumerClass);

        logger.fine("FoliaScheduler initialized successfully");
    }

    @Override
    public void cleanUp() {
        // Nothing to clean up
    }

    @Override
    public void schedule(Runnable r) {
        // Use global region scheduler for non-entity tasks
        try {
            Object globalScheduler = getGlobalRegionSchedulerMethod.invoke(plugin.getServer());
            // Consumer<ScheduledTask> that ignores the task and just runs our Runnable
            java.util.function.Consumer<?> consumer = (task) -> executeTask(r);
            globalRunMethod.invoke(globalScheduler, plugin, consumer);
        } catch (Exception e) {
            logger.error("Failed to schedule task via GlobalRegionScheduler", e);
        }
    }

    @Override
    public void scheduleForEntity(Entity entity, Runnable r) {
        try {
            Object entityScheduler = getSchedulerMethod.invoke(entity);
            // execute(Plugin, Runnable run, Runnable retired, long delay)
            // delay of 1 tick minimum is required
            executeMethod.invoke(entityScheduler, plugin, (Runnable) () -> executeTask(r), null, 1L);
        } catch (Exception e) {
            logger.error("Failed to schedule entity task", e);
        }
    }

    @Override
    public void scheduleForEntityDelayed(Entity entity, Runnable r, long delayTicks) {
        try {
            Object entityScheduler = getSchedulerMethod.invoke(entity);
            // runDelayed(Plugin, Consumer<ScheduledTask>, Runnable retired, long
            // delayTicks)
            // Minimum delay is 1 tick
            long actualDelay = Math.max(1L, delayTicks);
            java.util.function.Consumer<?> consumer = (task) -> executeTask(r);
            runDelayedMethod.invoke(entityScheduler, plugin, consumer, null, actualDelay);
        } catch (Exception e) {
            logger.error("Failed to schedule delayed entity task", e);
        }
    }

    private void executeTask(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            logger.error("Failed to execute the task", t);
        }
    }

    @Override
    public String toString() {
        return "FoliaScheduler{}";
    }
}
