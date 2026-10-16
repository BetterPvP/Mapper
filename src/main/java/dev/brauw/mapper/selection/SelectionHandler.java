package dev.brauw.mapper.selection;

import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PathRegion;
import dev.brauw.mapper.region.PerspectiveRegion;
import dev.brauw.mapper.region.PointRegion;
import dev.brauw.mapper.region.PolygonRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionNesting;
import dev.brauw.mapper.region.RegionOptions;
import dev.brauw.mapper.region.RegionTransform;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tag.TagRegistry;
import dev.brauw.mapper.util.RegionFormat;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

/**
 * Turns player interactions into regions.
 * <p>
 * In-progress selections (cuboid corners, polygon parts, path waypoints) are per-player, not
 * per-session: several members edit the same world at once, and one builder's half-placed corners
 * are not something anybody else should be able to finish or clobber. Only the finished region is
 * shared, which is why every entry point takes the acting player explicitly rather than reading a
 * single owner off the session.
 */
@RequiredArgsConstructor
public class SelectionHandler {

    private static final double MIN_DISTANCE_TO_INTERACT = 0.4;
    private static final long CONFIRM_MILLIS = 5000;

    private final GuiManager guiManager;
    private final TagRegistry tagRegistry;
    private final RegionClipboard clipboard;
    private final RegionTargetTracker targets;
    private final Map<Player, PendingDelete> pendingDeletes = new WeakHashMap<>();
    private final Map<Player, SelectionCorners> selections = new WeakHashMap<>();
    private final Map<Player, List<CuboidRegion>> polygonSelections = new WeakHashMap<>();
    private final Map<Player, List<Location>> pathSelections = new WeakHashMap<>();

    private SelectionCorners getSelection(Player player) {
        return selections.computeIfAbsent(player, key -> new SelectionCorners());
    }

    private List<CuboidRegion> getPolygonSelection(Player player) {
        return polygonSelections.computeIfAbsent(player, key -> new ArrayList<>());
    }

    private List<Location> getPathSelection(Player player) {
        return pathSelections.computeIfAbsent(player, key -> new ArrayList<>());
    }

    public void clearSelections(Player player) {
        selections.remove(player);
        polygonSelections.remove(player);
        pathSelections.remove(player);
    }

    public boolean hasCompleteSelection(Player player) {
        return getSelection(player).isComplete();
    }

    public int getPathPointCount(Player player) {
        return getPathSelection(player).size();
    }

    public int getPolygonPartCount(Player player) {
        return getPolygonSelection(player).size();
    }

    /**
     * Sets the first corner of a cuboid selection at the block the player is looking at, or at the
     * block they stand in when they look at none.
     *
     * @param actor the acting player
     */
    public void setFirstPosition(Player actor) {
        setCorner(actor, true, targetOrStanding(actor));
    }

    /**
     * Sets the second corner of a cuboid selection at the block the player is looking at, or at the
     * block they stand in when they look at none.
     *
     * @param actor the acting player
     */
    public void setSecondPosition(Player actor) {
        setCorner(actor, false, targetOrStanding(actor));
    }

    /**
     * Sets the first corner of a cuboid selection at the block the player stands in, whatever they
     * are looking at. This is what a click into the air does, so a corner can sit in mid-air without
     * a block placed there to aim at.
     *
     * @param actor the acting player
     */
    public void setFirstPositionHere(Player actor) {
        setCorner(actor, true, standing(actor));
    }

    /**
     * Sets the second corner of a cuboid selection at the block the player stands in, whatever they
     * are looking at.
     *
     * @param actor the acting player
     */
    public void setSecondPositionHere(Player actor) {
        setCorner(actor, false, standing(actor));
    }

    /**
     * Sets both corners of a cuboid selection at once.
     *
     * @param actor  the acting player
     * @param first  the first corner
     * @param second the second corner
     */
    public void setCorners(Player actor, Location first, Location second) {
        getSelection(actor).setFirstCorner(first);
        getSelection(actor).setSecondCorner(second);
    }

