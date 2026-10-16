package dev.brauw.mapper.command;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.export.ExportStrategy;
import dev.brauw.mapper.export.JsonExportStrategy;
import dev.brauw.mapper.metadata.MapMetadata;
import dev.brauw.mapper.metadata.MetadataManager;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.region.RegionTransform;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.session.SessionManager;
import dev.brauw.mapper.session.SessionMember;
import dev.brauw.mapper.session.SessionRole;
import dev.brauw.mapper.session.event.RegionsSavedEvent;
import dev.brauw.mapper.tag.Tag;
import dev.brauw.mapper.util.Messages;
import dev.brauw.mapper.util.RegionFormat;
import dev.brauw.mapper.validation.ValidationIssue;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Session lifecycle, saving and finding regions.
 * <p>
 * Region creation and editing live in {@link RegionEditCommand}; this class is what you run before
 * and after that work.
 */
@Command("mapper")
@Permission("mapper.use")
public class MapperCommand {

    private final Mapper mapper;
    private final CommandSupport support;
    private final RegionPrompt prompt;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public MapperCommand(Mapper mapper, CommandSupport support, RegionPrompt prompt) {
        this.mapper = mapper;
        this.support = support;
        this.prompt = prompt;
    }

    @Command("")
    public void defaultCmd(CommandSourceStack source) {
        help(source);
    }

    @Command("help")
    public void help(CommandSourceStack source) {
        final CommandSender sender = source.getSender();

        sender.sendMessage(Messages.PREFIX.append(Component.text("Help", NamedTextColor.GOLD)));
        line(sender, "/mapper edit", "Join or start this world's editing session.");
        line(sender, "/mapper watch", "Join this world's session as a viewer.");
        line(sender, "/mapper session", "Show who is editing and how much is unsaved.");
        line(sender, "/mapper leave", "Leave the session, leaving it open for the others.");
        line(sender, "/mapper save", "Validate and write this world's regions to disk.");
        line(sender, "/mapper validate", "Report problems without saving.");
        line(sender, "/mapper export [strategy]", "Export to a timestamped file. Defaults to JSON.");
        line(sender, "/mapper list [filter]", "Browse the regions in the session.");
        line(sender, "/mapper filter [text]", "Show only regions whose name or tags contain the text.");
        line(sender, "/mapper goto <region>", "Teleport to a region.");
        line(sender, "/mapper paste [name]", "Paste what the Region Clipboard copied.");
        line(sender, "/mapper metadata", "Change this world's metadata.");
        line(sender, "/mapper tags <region>", "List the tags available for a region name.");
        sender.sendMessage(Component.text("Region tools: ", NamedTextColor.GOLD)
                .append(Component.text("/mapper pos1, pos2, cuboid, point, perspective, polygon, path, "
                        + "delete, tag, copy, offset, mirror", NamedTextColor.GRAY)));
    }

    private void line(CommandSender sender, String command, String description) {
        sender.sendMessage(Component.text("● " + command, NamedTextColor.WHITE)
                .append(Component.text(" - " + description, NamedTextColor.GRAY)));
    }

    @Command("tags <region>")
    public void tags(CommandSourceStack source, @Argument("region") String region) {
        final CommandSender sender = source.getSender();
        final List<Tag> tags = mapper.getTagRegistry().getTags(region);

        if (tags.isEmpty()) {
            sender.sendMessage(Messages.PREFIX.append(Component.text("No tags available for ", NamedTextColor.RED))
                    .append(Component.text("'" + region + "'", NamedTextColor.DARK_RED))
                    .append(Component.text(".", NamedTextColor.RED)));
            return;
        }

        sender.sendMessage(Messages.PREFIX.append(Component.text("Tags for ", NamedTextColor.GOLD))
                .append(Component.text("'" + region + "'", NamedTextColor.YELLOW)));
        for (Tag tag : tags) {
            sender.sendMessage(Component.text("● ", NamedTextColor.WHITE)
                    .append(Component.text(tag.usage(), NamedTextColor.YELLOW))
                    .append(Component.text(" - " + tag.description(), NamedTextColor.GRAY)));
        }
    }

    @Command("edit")
    public void edit(CommandSourceStack source) {
        join(source, SessionRole.EDITOR);
    }

