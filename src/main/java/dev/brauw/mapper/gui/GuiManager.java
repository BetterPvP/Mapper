package dev.brauw.mapper.gui;

import dev.brauw.mapper.MapperPlugin;
import dev.brauw.mapper.gui.common.GuiSet;
import dev.brauw.mapper.gui.common.GuiSetName;
import dev.brauw.mapper.gui.metadata.GuiMetadata;
import dev.brauw.mapper.gui.selector.GuiColorSelect;
import dev.brauw.mapper.gui.tag.GuiTagEditor;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionOptions;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tag.Tag;
import dev.brauw.mapper.tag.TagRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.window.AnvilWindow;
import xyz.xenondevs.invui.window.Window;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class GuiManager {

    private final MapperPlugin mapperPlugin;

    public GuiManager(MapperPlugin mapperPlugin) {
        this.mapperPlugin = mapperPlugin;
        Structure.addGlobalIngredient('#', new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setDisplayName(""));
    }

    public void openRegionCreateGui(EditSession session, BiConsumer<String, RegionOptions> regionCreator, Runnable onClose) {
        AtomicReference<String> name = new AtomicReference<>("");
        final RegionOptions.RegionOptionsBuilder builder = RegionOptions.builder();

        final GuiSetName setNameGui = new GuiSetName(name, () -> builder.build().getColor().getMaterial(), () -> regionCreator.accept(name.get(), builder.build()));
        final GuiColorSelect colorGui = new GuiColorSelect(builder, setNameGui::update);
        AnvilWindow.split()
                .setUpperGui(setNameGui)
                .setLowerGui(colorGui)
                .addRenameHandler(updated -> {
                    name.set(updated);
                    setNameGui.update();
                })
                .setTitle("Create a new region")
                .addCloseHandler(onClose)
                .open(session.getOwner().getPlayer());
    }

    public void openTagEditor(Player player, Region region, TagRegistry tagRegistry) {
        Window.single()
                .setTitle("Tag Editor")
                .setGui(new GuiTagEditor(region, tagRegistry, this))
                .open(player);
    }

    /**
     * Opens an anvil GUI for the player to type a concrete value for an
     * input-requiring tag (e.g. {@code level:47}). The value is only accepted
     * when it satisfies {@link Tag#matches(String)}; on confirmation {@code onConfirm}
     * receives it. The tag editor is reopened afterwards, on the next tick so it
     * reflects the change.
     *
     * @param player       the editing player
     * @param region       the region the tag is being applied to
     * @param tagRegistry  the registry, used to rebuild the tag editor
     * @param tag          the tag whose value is being entered
     * @param onConfirm    consumes the accepted value
     */
    public void openTagValueInput(Player player, Region region, TagRegistry tagRegistry, Tag tag, Consumer<String> onConfirm) {
        AtomicReference<String> value = new AtomicReference<>("");
        GuiSet<String> input = new GuiSet<>(value, () -> Material.NAME_TAG, () -> onConfirm.accept(value.get()), tag::matches);
        AnvilWindow.single()
                .setGui(input)
                .addRenameHandler(updated -> {
                    value.set(updated);
                    input.update();
                })
                .setTitle("Enter " + tag.usage())
                .addCloseHandler(() -> mapperPlugin.getTaskScheduler().scheduleTask(
                        () -> openTagEditor(player, region, tagRegistry), 1L))
                .open(player);
    }

    public void openMetadataEditor(Player player, MapMetadata mapMetadata) {
        Window.single()
                .setTitle("Map Metadata")
                .setGui(new GuiMetadata(mapperPlugin.getTaskScheduler(), this, mapperPlugin.getMetadataManager(), player, mapMetadata))
                .open(player);
    }
}