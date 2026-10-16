package dev.brauw.mapper.region;

import dev.brauw.mapper.session.SessionMember;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionNestingTest {

    private World world;
    private CuboidRegion mine;
    private CuboidRegion shaft;
    private PointRegion spawn;
    private PointRegion ore;
    private PointRegion dock;
    private List<Region> regions;

    @BeforeEach
    void setUp() {
        final ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        mine = new CuboidRegion("starter_mine", at(0, 40, 0), at(30, 70, 30));
        shaft = new CuboidRegion("shaft", at(10, 40, 10), at(14, 60, 14));
        spawn = new PointRegion("mine_spawn", at(5, 65, 5));
        ore = new PointRegion("ore_node", at(12, 45, 12));
        dock = new PointRegion("dock", at(100, 64, 100));
        regions = List.of(mine, shaft, spawn, ore, dock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    @Test
    void aRegionsParentIsTheSmallestAreaHoldingIt() {
        assertEquals(mine, RegionNesting.parentOf(spawn, regions));
        assertEquals(shaft, RegionNesting.parentOf(ore, regions));
        assertEquals(mine, RegionNesting.parentOf(shaft, regions));
        assertNull(RegionNesting.parentOf(dock, regions));
        assertNull(RegionNesting.parentOf(mine, regions));
    }

    @Test
    void aCuboidSpillingOutOfAnotherIsNotInsideIt() {
        final CuboidRegion spill = new CuboidRegion("spill", at(25, 50, 25), at(40, 55, 40));

        assertNull(RegionNesting.parentOf(spill, List.of(mine, spill)));
    }

    @Test
    void identicalCuboidsDoNotClaimEachOther() {
        final CuboidRegion twin = new CuboidRegion("twin", at(0, 40, 0), at(30, 70, 30));

        assertNull(RegionNesting.parentOf(twin, List.of(mine, twin)));
        assertNull(RegionNesting.parentOf(mine, List.of(mine, twin)));
    }

    @Test
    void descendantsIncludeEveryDepth() {
        assertEquals(List.of(shaft, spawn, ore), RegionNesting.descendants(mine, regions));
        assertEquals(List.of(ore), RegionNesting.descendants(shaft, regions));
    }

    @Test
    void theTreeListsChildrenAfterTheirParent() {
        final List<RegionNesting.Nested> tree = RegionNesting.tree(regions, regions);

        assertEquals(List.of(mine, shaft, ore, spawn, dock), tree.stream().map(RegionNesting.Nested::getRegion).toList());
        assertEquals(List.of(0, 1, 2, 1, 0), tree.stream().map(RegionNesting.Nested::getDepth).toList());
    }

    @Test
    void aFilterMatchesNamesAndTagsIgnoringCase() {
        ore.getOptions().getTags().add("type=copper");

        assertTrue(SessionMember.matches(spawn, "MINE"));
        assertTrue(SessionMember.matches(ore, "copper"));
        assertFalse(SessionMember.matches(dock, "mine"));
    }
}
