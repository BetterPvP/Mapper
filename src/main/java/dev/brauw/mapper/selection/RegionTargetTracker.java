package dev.brauw.mapper.selection;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PathRegion;
import dev.brauw.mapper.region.PointRegion;
import dev.brauw.mapper.region.PolygonRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.session.display.RegionDisplayStrategy;
import dev.brauw.mapper.tool.RegionToolManager;
import dev.brauw.mapper.tool.ToolRegistry.ToolType;
import dev.brauw.mapper.util.RegionFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Shows each editor which region their held tool would act on, before they click.
 * <p>
 * While a player holds the deletion, tag or clipboard tool, the region under their crosshair is
 * outlined for them alone and named in their action bar. When several regions sit under the
 * crosshair (a point inside a cuboid inside another), sneak + scroll steps through them, and the
 * click acts on the one outlined. The order comes from {@link RegionTargeting}.
 */
public class RegionTargetTracker {

    private static final double REACH_BONUS = 0.4;
    private static final float POINT_SIZE = 0.35f;
    private static final float OUTSET = 0.03f;
    private static final int ACTION_BAR_REFRESH_TICKS = 20;

    private final Mapper mapper;
    private final RegionToolManager tools;
    private final Map<UUID, Aim> aims = new HashMap<>();
    private int ticks;

    public RegionTargetTracker(Mapper mapper, RegionToolManager tools) {
        this.mapper = mapper;
        this.tools = tools;
    }

    /** Refreshes every editor's target. Runs every couple of ticks. */
    public void tick() {
        ticks++;
        aims.keySet().removeIf(playerId -> {
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                return false;
            }
            aims.get(playerId).clearHighlight();
            return true;
        });

