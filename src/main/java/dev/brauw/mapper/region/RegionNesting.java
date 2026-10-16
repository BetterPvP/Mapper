package dev.brauw.mapper.region;

import lombok.Value;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which regions sit inside which, from their shapes alone.
 * <p>
 * Nothing is stored: a region's parent is the smallest cuboid or polygon that holds the whole of it,
 * so a datapoint dropped inside a mine belongs to the mine, and stops belonging to it once moved
 * out. Saved files keep the flat list they always had.
 */
public final class RegionNesting {

    private RegionNesting() {
    }

    /**
     * @param region  the region
     * @param regions every region in the session
     * @return the smallest cuboid or polygon that holds the whole of {@code region}, or {@code null}
     * if none does
     */
    public static @Nullable Region parentOf(Region region, Collection<Region> regions) {
        Region parent = null;
        double parentVolume = Double.MAX_VALUE;
        final double ownVolume = volume(region);
        for (Region candidate : regions) {
            if (candidate.getId().equals(region.getId()) || !holds(candidate, region)) {
                continue;
            }
            final double candidateVolume = volume(candidate);
            // Strictly larger, so two identical cuboids don't each claim the other.
            if (candidateVolume <= ownVolume) {
                continue;
            }
            if (candidateVolume < parentVolume) {
                parent = candidate;
                parentVolume = candidateVolume;
            }
        }
        return parent;
    }

    /**
     * @param parent  the region
     * @param regions every region in the session
     * @return every region nested inside {@code parent} at any depth, in session order
     */
    public static List<Region> descendants(Region parent, Collection<Region> regions) {
        final Map<Region, Region> parents = parents(regions);
        final List<Region> nested = new ArrayList<>();
        for (Region region : regions) {
            Region ancestor = parents.get(region);
            while (ancestor != null) {
                if (ancestor.getId().equals(parent.getId())) {
                    nested.add(region);
                    break;
                }
                ancestor = parents.get(ancestor);
            }
        }
        return nested;
    }

    /**
     * Orders regions as a tree: each region is followed by the regions inside it. Regions whose
     * parent is not in {@code shown} start their own branch.
     *
     * @param shown   the regions to order
     * @param regions every region in the session, so parents are judged against the whole session
     * @return each region with its depth in the tree, parents before their children
     */
    public static List<Nested> tree(List<Region> shown, Collection<Region> regions) {
        final Map<Region, Region> parents = parents(regions);
        final Map<Region, List<Region>> children = new HashMap<>();
        final List<Region> roots = new ArrayList<>();
        for (Region region : shown) {
            final Region parent = parents.get(region);
            if (parent != null && shown.contains(parent)) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(region);
            } else {
                roots.add(region);
            }
        }

        final List<Nested> ordered = new ArrayList<>(shown.size());
        roots.forEach(root -> walk(root, 0, children, parents, ordered));
        return ordered;
    }

    /** @return how many blocks a region covers. Points and paths cover none. */
    public static double volume(Region region) {
        return switch (region) {
            case CuboidRegion cuboid -> volume(cuboid);
            case PolygonRegion polygon -> polygon.getChildren().stream().mapToDouble(RegionNesting::volume).sum();
            default -> 0;
        };
    }

    private static void walk(Region region, int depth, Map<Region, List<Region>> children,
                             Map<Region, Region> parents, List<Nested> into) {
        into.add(new Nested(region, depth, parents.get(region)));
        for (Region child : children.getOrDefault(region, List.of())) {
            walk(child, depth + 1, children, parents, into);
        }
    }

    private static Map<Region, Region> parents(Collection<Region> regions) {
        final Map<Region, Region> parents = new HashMap<>();
        for (Region region : regions) {
            final Region parent = parentOf(region, regions);
            if (parent != null) {
                parents.put(region, parent);
            }
        }
        return parents;
    }

    /** Whether an area holds the whole of another region. */
    private static boolean holds(Region area, Region region) {
        if (!(area instanceof CuboidRegion) && !(area instanceof PolygonRegion)) {
            return false;
        }
        return corners(region).stream().allMatch(area::contains);
    }

    /** The locations that have to sit inside an area for the region to count as inside it. */
    private static List<Location> corners(Region region) {
        return switch (region) {
            case PointRegion point -> List.of(point.getLocation());
            case PathRegion path -> path.getPoints();
            case CuboidRegion cuboid -> List.of(cuboid.getMin(), cuboid.getMax());
            case PolygonRegion polygon -> polygon.getChildren().stream()
                    .flatMap(child -> List.of(child.getMin(), child.getMax()).stream())
                    .toList();
            default -> List.of(RegionTransform.anchor(region));
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

    /** A region's place in the tree. */
    @Value
    public static class Nested {
        Region region;
        int depth;
        @Nullable Region parent;
    }
}
