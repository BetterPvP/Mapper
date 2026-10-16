package dev.brauw.mapper.command;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.region.CuboidRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionTransform;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.selection.WorldEditBridge;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.util.RegionFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A command for every region tool, plus the transforms that have no tool.
 * <p>
 * The wands stay the fast path for placing something where you are standing and looking. Commands
 * are for everything they are bad at: naming a region without an anvil prompt, repeating an exact
 * offset, and acting on a region you can name but are not standing next to.
 */
@Command("mapper")
@Permission("mapper.use")
public class RegionEditCommand {

    private final Mapper mapper;
    private final CommandSupport support;
    private final RegionPrompt prompt;

    public RegionEditCommand(Mapper mapper, CommandSupport support, RegionPrompt prompt) {
        this.mapper = mapper;
        this.support = support;
        this.prompt = prompt;
    }

    private SelectionHandler selection() {
        return mapper.getSelectionHandler();
    }

    @Command("pos1")
    public void pos1(CommandSourceStack source) {
        final Player player = editor(source);
        if (player == null) return;
        selection().setFirstPosition(player);
    }

    @Command("pos2")
    public void pos2(CommandSourceStack source) {
        final Player player = editor(source);
        if (player == null) return;
        selection().setSecondPosition(player);
    }

    @Command("cuboid [name]")
    public void cuboid(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        selection().createCuboidRegion(support.editingHere(player), player, orPrompt(name));
    }

    /**
     * Makes a cuboid from the player's WorldEdit selection, for builders who already have the area
     * selected.
     */
    @Command("fromselection [name]")
    public void fromSelection(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        if (!worldEditInstalled()) {
            support.deny(player, "WorldEdit is not installed.");
            return;
        }
        final List<Location> corners = WorldEditBridge.selection(player);
        if (corners == null) {
            support.deny(player, "Make a cuboid WorldEdit selection in this world first.");
            return;
        }
        selection().setCorners(player, corners.get(0), corners.get(1));
        selection().createCuboidRegion(support.editingHere(player), player, orPrompt(name));
    }

