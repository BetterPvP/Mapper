package dev.brauw.mapper.gui.metadata.button;

import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.gui.common.GuiSet;
import dev.brauw.mapper.gui.common.GuiSetName;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.util.BukkitTaskScheduler;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.window.AnvilWindow;

import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class NameButton extends AbstractItem {

    private final BukkitTaskScheduler taskScheduler;
    private final GuiManager guiManager;
    private final MapMetadata metadata;

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
        return new ItemBuilder(Material.NAME_TAG)
                .setName(Component.text("Map Name: ", NamedTextColor.GOLD)
                        .append(Component.text(metadata.getName(), NamedTextColor.WHITE)))
                .addLoreLines(Component.text("Click to change", NamedTextColor.GRAY));
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        player.closeInventory();
        taskScheduler.scheduleTask(() -> openRenameGui(player), 1L);
    }

    private void openRenameGui(Player player) {
        AtomicReference<String> name = new AtomicReference<>(metadata.getName());
        final GuiSetName gui = new GuiSetName(name, () -> Material.PAPER, () -> {
            metadata.setName(name.get());
            taskScheduler.scheduleTask(() -> guiManager.openMetadataEditor(player, metadata), 1L);
        });
        AnvilWindow.builder()
                .setUpperGui(gui::gui)
                .addRenameHandler(updated -> {
                    name.set(updated);
                    gui.update();
                })
                .addCloseHandler(reason -> {
                    taskScheduler.scheduleTask(() -> guiManager.openMetadataEditor(player, metadata), 1L);
                })
                .setTitle("Rename the map")
                .open(player);
    }
}