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

public class ForwardItem extends AbstractPagedGuiBoundItem {

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
        final PagedGui<?> gui = getGui();
        return new ItemBuilder(Material.GREEN_STAINED_GLASS_PANE)
                .setName("Next page")
                .addLoreLines(gui.getPage() < gui.getPageCount() - 1
                        ? "Go to page " + (gui.getPage() + 2) + "/" + gui.getPageCount()
                        : "There are no more pages");
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        final PagedGui<?> gui = getGui();
        if (gui.getPage() < gui.getPageCount() - 1) {
            gui.setPage(gui.getPage() + 1);
        }
    }
}