    @Command("watch")
    public void watch(CommandSourceStack source) {
        join(source, SessionRole.VIEWER);
    }

    /**
     * Joins the session for the world the player is standing in, opening it if nobody is editing
     * there yet.
     * <p>
     * The world's regions are read from disk only when the session is opened. A second editor joins
     * the copy already in memory instead of loading their own, which is the whole point of keying
     * sessions by world: two private copies of one file is how edits get silently overwritten.
     */
    private void join(CommandSourceStack source, SessionRole role) {
        final Player player = support.player(source, "edit regions");
        if (player == null) return;

        final SessionManager sessionManager = mapper.getSessionManager();
        final EditSession existing = sessionManager.getSession(player);
        if (existing != null) {
            // Joining a second session would drop them from the first, and if they were its last
            // member that would close it and discard everyone's unsaved regions. Leaving has to be
            // something they said, not a side effect of walking through a portal and editing again.
            support.deny(player, existing.getWorld().equals(player.getWorld())
                    ? "You are already in this world's session."
                    : "You are still in the " + existing.getWorld().getName()
                    + " session. Use /mapper leave first.");
            return;
        }

        final boolean opening = sessionManager.getSession(player.getWorld()) == null;
        if (opening && !role.isCanEdit()) {
            // Nothing to watch yet, and a session of viewers alone could never be saved.
            support.deny(player, "Nobody is editing this world. Use /mapper edit to start.");
            return;
        }

        final SessionMember member = sessionManager.join(player, role);
        final EditSession session = sessionManager.getSession(player);

        if (opening) {
            loadRegions(session, player.getWorld());
            support.confirm(player, Component.text("Session started for ", NamedTextColor.GREEN)
                    .append(Component.text(player.getWorld().getName(), NamedTextColor.WHITE))
                    .append(Component.text(" with " + session.getRegions().size() + " regions.", NamedTextColor.GREEN)));
        } else {
            support.confirm(player, Component.text("Joined the session as ", NamedTextColor.GREEN)
                    .append(Component.text(member.getRole().getDisplayName(), member.getRole().getColor()))
                    .append(Component.text(" - " + session.getMembers().size() + " editors here.", NamedTextColor.GREEN)));
        }

        session.broadcastExcept(player, Component.text(player.getName() + " joined the session as ", NamedTextColor.GRAY)
                .append(Component.text(member.getRole().getDisplayName(), member.getRole().getColor())));
    }

    private void loadRegions(EditSession session, World world) {
        final JsonExportStrategy json = (JsonExportStrategy) mapper.getExportManager()
                .getAvailableStrategies().get("json");
        final File file = mapper.getStorageManager().getRegionsFile(world);
        final List<Region> read = json.read(file);
        // Locations are stored without a world; bind them to the editing world before display.
        read.forEach(region -> region.setWorld(world));
        read.forEach(session::addRegion);
    }

    @Command("session")
    public void session(CommandSourceStack source) {
        final Player player = support.player(source, "inspect a session");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        player.sendMessage(Messages.PREFIX.append(Component.text("Editing ", NamedTextColor.GOLD))
                .append(Component.text(session.getWorld().getName(), NamedTextColor.WHITE))
                .append(Component.text(" - " + session.getRegions().size() + " regions", NamedTextColor.GRAY)));

        for (SessionMember member : session.getMembers()) {
            final Component status = member.isOnline()
                    ? Component.text(" online", NamedTextColor.DARK_GRAY)
                    : Component.text(" offline", NamedTextColor.DARK_RED);
            player.sendMessage(Component.text("● ", NamedTextColor.WHITE)
                    .append(Component.text(member.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" " + member.getRole().getDisplayName(), member.getRole().getColor()))
                    .append(status));
        }
    }

    @Command("leave")
    public void leave(CommandSourceStack source) {
        final Player player = support.player(source, "leave a session");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        final boolean last = session.getMembers().size() == 1;
        session.broadcastExcept(player, Component.text(player.getName() + " left the session.", NamedTextColor.GRAY));
        mapper.getSessionManager().leave(player);
        prompt.clear(player);

        support.confirm(player, last
                ? Component.text("You left, and the session closed. Unsaved regions were discarded.", NamedTextColor.YELLOW)
                : Component.text("You left the session. The others are still editing.", NamedTextColor.GREEN));
    }

