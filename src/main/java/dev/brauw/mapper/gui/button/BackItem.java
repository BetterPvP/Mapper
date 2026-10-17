package dev.brauw.mapper.gui.button;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.AbstractPagedGuiBoundItem;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;

public class BackItem extends AbstractPagedGuiBoundItem {

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
        final PagedGui<?> gui = getGui();
        return new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .setName("Previous page")
                .addLoreLines(gui.getPage() > 0
                        ? "Go to page " + gui.getPage() + "/" + gui.getPageCount()
                        : "You can't go further back");
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        final PagedGui<?> gui = getGui();
        if (gui.getPage() > 0) {
            gui.setPage(gui.getPage() - 1);
        }
    }
}