        for (Player player : Bukkit.getOnlinePlayers()) {
            final EditSession session = editing(player);
            final ToolType tool = heldTool(player.getInventory().getItemInMainHand());
            if (session == null || tool == null) {
                clear(player);
                continue;
            }

            final Aim aim = aims.computeIfAbsent(player.getUniqueId(), id -> new Aim());
            final Region target = aim.aimAt(candidates(session, player));
            final boolean changed = aim.highlight(player, target);
            if (target != null && (changed || ticks % ACTION_BAR_REFRESH_TICKS == 0)) {
                player.sendActionBar(describe(tool, target, aim));
            }
        }
    }

    /**
     * Resolves the region a tool click should act on: the one outlined for the player, or the most
     * likely one if nothing is outlined yet.
     *
     * @param session the player's edit session
     * @param player  the acting player
     * @return the targeted region, or {@code null} if the crosshair is on none
     */
    public @Nullable Region target(EditSession session, Player player) {
        final Aim aim = aims.computeIfAbsent(player.getUniqueId(), id -> new Aim());
        return aim.aimAt(candidates(session, player));
    }

    /**
     * Steps the player's target to the next or previous region under their crosshair.
     *
     * @param player the player scrolling
     * @param step   {@code 1} for the next region, {@code -1} for the previous one
     * @return whether there was more than one region to step through
     */
    public boolean cycle(Player player, int step) {
        final Aim aim = aims.get(player.getUniqueId());
        if (aim == null || aim.candidates.size() < 2) {
            return false;
        }
        aim.index = Math.floorMod(aim.index + step, aim.candidates.size());
        final Region target = aim.candidates.get(aim.index);
        aim.highlight(player, target);
        final ToolType tool = heldTool(player.getInventory().getItemInMainHand());
        if (tool != null) {
            player.sendActionBar(describe(tool, target, aim));
        }
        return true;
    }

    /**
     * @param item the held item
     * @return the targeting tool it is, or {@code null} if it is none
     */
    public @Nullable ToolType heldTool(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        for (ToolType type : List.of(ToolType.REGION_DELETION_TOOL, ToolType.TAG_TOOL, ToolType.CLIPBOARD_TOOL)) {
            if (tools.isTool(item, type)) {
                return type;
            }
        }
        return null;
    }

    /** Removes the player's outline and forgets their target. */
    public void clear(Player player) {
        final Aim aim = aims.remove(player.getUniqueId());
        if (aim != null) {
            aim.clearHighlight();
        }
    }

    /** Removes every outline. Called when the host plugin disables. */
    public void shutdown() {
        aims.values().forEach(Aim::clearHighlight);
        aims.clear();
    }

    private @Nullable EditSession editing(Player player) {
        final EditSession session = mapper.getSessionManager().getSession(player);
        if (session == null || !session.getWorld().equals(player.getWorld()) || !session.canEdit(player)) {
            return null;
        }
        return session;
    }

    private static List<Region> candidates(EditSession session, Player player) {
        final Location eye = player.getEyeLocation();
        final double reach = Objects.requireNonNull(player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)).getValue()
                + REACH_BONUS;
        final RayTraceResult result = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), reach,
                FluidCollisionMode.NEVER, true);
        final Location hit = result == null ? null : result.getHitPosition().toLocation(player.getWorld());
        return RegionTargeting.candidates(session.getRegions(), eye.toVector(), eye.getDirection(), reach, hit);
    }

    private static Component describe(ToolType tool, Region target, Aim aim) {
        final String verb = switch (tool) {
            case REGION_DELETION_TOOL -> "Delete ";
            case TAG_TOOL -> "Tag ";
            default -> "Copy ";
        };
        Component line = Component.text(verb, NamedTextColor.GRAY).append(RegionFormat.describe(target));
        if (aim.candidates.size() > 1) {
            line = line.append(Component.text("  " + (aim.index + 1) + "/" + aim.candidates.size()
                    + " - sneak + scroll for the others", NamedTextColor.DARK_GRAY));
        }
        return line;
    }

    /** One player's view of the regions under their crosshair. */
    private final class Aim {

        private List<Region> candidates = List.of();
        private int index;
        private @Nullable Region outlined;
        private final List<BlockDisplay> outline = new ArrayList<>();

        /**
         * Takes a fresh list of candidates. The step the player scrolled to survives as long as the
         * same regions are under the crosshair; a different set starts over from the most likely.
         */
        private @Nullable Region aimAt(List<Region> fresh) {
            if (!sameRegions(candidates, fresh)) {
                index = 0;
            }
            candidates = fresh;
            if (candidates.isEmpty()) {
                return null;
            }
            index = Math.min(index, candidates.size() - 1);
            return candidates.get(index);
        }

        /**
         * Outlines {@code target} for the player alone.
         *
         * @return whether the outline changed
         */
        private boolean highlight(Player player, @Nullable Region target) {
            // Compared by identity: an edited region is replaced by a new object with the same id,
            // and its outline has to follow the new shape.
            if (target == outlined && outline.stream().allMatch(BlockDisplay::isValid)) {
                return false;
            }
            clearHighlight();
            outlined = target;
            if (target == null) {
                return true;
            }
            for (Box box : boxes(target)) {
                if (!RegionDisplayStrategy.canSpawnAt(box.origin)) {
                    continue;
                }
                final BlockDisplay display = box.origin.getWorld().spawn(box.origin, BlockDisplay.class, spawned -> {
                    spawned.setVisibleByDefault(false);
                    spawned.setPersistent(false);
                    spawned.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(Color.WHITE);
                    spawned.setBrightness(new Display.Brightness(15, 15));
                    spawned.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            box.size, new AxisAngle4f()));
                });
                player.showEntity(mapper.getPlugin(), display);
                outline.add(display);
            }
            return true;
        }

        private void clearHighlight() {
            outline.forEach(BlockDisplay::remove);
            outline.clear();
            outlined = null;
        }
    }

    private static boolean sameRegions(List<Region> a, List<Region> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getId().equals(b.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    private static List<Box> boxes(Region region) {
        return switch (region) {
            case PointRegion point -> List.of(Box.around(point.getLocation()));
            case PathRegion path -> path.getPoints().stream().map(Box::around).toList();
            case CuboidRegion cuboid -> List.of(Box.of(cuboid));
            case PolygonRegion polygon -> polygon.getChildren().stream().map(Box::of).toList();
            default -> List.of();
        };
    }

    /** A display-entity box: where its corner sits and how large it is. */
    private static final class Box {

        private final Location origin;
        private final Vector3f size;

        private Box(Location origin, Vector3f size) {
            this.origin = origin;
            this.size = size;
        }

        private static Box around(Location point) {
            final double half = POINT_SIZE / 2;
            return new Box(point.clone().subtract(half, half, half), new Vector3f(POINT_SIZE));
        }

        /** Covers the cuboid's whole blocks, since its bounds are inclusive, and stands slightly proud of them. */
        private static Box of(CuboidRegion cuboid) {
            final Location min = cuboid.getMin().toBlockLocation();
            final Location max = cuboid.getMax().toBlockLocation().add(1, 1, 1);
            final Vector span = max.toVector().subtract(min.toVector());
            return new Box(min.subtract(OUTSET, OUTSET, OUTSET),
                    new Vector3f((float) span.getX() + 2 * OUTSET, (float) span.getY() + 2 * OUTSET,
                            (float) span.getZ() + 2 * OUTSET));
        }
    }
}
