package dev.brauw.mapper.selection;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.world.World;
import dev.brauw.mapper.command.RegionEditCommand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Moves selections between Mapper and WorldEdit (or FastAsyncWorldEdit, which provides the same
 * API).
 * <p>
 * WorldEdit is optional, so nothing else in Mapper names its types, and this class is only touched
 * once {@link RegionEditCommand#worldEditInstalled()} has said WorldEdit is there. Loading it
 * without WorldEdit fails, since its methods name WorldEdit's classes.
 */
public final class WorldEditBridge {

    private WorldEditBridge() {
    }

    /**
     * @param player the player
     * @return the two corners of the player's WorldEdit cuboid selection in their current world, or
     * {@code null} if they have no complete cuboid selection there
     */
    public static @Nullable List<Location> selection(Player player) {
        final World world = BukkitAdapter.adapt(player.getWorld());
        final LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        final Region region;
        try {
            region = session.getSelection(world);
        } catch (IncompleteRegionException e) {
            return null;
        }
        if (!(region instanceof CuboidRegion)) {
            return null;
        }
        final BlockVector3 min = region.getMinimumPoint();
        final BlockVector3 max = region.getMaximumPoint();
        return List.of(
                new Location(player.getWorld(), min.x(), min.y(), min.z()),
                new Location(player.getWorld(), max.x(), max.y(), max.z()));
    }

    /**
     * Sets the player's WorldEdit selection to a cuboid's blocks.
     *
     * @param player the player
     * @param min    the cuboid's minimum block
     * @param max    the cuboid's maximum block
     */
    public static void select(Player player, Location min, Location max) {
        final World world = BukkitAdapter.adapt(player.getWorld());
        final LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        final CuboidRegionSelector selector = new CuboidRegionSelector(world,
                BlockVector3.at(min.getBlockX(), min.getBlockY(), min.getBlockZ()),
                BlockVector3.at(max.getBlockX(), max.getBlockY(), max.getBlockZ()));
        session.setRegionSelector(world, selector);
        session.dispatchCUISelection(BukkitAdapter.adapt(player));
    }
}
