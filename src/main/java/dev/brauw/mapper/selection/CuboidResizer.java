package dev.brauw.mapper.selection;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.geometry.Box;
import dev.brauw.mapper.geometry.BoxAim;
import dev.brauw.mapper.geometry.BoxFace;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.session.display.BoxOutline;
import dev.brauw.mapper.session.display.RegionDisplayStrategy;
import dev.brauw.mapper.tool.RegionToolManager;
import dev.brauw.mapper.tool.ToolRegistry.ToolType;
import dev.brauw.mapper.util.RegionFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Resizes cuboids by their faces with the resize tool.
 * <p>
 * Holding the tool outlines the cuboid under the crosshair and lights up the face it lands on.
 * Right-click grabs that face, and it then follows the player's look along its axis, snapping to
 * whole blocks. Scrolling nudges it one block at a time instead. Right-click again applies the new
 * size and left-click puts it back. Nothing changes for anyone else until the size is applied.
 */
public class CuboidResizer {

    private static final double REACH = 64;
    private static final float HIGHLIGHT_EDGE = 0.1f;
    private static final int ACTION_BAR_REFRESH_TICKS = 20;
    private static final Color GRABBED = Color.LIME;
    private static final Color HOVERED = Color.YELLOW;

    private final Mapper mapper;
    private final RegionToolManager tools;
    private final Map<UUID, Resize> resizes = new HashMap<>();
    private int ticks;

    public CuboidResizer(Mapper mapper, RegionToolManager tools) {
        this.mapper = mapper;
        this.tools = tools;
    }

    /** Follows every resizing player's look. Runs every couple of ticks. */
    public void tick() {
        ticks++;
        resizes.keySet().removeIf(playerId -> {
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                return false;
            }
            resizes.get(playerId).clearDisplays();
            return true;
        });

