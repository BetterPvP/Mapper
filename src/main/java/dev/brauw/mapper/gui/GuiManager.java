package dev.brauw.mapper.gui;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.gui.common.GuiSet;
import dev.brauw.mapper.gui.common.GuiSetName;
import dev.brauw.mapper.gui.metadata.GuiMetadata;
import dev.brauw.mapper.gui.region.GuiRegionBrowser;
import dev.brauw.mapper.gui.selector.GuiColorSelect;
import dev.brauw.mapper.gui.tag.GuiTagEditor;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionOptions;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tag.Tag;
import dev.brauw.mapper.tag.TagRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Structure;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.AnvilWindow;
import xyz.xenondevs.invui.window.Window;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GuiManager {

    private final Mapper mapper;

    public GuiManager(Mapper mapper) {
        this.mapper = mapper;
        Structure.addGlobalIngredient('#', new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(Component.empty()));
    }

    public void openRegionCreateGui(Player player, BiConsumer<String, RegionOptions> regionCreator, Runnable onClose) {
        AtomicReference<String> name = new AtomicReference<>("");
        final RegionOptions.RegionOptionsBuilder builder = RegionOptions.builder();

        final GuiSetName setNameGui = new GuiSetName(name, () -> builder.build().getColor().getMaterial(), () -> regionCreator.accept(name.get(), builder.build()));
        final GuiColorSelect colorGui = new GuiColorSelect(builder, setNameGui::update);
        AnvilWindow.builder()
                .setUpperGui(setNameGui::gui)
                .setLowerGui(colorGui.gui())
                .addRenameHandler(updated -> {
                    name.set(updated);
                    setNameGui.update();
                })
                .setTitle("Create a new region")
                .addCloseHandler(reason -> onClose.run())
                .open(player);
    }

    /**
     * Opens the paged region list.
     *
     * @param session          the session whose regions are being browsed
     * @param player           the browsing player
     * @param selectionHandler used to delete and tag regions picked out of the list
     * @param shown            the regions to list, already filtered by the caller
     * @param title            the window title, which is where the filter is described
     */
    public void openRegionBrowser(EditSession session, Player player, SelectionHandler selectionHandler,
                                  List<Region> shown, String title) {
        Window.builder()
                .setTitle(title)
                .setUpperGui(new GuiRegionBrowser(session, player, this, selectionHandler, shown).gui())
                .open(player);
    }

    public void openTagEditor(Player player, Region region, TagRegistry tagRegistry) {
        Window.builder()
                .setTitle("Tag Editor")
                .setUpperGui(new GuiTagEditor(region, tagRegistry, this).gui())
                .open(player);
    }

    /**
     * Opens an anvil GUI for the player to type a value for an input-requiring tag.
     * <p>
     * The prompt already knows which tag it is filling, so only the value half is typed
     * ({@code 47}, not {@code level:47}) and {@link Tag#completeInput(String)} supplies the rest.
     * The completed value is only accepted when it satisfies {@link Tag#matches(String)}; on
     * confirmation {@code onConfirm} receives it. The tag editor is reopened afterwards, on the
     * next tick so it reflects the change.
     *
     * @param player       the editing player
     * @param region       the region the tag is being applied to
     * @param tagRegistry  the registry, used to rebuild the tag editor
     * @param tag          the tag whose value is being entered
     * @param onConfirm    consumes the accepted value
     */
    public void openTagValueInput(Player player, Region region, TagRegistry tagRegistry, Tag tag, Consumer<String> onConfirm) {
        AtomicReference<String> value = new AtomicReference<>("");
        GuiSet<String> input = new GuiSet<>(value, () -> Material.NAME_TAG,
                () -> onConfirm.accept(tag.completeInput(value.get())),
                typed -> tag.matches(tag.completeInput(typed)));
        AnvilWindow.builder()
                .setUpperGui(input::gui)
                .addRenameHandler(updated -> {
                    value.set(updated);
                    input.update();
                })
                .setTitle("Enter " + tag.name() + " " + tag.inputHint())
                .addCloseHandler(reason -> mapper.getTaskScheduler().scheduleTask(
                        () -> openTagEditor(player, region, tagRegistry), 1L))
                .open(player);
    }

    /**
     * Opens an anvil GUI for the player to type a tag that no {@link Tag} definition covers.
     * <p>
     * The registry is the curated, discoverable vocabulary and stays the primary path; this is the
     * escape hatch for a builder who needs a tag that has not been registered yet, so adding one does
     * not require a code change and a restart. Input is shape-checked only ({@code key} or
     * {@code key:value}) - there is no definition to validate against.
     *
     * @param player      the editing player
     * @param region      the region the tag is being applied to
     * @param tagRegistry the registry, used to rebuild the tag editor
     * @param onConfirm   consumes the accepted value
     */
    public void openCustomTagInput(Player player, Region region, TagRegistry tagRegistry, Consumer<String> onConfirm) {
        AtomicReference<String> value = new AtomicReference<>("");
        GuiSet<String> input = new GuiSet<>(value, () -> Material.NAME_TAG,
                () -> onConfirm.accept(value.get()), TagRegistry::isWellFormedValue);
        AnvilWindow.builder()
                .setUpperGui(input::gui)
                .addRenameHandler(updated -> {
                    value.set(updated);
                    input.update();
                })
                .setTitle("Enter tag or tag:value")
                .addCloseHandler(reason -> mapper.getTaskScheduler().scheduleTask(
                        () -> openTagEditor(player, region, tagRegistry), 1L))
                .open(player);
    }

    public void openMetadataEditor(Player player, MapMetadata mapMetadata) {
        Window.builder()
                .setTitle("Map Metadata")
                .setUpperGui(new GuiMetadata(mapper.getTaskScheduler(), this, mapper.getMetadataManager(), mapMetadata).getGui())
                .open(player);
    }
}