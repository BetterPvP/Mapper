package dev.brauw.mapper.gui.region;

import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.gui.button.BackItem;
import dev.brauw.mapper.gui.button.ForwardItem;
import dev.brauw.mapper.region.PerspectiveRegion;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionNesting;
import dev.brauw.mapper.region.RegionTransform;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.util.RegionFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists the regions in a session so they can be found without walking the world.
 * <p>
 * This doubles as the answer to name collisions. Region names are intentionally not unique, so a
 * list that showed only names would be unusable; every row carries the region's coordinates, which
 * is what actually distinguishes two regions called {@code npc_resident}.
 */
public class GuiRegionBrowser {

    private final EditSession session;
    private final Player viewer;
    private final GuiManager guiManager;
    private final SelectionHandler selectionHandler;
    private final List<Region> shown;
    private final PagedGui<Item> gui;

    public GuiRegionBrowser(EditSession session, Player viewer, GuiManager guiManager,
                            SelectionHandler selectionHandler, List<Region> shown) {
        this.session = session;
        this.viewer = viewer;
        this.guiManager = guiManager;
        this.selectionHandler = selectionHandler;
        this.shown = shown;

        this.gui = PagedGui.itemsBuilder()
                .setStructure(
                        "# # # # # # # # #",
                        "# r r r r r r r #",
                        "# r r r r r r r #",
                        "# r r r r r r r #",
                        "# # # < # > # # #")
                .addIngredient('r', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', new BackItem())
                .addIngredient('>', new ForwardItem())
                .build();
        rebuild();
    }

    private void rebuild() {
        final List<Item> items = new ArrayList<>(shown.size());
        for (RegionNesting.Nested nested : RegionNesting.tree(shown, session.getRegions())) {
            items.add(new RegionButton(nested));
        }
        gui.setContent(items);
    }

    public PagedGui<Item> gui() {
        return gui;
    }

    /** One region: click to travel to it, right-click to edit its tags, shift-click to delete it. */
    private class RegionButton extends AbstractItem {

        private final Region region;
        private final RegionNesting.Nested nested;

        RegionButton(RegionNesting.Nested nested) {
            this.region = nested.getRegion();
            this.nested = nested;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player who) {
            final boolean editable = session.canEdit(viewer);
            final ItemBuilder builder = new ItemBuilder(region.getOptions().getColor().getMaterial())
                    .setName(Component.text(indent() + "#" + region.getName(), NamedTextColor.YELLOW))
                    .addLoreLines(
                            Component.text(
                                    region.getType().name().toLowerCase(), NamedTextColor.DARK_GRAY),
                            Component.text(
                                    RegionFormat.coordinates(region), NamedTextColor.GRAY));

            if (nested.getParent() != null) {
                builder.addLoreLines(Component.text(
                        "Inside #" + nested.getParent().getName(), NamedTextColor.GRAY));
            }

            final Component tags = RegionFormat.tags(region);
            if (!Component.empty().equals(tags)) {
                builder.addLoreLines(tags);
            }

            builder.addLoreLines(Component.text("Click to teleport", NamedTextColor.GREEN));
            if (editable) {
                builder.addLoreLines(
                        Component.text("Right-click to edit tags", NamedTextColor.AQUA),
                        Component.text("Shift-click to delete", NamedTextColor.RED));
            }
            return builder;
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isShiftClick()) {
                if (!requireEdit(player)) return;
                player.closeInventory();
                selectionHandler.deleteRegion(session, player, region);
                return;
            }

            if (clickType.isRightClick()) {
                if (!requireEdit(player)) return;
                selectionHandler.openTagEditor(player, region);
                return;
            }

            player.closeInventory();
            teleport(player);
        }

        /** Regions inside another are listed right after it, stepped in by their depth. */
        private String indent() {
            return nested.getDepth() == 0 ? "" : "  ".repeat(nested.getDepth() - 1) + "↳ ";
        }

        private boolean requireEdit(Player player) {
            if (session.canEdit(player)) {
                return true;
            }
            player.sendMessage(Component.text("You are viewing this session, not editing it.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return false;
        }

        private void teleport(Player player) {
            final Location anchor = RegionTransform.anchor(region);
            // A perspective region exists to record a viewpoint, so arriving at one should adopt it.
            // Every other type has no facing worth taking, and being spun around on arrival is
            // disorienting, so the player keeps their own.
            if (!(region instanceof PerspectiveRegion)) {
                anchor.setYaw(player.getLocation().getYaw());
                anchor.setPitch(player.getLocation().getPitch());
            }
            player.teleport(anchor);
            player.sendMessage(Component.text("Teleported to ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(region)));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.6f);
        }
    }
}
