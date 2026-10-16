package dev.brauw.mapper.region;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Produces moved, mirrored and duplicated copies of regions.
 * <p>
 * Regions are immutable once built, so every operation here returns a new instance rather than
 * editing one in place. Moving a region keeps its id - it is still the same region, somewhere else,
 * and anything referring to it by id should follow it. Duplicating mints a new id, because the
 * result is a second region that happens to look like the first.
 */
public final class RegionTransform {

    private RegionTransform() {
    }

    /**
     * The single point that best stands for a region's position: where {@code /mapper goto} puts
     * you, and what an offset is measured from.
     *
     * @param region the region to locate
     * @return a representative location - a point's own location, a cuboid's centre, a polygon's
     * overall centre, or a path's first waypoint
     */
    public static Location anchor(Region region) {
        return switch (region) {
            case PointRegion point -> point.getLocation();
            case CuboidRegion cuboid -> centre(cuboid.getMin(), cuboid.getMax());
            case PolygonRegion polygon -> polygonCentre(polygon);
            case PathRegion path -> path.getPoints().getFirst();
            default -> throw new IllegalArgumentException("No anchor defined for " + region.getType());
        };
    }

    /**
     * Moves a region by a delta, keeping its identity.
     *
     * @param region the region to move
     * @param delta  how far to move it
     * @return the moved region
     */
    public static Region translate(Region region, Vector delta) {
        return rebuild(region, region.getId(), region.getName(),
                location -> location.clone().add(delta));
    }

    /**
     * Mirrors a region across an axis-aligned plane, keeping its identity.
     * <p>
     * Facing is mirrored too, so a perspective region that looked at the plane still looks at it
     * from the other side, and a path's waypoints still face along the route.
     *
     * @param region the region to mirror
     * @param axis   the axis the plane is perpendicular to
     * @param plane  the coordinate of the plane on that axis
     * @return the mirrored region
     */
    public static Region mirror(Region region, Axis axis, double plane) {
        return rebuild(region, region.getId(), region.getName(),
                location -> mirrorLocation(location, axis, plane));
    }

    /**
     * Duplicates a region at an offset under a new identity.
     *
     * @param region the region to duplicate
     * @param delta  how far from the original to place the copy
     * @param name   the name for the copy
     * @return the new region
     */
    public static Region duplicate(Region region, Vector delta, String name) {
        return duplicate(region, delta, 0, name);
    }

    /**
     * Duplicates a region turned to face a new direction, then moved into place.
     * <p>
     * The turn happens about the region's own {@link #anchor}, so its shape is carried over intact
     * and only its bearing changes - a path copied running north can be dropped running east and
     * still be the same route. Waypoint facings turn with it, so anything walking the copy still
     * looks along it.
     *
     * @param region the region to duplicate
     * @param delta  how far from the original to place the copy, measured from its anchor
     * @param yaw    how far to turn it, in degrees clockwise
     * @param name   the name for the copy
     * @return the new region
     */
    public static Region duplicate(Region region, Vector delta, float yaw, String name) {
        return duplicate(region, delta, yaw, anchor(region), name);
    }

    /**
     * Duplicates a region turned about a given pivot, then moved into place. Copying a region along
     * with the regions inside it turns them all about the outer region's anchor, so they keep their
     * places relative to each other.
     *
     * @param region the region to duplicate
     * @param delta  how far from the original to place the copy
     * @param yaw    how far to turn it, in degrees clockwise
     * @param pivot  the point to turn it about
     * @param name   the name for the copy
     * @return the new region
     */
    public static Region duplicate(Region region, Vector delta, float yaw, Location pivot, String name) {
        return rebuild(region, UUID.randomUUID(), name,
                location -> rotateAround(location, pivot, yaw).add(delta));
    }

