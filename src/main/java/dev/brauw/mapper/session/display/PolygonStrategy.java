package dev.brauw.mapper.session.display;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.PolygonRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a polygon region to the player.
 * <p>
 * Delegates to the {@link BlockStrategy} to render the polygon as a cuboid.
 */
public class PolygonStrategy implements RegionDisplayStrategy<PolygonRegion> {

    private final BlockStrategy blockStrategy;
    private final Plugin plugin;
    private final Map<PolygonRegion, TextDisplay> labels = new HashMap<>();
    private final Multimap<PolygonRegion, UUID> viewers = ArrayListMultimap.create();

    public PolygonStrategy(Plugin plugin, BlockStrategy blockStrategy) {
        this.plugin = plugin;
        this.blockStrategy = blockStrategy;
    }

    private TextDisplay getLabel(PolygonRegion region) {
        final List<CuboidRegion> children = region.getChildren();
        double minX = children.stream().mapToDouble(c -> c.getMin().getX()).min().orElse(0);
        double maxX = children.stream().mapToDouble(c -> c.getMax().getX()).max().orElse(0);
        double minZ = children.stream().mapToDouble(c -> c.getMin().getZ()).min().orElse(0);
        double maxZ = children.stream().mapToDouble(c -> c.getMax().getZ()).max().orElse(0);
        double maxY = children.stream().mapToDouble(c -> c.getMax().getY()).max().orElse(0);

        final Location labelLocation = new Location(
                region.getWorld(),
                (minX + maxX) / 2,
                maxY + 0.5,
                (minZ + maxZ) / 2
        );
        final Color color = region.getOptions().getColor().getBukkitColor();

        final TextDisplay existingLabel = labels.get(region);
        if (existingLabel != null && !existingLabel.isValid()) {
            labels.remove(region);
        }

        return labels.computeIfAbsent(region, key -> labelLocation.getWorld().spawn(labelLocation, TextDisplay.class, spawned -> {
            spawned.text(Component.text(region.getName()).color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        }));
    }

    @Override
    public void display(@NotNull PolygonRegion region, @NotNull Player player) {
        region.getChildren().forEach(child -> blockStrategy.display(child, player));
        player.showEntity(plugin, getLabel(region));
        viewers.put(region, player.getUniqueId());
    }

    @Override
    public void update(@NotNull PolygonRegion region, @NotNull Player player) {
        region.getChildren().forEach(child -> blockStrategy.update(child, player));
        final TextDisplay removedLabel = labels.remove(region);
        if (removedLabel != null && removedLabel.isValid()) {
            removedLabel.remove();
        }
        player.showEntity(plugin, getLabel(region));
    }

    @Override
    public void revalidate(@NotNull PolygonRegion region, @NotNull Player player) {
        region.getChildren().forEach(child -> blockStrategy.revalidate(child, player));
        final TextDisplay label = labels.get(region);
        if (label != null && !label.isValid()) {
            labels.remove(region);
            player.showEntity(plugin, getLabel(region));
        }
    }

    @Override
    public void hide(@NotNull PolygonRegion region, @NotNull Player player) {
        region.getChildren().forEach(child -> blockStrategy.hide(child, player));

        final UUID playerUUID = player.getUniqueId();
        if (viewers.remove(region, playerUUID)) {
            final TextDisplay label = labels.get(region);
            if (label != null) {
                player.hideEntity(plugin, label);
            }

            if (viewers.get(region).isEmpty()) {
                final TextDisplay removedLabel = labels.remove(region);
                if (removedLabel != null && removedLabel.isValid()) {
                    removedLabel.remove();
                }
            }
        }
    }
}
