package dev.brauw.mapper.gui.tag;

import dev.brauw.mapper.gui.GuiManager;
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
import java.util.Optional;
import java.util.Set;

public class GuiTagEditor extends AbstractPagedGui<Item> {

    private final Region region;
    private final TagRegistry tagRegistry;
    private final GuiManager guiManager;
    private final List<Tag> availableTags;

    public GuiTagEditor(Region region, TagRegistry tagRegistry, GuiManager guiManager) {
        super(9, 4, false, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
        this.region = region;
        this.tagRegistry = tagRegistry;
        this.guiManager = guiManager;
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

        /**
         * @return the concrete applied value of this tag on the region, if any.
         * For a pattern tag this is e.g. {@code level:47}; for a simple tag it is
         * its own name.
         */
        private Optional<String> appliedValue() {
            return activeTags.stream().filter(tag::matches).findFirst();
        }

        @Override
        public ItemProvider getItemProvider() {
            Optional<String> applied = appliedValue();
            boolean active = applied.isPresent();
            Material material = active ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;

            // When active show the concrete value (e.g. #level:47), otherwise the tag's name.
            Component title = Component.text("#" + applied.orElseGet(tag::name), NamedTextColor.YELLOW);

            ItemBuilder builder = new ItemBuilder(material)
                    .setDisplayName(new AdventureComponentWrapper(title))
                    .addLoreLines(
                            new AdventureComponentWrapper(Component.text(tag.usage(), NamedTextColor.GOLD)),
                            new AdventureComponentWrapper(Component.text(tag.description(), NamedTextColor.GRAY))
                    );

            Component hint = active
                    ? Component.text("Click to remove", NamedTextColor.RED)
                    : Component.text(tag.requiresInput() ? "Click to set a value" : "Click to add", NamedTextColor.GREEN);
            return builder.addLoreLines(new AdventureComponentWrapper(hint));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
            // Remove any value of this tag's type if one is already applied.
            if (appliedValue().isPresent()) {
                activeTags.removeIf(tag::matches);
                notifyWindows();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                return;
            }

            // Input tags open an anvil GUI to type and validate a concrete value.
            if (tag.requiresInput()) {
                guiManager.openTagValueInput(player, region, tagRegistry, tag, activeTags::add);
                return;
            }

            // Simple tags toggle their fixed name directly.
            activeTags.add(tag.name());
            notifyWindows();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }
    }
}