    private void setCorner(Player actor, boolean first, Location location) {
        final SelectionCorners selection = getSelection(actor);
        if (first) {
            selection.setFirstCorner(location);
        } else {
            selection.setSecondCorner(location);
        }
        actor.sendMessage(Component.text(first ? "First position set " : "Second position set ",
                        first ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .append(RegionFormat.location(location)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, first ? 1.0f : 1.5f);
    }

    private static Location targetOrStanding(Player player) {
        final Location target = getTargetBlock(player);
        return target != null ? target : standing(player);
    }

    private static Location standing(Player player) {
        return player.getLocation().toBlockLocation();
    }

    /**
     * Creates a cuboid region from the player's two corners.
     *
     * @param session the session to add the region to
     * @param actor   the acting player
     * @param name    the region name, or {@code null} to prompt for one in the create GUI
     */
    public void createCuboidRegion(EditSession session, Player actor, @Nullable String name) {
        final SelectionCorners selection = getSelection(actor);
        if (!selection.isComplete()) {
            actor.sendMessage(Component.text("You need to set both positions first!", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final Location first = selection.getFirstCorner();
        final Location second = selection.getSecondCorner();
        create(session, actor, name, (regionName, options) -> new CuboidRegion(regionName, first, second, options));
    }

    public void addPolygonChild(Player actor) {
        final SelectionCorners selection = getSelection(actor);
        if (!selection.isComplete()) {
            actor.sendMessage(Component.text("You need to set both positions before adding a polygon part.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final Location first = selection.getFirstCorner();
        final Location second = selection.getSecondCorner();
        final List<CuboidRegion> children = getPolygonSelection(actor);
        children.add(new CuboidRegion("polygon-child-" + children.size(), first, second));
        selections.remove(actor);

        actor.sendMessage(Component.text("Added polygon part ", NamedTextColor.GREEN)
                .append(Component.text("#" + children.size(), NamedTextColor.YELLOW)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0f, 1.2f);
    }

    /**
     * Creates a polygon region from the parts the player has added.
     *
     * @param session the session to add the region to
     * @param actor   the acting player
     * @param name    the region name, or {@code null} to prompt for one in the create GUI
     */
    public void createPolygonRegion(EditSession session, Player actor, @Nullable String name) {
        final List<CuboidRegion> children = polygonSelections.get(actor);
        if (children == null || children.isEmpty()) {
            actor.sendMessage(Component.text("Add at least one cuboid part before creating a polygon region.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        if (getSelection(actor).isComplete()) {
            actor.sendMessage(Component.text("Finish the current cuboid part first.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final List<CuboidRegion> snapshot = List.copyOf(children);
        create(session, actor, name, (regionName, options) -> new PolygonRegion(regionName, snapshot, options));
    }

    /**
     * Appends a waypoint to the player's in-progress path, capturing their facing so the point
     * carries a direction as well as a position.
     *
     * @param actor    the acting player
     * @param location the interaction point, or {@code null} when the player clicked open air
     */
    public void addPathPoint(Player actor, @Nullable Location location) {
        // Clicking air yields no interaction point; standing position is the sensible fallback and is
        // usually what a builder wants for a waypoint anyway.
        final Location target = (location == null ? actor.getLocation() : location).clone();
        target.setDirection(actor.getLocation().getDirection());

        final List<Location> points = getPathSelection(actor);
        points.add(target);

        actor.sendMessage(Component.text("Added waypoint ", NamedTextColor.GREEN)
                .append(Component.text("#" + points.size(), NamedTextColor.YELLOW))
                .append(Component.text(" ", NamedTextColor.GRAY))
                .append(RegionFormat.location(target)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0f, 1.2f);
    }

    /**
     * Removes the most recently added waypoint from the player's in-progress path.
     *
     * @param actor the acting player
     */
    public void undoPathPoint(Player actor) {
        final List<Location> points = getPathSelection(actor);
        if (points.isEmpty()) {
            actor.sendMessage(Component.text("No waypoints to undo.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        points.removeLast();
        actor.sendMessage(Component.text("Removed the last waypoint, ", NamedTextColor.YELLOW)
                .append(Component.text(points.size() + " remaining", NamedTextColor.GRAY)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.4f);
    }

    /**
     * Creates a path region from the waypoints the player has placed, in click order.
     *
     * @param session the session to add the region to
     * @param actor   the acting player
     * @param name    the region name, or {@code null} to prompt for one in the create GUI
     */
    public void createPathRegion(EditSession session, Player actor, @Nullable String name) {
        final List<Location> points = pathSelections.get(actor);
        if (points == null || points.size() < 2) {
            actor.sendMessage(Component.text("Add at least two waypoints before creating a path region.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final List<Location> snapshot = List.copyOf(points);
        create(session, actor, name, (regionName, options) -> new PathRegion(regionName, snapshot, options));
    }

    /**
     * Creates a point region at the given location, or at the player's feet when they are sneaking.
     *
     * @param session  the session to add the region to
     * @param actor    the acting player
     * @param location the location to place the point at, or {@code null} to use the player's feet
     * @param name     the region name, or {@code null} to prompt for one in the create GUI
     */
    public void createPointRegion(EditSession session, Player actor, @Nullable Location location, @Nullable String name) {
        final Location target = (actor.isSneaking() || location == null ? actor.getLocation() : location).clone();
        target.setYaw(0);
        target.setPitch(0);
        create(session, actor, name, (regionName, options) -> new PointRegion(regionName, target, options));
    }

    /**
     * Creates a perspective region - a point that also records which way the player was facing.
     *
     * @param session  the session to add the region to
     * @param actor    the acting player
     * @param location the location to place the point at, or {@code null} to use the player's feet
     * @param name     the region name, or {@code null} to prompt for one in the create GUI
     */
    public void createPerspectiveRegion(EditSession session, Player actor, @Nullable Location location, @Nullable String name) {
        final Location target = (actor.isSneaking() || location == null ? actor.getLocation() : location).clone();
        target.setDirection(actor.getLocation().getDirection());
        create(session, actor, name, (regionName, options) -> new PerspectiveRegion(regionName, target, options));
    }

    /**
     * Builds a region and adds it to the session, prompting for a name and colour in the create GUI
     * when the caller did not supply a name.
     * <p>
     * The tool path and the command path share this, so a region is announced, validated and has its
     * selection cleared identically either way.
     */
    private void create(EditSession session, Player actor, @Nullable String name,
                        BiFunction<String, RegionOptions, Region> factory) {
        if (name != null) {
            if (!validate(actor, name)) {
                return;
            }
            addAndAnnounce(session, actor, factory.apply(name, RegionOptions.builder().build()));
            clearSelections(actor);
            return;
        }

        guiManager.openRegionCreateGui(actor, (chosenName, options) -> {
            if (!validate(actor, chosenName)) {
                return;
            }
            addAndAnnounce(session, actor, factory.apply(chosenName, options));
            clearSelections(actor);
        }, () -> clearSelections(actor));
    }

    private void addAndAnnounce(EditSession session, Player actor, Region region) {
        session.addRegion(region);
        actor.sendMessage(Component.text("Created ", NamedTextColor.GREEN)
                .append(RegionFormat.describe(region)));
        session.broadcastExcept(actor, Component.text(actor.getName() + " added ", NamedTextColor.GRAY)
                .append(RegionFormat.describe(region)));
    }

    /**
     * Deletes the region the player's crosshair is on: the one {@link RegionTargetTracker} outlines.
     * A region that holds others is only deleted on a second click within a few seconds.
     *
     * @param session the session to search
     * @param actor   the acting player
     */
    public void handleRegionDeletion(EditSession session, Player actor) {
        final Region region = targets.target(session, actor);
        if (region == null) {
            notifyNoRegion(actor);
            return;
        }
        deleteConfirmed(session, actor, region);
    }

    /**
     * Opens the tag editor for the region the player's crosshair is on.
     *
     * @param session the session to search
     * @param actor   the acting player
     */
    public void handleTagEditor(EditSession session, Player actor) {
        final Region region = targets.target(session, actor);
        if (region == null) {
            notifyNoRegion(actor);
            return;
        }
        openTagEditor(actor, region);
    }

    /**
     * Deletes a region, asking for a second click first when other regions sit inside it. Those are
     * usually the datapoints a builder meant to delete instead, and they are not deleted with it.
     *
     * @param session the owning edit session
     * @param actor   the acting player
     * @param region  the region to delete
     */
    public void deleteConfirmed(EditSession session, Player actor, Region region) {
        final long inside = RegionNesting.descendants(region, session.getRegions()).size();
        final PendingDelete pending = pendingDeletes.remove(actor);
        final boolean confirmed = pending != null && pending.getRegionId().equals(region.getId())
                && System.currentTimeMillis() <= pending.getExpiresAt();
        if (inside > 0 && !confirmed) {
            pendingDeletes.put(actor, new PendingDelete(region.getId(), System.currentTimeMillis() + CONFIRM_MILLIS));
            actor.sendMessage(RegionFormat.describe(region)
                    .append(Component.text(" holds " + inside + (inside == 1 ? " region" : " regions")
                            + ". Click again within " + CONFIRM_MILLIS / 1000 + " seconds to delete it.", NamedTextColor.GOLD)));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return;
        }
        deleteRegion(session, actor, region);
    }

    /**
     * Deletes a region that has already been resolved (e.g. from an entity click or a command),
     * skipping the raytrace and the "no region found" path.
     *
     * @param session the owning edit session
     * @param actor   the acting player
     * @param region  the region to delete
     */
    public void deleteRegion(EditSession session, Player actor, Region region) {
        // A region resolved a moment ago (a pending pick, an open browser) may already be gone -
        // somebody else shares this session. Reporting a delete that did not happen is worse than
        // saying so.
        if (!session.removeRegion(region)) {
            actor.sendMessage(Component.text("That region is already gone.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        actor.sendMessage(Component.text("Deleted ", NamedTextColor.RED)
                .append(RegionFormat.describe(region)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
        session.broadcastExcept(actor, Component.text(actor.getName() + " deleted ", NamedTextColor.GRAY)
                .append(RegionFormat.describe(region)));
    }

    /**
     * Copies the region the player's crosshair is on into their clipboard.
     *
     * @param session the session to search
     * @param actor   the acting player
     */
    public void handleCopy(EditSession session, Player actor) {
        final Region region = targets.target(session, actor);
        if (region == null) {
            notifyNoRegion(actor);
            return;
        }
        copyRegion(session, actor, region);
    }

    /**
     * Copies a region that has already been resolved (e.g. from clicking its display entity), along
     * with the regions inside it.
     *
     * @param session the session the region is in
     * @param actor   the acting player
     * @param region  the region to copy
     */
    public void copyRegion(EditSession session, Player actor, Region region) {
        final List<Region> group = withNested(session, region);
        clipboard.copy(actor, group);
        actor.sendMessage(Component.text("Copied ", NamedTextColor.GREEN)
                .append(RegionFormat.describe(region))
                .append(Component.text(nestedSuffix(group) + " - sneak and right-click to paste", NamedTextColor.GRAY)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
    }

    /**
     * Places a copy of the player's clipboard at their feet.
     * <p>
     * The copy takes a fresh id, so the two are separate datapoints rather than one written twice,
     * and is re-homed onto the session's world: a region copied in one world and pasted in another
     * would otherwise carry its old world and make the whole set unsaveable.
     * <p>
     * It is also turned to the direction the player is facing rather than the one it was copied
     * with, so a route or an arrangement can be dropped in facing a different way and keep its shape.
     * The regions copied with it turn about the same point, so they keep their places inside it.
     *
     * @param session the session to paste into
     * @param actor   the acting player
     * @param name    the name for the copy, or {@code null} to keep the original's
     */
    public void handlePaste(EditSession session, Player actor, @Nullable String name) {
        final List<Region> group = clipboard.get(actor);
        if (group.isEmpty()) {
            actor.sendMessage(Component.text("Nothing copied yet - right-click a region first.", NamedTextColor.RED));
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final Region source = group.getFirst();
        final Location pivot = RegionTransform.anchor(source);
        final Vector delta = actor.getLocation().toVector().subtract(pivot.toVector());
        final float turn = actor.getLocation().getYaw() - pivot.getYaw();
        Region pasted = null;
        for (Region member : group) {
            final String copyName = member == source && name != null ? name : member.getName();
            final Region copy = RegionTransform.duplicate(member, delta, turn, pivot, copyName);
            copy.setWorld(session.getWorld());
            session.addRegion(copy);
            if (pasted == null) {
                pasted = copy;
            }
        }

        actor.sendMessage(Component.text("Pasted ", NamedTextColor.GREEN)
                .append(RegionFormat.describe(pasted))
                .append(Component.text(nestedSuffix(group), NamedTextColor.GRAY)));
        actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        session.broadcastExcept(actor, Component.text(actor.getName() + " pasted ", NamedTextColor.GRAY)
                .append(RegionFormat.describe(pasted)));
    }

    /**
     * @param session the session the region is in
     * @param region  the region
     * @return the region first, then every region nested inside it
     */
    public static List<Region> withNested(EditSession session, Region region) {
        final List<Region> group = new ArrayList<>();
        group.add(region);
        group.addAll(RegionNesting.descendants(region, session.getRegions()));
        return group;
    }

    /** @return " and the N regions inside it", or nothing for a lone region */
    public static String nestedSuffix(List<Region> group) {
        final int inside = group.size() - 1;
        if (inside == 0) {
            return "";
        }
        return inside == 1 ? " and the region inside it" : " and the " + inside + " regions inside it";
    }

    /**
     * Opens the tag editor for a region that has already been resolved.
     *
     * @param actor  the acting player
     * @param region the region whose tags to edit
     */
    public void openTagEditor(Player actor, Region region) {
        // Opened unconditionally: even with no tag registered for this region name the editor is useful,
        // since it can write a free-form tag and can remove values already applied.
        guiManager.openTagEditor(actor, region, tagRegistry);
    }

    private void notifyNoRegion(Player player) {
        player.sendMessage(Component.text("No region found at this location.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private static @Nullable Location getTargetBlock(Player player) {
        final RayTraceResult result = rayTrace(player);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        return player.isSneaking()
                ? result.getHitBlock().getLocation().toCenterLocation()
                : result.getHitPosition().toLocation(result.getHitBlock().getWorld());
    }


    private static @Nullable RayTraceResult rayTrace(Player player) {
        return player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)).getValue()
                        + MIN_DISTANCE_TO_INTERACT,
                FluidCollisionMode.NEVER,
                true
        );
    }

    private boolean validate(Player player, String name) {
        if (name == null || name.isEmpty()) {
            player.sendMessage(Component.text("Please enter a name for the region.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return false;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        return true;
    }

    @Value
    private static class PendingDelete {
        UUID regionId;
        long expiresAt;
    }
}
