package com.turikhay.mc.mapmodcompanion.spigot;

import com.turikhay.mc.mapmodcompanion.*;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class XaeroHandler implements Handler, Listener {
    private static final long TICKS_PER_SECOND = 20L;
    private static final long POLL_INTERVAL_TICKS = TICKS_PER_SECOND * 3; // Poll every 3 seconds

    private final Logger logger;
    private final String configPath;
    private final String channelName;
    private final MapModCompanion plugin;
    private final PluginScheduler scheduler;

    // Track each player's last known world UUID
    private final Map<UUID, UUID> playerWorldMap = new ConcurrentHashMap<>();

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
        playerWorldMap.clear();
        logger.fine("Event listener has been unregistered");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // Record initial world
        playerWorldMap.put(player.getUniqueId(), world.getUID());

        // Send initial packet immediately
        sendWorldPacket(player, world);

        // Send additional packets with delay (client may not be ready immediately)
        scheduler.scheduleForEntityDelayed(player, () -> sendWorldPacket(player, player.getWorld()), TICKS_PER_SECOND);
        scheduler.scheduleForEntityDelayed(player, () -> sendWorldPacket(player, player.getWorld()),
                TICKS_PER_SECOND * 2);

        // Start polling task for this player
        startPollingTask(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player's world tracking
        playerWorldMap.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Start a recurring polling task for the player to detect world changes.
     * This runs on the entity's region thread and schedules itself repeatedly.
     */
    private void startPollingTask(Player player) {
        scheduler.scheduleForEntityDelayed(player, () -> pollPlayerWorld(player), POLL_INTERVAL_TICKS);
    }

    /**
     * Check if the player's world has changed since last check.
     * If changed, send the world packet and update tracking.
     * Then reschedule itself.
     */
    private void pollPlayerWorld(Player player) {
        if (!player.isOnline()) {
            playerWorldMap.remove(player.getUniqueId());
            return;
        }

        UUID playerUUID = player.getUniqueId();
        World currentWorld = player.getWorld();
        UUID currentWorldUID = currentWorld.getUID();
        UUID lastKnownWorldUID = playerWorldMap.get(playerUUID);

        if (lastKnownWorldUID == null || !lastKnownWorldUID.equals(currentWorldUID)) {
            // World changed! Send packet and update tracking
            logger.fine("Detected world change for " + player.getName() + ": " +
                    (lastKnownWorldUID != null ? lastKnownWorldUID : "null") + " -> " + currentWorldUID);
            playerWorldMap.put(playerUUID, currentWorldUID);
            sendWorldPacket(player, currentWorld);
        }

        // Reschedule the polling task
        scheduler.scheduleForEntityDelayed(player, () -> pollPlayerWorld(player), POLL_INTERVAL_TICKS);
    }

    /**
     * Send the world ID packet to the player.
     */
    private void sendWorldPacket(Player player, World world) {
        if (!player.isOnline()) {
            return;
        }

        int id = plugin.getRegistry().getId(world);
        byte[] payload = LevelMapProperties.Serializer.instance().serialize(id);

        logger.fine(
                () -> "Sending Xaero's LevelMapProperties to " + player.getName() + ": " + Arrays.toString(payload));
        player.sendPluginMessage(plugin, channelName, payload);
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
