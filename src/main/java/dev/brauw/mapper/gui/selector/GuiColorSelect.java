package dev.brauw.mapper.gui.selector;

import dev.brauw.mapper.region.RegionColor;
import dev.brauw.mapper.region.RegionOptions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;

/** The colour row under the region-name anvil. */
public class GuiColorSelect {

    private final RegionOptions.RegionOptionsBuilder builder;
    private final Runnable updater;
    private final Gui gui;

    public GuiColorSelect(RegionOptions.RegionOptionsBuilder builder, Runnable updater) {
        this.builder = builder;
        this.updater = updater;
        this.gui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# r o y g b p w #",
                        "# # # # # # # # #",
                        "# # # # # # # # #")
                .addIngredient('r', new ColorButton(RegionColor.RED))
                .addIngredient('o', new ColorButton(RegionColor.ORANGE))
                .addIngredient('y', new ColorButton(RegionColor.YELLOW))
                .addIngredient('g', new ColorButton(RegionColor.GREEN))
                .addIngredient('b', new ColorButton(RegionColor.BLUE))
                .addIngredient('p', new ColorButton(RegionColor.PURPLE))
                .addIngredient('w', new ColorButton(RegionColor.WHITE))
                .build();
    }

    public Gui gui() {
        return gui;
    }

    private class ColorButton extends AbstractItem {

        private final RegionColor color;

        private ColorButton(RegionColor color) {
            this.color = color;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            final Color rgb = color.getBukkitColor();
            return new ItemBuilder(color.getMaterial())
                    .setName(Component.text(color.getName(), TextColor.color(rgb.getRed(), rgb.getGreen(), rgb.getBlue())));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            builder.color(color);
            updater.run();
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }
    }

}