    @Command("save")
    public void save(CommandSourceStack source) {
        performSave(source, false);
    }

    @Command("save force")
    public void saveForce(CommandSourceStack source) {
        performSave(source, true);
    }

    /**
     * Validates and writes the session's regions.
     * <p>
     * The session deliberately stays open afterwards. A save is a checkpoint in a shared world, not
     * the end of everyone else's work, and closing it here would drop every other member's display
     * and tools the moment one person saved.
     *
     * @param force whether to write despite validation errors
     */
    private void performSave(CommandSourceStack source, boolean force) {
        final Player player = support.player(source, "save regions");
        if (player == null) return;
        final EditSession session = support.editing(player);
        if (session == null) return;

        final List<Region> regions = session.getRegions();
        if (regions.isEmpty()) {
            support.deny(player, "There is nothing to save.");
            return;
        }

        final List<ValidationIssue> issues =
                mapper.getValidationRegistry().validate(session.getWorld(), regions);
        report(player, issues);

        final boolean hasErrors = issues.stream().anyMatch(ValidationIssue::isError);
        if (hasErrors && !force) {
            player.sendMessage(Messages.PREFIX.append(Component.text("Save blocked. ", NamedTextColor.RED))
                    .append(Component.text("Fix the errors, or run ", NamedTextColor.GRAY))
                    .append(Component.text("/mapper save force", NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand("/mapper save force"))));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        final ExportStrategy json = mapper.getExportManager().getAvailableStrategies().get("json");
        final File file = mapper.getStorageManager().getRegionsFile(session.getWorld());
        if (!json.export(new ArrayList<>(regions), file)) {
            support.deny(player, "Failed to write " + file.getName() + " - see the console.");
            return;
        }

        session.broadcast(Messages.PREFIX.append(Component.text("Saved ", NamedTextColor.GREEN))
                .append(Component.text(regions.size() + " regions", NamedTextColor.WHITE))
                .append(Component.text(" (" + player.getName() + ")", NamedTextColor.GRAY)));
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        // Announced after the write, so a listener reloading from the file reads the new contents.
        new RegionsSavedEvent(session.getWorld(), file, new ArrayList<>(regions), player).callEvent();
    }

    @Command("validate")
    public void validate(CommandSourceStack source) {
        final Player player = support.player(source, "validate regions");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        final List<ValidationIssue> issues =
                mapper.getValidationRegistry().validate(session.getWorld(), session.getRegions());
        if (issues.isEmpty()) {
            support.confirm(player, Component.text("No problems found in "
                    + session.getRegions().size() + " regions.", NamedTextColor.GREEN));
            return;
        }
        report(player, issues);
    }

    /**
     * Prints validation issues, each one clickable so the player lands next to the region it is
     * about. A problem you cannot navigate to is barely a report.
     */
    private void report(Player player, List<ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }

        final long errors = issues.stream().filter(ValidationIssue::isError).count();
        player.sendMessage(Messages.PREFIX
                .append(Component.text(errors + " errors", NamedTextColor.RED))
                .append(Component.text(", " + (issues.size() - errors) + " warnings", NamedTextColor.YELLOW)));

        for (ValidationIssue issue : issues) {
            Component line = Component.text("● ", issue.getSeverity().getColor())
                    .append(Component.text(issue.getMessage(), NamedTextColor.GRAY));

            final Region region = issue.getRegion();
            if (region != null) {
                line = line.append(Component.text(" "))
                        .append(RegionFormat.describe(region))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/mapper goto " + region.getName()));
            }
            player.sendMessage(line);
        }
    }

    /**
     * The filter carries no {@code @Default}: an empty one hangs Cloud's parser, which re-parses the
     * same node with the default appended and so never consumes anything. An omitted optional
     * arrives as {@code null}.
     */
    @Command("list [filter]")
    public void list(CommandSourceStack source, @Nullable @Argument("filter") String filter) {
        final Player player = support.player(source, "browse regions");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        final String needle = filter == null ? "" : filter;
        final List<Region> shown = session.getRegions().stream()
                .filter(region -> needle.isEmpty() || SessionMember.matches(region, needle))
                .toList();

        if (shown.isEmpty()) {
            support.deny(player, "No regions match '" + filter + "'.");
            return;
        }

        final String title = needle.isEmpty()
                ? "Regions (" + shown.size() + ")"
                : "Regions matching '" + filter + "' (" + shown.size() + ")";
        mapper.getGuiManager().openRegionBrowser(session, player, mapper.getSelectionHandler(), shown, title);
    }

