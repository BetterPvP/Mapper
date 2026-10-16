package dev.brauw.mapper.session.display;

import dev.brauw.mapper.geometry.Box;
import dev.brauw.mapper.geometry.BoxFace;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a box as its twelve edges, one thin glowing block display each, so the regions around and
 * inside it stay visible.
 * <p>
 * Each edge is spawned at its own start rather than all at the box's corner: display entities are
 * tracked by their own position, and a large box drawn from one corner vanishes for anyone standing
 * at the far end.
 */
public final class BoxOutline {

    /** How thick a region's edges are drawn. */
    public static final float EDGE = 0.06f;

    private BoxOutline() {
    }

    /**
     * Spawns the twelve edges of a box, hidden from everyone.
     *
     * @param box       the box
     * @param glow      the glow colour
     * @param thickness how thick each edge is
     * @return the spawned edges, none of them visible to anyone yet
     */
    public static List<BlockDisplay> spawn(Box box, Color glow, float thickness) {
        final Vector size = box.getMax().clone().subtract(box.getMin());
        final List<BlockDisplay> edges = new ArrayList<>(12);
        for (int a = 0; a <= 1; a++) {
            for (int b = 0; b <= 1; b++) {
                edges.add(edge(box, glow, thickness, new Vector(0, a * size.getY(), b * size.getZ()), new Vector(size.getX(), 0, 0)));
                edges.add(edge(box, glow, thickness, new Vector(a * size.getX(), 0, b * size.getZ()), new Vector(0, size.getY(), 0)));
                edges.add(edge(box, glow, thickness, new Vector(a * size.getX(), b * size.getY(), 0), new Vector(0, 0, size.getZ())));
            }
        }
        return edges;
    }

    /**
     * Spawns a thin flat panel covering one face of a box, hidden from everyone.
     *
     * @param box  the box
     * @param face the face to cover
     * @param glow the glow colour
     * @return the spawned panel
     */
    public static BlockDisplay spawnFace(Box box, BoxFace face, Color glow) {
        final Vector size = box.getMax().clone().subtract(box.getMin());
        final Vector start = box.getMin().clone();
        final Vector extent = size.clone();
        switch (face.getAxis()) {
            case X -> {
                start.setX((face.isPositive() ? box.getMax() : box.getMin()).getX());
                extent.setX(0);
            }
            case Y -> {
                start.setY((face.isPositive() ? box.getMax() : box.getMin()).getY());
                extent.setY(0);
            }
            case Z -> {
                start.setZ((face.isPositive() ? box.getMax() : box.getMin()).getZ());
                extent.setZ(0);
            }
        }
        return display(box.getWorld(), start, extent, glow, EDGE, Material.YELLOW_STAINED_GLASS);
    }

    /**
     * An outline is stale when an edge that should be showing is gone. An edge whose chunk is not
     * loaded is not valid either, but rebuilding for it would only spawn it into the same unloaded
     * chunk again, so it only counts once its chunk is back.
     *
     * @param outline the spawned edges
     * @return whether the outline needs rebuilding
     */
    public static boolean isStale(List<BlockDisplay> outline) {
        return outline.stream().anyMatch(edge -> !edge.isValid() && RegionDisplayStrategy.canSpawnAt(edge.getLocation()));
    }

    private static BlockDisplay edge(Box box, Color glow, float thickness, Vector offset, Vector extent) {
        return display(box.getWorld(), box.getMin().clone().add(offset), extent, glow, thickness, Material.WHITE_CONCRETE);
    }

    /**
     * Spawns a block display covering {@code start} to {@code start + extent}, thickened by
     * {@code thickness} on every axis so a zero-length side still shows.
     */
    private static BlockDisplay display(World world, Vector start, Vector extent, Color glow, float thickness,
                                        Material material) {
        final Location at = start.toLocation(world);
        final float half = thickness / 2;
        return world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
            spawned.setBlock(material.createBlockData());
            spawned.setGlowing(true);
            spawned.setGlowColorOverride(glow);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(new Transformation(
                    new Vector3f(-half, -half, -half),
                    new AxisAngle4f(),
                    new Vector3f((float) extent.getX() + thickness, (float) extent.getY() + thickness,
                            (float) extent.getZ() + thickness),
                    new AxisAngle4f()));
        });
    }
}