        for (Player player : Bukkit.getOnlinePlayers()) {
            final EditSession session = editing(player);
            if (session == null || !isHolding(player)) {
                clear(player);
                continue;
            }

            final Resize resize = resizes.computeIfAbsent(player.getUniqueId(), id -> new Resize());
            final boolean changed = resize.grabbed == null ? hover(session, player, resize) : drag(player, resize);
            if (changed || ticks % ACTION_BAR_REFRESH_TICKS == 0) {
                final Component status = resize.status();
                if (status != null) {
                    player.sendActionBar(status);
                }
            }
        }
    }

    /** @return whether the player holds the resize tool */
    public boolean isHolding(Player player) {
        return tools.isTool(player.getInventory().getItemInMainHand(), ToolType.RESIZE_TOOL);
    }

    /** @return whether the player has a face grabbed */
    public boolean isGrabbing(Player player) {
        final Resize resize = resizes.get(player.getUniqueId());
        return resize != null && resize.grabbed != null;
    }

    /**
     * Right-click with the tool: grabs the face under the crosshair, or applies the grabbed face's
     * new position.
     *
     * @param session the player's edit session
     * @param player  the acting player
     */
    public void use(EditSession session, Player player) {
        final Resize resize = resizes.computeIfAbsent(player.getUniqueId(), id -> new Resize());
        if (resize.grabbed != null) {
            apply(session, player, resize);
            return;
        }

        hover(session, player, resize);
        if (resize.region == null) {
            player.sendMessage(Component.text("Look at a cuboid's face to resize it.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        resize.grabbed = resize.face;
        resize.preview = Box.of(resize.region);
        resize.locked = false;
        resize.redraw(player);
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 1.2f);
        player.sendActionBar(Objects.requireNonNull(resize.status()));
    }

    /**
     * Left-click with the tool: lets go of the grabbed face without changing the cuboid.
     *
     * @param player the acting player
     */
    public void cancel(Player player) {
        final Resize resize = resizes.get(player.getUniqueId());
        if (resize == null || resize.grabbed == null) {
            return;
        }
        resize.release();
        resize.redraw(player);
        player.sendActionBar(Component.text("Resize cancelled", NamedTextColor.GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 1.0f, 0.8f);
    }

    /**
     * Moves the grabbed face one block, and stops it following the player's look.
     *
     * @param player the acting player
     * @param grow   whether the face moves outward rather than inward
     */
    public void nudge(Player player, boolean grow) {
        final Resize resize = resizes.get(player.getUniqueId());
        if (resize == null || resize.grabbed == null || resize.preview == null) {
            return;
        }
        final BoxFace face = resize.grabbed;
        final int outward = face.isPositive() ? 1 : -1;
        resize.locked = true;
        resize.preview = resize.preview.withEdge(face, resize.preview.edge(face) + (grow ? outward : -outward));
        resize.redraw(player);
        player.sendActionBar(Objects.requireNonNull(resize.status()));
    }

    /** Lets go of the player's face and removes what was drawn for them. */
    public void clear(Player player) {
        final Resize resize = resizes.remove(player.getUniqueId());
        if (resize != null) {
            resize.clearDisplays();
        }
    }

    /** Removes everything drawn. Called when the host plugin disables. */
    public void shutdown() {
        resizes.values().forEach(Resize::clearDisplays);
        resizes.clear();
    }

    private boolean hover(EditSession session, Player player, Resize resize) {
        final Location eye = player.getEyeLocation();
        CuboidRegion nearest = null;
        BoxAim nearestAim = null;
        for (Region region : session.getRegions()) {
            if (!(region instanceof CuboidRegion cuboid)) {
                continue;
            }
            final BoxAim aim = BoxAim.at(Box.of(cuboid), eye.toVector(), eye.getDirection(), REACH);
            if (aim != null && (nearestAim == null || aim.getDistance() < nearestAim.getDistance())) {
                nearest = cuboid;
                nearestAim = aim;
            }
        }

        final BoxFace face = nearestAim == null ? null : nearestAim.getFace();
        if (nearest == resize.region && face == resize.face && !BoxOutline.isStale(resize.displays)) {
            return false;
        }
        resize.region = nearest;
        resize.face = face;
        resize.redraw(player);
        return true;
    }

    private boolean drag(Player player, Resize resize) {
        if (resize.locked || resize.region == null || resize.grabbed == null) {
            return false;
        }
        final Location eye = player.getEyeLocation();
        final Box original = Box.of(resize.region);
        final Double edge = BoxAim.drag(original, resize.grabbed, eye.toVector(), eye.getDirection());
        if (edge == null) {
            return false;
        }
        final Box moved = original.withEdge(resize.grabbed, edge);
        if (moved.equals(resize.preview) && !BoxOutline.isStale(resize.displays)) {
            return false;
        }
        resize.preview = moved;
        resize.redraw(player);
        return true;
    }

    private void apply(EditSession session, Player player, Resize resize) {
        final CuboidRegion grabbed = resize.region;
        final Box preview = resize.preview;
        resize.release();
        if (grabbed == null || preview == null) {
            return;
        }

        // Looked up again by id: somebody else in the session may have moved or recoloured the
        // cuboid while this player was dragging, and replacing a stale copy would fail.
        final Region current = session.getRegions().stream()
                .filter(region -> region.getId().equals(grabbed.getId()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            player.sendMessage(Component.text("That region is already gone.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            resize.redraw(player);
            return;
        }

        final CuboidRegion resized = preview.toCuboid(current.getId(), current.getName(), current.getOptions());
        session.replaceRegion(current, resized);
        resize.region = resized;
        resize.redraw(player);
        player.sendMessage(Component.text("Resized ", NamedTextColor.GREEN).append(RegionFormat.describe(resized))
                .append(Component.text(" to " + size(preview), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
        session.broadcastExcept(player, Component.text(player.getName() + " resized ", NamedTextColor.GRAY)
                .append(RegionFormat.describe(resized)));
    }

    private @Nullable EditSession editing(Player player) {
        final EditSession session = mapper.getSessionManager().getSession(player);
        if (session == null || !session.getWorld().equals(player.getWorld()) || !session.canEdit(player)) {
            return null;
        }
        return session;
    }

    private static String size(Box box) {
        final Vector size = box.size();
        return size.getBlockX() + " × " + size.getBlockY() + " × " + size.getBlockZ();
    }

    /** One player's resize: the cuboid and face under their crosshair, or the face they hold. */
    private final class Resize {

        private @Nullable CuboidRegion region;
        private @Nullable BoxFace face;
        private @Nullable BoxFace grabbed;
        private @Nullable Box preview;
        private boolean locked;
        private final List<BlockDisplay> displays = new ArrayList<>();

        private void release() {
            grabbed = null;
            preview = null;
            locked = false;
        }

        private @Nullable Component status() {
            if (region == null) {
                return null;
            }
            if (grabbed == null || preview == null) {
                return Component.text("Resize ", NamedTextColor.GRAY).append(RegionFormat.describe(region))
                        .append(Component.text(face == null ? "" : " from its " + name(face) + " face"
                                + " - right-click to grab", NamedTextColor.DARK_GRAY));
            }
            return Component.text(name(grabbed) + " face: " + size(preview), NamedTextColor.GREEN)
                    .append(Component.text("  right-click to apply, left-click to cancel, scroll to nudge",
                            NamedTextColor.DARK_GRAY));
        }

        private void redraw(Player player) {
            clearDisplays();
            if (region == null) {
                return;
            }
            final Box box = grabbed != null && preview != null ? preview : Box.of(region);
            final BoxFace lit = grabbed != null ? grabbed : face;
            if (!RegionDisplayStrategy.canSpawnAt(box.getMin().toLocation(box.getWorld()))) {
                return;
            }
            displays.addAll(BoxOutline.spawn(box, Color.WHITE, HIGHLIGHT_EDGE));
            if (lit != null) {
                displays.add(BoxOutline.spawnFace(box, lit, grabbed != null ? GRABBED : HOVERED));
            }
            displays.forEach(display -> player.showEntity(mapper.getPlugin(), display));
        }

        private void clearDisplays() {
            displays.forEach(BlockDisplay::remove);
            displays.clear();
        }
    }

    private static String name(BoxFace face) {
        return face.name().toLowerCase(Locale.ROOT);
    }
}
