package dev.brauw.mapper.gui.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.invui.gui.AbstractGui;
import xyz.xenondevs.invui.gui.structure.Structure;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.AbstractItem;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class GuiSet<T> extends AbstractGui {

    private final Runnable creator;
    private final AtomicReference<T> value;
    private final Predicate<T> validator;
    private final DoneButton doneButton;
    private final Supplier<Material> materialSupplier;

    public GuiSet(AtomicReference<T> value, Supplier<Material> materialSupplier, Runnable creator, Predicate<T> validator) {
        super(3, 1);
        this.creator = creator;
        this.materialSupplier = materialSupplier;
        this.value = value;
        this.validator = validator;
        this.doneButton = new DoneButton();

        Structure structure = new Structure("-+.");
        structure.addIngredient('-', new ItemBuilder(Material.PAPER).setDisplayName(""));
        structure.addIngredient('.', doneButton);
        applyStructure(structure);
    }

    public void update() {
        doneButton.notifyWindows();
    }

    private class DoneButton extends AbstractItem {

        @Override
        public ItemProvider getItemProvider() {
            if (validator.test(value.get())) {
                ItemBuilder itemBuilder = new ItemBuilder(materialSupplier.get());
                itemBuilder.setDisplayName(new AdventureComponentWrapper(Component.text("Done!", NamedTextColor.GREEN)));
                return itemBuilder;
            } else {
                ItemBuilder itemBuilder = new ItemBuilder(Material.TNT);
                itemBuilder.setDisplayName(new AdventureComponentWrapper(Component.text("Choose another value.", NamedTextColor.RED)));
                return itemBuilder;
            }
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull InventoryClickEvent event) {
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
