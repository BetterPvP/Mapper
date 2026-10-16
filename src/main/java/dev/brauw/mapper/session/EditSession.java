package dev.brauw.mapper.session;

import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.session.display.RegionDisplayStrategy;
import lombok.CustomLog;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The editable state of one world's regions, shared by everyone editing that world.
 * <p>
 * A session is keyed by <em>world</em>, not by player, because the world is already the unit on
 * disk - {@code /mapper save} writes one regions file per world. Giving each editor a private copy
 * of that file would let two people load the same regions, both add to their own copy, and have the
 * second save silently discard the first's work. There is exactly one in-memory copy per world, so
 * there is nothing to diverge.
 * <p>
 * Membership is what makes the session visible: joining shows every region to the joining player,
 * leaving hides them again, and every mutation is pushed to all online members immediately. Who may
 * mutate is decided by {@link SessionRole}.
 */
@Getter
@CustomLog
public class EditSession {

    private final SessionManager sessionManager;
    private final UUID sessionId;
    private final World world;
    private final List<Region> regions;
    private final Map<UUID, SessionMember> members;
    private long lastActivity = System.currentTimeMillis();

    public EditSession(SessionManager sessionManager, World world) {
        this.sessionManager = sessionManager;
        this.sessionId = UUID.randomUUID();
        this.world = world;
        this.regions = new ArrayList<>();
        this.members = new LinkedHashMap<>();
        log.info("Created edit session for world " + world.getName());
    }

    /**
     * @return the regions in this session, as an unmodifiable view. Mutate through
     * {@link #addRegion(Region)} / {@link #removeRegion(Region)} so every member's display stays in
     * step with the list.
     */
    public List<Region> getRegions() {
        return Collections.unmodifiableList(regions);
    }

    /**
     * @return the members of this session, as an unmodifiable view, in join order
     */
    public Collection<SessionMember> getMembers() {
        return Collections.unmodifiableCollection(members.values());
    }

    public @Nullable SessionMember getMember(Player player) {
        return members.get(player.getUniqueId());
    }

    public boolean isMember(Player player) {
        return members.containsKey(player.getUniqueId());
    }

    /**
     * @return whether this player is a member allowed to change regions, save them, or close the
     * session
     */
    public boolean canEdit(Player player) {
        final SessionMember member = getMember(player);
        return member != null && member.canEdit();
    }

    /**
     * Adds a player to the session and shows them every region already in it.
     * Re-joining an existing member only refreshes their display.
     *
     * @param player the joining player
     * @param role   the role to join with
     * @return the member record
     */
    public SessionMember join(Player player, SessionRole role) {
        final SessionMember existing = members.get(player.getUniqueId());
        if (existing != null) {
            showAll(player);
            return existing;
        }

        final SessionMember member = new SessionMember(player, role);
        members.put(player.getUniqueId(), member);
        showAll(player);
        touch();
        log.info("Player " + player.getName() + " joined the " + world.getName() + " session as " + role);
        return member;
    }

    /**
     * Removes a player from the session and hides its regions from them.
     *
     * @param player the leaving player
     * @return the removed member, or {@code null} if they were not a member
     */
    public @Nullable SessionMember leave(Player player) {
        final SessionMember member = members.remove(player.getUniqueId());
        if (member == null) {
            return null;
        }
        hideAll(player);
        touch();
        log.info("Player " + player.getName() + " left the " + world.getName() + " session");
        return member;
    }

    /**
     * Drops a member without touching any display. Only correct for a player who is offline, and so
     * has no display entities left to hide; everyone else should go through {@link #leave(Player)}.
     *
     * @param playerId the member to drop
     * @return the removed member, or {@code null} if there was none
     */
    public @Nullable SessionMember removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    /**
     * Shows every region in the session to a player. Used on join, and again on reconnect, since
     * the display entities a player was shown do not survive their leaving the server.
     */
    public void showAll(Player player) {
        for (Region region : regions) {
            if (isShownTo(region, player)) {
                strategyFor(region).display(region, player);
            }
        }
    }

