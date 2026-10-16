package dev.brauw.mapper.selection;

import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PointRegion;
import dev.brauw.mapper.region.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTargetingTest {

    private static final Vector EYE = new Vector(0.5, 65.6, 0.5);
    private static final Vector EAST = new Vector(1, 0, 0);
    private static final double REACH = 5;

    private World world;

    @BeforeEach
    void setUp() {
        final ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    @Test
    void aPointInsideACuboidComesBeforeTheCuboid() {
        final CuboidRegion mine = new CuboidRegion("mine", at(-10, 60, -10), at(10, 70, 10));
        final PointRegion spawn = new PointRegion("spawn", at(3.2, 65.5, 0.6));

        final List<Region> candidates = RegionTargeting.candidates(List.of(mine, spawn), EYE, EAST, REACH,
                at(5, 65.6, 0.5));

        assertEquals(List.of(spawn, mine), candidates);
    }

    @Test
    void aPointOffTheLineOfSightIsNotAimedAt() {
        final PointRegion spawn = new PointRegion("spawn", at(3, 67, 0.5));

        final List<Region> candidates = RegionTargeting.candidates(List.of(spawn), EYE, EAST, REACH, null);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void aPointPastTheHitBlockIsNotAimedAt() {
        final PointRegion behindWall = new PointRegion("behind", at(4.5, 65.6, 0.5));

        final List<Region> candidates = RegionTargeting.candidates(List.of(behindWall), EYE, EAST, REACH,
                at(2, 65.6, 0.5));

        assertTrue(candidates.isEmpty());
    }

    @Test
    void nearerPointsComeFirst() {
        final PointRegion far = new PointRegion("far", at(4, 65.6, 0.5));
        final PointRegion near = new PointRegion("near", at(2, 65.6, 0.5));

        final List<Region> candidates = RegionTargeting.candidates(List.of(far, near), EYE, EAST, REACH, null);

        assertEquals(List.of(near, far), candidates);
    }

    @Test
    void smallerCuboidsComeBeforeTheOnesAroundThem() {
        final CuboidRegion outer = new CuboidRegion("outer", at(-10, 60, -10), at(10, 70, 10));
        final CuboidRegion inner = new CuboidRegion("inner", at(2, 64, -1), at(6, 67, 1));

        final List<Region> candidates = RegionTargeting.candidates(List.of(outer, inner), EYE, EAST, REACH,
                at(4, 65.6, 0.5));

        assertEquals(List.of(inner, outer), candidates);
    }

    @Test
    void aCuboidCountsTheRegionsInsideIt() {
        final CuboidRegion mine = new CuboidRegion("mine", at(-10, 60, -10), at(10, 70, 10));
        final PointRegion inside = new PointRegion("spawn", at(0, 65, 0));
        final PointRegion outside = new PointRegion("dock", at(50, 65, 0));

        assertEquals(1, RegionTargeting.countInside(mine, List.of(mine, inside, outside)));
        assertEquals(0, RegionTargeting.countInside(inside, List.of(mine, inside, outside)));
    }
}
