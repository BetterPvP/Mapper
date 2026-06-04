package dev.brauw.mapper.selection;

import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PointRegion;
import dev.brauw.mapper.region.PolygonRegion;
import dev.brauw.mapper.region.PerspectiveRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionOptions;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tag.TagRegistry;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Handles the selection and creation of regions based on player interactions.
 * This class manages the selection corners for cuboid regions and provides
 * methods for creating different types of regions (Cuboid, Point, Perspective).
 */
@RequiredArgsConstructor
public class SelectionHandler {

    private static final double MIN_DISTANCE_TO_INTERACT = 0.4;

    private final GuiManager guiManager;
    private final TagRegistry tagRegistry;
    private final Map<Player, SelectionCorners> selections = new WeakHashMap<>();
    private final Map<Player, List<CuboidRegion>> polygonSelections = new WeakHashMap<>();

    /**
     * Retrieves the SelectionCorners object for a given player.
     * If the player does not have a SelectionCorners object yet, it creates a new one.
     *
     * @param player The player for whom to retrieve the SelectionCorners.
     * @return The SelectionCorners object for the player.
     */
    private SelectionCorners getSelection(Player player) {
        return selections.computeIfAbsent(player, key -> new SelectionCorners());
    }

    private List<CuboidRegion> getPolygonSelection(Player player) {
        return polygonSelections.computeIfAbsent(player, key -> new ArrayList<>());
    }

    private void clearSelections(Player player) {
        selections.remove(player);
        polygonSelections.remove(player);
    }

    public boolean hasCompleteSelection(EditSession session) {
        return getSelection(session.getOwner()).isComplete();
    }