    /**
     * Rebuilds a region of any type with every one of its locations passed through {@code mapper}.
     * <p>
     * This is where each region type's structure is handled once, so {@link #translate},
     * {@link #mirror} and {@link #duplicate} are each a one-line choice of mapper and identity
     * rather than five type cases apiece.
     * <p>
     * Options are copied rather than carried over: their tag set is mutable and the tag editor
     * writes to it in place, so a shared instance would tag a duplicate and its source together.
     *
     * @param region the region to rebuild
     * @param id     the id the result should carry
     * @param name   the name the result should carry
     * @param mapper maps each of the region's locations to its new position
     * @return the rebuilt region
     */
    private static Region rebuild(Region region, UUID id, String name, UnaryOperator<Location> mapper) {
        return switch (region) {
            // Checked before PointRegion: a perspective region is a point region, and matching it as
            // one would silently drop the facing that makes it a different type.
            case PerspectiveRegion perspective -> {
                final Location moved = mapper.apply(perspective.getLocation());
                yield new PerspectiveRegion(id, name, moved, perspective.getOptions().copy(),
                        moved.getYaw(), moved.getPitch());
            }
            case PointRegion point -> new PointRegion(id, name, mapper.apply(point.getLocation()),
                    point.getOptions().copy());
            case CuboidRegion cuboid -> rebuildCuboid(cuboid, id, name, mapper);
            case PolygonRegion polygon -> {
                final List<CuboidRegion> children = new ArrayList<>(polygon.getChildren().size());
                for (CuboidRegion child : polygon.getChildren()) {
                    children.add(rebuildCuboid(child, child.getId(), child.getName(), mapper));
                }
                yield new PolygonRegion(id, name, children, polygon.getOptions().copy());
            }
            case PathRegion path -> {
                final List<Location> points = new ArrayList<>(path.size());
                for (Location point : path.getPoints()) {
                    points.add(mapper.apply(point));
                }
                yield new PathRegion(id, name, points, path.getOptions().copy());
            }
            default -> throw new IllegalArgumentException("Cannot transform " + region.getType());
        };
    }

    /**
     * Rebuilds a cuboid, re-deriving min and max after the mapping. A mirror swaps which corner is
     * lower on the mirrored axis, and the id-carrying constructor stores min and max verbatim, so
     * skipping this would leave a cuboid whose "min" is above its "max" and which therefore contains
     * nothing.
     */
    private static CuboidRegion rebuildCuboid(CuboidRegion cuboid, UUID id, String name, UnaryOperator<Location> mapper) {
        final Location first = mapper.apply(cuboid.getMin());
        final Location second = mapper.apply(cuboid.getMax());
        final Location min = new Location(first.getWorld(),
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        final Location max = new Location(first.getWorld(),
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        return new CuboidRegion(id, name, min, max, cuboid.getOptions().copy());
    }

    /**
     * Reflects a location across an axis-aligned plane, including its facing.
     * <p>
     * Minecraft's yaw runs clockwise from south, so its direction vector is
     * {@code (-sin yaw, ., cos yaw)}. Negating x therefore negates yaw, and negating z reflects it
     * about 180. Negating y touches only pitch.
     */
    private static Location mirrorLocation(Location location, Axis axis, double plane) {
        final Location mirrored = location.clone();
        switch (axis) {
            case X -> {
                mirrored.setX(2 * plane - location.getX());
                mirrored.setYaw(-location.getYaw());
            }
            case Y -> {
                mirrored.setY(2 * plane - location.getY());
                mirrored.setPitch(-location.getPitch());
            }
            case Z -> {
                mirrored.setZ(2 * plane - location.getZ());
                mirrored.setYaw(180 - location.getYaw());
            }
        }
        return mirrored;
    }

    /**
     * Turns a location about a vertical axis through {@code pivot}, facing included.
     * <p>
     * Minecraft's yaw runs clockwise from south while {@link Vector#rotateAroundY} turns
     * anticlockwise, so the angle is negated to make the offset follow the same turn the yaw does.
     */
    private static Location rotateAround(Location location, Location pivot, float yaw) {
        if (yaw == 0) {
            return location.clone();
        }
        final Vector offset = location.toVector().subtract(pivot.toVector())
                .rotateAroundY(-Math.toRadians(yaw));

        final Location rotated = pivot.clone().add(offset);
        rotated.setYaw(location.getYaw() + yaw);
        rotated.setPitch(location.getPitch());
        return rotated;
    }

    private static Location centre(Location min, Location max) {
        return new Location(min.getWorld(),
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
    }

    private static Location polygonCentre(PolygonRegion polygon) {
        final List<CuboidRegion> children = polygon.getChildren();
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (CuboidRegion child : children) {
            minX = Math.min(minX, child.getMin().getX());
            minY = Math.min(minY, child.getMin().getY());
            minZ = Math.min(minZ, child.getMin().getZ());
            maxX = Math.max(maxX, child.getMax().getX());
            maxY = Math.max(maxY, child.getMax().getY());
            maxZ = Math.max(maxZ, child.getMax().getZ());
        }
        return new Location(polygon.getWorld(), (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }
}
