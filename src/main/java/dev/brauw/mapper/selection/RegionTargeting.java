package dev.brauw.mapper.selection;

import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PathRegion;
import dev.brauw.mapper.region.PointRegion;
import dev.brauw.mapper.region.PolygonRegion;
import dev.brauw.mapper.region.Region;
import lombok.Value;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Orders the regions a player's line of sight could mean, the most likely first.
 * <p>
 * Points and path waypoints are matched against the whole line of sight, not the block it lands on:
 * a point floats in the air, so the block behind it is almost never close enough to tell it apart
 * from the cuboid around it. They come first, nearest the eye first. Cuboids and polygons follow,
 * matched on the block the line lands on, smallest first.
 */
public final class RegionTargeting {

    /** How far from the line of sight a point still counts as aimed at. */
    static final double POINT_RADIUS = 0.5;

    private static final double MISSED = Double.MAX_VALUE;

    private RegionTargeting() {
    }

    /**
     * @param regions   the regions to choose from
     * @param eye       where the line of sight starts
     * @param direction the line of sight, normalised
     * @param reach     how far the line of sight goes when it hits no block
     * @param hit       where the line of sight hits a block, or {@code null} if it hits none
     * @return every region the line of sight could mean, the most likely first
     */
    public static List<Region> candidates(Collection<Region> regions, Vector eye, Vector direction,
                                          double reach, @Nullable Location hit) {
        final double limit = (hit == null ? reach : hit.toVector().distance(eye)) + POINT_RADIUS;
        final List<Aimed> points = new ArrayList<>();
        final List<Region> areas = new ArrayList<>();

        for (Region region : regions) {
            switch (region) {
                case PointRegion point -> {
                    final double distance = along(point.getLocation(), eye, direction, limit);
                    if (distance != MISSED) {
                        points.add(new Aimed(point, distance));
                    }
                }
                case PathRegion path -> {
                    double nearest = MISSED;
                    for (Location waypoint : path.getPoints()) {
                        nearest = Math.min(nearest, along(waypoint, eye, direction, limit));
                    }
                    if (nearest != MISSED) {
                        points.add(new Aimed(path, nearest));
                    }
                }
                default -> {
                    if (hit != null && region.contains(hit)) {
                        areas.add(region);
                    }
                }
            }
        }

        points.sort(Comparator.comparingDouble(Aimed::getDistance));
        areas.sort(Comparator.comparingDouble(RegionTargeting::volume));
        final List<Region> ordered = new ArrayList<>(points.size() + areas.size());
        points.forEach(aimed -> ordered.add(aimed.getRegion()));
        ordered.addAll(areas);
        return ordered;
    }

    /**
     * @return how far along the line of sight the location sits, or {@link #MISSED} if it is too far
     * off the line, behind the eye or past where the line ends
     */
    private static double along(Location location, Vector eye, Vector direction, double limit) {
        final Vector offset = location.toVector().subtract(eye);
        final double distance = offset.dot(direction);
        if (distance < 0 || distance > limit) {
            return MISSED;
        }
        final double offAxis = offset.clone().subtract(direction.clone().multiply(distance)).length();
        return offAxis <= POINT_RADIUS ? distance : MISSED;
    }

    private static double volume(Region region) {
        return switch (region) {
            case CuboidRegion cuboid -> volume(cuboid);
            case PolygonRegion polygon -> polygon.getChildren().stream()
                    .mapToDouble(RegionTargeting::volume)
                    .min()
                    .orElse(Double.MAX_VALUE);
            default -> Double.MAX_VALUE;
        };
    }

    /** Bounds are inclusive, so a single block has a volume of 1. */
    private static double volume(CuboidRegion cuboid) {
        final Location min = cuboid.getMin();
        final Location max = cuboid.getMax();
        return (max.getBlockX() - min.getBlockX() + 1.0)
                * (max.getBlockY() - min.getBlockY() + 1.0)
                * (max.getBlockZ() - min.getBlockZ() + 1.0);
    }

    @Value
    private static class Aimed {
        Region region;
        double distance;
    }
}