    /**
     * Sets the player's WorldEdit selection to a cuboid's blocks, so it can be adjusted with
     * {@code //expand} and friends and turned back into a cuboid with {@code fromselection}.
     */
    @Command("toselection <region>")
    public void toSelection(CommandSourceStack source,
                            @Argument(value = "region", suggestions = "regionNames") String region) {
        final Player player = editor(source);
        if (player == null) return;
        if (!worldEditInstalled()) {
            support.deny(player, "WorldEdit is not installed.");
            return;
        }
        final EditSession session = support.editingHere(player);
        final List<Region> cuboids = support.matching(session, region).stream()
                .filter(CuboidRegion.class::isInstance)
                .toList();
        prompt.choose(player, cuboids, "select", target -> {
            final CuboidRegion cuboid = (CuboidRegion) target;
            WorldEditBridge.select(player, cuboid.getMin(), cuboid.getMax());
            support.confirm(player, Component.text("Selected ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(cuboid))
                    .append(Component.text(" in WorldEdit", NamedTextColor.GREEN)));
        });
    }

    @Command("point [name]")
    public void point(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        // No interaction point from a command, so the player's own position is the placement.
        selection().createPointRegion(support.editingHere(player), player, null, orPrompt(name));
    }

    @Command("perspective [name]")
    public void perspective(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        selection().createPerspectiveRegion(support.editingHere(player), player, null, orPrompt(name));
    }

    @Command("polygon add")
    public void polygonAdd(CommandSourceStack source) {
        final Player player = editor(source);
        if (player == null) return;
        selection().addPolygonChild(player);
    }

    @Command("polygon create [name]")
    public void polygonCreate(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        selection().createPolygonRegion(support.editingHere(player), player, orPrompt(name));
    }

    @Command("path add")
    public void pathAdd(CommandSourceStack source) {
        final Player player = editor(source);
        if (player == null) return;
        selection().addPathPoint(player, null);
    }

    @Command("path undo")
    public void pathUndo(CommandSourceStack source) {
        final Player player = editor(source);
        if (player == null) return;
        selection().undoPathPoint(player);
    }

    @Command("path create [name]")
    public void pathCreate(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        selection().createPathRegion(support.editingHere(player), player, orPrompt(name));
    }

    @Command("cancel")
    public void cancel(CommandSourceStack source) {
        final Player player = support.player(source, "cancel a selection");
        if (player == null) return;
        selection().clearSelections(player);
        prompt.clear(player);
        support.confirm(player, Component.text("Cleared your selection.", NamedTextColor.GREEN));
    }

    @Command("delete <region>")
    public void delete(CommandSourceStack source,
                       @Argument(value = "region", suggestions = "regionNames") String region) {
        final Player player = editor(source);
        if (player == null) return;
        final EditSession session = support.editingHere(player);

        prompt.choose(player, support.matching(session, region), "delete",
                target -> selection().deleteRegion(session, player, target));
    }

    @Command("tag <region>")
    public void tag(CommandSourceStack source,
                    @Argument(value = "region", suggestions = "regionNames") String region) {
        final Player player = editor(source);
        if (player == null) return;
        final EditSession session = support.editingHere(player);

        prompt.choose(player, support.matching(session, region), "tag",
                target -> selection().openTagEditor(player, target));
    }

    /**
     * Duplicates a region at the player's feet.
     * <p>
     * The copy keeps the original's tags and colour but takes a fresh id, so the two are separate
     * datapoints rather than the same one written twice. Name defaults to the original's, which is
     * usually what you want - a second {@code npc_resident} is still an {@code npc_resident}.
     */
    @Command("copy <region> [name]")
    public void copy(CommandSourceStack source,
                     @Argument(value = "region", suggestions = "regionNames") String region,
                     @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        final EditSession session = support.editingHere(player);

        prompt.choose(player, support.matching(session, region), "copy", target -> {
            final Vector delta = player.getLocation().toVector()
                    .subtract(RegionTransform.anchor(target).toVector());
            final String copyName = orPrompt(name) == null ? target.getName() : name;
            final List<Region> group = SelectionHandler.withNested(session, target);
            final Region duplicate = RegionTransform.duplicate(target, delta, copyName);
            session.addRegion(duplicate);
            for (Region nested : group.subList(1, group.size())) {
                session.addRegion(RegionTransform.duplicate(nested, delta, nested.getName()));
            }

            support.confirm(player, Component.text("Copied to ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(duplicate))
                    .append(Component.text(SelectionHandler.nestedSuffix(group), NamedTextColor.GRAY)));
            session.broadcastExcept(player, Component.text(player.getName() + " copied ", NamedTextColor.GRAY)
                    .append(RegionFormat.describe(duplicate)));
        });
    }

    /**
     * Pastes what the Region Clipboard tool last copied.
     * <p>
     * Distinct from {@link #copy}: that names a region and duplicates it in one step, while the
     * clipboard separates the two, so you can copy something here and place it somewhere you have
     * not decided on yet.
     */
    @Command("paste [name]")
    public void paste(CommandSourceStack source, @Nullable @Argument("name") String name) {
        final Player player = editor(source);
        if (player == null) return;
        selection().handlePaste(support.editingHere(player), player, orPrompt(name));
    }

    @Command("offset <region> <x> <y> <z>")
    public void offset(CommandSourceStack source,
                       @Argument(value = "region", suggestions = "regionNames") String region,
                       @Argument("x") double x,
                       @Argument("y") double y,
                       @Argument("z") double z) {
        final Player player = editor(source);
        if (player == null) return;
        final EditSession session = support.editingHere(player);

        prompt.choose(player, support.matching(session, region), "move", target -> {
            final List<Region> group = SelectionHandler.withNested(session, target);
            final Region moved = RegionTransform.translate(target, new Vector(x, y, z));
            if (!session.replaceRegion(target, moved)) {
                support.deny(player, "That region is already gone.");
                return;
            }
            for (Region nested : group.subList(1, group.size())) {
                session.replaceRegion(nested, RegionTransform.translate(nested, new Vector(x, y, z)));
            }
            support.confirm(player, Component.text("Moved to ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(moved))
                    .append(Component.text(SelectionHandler.nestedSuffix(group), NamedTextColor.GRAY)));
            session.broadcastExcept(player, Component.text(player.getName() + " moved ", NamedTextColor.GRAY)
                    .append(RegionFormat.describe(moved)));
        });
    }

    /**
     * Mirrors a region across the plane running through the player on the given axis.
     * <p>
     * Standing on the mirror line is how a builder thinks about this - "flip it to the other side of
     * where I am" - and it avoids having to type a coordinate that has to be exactly right for the
     * result to line up with the build.
     */
    @Command("mirror <region> <axis>")
    public void mirror(CommandSourceStack source,
                       @Argument(value = "region", suggestions = "regionNames") String region,
                       @Argument("axis") Axis axis) {
        final Player player = editor(source);
        if (player == null) return;
        final EditSession session = support.editingHere(player);

        final Location standing = player.getLocation();
        final double plane = switch (axis) {
            case X -> standing.getX();
            case Y -> standing.getY();
            case Z -> standing.getZ();
        };

        prompt.choose(player, support.matching(session, region), "mirror", target -> {
            final List<Region> group = SelectionHandler.withNested(session, target);
            final Region mirrored = RegionTransform.mirror(target, axis, plane);
            if (!session.replaceRegion(target, mirrored)) {
                support.deny(player, "That region is already gone.");
                return;
            }
            for (Region nested : group.subList(1, group.size())) {
                session.replaceRegion(nested, RegionTransform.mirror(nested, axis, plane));
            }
            support.confirm(player, Component.text("Mirrored across " + axis + " to ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(mirrored))
                    .append(Component.text(SelectionHandler.nestedSuffix(group), NamedTextColor.GRAY)));
            session.broadcastExcept(player, Component.text(player.getName() + " mirrored ", NamedTextColor.GRAY)
                    .append(RegionFormat.describe(mirrored)));
        });
    }

    /** @return whether WorldEdit or FastAsyncWorldEdit is installed and enabled */
    public static boolean worldEditInstalled() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    /**
     * @return the sender if they are a player who may edit their session, else {@code null} after
     * they have been told why not
     */
    private @Nullable Player editor(CommandSourceStack source) {
        final Player player = support.player(source, "edit regions");
        if (player == null) {
            return null;
        }
        return support.editingHere(player) == null ? null : player;
    }

    /**
     * Normalises an omitted optional name to {@code null}, which every creation method reads as
     * "ask for one in the create GUI".
     * <p>
     * The optional name arguments here deliberately carry no {@code @Default}. An empty default is
     * not merely redundant, it hangs the parser: when the input is exhausted Cloud re-parses the same
     * node with the default appended to the input, so a default that appends nothing never makes
     * progress and recurses until the stack overflows. An absent optional arrives as {@code null}
     * instead, which is what this collapses.
     *
     * @param name the typed name, or {@code null} if the argument was omitted
     * @return the name, or {@code null} to prompt for one
     */
    private @Nullable String orPrompt(@Nullable String name) {
        return name == null || name.isEmpty() ? null : name;
    }
}
