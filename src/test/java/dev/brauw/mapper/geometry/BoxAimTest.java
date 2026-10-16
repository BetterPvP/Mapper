package dev.brauw.mapper.geometry;

import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.RegionOptions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoxAimTest {

    private World world;
    private Box box;

    @BeforeEach
    void setUp() {
        final ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        // Blocks 0..9 on every axis, so the box runs from 0 to 10.
        box = Box.of(new CuboidRegion("mine", new Location(world, 0, 0, 0), new Location(world, 9, 9, 9)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCuboidsBoxCoversItsMaximumBlock() {
        assertEquals(new Vector(0, 0, 0), box.getMin());
        assertEquals(new Vector(10, 10, 10), box.getMax());
    }

    @Test
    void fromOutsideTheAimedFaceIsTheOneTheSightEnters() {
        final BoxAim aim = BoxAim.at(box, new Vector(-5, 5, 5), new Vector(1, 0, 0), 64);

        assertNotNull(aim);
        assertEquals(BoxFace.WEST, aim.getFace());
        assertEquals(5, aim.getDistance(), 1.0E-9);
    }

    @Test
    void fromInsideTheAimedFaceIsTheOneTheSightLeaves() {
        final BoxAim aim = BoxAim.at(box, new Vector(5, 5, 5), new Vector(0, 1, 0), 64);

        assertNotNull(aim);
        assertEquals(BoxFace.UP, aim.getFace());
    }

    @Test
    void aSightThatMissesHasNoFace() {
        assertNull(BoxAim.at(box, new Vector(-5, 20, 5), new Vector(1, 0, 0), 64));
    }

    @Test
    void aFacePastTheReachIsNotAimedAt() {
        assertNull(BoxAim.at(box, new Vector(-50, 5, 5), new Vector(1, 0, 0), 20));
    }

    @Test
    void draggingFollowsTheSightAlongTheFacesAxis() {
        // Standing south of the box, looking at the point (14, 5, 10): the east face should move to x = 14.
        final Vector eye = new Vector(14, 5, 20);
        final Vector direction = new Vector(0, 0, -1);

        final Double edge = BoxAim.drag(box, BoxFace.EAST, eye, direction);

        assertNotNull(edge);
        assertEquals(14, edge, 1.0E-9);
    }

    @Test
    void lookingStraightAlongTheAxisCannotDrag() {
        assertNull(BoxAim.drag(box, BoxFace.EAST, new Vector(20, 5, 5), new Vector(-1, 0, 0)));
    }

    @Test
    void movingAFaceSnapsToWholeBlocks() {
        final Box grown = box.withEdge(BoxFace.EAST, 13.6);

        assertEquals(14, grown.getMax().getX());
        assertEquals(0, grown.getMin().getX());
    }

    @Test
    void aFaceDraggedThroughTheOppositeOneStopsABlockShort() {
        final Box shrunk = box.withEdge(BoxFace.EAST, -5);
        final Box raised = box.withEdge(BoxFace.DOWN, 40);

        assertEquals(1, shrunk.getMax().getX());
        assertEquals(9, raised.getMin().getY());
    }

    @Test
    void aBoxTurnsBackIntoTheSameCuboid() {
        final CuboidRegion cuboid = box.withEdge(BoxFace.SOUTH, 12).toCuboid(UUID.randomUUID(), "mine",
                RegionOptions.builder().build());

        assertEquals(0, cuboid.getMin().getBlockZ());
        assertEquals(11, cuboid.getMax().getBlockZ());
        assertEquals(box.withEdge(BoxFace.SOUTH, 12), Box.of(cuboid));
    }
}
