package dev.brauw.mapper.gui.metadata.button;

import dev.brauw.mapper.gui.metadata.GuiMetadata;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;

public class SaveButton extends AbstractItem {
    private final GuiMetadata guiMetadata;

    public SaveButton(GuiMetadata guiMetadata) {
        this.guiMetadata = guiMetadata;
    }

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
        return new ItemBuilder(Material.EMERALD)
                .setName(Component.text("Save Metadata", NamedTextColor.GREEN))
                .addLoreLines(Component.text("Click to save changes", NamedTextColor.GRAY));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        guiMetadata.getMetadataManager().saveMetadata(player.getWorld(), guiMetadata.getMetadata());
        player.closeInventory();
        player.sendMessage(Component.text("Map metadata saved successfully!", NamedTextColor.GREEN));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }
}
