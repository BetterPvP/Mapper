package dev.brauw.mapper.gui.tag;

import dev.brauw.mapper.gui.button.BackItem;
import dev.brauw.mapper.gui.button.ForwardItem;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.tag.Tag;
import dev.brauw.mapper.tag.TagRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.AbstractPagedGui;
import xyz.xenondevs.invui.gui.SlotElement;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;
import xyz.xenondevs.invui.item.impl.SimpleItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GuiTagEditor extends AbstractPagedGui<Item> {

    private final Region region;
    private final List<Tag> availableTags;

    public GuiTagEditor(Region region, TagRegistry tagRegistry) {
        super(9, 4, false, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
        this.region = region;
        this.availableTags = tagRegistry.getTags(region.getName());

        Structure structure = new Structure(
                "# # # # # # # # #",
                "# t t t t t t t #",
                "# t t t t t t t #",
                "# # # < # > # # #"
        );

        structure.addIngredient('t', Markers.CONTENT_LIST_SLOT_HORIZONTAL);
        structure.addIngredient('<', new BackItem());
        structure.addIngredient('>', new ForwardItem());

        applyStructure(structure);
        updateTagList();
    }

    private void updateTagList() {
        Set<String> activeTags = region.getOptions().getTags();
        List<Item> items = availableTags.stream()
                .<Item>map(tag -> new TagButton(tag, activeTags))
                .toList();
        setContent(items);
    }

    @Override
    public void bake() {
        int contentSize = getContentListSlots().length;
        List<List<SlotElement>> pages = new ArrayList<>();
        List<SlotElement> page = new ArrayList<>(contentSize);

        for (Item item : content) {
            page.add(new SlotElement.ItemSlotElement(item));
            if (page.size() >= contentSize) {
                pages.add(page);
                page = new ArrayList<>(contentSize);
            }
        }

        if (!page.isEmpty()) {
            pages.add(page);
        }

        this.pages = pages;
        update();
    }

    private class TagButton extends AbstractItem {

        private final Tag tag;
        private final Set<String> activeTags;

        public TagButton(Tag tag, Set<String> activeTags) {
            this.tag = tag;
            this.activeTags = activeTags;
        }

        @Override
        public ItemProvider getItemProvider() {
            boolean active = activeTags.contains(tag.name());
            Material material = active ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;

            Component title = Component.text("#" + tag.name(), NamedTextColor.YELLOW);

            return new ItemBuilder(material)
                    .setDisplayName(new AdventureComponentWrapper(title))
                    .addLoreLines(
                            new AdventureComponentWrapper(Component.text(tag.usage(), NamedTextColor.GOLD)),
                            new AdventureComponentWrapper(Component.text(tag.description(), NamedTextColor.GRAY))
                    );
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
            if (activeTags.contains(tag.name())) {
                activeTags.remove(tag.name());
            } else {
                activeTags.add(tag.name());
            }
            notifyWindows();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f,
                    activeTags.contains(tag.name()) ? 2.0f : 0.5f);
        }
    }
}