    /**
     * Shows the player only the regions whose name or tags contain the text, so one kind of
     * datapoint can be worked on without the rest in the way. Without text, shows everything again.
     * The tools only act on regions the player can see.
     */
    @Command("filter [text]")
    public void filter(CommandSourceStack source, @Nullable @Argument("text") String text) {
        final Player player = support.player(source, "filter regions");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        final String filter = text == null || text.isBlank() ? null : text;
        session.setFilter(player, filter);
        if (filter == null) {
            support.confirm(player, Component.text("Showing every region.", NamedTextColor.GREEN));
            return;
        }
        final long shown = session.visibleTo(player).size();
        support.confirm(player, Component.text("Showing " + shown + " of " + session.getRegions().size()
                + " regions matching '" + filter + "'. /mapper filter to show all.", NamedTextColor.GREEN));
    }

    @Command("goto <region>")
    public void gotoRegion(CommandSourceStack source,
                           @Argument(value = "region", suggestions = "regionNames") String region) {
        final Player player = support.player(source, "teleport to regions");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        prompt.choose(player, support.matching(session, region), "teleport to", target -> {
            final Location anchor = RegionTransform.anchor(target);
            anchor.setYaw(player.getLocation().getYaw());
            anchor.setPitch(player.getLocation().getPitch());
            player.teleport(anchor);
            support.confirm(player, Component.text("Teleported to ", NamedTextColor.GREEN)
                    .append(RegionFormat.describe(target)));
        });
    }

    @Command("pick <index>")
    public void pick(CommandSourceStack source, @Argument("index") int index) {
        final Player player = support.player(source, "pick a region");
        if (player == null) return;
        prompt.pick(player, index);
    }

    @Command("export [strategy]")
    public void export(CommandSourceStack source, @Default("json") @Argument("strategy") String strategy) {
        final Player player = support.player(source, "export regions");
        if (player == null) return;
        final EditSession session = support.session(player);
        if (session == null) return;

        final List<Region> regions = session.getRegions();
        if (regions.isEmpty()) {
            support.deny(player, "There is nothing to export.");
            return;
        }

        final ExportStrategy exportStrategy =
                mapper.getExportManager().getAvailableStrategies().get(strategy.toLowerCase(Locale.ROOT));
        if (exportStrategy == null) {
            player.sendMessage(Messages.PREFIX.append(Component.text("Unknown export strategy.", NamedTextColor.RED)));
            final List<TextComponent> strategies = mapper.getExportManager().getAvailableStrategies().keySet().stream()
                    .map(str -> Component.text(str, NamedTextColor.WHITE))
                    .toList();
            player.sendMessage(Component.text("Available strategies: ", NamedTextColor.GRAY)
                    .append(Component.join(JoinConfiguration.commas(true), strategies)));
            player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.4f);
            return;
        }

        final File folder = mapper.getStorageManager().getExportDirectory(session.getWorld());
        final File file = new File(folder, dateFormat.format(new Date()) + ".json");
        exportStrategy.export(new ArrayList<>(regions), file);

        Component savedMessage = Messages.PREFIX.append(Component.text("Regions exported to: ", NamedTextColor.GREEN))
                .append(Component.text(file.getName(), NamedTextColor.DARK_GREEN));

        // If the strategy can produce a copyable string, make the message click-to-copy.
        final String serialized = exportStrategy.serialize(new ArrayList<>(regions));
        if (serialized != null) {
            savedMessage = savedMessage
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy the exported data", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.copyToClipboard(serialized));
        }

        player.sendMessage(savedMessage);
        player.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
    }

    @Command("metadata")
    public void metadata(CommandSourceStack source) {
        final Player player = support.player(source, "edit metadata");
        if (player == null) return;

        final World world = player.getWorld();
        final MetadataManager metadataManager = mapper.getMetadataManager();
        final MapMetadata metadata = metadataManager.loadOrCreateMetadata(world);
        mapper.getGuiManager().openMetadataEditor(player, metadata);
    }
}
