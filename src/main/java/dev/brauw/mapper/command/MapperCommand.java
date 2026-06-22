package dev.brauw.mapper.command;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.export.ExportStrategy;
import dev.brauw.mapper.export.JsonExportStrategy;
import dev.brauw.mapper.gui.metadata.GuiMetadata;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.metadata.MetadataManager;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.session.SessionManager;
import dev.brauw.mapper.tag.Tag;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@AllArgsConstructor
@Command("mapper")
@Permission("mapper.use")
public class MapperCommand {

    private final Mapper mapper;
    private final Component prefix = MiniMessage.miniMessage().deserialize("<gradient:#ff2424:#ff0000><bold>Mapper</bold></gradient> ");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    @Command("")
    public void defaultCmd(CommandSourceStack source) {
        help(source);
    }

    @Command("help")
    public void help(CommandSourceStack source) {
        final CommandSender sender = source.getSender();

        sender.sendMessage(prefix.append(Component.text("Help", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("● /mapper edit", NamedTextColor.WHITE)
                .append(Component.text(" - Edit the regions in the world.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("● /mapper save", NamedTextColor.WHITE)
                .append(Component.text(" - Save the regions to the active world.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("● /mapper export [strategy]", NamedTextColor.WHITE)
                .append(Component.text(" - Export the regions to the plugin data folder. Defaults to JSON.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("● /mapper discard", NamedTextColor.WHITE)
                .append(Component.text(" - Discard your editing session.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("● /mapper metadata", NamedTextColor.WHITE)
                .append(Component.text(" - Change this world's metadata.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("● /mapper tags <region>", NamedTextColor.WHITE)
                .append(Component.text(" - List the tags available for a region.", NamedTextColor.GRAY)));
    }

    @Command("tags <region>")
    public void tags(CommandSourceStack source, @Argument("region") String region) {
        final CommandSender sender = source.getSender();
        final List<Tag> tags = mapper.getTagRegistry().getTags(region);

        if (tags.isEmpty()) {
            sender.sendMessage(prefix.append(Component.text("No tags available for ", NamedTextColor.RED))
                    .append(Component.text("'" + region + "'", NamedTextColor.DARK_RED))
                    .append(Component.text(".", NamedTextColor.RED)));
            return;
        }

        sender.sendMessage(prefix.append(Component.text("Tags for ", NamedTextColor.GOLD))
                .append(Component.text("'" + region + "'", NamedTextColor.YELLOW)));
        for (Tag tag : tags) {
            sender.sendMessage(Component.text("● ", NamedTextColor.WHITE)
                    .append(Component.text(tag.usage(), NamedTextColor.YELLOW))
                    .append(Component.text(" - " + tag.description(), NamedTextColor.GRAY)));
        }
    }

    @Command("edit")
    public void edit(CommandSourceStack source) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix.append(Component.text("Only players can edit regions.", NamedTextColor.RED)));
            return;
        }

        final SessionManager sessionManager = mapper.getSessionManager();
        if (sessionManager.hasSession(player)) {
            sender.sendMessage(prefix.append(Component.text("You already have an active session.", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final EditSession session = sessionManager.getSession(player);// Creates a new session
        // load regions from file so they can be edited
        final JsonExportStrategy json = (JsonExportStrategy) mapper.getExportManager().getAvailableStrategies().get("json");
        final File file = mapper.getStorageManager().getRegionsFile(player.getWorld());
        final List<Region> read = json.read(file);
        // Locations are stored without a world; bind them to the editing world before display.
        read.forEach(region -> region.setWorld(player.getWorld()));
        read.forEach(session::addRegion);

        sender.sendMessage(prefix.append(Component.text("Session started.", NamedTextColor.GREEN)));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

    @Command("save")
    public void save(CommandSourceStack source) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix.append(Component.text("Only players can save regions.", NamedTextColor.RED)));
            return;
        }

        final SessionManager sessionManager = mapper.getSessionManager();
        if (!sessionManager.hasSession(player)) {
            sender.sendMessage(prefix.append(Component.text("You do not have an active session.", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final EditSession session = sessionManager.getSession(player);
        final List<Region> regions = session.getRegions();
        if (regions.isEmpty()) {
            sender.sendMessage(prefix.append(Component.text("No regions to save. Discard instead", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final ExportStrategy exportStrategy = this.mapper.getExportManager().getAvailableStrategies().get("json");
        final File file = mapper.getStorageManager().getRegionsFile(player.getWorld());
        exportStrategy.export(regions, file);
        sender.sendMessage(prefix.append(Component.text("Regions saved.", NamedTextColor.GREEN)));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        mapper.getSessionManager().endSession(player);
    }

    @Command("export [strategy]")
    public void save(CommandSourceStack source, @Default("json") @Argument("strategy") String strategy) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix.append(Component.text("Only players can save regions.", NamedTextColor.RED)));
            return;
        }

        final SessionManager sessionManager = mapper.getSessionManager();
        if (!sessionManager.hasSession(player)) {
            sender.sendMessage(prefix.append(Component.text("You do not have an active session.", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final EditSession session = sessionManager.getSession(player);
        final List<Region> regions = session.getRegions();
        if (regions.isEmpty()) {
            sender.sendMessage(prefix.append(Component.text("No regions to save. Discard instead", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final ExportStrategy exportStrategy = this.mapper.getExportManager().getAvailableStrategies().get(strategy.toLowerCase());
        if (exportStrategy == null) {
            player.sendMessage(prefix.append(Component.text("Unknown export strategy.", NamedTextColor.RED)));
            final List<TextComponent> strategies = mapper.getExportManager().getAvailableStrategies().keySet().stream()
                    .map(str -> Component.text(str, NamedTextColor.WHITE))
                    .toList();
            sender.sendMessage(Component.text("Available strategies: ", NamedTextColor.GRAY)
                    .append(Component.join(JoinConfiguration.commas(true), strategies)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final File folder = mapper.getStorageManager().getExportDirectory(player.getWorld());
        final File file = new File(folder, dateFormat.format(new Date()) + ".json");
        exportStrategy.export(regions, file);

        Component savedMessage = prefix.append(Component.text("Regions saved to: ", NamedTextColor.GREEN))
                .append(Component.text(file.getName(), NamedTextColor.DARK_GREEN));

        // If the strategy can produce a copyable string, make the message click-to-copy.
        final String serialized = exportStrategy.serialize(regions);
        if (serialized != null) {
            savedMessage = savedMessage
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy the exported data", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.copyToClipboard(serialized));
        }

        sender.sendMessage(savedMessage);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        mapper.getSessionManager().endSession(player);
    }

    @Command("metadata")
    public void metadata(CommandSourceStack source) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix.append(Component.text("Only players can edit metadata.", NamedTextColor.RED)));
            return;
        }

        final World world = player.getWorld();
        final MetadataManager metadataManager = mapper.getMetadataManager();
        final MapMetadata metadata = metadataManager.loadOrCreateMetadata(world);

        // Open metadata GUI
        mapper.getGuiManager().openMetadataEditor(player, metadata);
    }

    @Command("discard")
    public void discard(CommandSourceStack source) {
        final CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix.append(Component.text("Only players can discard regions.", NamedTextColor.RED)));
            return;
        }

        final SessionManager sessionManager = mapper.getSessionManager();
        if (!sessionManager.hasSession(player)) {
            sender.sendMessage(prefix.append(Component.text("You do not have an active session.", NamedTextColor.RED)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        sessionManager.endSession(player);
        sender.sendMessage(prefix.append(Component.text("Session discarded.", NamedTextColor.GREEN)));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

}