    /**
     * Sets the first position for a cuboid region selection.
     * This method is called when a player interacts with the world to define the first corner of a region.
     *
     * @param session The EditSession for the player.
     * @param event   The PlayerInteractEvent containing information about the interaction.
     */
    public void setFirstPosition(EditSession session, PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location location = getTargetBlock(player);
        if (location == null) return;

        getSelection(player).setFirstCorner(location);

        player.sendMessage(Component.text("First position set ", NamedTextColor.GREEN)
                .append(formatLocation(location)));
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.0f);
    }

    /**
     * Sets the second position for a cuboid region selection.
     * This method is called when a player interacts with the world to define the second corner of a region.
     *
     * @param session The EditSession for the player.
     * @param event   The PlayerInteractEvent containing information about the interaction.
     */
    public void setSecondPosition(EditSession session, PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Location location = getTargetBlock(player);
        if (location == null) return;

        getSelection(player).setSecondCorner(location);

        player.sendMessage(Component.text("Second position set ", NamedTextColor.YELLOW)
                .append(formatLocation(location)));
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.5f);
    }

    /**
     * Creates a cuboid region based on the selected positions.
     * This method checks if both positions have been set and then opens the GUI
     * to create the region with a name and options.
     *
     * @param session The EditSession for the player.
     */
    public void createCuboidRegion(EditSession session) {
        Player player = session.getOwner();

        final SelectionCorners selection = getSelection(player);
        if (!selection.isComplete()) {
            player.sendMessage(Component.text("You need to set both positions first!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final Location first = selection.getFirstCorner();
        final Location second = selection.getSecondCorner();
        player.sendMessage(Component.text("Creating cuboid region...", NamedTextColor.YELLOW));
        guiManager.openRegionCreateGui(session, (name, options) -> {
            if (!validate(player, name, options)) {
                return;
            }

            CuboidRegion region = new CuboidRegion(name, first, second, options);
            session.addRegion(region);

            clearSelections(player);
        }, () -> {
            clearSelections(player);
        });
    }

    public void addPolygonChild(EditSession session) {
        Player player = session.getOwner();
        SelectionCorners selection = getSelection(player);
        if (!selection.isComplete()) {
            player.sendMessage(Component.text("You need to set both positions before adding a polygon part.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        Location first = selection.getFirstCorner();
        Location second = selection.getSecondCorner();
        List<CuboidRegion> children = getPolygonSelection(player);
        children.add(new CuboidRegion("polygon-child-" + children.size(), first, second));
        selections.remove(player);

        player.sendMessage(Component.text("Added polygon part ", NamedTextColor.GREEN)
                .append(Component.text("#" + children.size(), NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_STEP, 1.0f, 1.2f);
    }

    public void createPolygonRegion(EditSession session) {
        Player player = session.getOwner();
        List<CuboidRegion> children = polygonSelections.get(player);
        if (children == null || children.isEmpty()) {
            player.sendMessage(Component.text("Add at least one cuboid part before creating a polygon region.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        if (getSelection(player).isComplete()) {
            player.sendMessage(Component.text("Finish the current cuboid part first by sneaking and right-clicking again.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        List<CuboidRegion> snapshot = List.copyOf(children);
        player.sendMessage(Component.text("Creating polygon region...", NamedTextColor.YELLOW));
        guiManager.openRegionCreateGui(session, (name, options) -> {
            if (!validate(player, name, options)) {
                return;
            }

            PolygonRegion region = new PolygonRegion(name, snapshot, options);
            session.addRegion(region);
            clearSelections(player);
        }, () -> clearSelections(player));
    }

    /**
     * Creates a point region at the specified location.
     * This method opens the GUI to create the region with a name and options.
     *
     * @param session  The EditSession for the player.
     * @param location The location at which to create the point region.
     */
    public void createPointRegion(EditSession session, Location location) {
        final Player player = session.getOwner();
        final Location target = player.isSneaking() ? player.getLocation() : location;

        if (target == null) {
            return;
        }

        target.setYaw(0);
        target.setPitch(0);
        guiManager.openRegionCreateGui(session, (name, options) -> {
            if (!validate(session.getOwner(), name, options)) {
                return;
            }

            PointRegion region = new PointRegion(name, target, options);
            session.addRegion(region);
        }, () -> {
            clearSelections(session.getOwner());
        });
    }

    /**
     * Creates a perspective region at the specified location.
     * This method opens the GUI to create the region with a name and options.
     *
     * @param session  The EditSession for the player.
     * @param location The location at which to create the perspective region.
     */
    public void createPerspectiveRegion(EditSession session, Location location) {
        final Player player = session.getOwner();
        final Location target = player.isSneaking() ? player.getLocation() : location;

        if (target == null) {
            return;
        }

        target.setDirection(player.getLocation().getDirection());
        guiManager.openRegionCreateGui(session, (name, options) -> {
            if (!validate(player, name, options)) {
                return;
            }

            PerspectiveRegion region = new PerspectiveRegion(name, target, options);
            session.addRegion(region);
        }, () -> {
            clearSelections(player);
        });
    }

    /**
     * Handles the deletion of a region based on player interaction.
     * This method ray traces to find the block the player is looking at and deletes
     * the region at that location, if one exists.
     *
     * @param session The EditSession for the player.
     * @param event   The PlayerInteractEvent containing information about the interaction.
     */
    public void handleRegionDeletion(EditSession session, PlayerInteractEvent event) {
        Player player = session.getOwner();
        final Location location = getTargetPoint(player);
        if (location == null) return;

        findRegionAt(session, location)
                .ifPresentOrElse(
                        region -> deleteRegion(session, region),
                        () -> notifyNoRegion(player)
                );
    }

    public void handleTagEditor(EditSession session, PlayerInteractEvent event) {
        Player player = session.getOwner();
        final Location location = getTargetPoint(player);
        if (location == null) return;

        findRegionAt(session, location)
                .ifPresentOrElse(
                        region -> openTagEditor(session, region),
                        () -> notifyNoRegion(player)
                );
    }

    /**
     * Finds the region the player is targeting at the given location, resolving
     * overlaps in favour of the most specific (smallest) region. This is what
     * lets a player select a label region nested inside a larger cuboid instead
     * of always grabbing the enclosing cuboid.
     *
     * @param session  the edit session whose regions to search
     * @param location the targeted location
     * @return the most specific region containing the location, if any
     */
    private Optional<Region> findRegionAt(EditSession session, Location location) {
        return session.getRegions().stream()
                .filter(region -> region.contains(location)
                        || (region instanceof PointRegion point && point.getLocation().distance(location) < 0.2))
                .min(Comparator.comparingDouble(this::regionSpecificity));
    }

    /**
     * Computes a specificity score for a region: lower means "more specific" and
     * should win when regions overlap. A point is the most specific thing a
     * player can target; a large cuboid the least.
     *
     * <ul>
     *   <li>{@link PointRegion} / {@link PerspectiveRegion} → 0 (always wins)</li>
     *   <li>{@link CuboidRegion} → its block volume (inclusive bounds)</li>
     *   <li>{@link PolygonRegion} → the volume of its smallest child, since the
     *       player is standing inside exactly one child cuboid</li>
     * </ul>
     *
     * @param region the region to score
     * @return the specificity score; smaller wins
     */
    private double regionSpecificity(Region region) {
        return switch (region) {
            case CuboidRegion cuboid -> cuboidVolume(cuboid);
            case PolygonRegion polygon -> polygon.getChildren().stream()
                    .mapToDouble(this::cuboidVolume)
                    .min()
                    .orElse(Double.MAX_VALUE);
            default -> 0; // PointRegion / PerspectiveRegion: point-like, always most specific
        };
    }

    /**
     * Computes the inclusive block volume of a cuboid. Bounds are inclusive
     * (see {@link CuboidRegion#contains}), so each span gets a +1 and a single
     * block scores 1 rather than 0.
     *
     * @param cuboid the cuboid to measure
     * @return the block volume
     */
    private double cuboidVolume(CuboidRegion cuboid) {
        Location min = cuboid.getMin();
        Location max = cuboid.getMax();
        double width = max.getBlockX() - min.getBlockX() + 1;
        double height = max.getBlockY() - min.getBlockY() + 1;
        double depth = max.getBlockZ() - min.getBlockZ() + 1;
        return width * height * depth;
    }

    /**
     * Deletes a region that has already been resolved (e.g. from an entity click),
     * skipping the raytrace + "no region found" path.
     *
     * @param session the owning edit session
     * @param region  the region to delete
     */
    public void deleteRegion(EditSession session, Region region) {
        Player player = session.getOwner();
        session.removeRegion(region);
        player.sendMessage(Component.text("Region deleted: ", NamedTextColor.RED)
                .append(Component.text(region.getName(), NamedTextColor.DARK_RED)));
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.0f);
    }

    /**
     * Opens the tag editor for a region that has already been resolved (e.g. from
     * an entity click), skipping the raytrace + "no region found" path.
     *
     * @param session the owning edit session
     * @param region  the region whose tags to edit
     */
    public void openTagEditor(EditSession session, Region region) {
        Player player = session.getOwner();
        if (!tagRegistry.hasTags(region.getName())) {
            player.sendMessage(Component.text("No tags available for ", NamedTextColor.RED)
                    .append(Component.text("'" + region.getName() + "'", NamedTextColor.DARK_RED))
                    .append(Component.text(".", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        guiManager.openTagEditor(player, region, tagRegistry);
    }

    private void notifyNoRegion(Player player) {
        player.sendMessage(Component.text("No region found at this location.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }

    private static @Nullable Location getTargetBlock(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                (Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)).getValue() + MIN_DISTANCE_TO_INTERACT),
                FluidCollisionMode.NEVER,
                true
        );

        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        return player.isSneaking() ? result.getHitBlock().getLocation().toCenterLocation() : result.getHitPosition().toLocation(result.getHitBlock().getWorld());
    }

    private static @Nullable Location getTargetPoint(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                (Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)).getValue() + MIN_DISTANCE_TO_INTERACT),
                FluidCollisionMode.NEVER,
                true
        );

        if (result == null) {
            return null;
        }

        return result.getHitPosition().toLocation(player.getWorld());
    }

    /**
     * Formats a Location object into a Component for displaying coordinates.
     *
     * @param loc The Location to format.
     * @return A Component containing the formatted location coordinates.
     */
    private Component formatLocation(Location loc) {
        return Component.text(String.format("(%.1f, %.1f, %.1f)",
                loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY);
    }

    private boolean validate(Player player, String name, RegionOptions regionOptions) {
        if (name == null || name.isEmpty()) {
            player.sendMessage(Component.text("Please enter a name for the region.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            return false;
        }

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        return true;
    }
}
