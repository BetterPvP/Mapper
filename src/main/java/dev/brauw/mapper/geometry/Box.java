package dev.brauw.mapper.geometry;

import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.RegionOptions;
import lombok.Value;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * An axis-aligned box in world space, from its minimum corner to its maximum corner.
 * <p>
 * A cuboid region's bounds are inclusive block coordinates, so its box runs from its minimum block
 * to the far side of its maximum block. A region from (0, 0, 0) to (2, 0, 0) covers three blocks and
 * its box is three blocks long.
 */
@Value
public class Box {

    World world;
    Vector min;
    Vector max;

    /**
     * @param cuboid the cuboid
     * @return the box the cuboid covers
     */
    public static Box of(CuboidRegion cuboid) {
        final Vector min = cuboid.getMin().toBlockLocation().toVector();
        final Vector max = cuboid.getMax().toBlockLocation().toVector().add(new Vector(1, 1, 1));
        return new Box(cuboid.getWorld(), min, max);
    }

    /**
     * @param id      the region's id
     * @param name    the region's name
     * @param options the region's options
     * @return a cuboid region covering exactly this box's blocks
     */
    public CuboidRegion toCuboid(UUID id, String name, RegionOptions options) {
        return new CuboidRegion(id, name, min.toLocation(world),
                max.clone().subtract(new Vector(1, 1, 1)).toLocation(world), options);
    }

    /** @return how many blocks the box spans on each axis */
    public Vector size() {
        return max.clone().subtract(min);
    }

    /** @return the coordinate of a face along its axis */
    public double edge(BoxFace face) {
        return BoxFace.component(face.isPositive() ? max : min, face.getAxis());
    }

    /** @return the centre of a face */
    public Vector centre(BoxFace face) {
        final Vector centre = min.clone().add(max).multiply(0.5);
        BoxFace.setComponent(centre, face.getAxis(), edge(face));
        return centre;
    }

    /**
     * Moves one face to a new coordinate along its axis. The box keeps at least one block on that
     * axis, so a face dragged through the opposite one stops a block short of it.
     *
     * @param face the face to move
     * @param edge where it goes, rounded to the nearest block boundary
     * @return the resized box
     */
    public Box withEdge(BoxFace face, double edge) {
        final long rounded = Math.round(edge);
        final Vector newMin = min.clone();
        final Vector newMax = max.clone();
        if (face.isPositive()) {
            final double floor = BoxFace.component(min, face.getAxis()) + 1;
            BoxFace.setComponent(newMax, face.getAxis(), Math.max(rounded, floor));
        } else {
            final double ceiling = BoxFace.component(max, face.getAxis()) - 1;
            BoxFace.setComponent(newMin, face.getAxis(), Math.min(rounded, ceiling));
        }
        return new Box(world, newMin, newMax);
    }
}
