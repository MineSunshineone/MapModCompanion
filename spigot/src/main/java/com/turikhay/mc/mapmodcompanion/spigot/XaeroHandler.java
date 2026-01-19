package com.turikhay.mc.mapmodcompanion.spigot;

import com.turikhay.mc.mapmodcompanion.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class XaeroHandler implements Handler, Listener {
    private static final long TICKS_PER_SECOND = 20L;

    private final Logger logger;
    private final String configPath;
    private final String channelName;
    private final MapModCompanion plugin;
    private final PluginScheduler scheduler;

    public XaeroHandler(Logger logger, String configPath, String channelName, MapModCompanion plugin,
            PluginScheduler scheduler) {
        this.logger = logger;
        this.configPath = configPath;
        this.channelName = channelName;
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void init() throws InitializationException {
        plugin.registerOutgoingChannel(channelName);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.fine("Event listener has been registered");
    }

    @Override
    public void cleanUp() {
        plugin.unregisterOutgoingChannel(channelName);
        HandlerList.unregisterAll(this);
        logger.fine("Event listener has been unregistered");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoined(PlayerJoinEvent event) {
        sendPacket(event, Type.JOIN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        sendPacket(event, Type.WORLD_CHANGE);
    }

    private void sendPacket(PlayerEvent event, Type type) {
        Player p = event.getPlayer();
        World world = p.getWorld();
        int id = plugin.getRegistry().getId(world);
        byte[] payload = LevelMapProperties.Serializer.instance().serialize(id);
        UUID expectedWorld = world.getUID();

        int repeatTimes = plugin.getConfig().getInt(
                configPath + ".events." + type.name().toLowerCase(Locale.ROOT) + ".repeat_times",
                1);

        for (int i = 0; i < repeatTimes; i++) {
            long delayTicks = i * TICKS_PER_SECOND;
            if (delayTicks == 0) {
                // Run immediately on entity's thread
                scheduler.scheduleForEntity(p, () -> sendPayload(p, expectedWorld, payload));
            } else {
                // Run delayed on entity's thread
                scheduler.scheduleForEntityDelayed(p, () -> sendPayload(p, expectedWorld, payload), delayTicks);
            }
        }
    }

    private void sendPayload(Player player, UUID expectedWorld, byte[] payload) {
        if (!player.isOnline()) {
            return;
        }
        UUID currentWorld = player.getWorld().getUID();
        if (!currentWorld.equals(expectedWorld)) {
            logger.fine("Skipping sending Xaero's LevelMapProperties to " + player.getName() + ": unexpected world");
            return;
        }
        logger.fine(
                () -> "Sending Xaero's LevelMapProperties to " + player.getName() + ": " + Arrays.toString(payload));
        player.sendPluginMessage(plugin, channelName, payload);
    }

    private enum Type {
        JOIN,
        WORLD_CHANGE,
    }

    public static class Factory implements Handler.Factory<MapModCompanion> {
        private final String configPath;
        private final String channelName;
        private final PluginScheduler scheduler;

        public Factory(String configPath, String channelName, PluginScheduler scheduler) {
            this.configPath = configPath;
            this.channelName = channelName;
            this.scheduler = scheduler;
        }

        @Override
        public String getName() {
            return channelName;
        }

        @Override
        public XaeroHandler create(MapModCompanion plugin) throws InitializationException {
            plugin.checkEnabled(configPath);
            XaeroHandler handler = new XaeroHandler(
                    new PrefixLogger(plugin.getVerboseLogger(), channelName),
                    configPath, channelName, plugin, scheduler);
            handler.init();
            return handler;
        }
    }
}
