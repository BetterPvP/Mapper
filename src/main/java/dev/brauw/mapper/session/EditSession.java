package dev.brauw.mapper.session;

import dev.brauw.mapper.region.Region;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a session for a player defining regions.
 * Stores regions in-memory until they are committed or exported.
 */
@Getter
@CustomLog
public class EditSession {

    private final SessionManager sessionManager;
    private final UUID sessionId;
    private final Player owner;
    private final List<Region> regions;
    private long lastActivity = System.currentTimeMillis();
    
    /**
     * Creates a new edit session for the specified player.
     *
     * @param sessionManager
     * @param owner          the player who owns this session
     */
    public EditSession(SessionManager sessionManager, Player owner) {
        this.sessionManager = sessionManager;
        this.sessionId = UUID.randomUUID();
        this.owner = owner;
        this.regions = new ArrayList<>();
        log.info("Created new edit session for player " + owner.getName());
    }
    
    /**
     * Adds a region to this session.
     *
     * @param region the region to add
     * @return true if the region was added successfully
     */
    public boolean addRegion(Region region) {
        log.info("Player " + owner.getName() + " added region '" +
                region.getName() + "' to their session");
        lastActivity = System.currentTimeMillis();
        sessionManager.getDisplayStrategy(region).display(region, owner);
        regions.add(region);
        return true;
    }
    
    /**
     * Removes a region from this session.
     *
     * @param region the region to remove
     * @return true if the region was removed successfully
     */
    public boolean removeRegion(Region region) {
        sessionManager.getDisplayStrategy(region).hide(region, owner);
        return regions.remove(region);
    }

    /**
     * Clears all regions from this session.
     */
    public void clearRegions() {
        regions.clear();
        lastActivity = System.currentTimeMillis();
        log.info("Player " + owner.getName() + " cleared their session regions");
    }

}