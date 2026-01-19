package com.turikhay.mc.mapmodcompanion.spigot;

import com.turikhay.mc.mapmodcompanion.DaemonThreadFactory;
import com.turikhay.mc.mapmodcompanion.ILogger;
import org.bukkit.entity.Entity;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SingleThreadScheduler implements PluginScheduler {
    private static final long MS_PER_TICK = 50L;

    private final ScheduledThreadPoolExecutor service;

    public SingleThreadScheduler(ILogger logger) {
        this.service = new ScheduledThreadPoolExecutor(
                1,
                new DaemonThreadFactory(logger, SingleThreadScheduler.class));
        service.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public void cleanUp() {
        service.shutdown();
    }

    @Override
    public void schedule(Runnable r) {
        service.submit(r);
    }

    @Override
    public void scheduleForEntity(Entity entity, Runnable r) {
        // Fallback: just run on our async thread
        service.submit(r);
    }

    @Override
    public void scheduleForEntityDelayed(Entity entity, Runnable r, long delayTicks) {
        // Convert ticks to milliseconds (1 tick = 50ms)
        long delayMs = delayTicks * MS_PER_TICK;
        service.schedule(r, delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public String toString() {
        return "SingleThreadScheduler{" +
                "service=" + service +
                '}';
    }
}
