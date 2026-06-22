package dev.brauw.mapper.session.display;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.brauw.mapper.region.CuboidRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Renders a glowing block at the cuboid's location.
 */
public class BlockStrategy implements RegionDisplayStrategy<CuboidRegion> {

    private final Map<CuboidRegion, BlockDisplay> displays = new HashMap<>();
    private final Map<CuboidRegion, TextDisplay> labels = new HashMap<>();
    private final Multimap<CuboidRegion, UUID> viewers = ArrayListMultimap.create();
    private final Plugin plugin;

    public BlockStrategy(Plugin plugin) {
        this.plugin = plugin;
    }

    private BlockDisplay getDisplay(CuboidRegion region) {
        final BlockDisplay existing = displays.get(region);
        if (existing != null && !existing.isValid()) {
            displays.remove(region);
        }

        final Location min = region.getMin();
        final Location max = region.getMax();
        final Location center = min.clone().add(
                (max.getX() - min.getX()) / 2,
                (max.getY() - min.getY()) / 2,
                (max.getZ() - min.getZ()) / 2
        );

        final float widthX = (float) Math.abs(max.getX() - min.getX());
        final float widthY = (float) Math.abs(max.getY() - min.getY());
        final float widthZ = (float) Math.abs(max.getZ() - min.getZ());

        return displays.computeIfAbsent(region, key -> {
            return center.getWorld().spawn(center, BlockDisplay.class, spawned -> {
                spawned.setGlowing(true);
                spawned.setVisibleByDefault(false);
                spawned.setGlowColorOverride(region.getOptions().getColor().getBukkitColor());

                // Block data
                spawned.setBlock(Material.TINTED_GLASS.createBlockData());
                spawned.setTransformation(new Transformation(
                        new Vector3f(widthX / 2, widthY / 2, widthZ / 2).mul(-1),
                        new AxisAngle4f(),
                        new Vector3f(widthX, widthY, widthZ),
                        new AxisAngle4f()
                ));

                // Important, because we don't want the display to be saved in case the server shuts down
                spawned.setPersistent(false);
            });
        });
    }

    private TextDisplay getLabel(CuboidRegion region) {
        final TextDisplay existingLabel = labels.get(region);
        if (existingLabel != null && !existingLabel.isValid()) {
            labels.remove(region);
        }

        final Location min = region.getMin();
        final Location max = region.getMax();
        final Location labelLocation = new Location(
                min.getWorld(),
                (min.getX() + max.getX()) / 2,
                max.getY() + 0.5,
                (min.getZ() + max.getZ()) / 2
        );
        final Color color = region.getOptions().getColor().getBukkitColor();

        return labels.computeIfAbsent(region, key -> labelLocation.getWorld().spawn(labelLocation, TextDisplay.class, spawned -> {
            spawned.text(Component.text(region.getName()).color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        }));
    }

    @Override
    public void display(@NotNull CuboidRegion region, @NotNull Player player) {
        // Get or create the block display for this region
        final BlockDisplay blockDisplay = getDisplay(region);

        // Show the display and label to the player
        player.showEntity(plugin, blockDisplay);
        player.showEntity(plugin, getLabel(region));
        viewers.put(region, player.getUniqueId());
    }

    @Override
    public void update(@NotNull CuboidRegion region, @NotNull Player player) {
        final BlockDisplay removed = displays.remove(region);
        if (removed != null && removed.isValid()) {
            removed.remove();
        }
        final TextDisplay removedLabel = labels.remove(region);
        if (removedLabel != null && removedLabel.isValid()) {
            removedLabel.remove();
        }

        display(region, player);
    }

    @Override
    public void revalidate(@NotNull CuboidRegion region, @NotNull Player player) {
        final BlockDisplay entity = displays.get(region);
        final TextDisplay label = labels.get(region);
        boolean needsRefresh = (entity != null && !entity.isValid()) || (label != null && !label.isValid());
        if (needsRefresh) {
            if (entity != null && !entity.isValid()) displays.remove(region);
            if (label != null && !label.isValid()) labels.remove(region);
            player.showEntity(plugin, getDisplay(region));
            player.showEntity(plugin, getLabel(region));
        }
    }

    @Override
    public void hide(@NotNull CuboidRegion region, @NotNull Player player) {
        final UUID playerUUID = player.getUniqueId();
        if (viewers.remove(region, playerUUID)) {
            // Hide entity if we could remove them as viewers
            final BlockDisplay entity = Objects.requireNonNull(displays.get(region));
            player.hideEntity(plugin, entity);

            final TextDisplay label = labels.get(region);
            if (label != null) {
                player.hideEntity(plugin, label);
            }

            // If there are no more viewers, remove the displays
            if (viewers.get(region).isEmpty()) {
                final BlockDisplay blockDisplay = displays.remove(region);
                if (blockDisplay != null && blockDisplay.isValid()) {
                    blockDisplay.remove();
                }
                final TextDisplay removedLabel = labels.remove(region);
                if (removedLabel != null && removedLabel.isValid()) {
                    removedLabel.remove();
                }
            }
        }
    }
}