    /**
     * @return whether a player sees the region: everyone does, unless they are a member with a
     * filter it doesn't match
     */
    public boolean isShownTo(Region region, Player player) {
        final SessionMember member = getMember(player);
        return member == null || member.shows(region);
    }

    /** @return the regions this player sees, in session order */
    public List<Region> visibleTo(Player player) {
        return regions.stream().filter(region -> isShownTo(region, player)).toList();
    }

    /**
     * Shows a member only the regions whose name or tags contain the filter, or every region again.
     *
     * @param player the member
     * @param filter the text to look for, or {@code null} to show everything
     */
    public void setFilter(Player player, @Nullable String filter) {
        final SessionMember member = getMember(player);
        if (member == null) {
            return;
        }
        hideAll(player);
        member.setFilter(filter);
        showAll(player);
    }

    /** Hides every region in the session from a player. */
    public void hideAll(Player player) {
        for (Region region : regions) {
            strategyFor(region).hide(region, player);
        }
    }

    /**
     * Adds a region and shows it to every online member.
     *
     * @param region the region to add
     * @return true, so this stays usable as a {@code Consumer}-shaped method reference
     */
    public boolean addRegion(Region region) {
        regions.add(region);
        final RegionDisplayStrategy<Region> strategy = strategyFor(region);
        for (Player viewer : onlinePlayers()) {
            if (isShownTo(region, viewer)) {
                strategy.display(region, viewer);
            }
        }
        touch();
        return true;
    }

    /**
     * Removes a region and hides it from every online member.
     *
     * @param region the region to remove
     * @return true if the region was in this session
     */
    public boolean removeRegion(Region region) {
        if (!regions.remove(region)) {
            return false;
        }
        final RegionDisplayStrategy<Region> strategy = strategyFor(region);
        for (Player viewer : onlinePlayers()) {
            strategy.hide(region, viewer);
        }
        touch();
        return true;
    }

    /**
     * Swaps one region for another in place, keeping its position in the list.
     * <p>
     * Regions are immutable once built, so moving one (offset, mirror) means producing a new
     * instance. Replacing rather than remove-then-add keeps the list order stable, which is the
     * order the region browser and the exported file both use.
     *
     * @param existing    the region to replace
     * @param replacement the region to put in its place
     * @return true if {@code existing} was in this session
     */
    public boolean replaceRegion(Region existing, Region replacement) {
        final int index = regions.indexOf(existing);
        if (index < 0) {
            return false;
        }

        final RegionDisplayStrategy<Region> oldStrategy = strategyFor(existing);
        final RegionDisplayStrategy<Region> newStrategy = strategyFor(replacement);
        for (Player viewer : onlinePlayers()) {
            oldStrategy.hide(existing, viewer);
        }
        regions.set(index, replacement);
        for (Player viewer : onlinePlayers()) {
            if (isShownTo(replacement, viewer)) {
                newStrategy.display(replacement, viewer);
            }
        }
        touch();
        return true;
    }

    /** Clears every region, hiding them all first. */
    public void clearRegions() {
        for (Region region : List.copyOf(regions)) {
            removeRegion(region);
        }
        touch();
    }

    /** Sends a message to every online member. */
    public void broadcast(Component message) {
        for (Player viewer : onlinePlayers()) {
            viewer.sendMessage(message);
        }
    }

    /**
     * Sends a message to every online member except one - normally the person who caused it, who
     * already got a more detailed confirmation of their own action.
     */
    public void broadcastExcept(@Nullable Player except, Component message) {
        for (Player viewer : onlinePlayers()) {
            if (except == null || !viewer.getUniqueId().equals(except.getUniqueId())) {
                viewer.sendMessage(message);
            }
        }
    }

    /** @return the online players among this session's members */
    public List<Player> onlinePlayers() {
        final List<Player> online = new ArrayList<>(members.size());
        for (SessionMember member : members.values()) {
            final Player player = member.getPlayer();
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    public void touch() {
        this.lastActivity = System.currentTimeMillis();
    }

    private RegionDisplayStrategy<Region> strategyFor(Region region) {
        return sessionManager.getDisplayStrategy(region);
    }
}
