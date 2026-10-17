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
import java.util.Optional;
import java.util.Set;

public class GuiTagEditor {

    private final Region region;
    private final TagRegistry tagRegistry;
    private final GuiManager guiManager;
    private final List<Tag> availableTags;
    private final PagedGui<Item> gui;

    public GuiTagEditor(Region region, TagRegistry tagRegistry, GuiManager guiManager) {
        this.region = region;
        this.tagRegistry = tagRegistry;
        this.guiManager = guiManager;
        this.availableTags = tagRegistry.getTags(region.getName());
        this.gui = PagedGui.itemsBuilder()
                .setStructure(
                        "# # # # c # # # #",
                        "# t t t t t t t #",
                        "# t t t t t t t #",
                        "# # # < # > # # #")
                .addIngredient('t', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('c', new CustomTagButton())
                .addIngredient('<', new BackItem())
                .addIngredient('>', new ForwardItem())
                .build();
        updateTagList();
    }

    public PagedGui<Item> gui() {
        return gui;
    }

    private void updateTagList() {
        Set<String> activeTags = region.getOptions().getTags();
        List<Item> items = new ArrayList<>(availableTags.stream()
                .<Item>map(tag -> new TagButton(tag, activeTags))
                .toList());

        // Values applied to the region that no definition owns - typed through the custom-tag input, or
        // left behind by a definition that has since been unregistered. Without these they would be
        // invisible here and therefore impossible to remove in-game.
        activeTags.stream()
                .filter(value -> availableTags.stream().noneMatch(tag -> tag.matches(value)))
                .sorted()
                .<Item>map(value -> new CustomTagValueButton(value, activeTags))
                .forEach(items::add);

        gui.setContent(items);
    }

    /** Opens the free-form tag input, for tags the registry does not define. */
    private class CustomTagButton extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return new ItemBuilder(Material.WRITABLE_BOOK)
                    .setName(Component.text("Custom tag", NamedTextColor.YELLOW))
                    .addLoreLines(
                            Component.text("tag or tag:value", NamedTextColor.GOLD),
                            Component.text("Write a tag that isn't in the list.", NamedTextColor.GRAY),
                            Component.text("Click to write", NamedTextColor.GREEN));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            guiManager.openCustomTagInput(player, region, tagRegistry, region.getOptions().getTags()::add);
        }
    }

    /** An applied value with no owning definition; exists so it can still be removed. */
    private class CustomTagValueButton extends AbstractItem {

        private final String value;
        private final Set<String> activeTags;

        public CustomTagValueButton(String value, Set<String> activeTags) {
            this.value = value;
            this.activeTags = activeTags;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return new ItemBuilder(Material.LIME_CONCRETE)
                    .setName(Component.text("#" + value, NamedTextColor.YELLOW))
                    .addLoreLines(
                            Component.text("Custom tag", NamedTextColor.GOLD),
                            Component.text("Not defined by any plugin.", NamedTextColor.GRAY),
                            Component.text("Click to remove", NamedTextColor.RED));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            activeTags.remove(value);
            // Rebuild rather than notify: this button must disappear entirely, not just re-render.
            updateTagList();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
        }
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
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Optional<String> applied = appliedValue();
            boolean active = applied.isPresent();
            Material material = active ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;

            // When active show the concrete value (e.g. #level:47), otherwise the tag's name.
            Component title = Component.text("#" + applied.orElseGet(tag::name), NamedTextColor.YELLOW);
            Component hint = active
                    ? Component.text("Click to remove", NamedTextColor.RED)
                    : Component.text(tag.requiresInput() ? "Click to set a value" : "Click to add", NamedTextColor.GREEN);

            return new ItemBuilder(material)
                    .setName(title)
                    .addLoreLines(
                            Component.text(tag.usage(), NamedTextColor.GOLD),
                            Component.text(tag.description(), NamedTextColor.GRAY),
                            hint);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
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
