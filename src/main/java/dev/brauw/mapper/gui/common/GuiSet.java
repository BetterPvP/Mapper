package dev.brauw.mapper.gui.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The anvil row that takes a typed value: the paper the player renames, and a button that accepts the
 * value once the validator does.
 */
public class GuiSet<T> {

    private final Runnable creator;
    private final AtomicReference<T> value;
    private final Predicate<T> validator;
    private final DoneButton doneButton;
    private final Supplier<Material> materialSupplier;
    private final Gui gui;

    public GuiSet(AtomicReference<T> value, Supplier<Material> materialSupplier, Runnable creator, Predicate<T> validator) {
        this.creator = creator;
        this.materialSupplier = materialSupplier;
        this.value = value;
        this.validator = validator;
        this.doneButton = new DoneButton();
        this.gui = Gui.builder()
                .setStructure("-+.")
                .addIngredient('-', new ItemBuilder(Material.PAPER).setName(Component.empty()))
                .addIngredient('.', doneButton)
                .build();
    }

    public Gui gui() {
        return gui;
    }

    public void update() {
        doneButton.notifyWindows();
    }

    private class DoneButton extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (validator.test(value.get())) {
                return new ItemBuilder(materialSupplier.get())
                        .setName(Component.text("Done!", NamedTextColor.GREEN));
            }
            return new ItemBuilder(Material.TNT)
                    .setName(Component.text("Choose another value.", NamedTextColor.RED));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            // Do not accept a value the validator rejects (e.g. it doesn't match a tag's pattern).
            if (!validator.test(value.get())) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            player.closeInventory();
            creator.run();
        }
    }

}
