package dev.brauw.mapper.gui.metadata;

import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.gui.button.BackItem;
import dev.brauw.mapper.gui.button.ForwardItem;
import dev.brauw.mapper.gui.metadata.button.AddAuthorButton;
import dev.brauw.mapper.gui.metadata.button.AuthorButton;
import dev.brauw.mapper.gui.metadata.button.HeightButton;
import dev.brauw.mapper.gui.metadata.button.NameButton;
import dev.brauw.mapper.gui.metadata.button.SaveButton;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.metadata.MetadataManager;
import dev.brauw.mapper.util.BukkitTaskScheduler;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;

import java.util.List;

@Getter
public class GuiMetadata {

    private final MapMetadata metadata;
    private final MetadataManager metadataManager;
    private final PagedGui<Item> gui;

    public GuiMetadata(BukkitTaskScheduler taskScheduler, GuiManager guiManager, MetadataManager metadataManager, MapMetadata metadata) {
        this.metadata = metadata;
        this.metadataManager = metadataManager;
        this.gui = PagedGui.itemsBuilder()
                .setStructure(
                        "# # # # # # # # #",
                        "# n g h # # # + #",
                        "# a a a a a a a #",
                        "# # # < s > # # #")
                .addIngredient('#', new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(Component.empty()))
                .addIngredient('n', new NameButton(taskScheduler, guiManager, metadata))
                .addIngredient('g', new GameModeButton(metadata, metadataManager))
                .addIngredient('h', new HeightButton(taskScheduler, guiManager, metadata))
                .addIngredient('+', new AddAuthorButton(taskScheduler, guiManager, metadata))
                .addIngredient('s', new SaveButton(this))
                .addIngredient('a', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', new BackItem())
                .addIngredient('>', new ForwardItem())
                .build();
        updateAuthorList();
    }

    public void updateAuthorList() {
        final List<Item> authors = metadata.getAuthors().stream()
                .<Item>map(authorId -> new AuthorButton(metadata, authorId, this::updateAuthorList))
                .toList();
        gui.setContent(authors);
    }
}
