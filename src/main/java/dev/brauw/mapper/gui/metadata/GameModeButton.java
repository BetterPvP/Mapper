package dev.brauw.mapper.gui.metadata;

import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.metadata.MetadataManager;
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

import java.util.List;

/** Lists the available game modes with the map's one marked. A click moves to the next. */
public class GameModeButton extends AbstractItem {

    private final MapMetadata metadata;
    private final MetadataManager metadataManager;

    public GameModeButton(MapMetadata metadata, MetadataManager metadataManager) {
        this.metadata = metadata;
        this.metadataManager = metadataManager;
    }

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
        final ItemBuilder builder = new ItemBuilder(Material.PAPER).setName(Component.empty());
        for (String gameMode : metadataManager.getGameModes()) {
            builder.addLoreLines(gameMode.equals(metadata.getGameMode())
                    ? Component.text("▶ " + gameMode, NamedTextColor.GREEN)
                    : Component.text(gameMode, NamedTextColor.GRAY));
        }
        return builder.addLoreLines(Component.empty());
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        final List<String> gameModes = metadataManager.getGameModes();
        if (gameModes.isEmpty()) {
            return;
        }
        final int step = clickType.isRightClick() ? -1 : 1;
        final int next = Math.floorMod(gameModes.indexOf(metadata.getGameMode()) + step, gameModes.size());
        metadata.setGameMode(gameModes.get(next));
        notifyWindows();
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }
}
