package dev.brauw.mapper.session.display;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.brauw.mapper.geometry.Box;
import dev.brauw.mapper.region.CuboidRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a cuboid as the outline of the blocks it covers, with its name floating above it.
 */
public class BlockStrategy implements RegionDisplayStrategy<CuboidRegion> {

    private final Map<CuboidRegion, List<BlockDisplay>> displays = new HashMap<>();
    private final Map<CuboidRegion, TextDisplay> labels = new HashMap<>();
    // A set, not a list: showing a region to someone already viewing it (a member refreshing on
    // reconnect) must not leave a second entry that keeps the entity alive after they hide it.
    private final Multimap<CuboidRegion, UUID> viewers = HashMultimap.create();
    private final Plugin plugin;

    public BlockStrategy(Plugin plugin) {
        this.plugin = plugin;
    }

    private List<BlockDisplay> getOutline(CuboidRegion region) {
        final List<BlockDisplay> existing = displays.get(region);
        if (existing != null && BoxOutline.isStale(existing)) {
            despawnOutline(region);
        }

        return RegionDisplayStrategy.spawnIfAbsent(displays, region, key -> BoxOutline.spawn(
                Box.of(region), region.getOptions().getColor().getBukkitColor(), BoxOutline.EDGE));
    }

    private TextDisplay getLabel(CuboidRegion region) {
        final TextDisplay existingLabel = labels.get(region);
        if (existingLabel != null && !existingLabel.isValid()) {
            labels.remove(region);
        }

        final Box box = Box.of(region);
        final Location labelLocation = new Location(
                box.getWorld(),
                (box.getMin().getX() + box.getMax().getX()) / 2,
                box.getMax().getY() + 0.5,
                (box.getMin().getZ() + box.getMax().getZ()) / 2
        );
        final Color color = region.getOptions().getColor().getBukkitColor();

        return RegionDisplayStrategy.spawnIfAbsent(labels, region, key -> labelLocation.getWorld().spawn(labelLocation, TextDisplay.class, spawned -> {
            spawned.text(Component.text(region.getName()).color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        }));
    }

    @Override
    public void display(@NotNull CuboidRegion region, @NotNull Player player) {
        getOutline(region).forEach(edge -> player.showEntity(plugin, edge));
        player.showEntity(plugin, getLabel(region));
        viewers.put(region, player.getUniqueId());
    }

    @Override
    public void update(@NotNull CuboidRegion region) {
        despawn(region);
        final List<BlockDisplay> outline = getOutline(region);
        final TextDisplay label = getLabel(region);
        for (Player viewer : viewersOf(region)) {
            outline.forEach(edge -> viewer.showEntity(plugin, edge));
            viewer.showEntity(plugin, label);
        }
    }

    @Override
    public void revalidate(@NotNull CuboidRegion region) {
        final List<Player> currentViewers = viewersOf(region);
        if (currentViewers.isEmpty()) {
            return;
        }

        final List<BlockDisplay> outline = displays.get(region);
        final TextDisplay label = labels.get(region);
        // Missing counts as stale, not as "nothing to do". A viewer with no entity is exactly the
        // state a region ends up in when its chunk unloaded and something dropped the entry.
        if (outline == null || BoxOutline.isStale(outline) || label == null || !label.isValid()) {
            if (RegionDisplayStrategy.canSpawnAt(region.getMin())) {
                update(region);
            }
            return;
        }

        // Re-granted every pass rather than only after a rebuild. A showEntity grant is per-player
        // state that several things can quietly invalidate - the entity being respawned in a chunk
        // the viewer had not loaded, or a tracker that never sent it - and the symptom is a region
        // that stays invisible until the player leaves and rejoins the session. Re-asserting is
        // cheap and idempotent, so the sweep repairs that within a second instead of never.
        for (Player viewer : currentViewers) {
            outline.forEach(edge -> viewer.showEntity(plugin, edge));
            viewer.showEntity(plugin, label);
        }
    }

    @Override
    public void hide(@NotNull CuboidRegion region, @NotNull Player player) {
        if (!viewers.remove(region, player.getUniqueId())) {
            return;
        }

        final List<BlockDisplay> outline = displays.get(region);
        if (outline != null) {
            outline.forEach(edge -> player.hideEntity(plugin, edge));
        }
        final TextDisplay label = labels.get(region);
        if (label != null) {
            player.hideEntity(plugin, label);
        }

        if (viewers.get(region).isEmpty()) {
            despawn(region);
        }
    }

    /** @return the online players currently viewing this region */
    private List<Player> viewersOf(CuboidRegion region) {
        final List<Player> online = new ArrayList<>();
        for (UUID viewerId : viewers.get(region)) {
            final Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) {
                online.add(viewer);
            }
        }
        return online;
    }

    /**
     * Removal is unconditional. {@code isValid()} is false for an entity whose chunk is merely
     * unloaded, and dropping the map entry without removing such an entity strands it in the world
     * with nothing left holding a reference to it.
     */
    private void despawn(CuboidRegion region) {
        despawnOutline(region);
        final TextDisplay label = labels.remove(region);
        if (label != null) {
            label.remove();
        }
    }

    private void despawnOutline(CuboidRegion region) {
        final List<BlockDisplay> outline = displays.remove(region);
        if (outline != null) {
            outline.forEach(BlockDisplay::remove);
        }
    }
}
