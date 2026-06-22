package dev.brauw.mapper.session;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.session.display.*;
import dev.brauw.mapper.session.event.SessionCreateEvent;
import dev.brauw.mapper.session.event.SessionEndEvent;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages player edit sessions for region definitions.
 */
@CustomLog
public class SessionManager {

    private final EnumMap<Region.RegionType, RegionDisplayStrategy<?>> displayStrategies;
    private final Map<UUID, EditSession> playerSessions;
    private final long sessionTimeoutMillis;
    private final Mapper mapper;
    private BukkitTask revalidateTask;

    /**
     * Creates a new SessionManager with the default timeout.
     */
    public SessionManager(Mapper mapper) {
        this(TimeUnit.HOURS.toMillis(1), mapper); // Default 1 hour timeout
    }

    /**
     * Creates a new SessionManager with a custom timeout.
     *
     * @param sessionTimeoutMillis timeout in milliseconds for inactive sessions
     */
    public SessionManager(long sessionTimeoutMillis, Mapper mapper) {
        this.sessionTimeoutMillis = sessionTimeoutMillis;
        this.mapper = mapper;
        this.playerSessions = new HashMap<>();
        this.displayStrategies = new EnumMap<>(Region.RegionType.class);
        createDisplayStrategies();
        startRevalidateTask();
        log.info("Session manager initialized with " +
                TimeUnit.MILLISECONDS.toMinutes(sessionTimeoutMillis) +
                " minute timeout");
    }

    private void startRevalidateTask() {
        this.revalidateTask = mapper.getTaskScheduler().scheduleRecurringTask(() -> {
            for (EditSession session : playerSessions.values()) {
                final Player owner = session.getOwner();
                if (!owner.isOnline()) continue;
                for (Region region : session.getRegions()) {
                    final RegionDisplayStrategy<Region> strategy = getDisplayStrategy(region);
                    strategy.revalidate(region, owner);
                }
            }
        }, 20L, 20L);
    }

    private void createDisplayStrategies() {
        final ItemStrategy pointStrategy = new ItemStrategy(mapper.getPlugin(), Material.REDSTONE_LAMP);
        final ArmorStandStrategy perspectiveStrategy =
                new ArmorStandStrategy(mapper.getPlugin(), mapper.getRegionIdKey());
        final BlockStrategy blockStrategy = new BlockStrategy(mapper.getPlugin());
        final PolygonStrategy polygonStrategy = new PolygonStrategy(mapper.getPlugin(), blockStrategy);
        this.displayStrategies.put(Region.RegionType.POLYGON, polygonStrategy);
        this.displayStrategies.put(Region.RegionType.CUBOID, blockStrategy);
        this.displayStrategies.put(Region.RegionType.POINT, pointStrategy);
        this.displayStrategies.put(Region.RegionType.PERSPECTIVE, perspectiveStrategy);
    }

    /**
     * Gets the display strategy for the specified region type.
     *
     * @param region the region to get the display strategy for
     * @return the display strategy for the region type
     */
    public <T extends Region> RegionDisplayStrategy<T> getDisplayStrategy(T region) {
        //noinspection unchecked
        return (RegionDisplayStrategy<T>) displayStrategies.get(region.getType());
    }

    /**
     * Gets or creates an edit session for the specified player.
     *
     * @param player the player to get a session for
     * @return the player's edit session
     */
    public EditSession getSession(Player player) {
        return playerSessions.computeIfAbsent(
                player.getUniqueId(),
                uuid -> {
                    final EditSession session = new EditSession(this, player);
                    new SessionCreateEvent(player).callEvent();
                    return session;
                });
    }
    
    /**
     * Checks if the player has an active edit session.
     *
     * @param player the player to check
     * @return true if the player has an active session
     */
    public boolean hasSession(Player player) {
        return playerSessions.containsKey(player.getUniqueId());
    }
    
    /**
     * Ends the edit session for the specified player.
     *
     * @param player the player whose session to end
     * @return true if a session was ended, false if no session existed
     */
    public boolean endSession(Player player) {
        EditSession removed = playerSessions.remove(player.getUniqueId());
        if (removed != null) {
            for (Region region : removed.getRegions()) {
                final RegionDisplayStrategy<Region> strategy = getDisplayStrategy(region);
                strategy.hide(region, player);
            }

            new SessionEndEvent(player).callEvent();
            log.info("Ended edit session for player " + player.getName());
            return true;
        }
        return false;
    }
    
    /**
     * Cleans up expired sessions.
     * This should be called periodically, e.g., by a scheduled task.
     */
    public void cleanupExpiredSessions() {
        final Iterator<EditSession> iterator = playerSessions.values().iterator();
        while (iterator.hasNext()) {
            EditSession session = iterator.next();
            if (System.currentTimeMillis() - session.getLastActivity() > sessionTimeoutMillis) {
                iterator.remove();
                log.info("Removed expired session for player " + session.getOwner().getName());
            }
        }
    }
}