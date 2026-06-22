package dev.brauw.mapper.session.display;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.brauw.mapper.region.PointRegion;
import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Renders a glowing armorstand at the region's location.
 */
public class ArmorStandStrategy implements RegionDisplayStrategy<PointRegion> {

    private final Map<PointRegion, ArmorStand> displays = new HashMap<>();
    private final Map<PointRegion, TextDisplay> labels = new HashMap<>();
    private final Multimap<PointRegion, UUID> viewers = ArrayListMultimap.create();
    private final Map<UUID, Map<PointRegion, String>> playerTeams = new HashMap<>();
    private final Plugin plugin;
    private final NamespacedKey regionIdKey;

    public ArmorStandStrategy(Plugin plugin, NamespacedKey regionIdKey) {
        this.plugin = plugin;
        this.regionIdKey = regionIdKey;
    }

    private ArmorStand getEntity(PointRegion region) {
        final ArmorStand existing = displays.get(region);
        if (existing != null && !existing.isValid()) {
            displays.remove(region);
        }

        final Location location = region.getLocation();

        return displays.computeIfAbsent(region, key -> {
            return location.getWorld().spawn(location, ArmorStand.class, spawned -> {
                spawned.setGlowing(true);
                spawned.setVisibleByDefault(false);
                spawned.setSmall(true);
                spawned.setAI(false); // no gravity or physics
                spawned.setDisabledSlots(EquipmentSlot.values());
                spawned.setArms(true);
                spawned.setInvulnerable(true);
                spawned.setGravity(false);
                spawned.setNoPhysics(true);

                // make it so we can see the armorstand head with an item
                spawned.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));

                // Important, because we don't want the entity to be saved in case the server shuts down
                spawned.setPersistent(false);

                // Stamp the owning region's id so a click on this entity can be resolved back to the region
                spawned.getPersistentDataContainer().set(
                        regionIdKey, PersistentDataType.STRING, region.getId().toString());

                // Direction to rotation angles
                Vector direction = location.getDirection();
                final Location lookAt = location.clone().add(direction);
                spawned.lookAt(lookAt, LookAnchor.EYES);
            });
        });
    }

    private void setupTeamForPlayer(PointRegion region, Player player, ArmorStand armorStand) {
        Scoreboard scoreboard = player.getScoreboard();
        String teamName = "mapper_" + player.getUniqueId().toString().substring(0, 8) + "_" + region.hashCode();

        // Remove from any existing teams first
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(armorStand.getUniqueId().toString())) {
                team.removeEntry(armorStand.getUniqueId().toString());
            }
        }

        // Get or create the team
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            final Color color = region.getOptions().getColor().getBukkitColor();
            team.color(NamedTextColor.nearestTo(TextColor.color(color.asRGB())));
        }

        // Add the armorstand to the team
        team.addEntry(armorStand.getUniqueId().toString());

        // Store the team name for cleanup
        playerTeams.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(region, teamName);
    }

    private void cleanupTeamForPlayer(PointRegion region, Player player) {
        UUID playerUUID = player.getUniqueId();
        Map<PointRegion, String> teams = playerTeams.get(playerUUID);

        if (teams != null && teams.containsKey(region)) {
            String teamName = teams.remove(region);
            Scoreboard scoreboard = player.getScoreboard();
            Team team = scoreboard.getTeam(teamName);

            if (team != null) {
                team.unregister();
            }

            if (teams.isEmpty()) {
                playerTeams.remove(playerUUID);
            }
        }
    }

    private TextDisplay getLabel(PointRegion region) {
        final TextDisplay existingLabel = labels.get(region);
        if (existingLabel != null && !existingLabel.isValid()) {
            labels.remove(region);
        }

        final Location location = region.getLocation().add(0, 1.5, 0);
        final Color color = region.getOptions().getColor().getBukkitColor();

        return labels.computeIfAbsent(region, key -> location.getWorld().spawn(location, TextDisplay.class, spawned -> {
            spawned.text(Component.text(region.getName()).color(TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        }));
    }

    @Override
    public void display(@NotNull PointRegion region, @NotNull Player player) {
        // Get or create the armorstand for this region
        final ArmorStand armorStand = getEntity(region);

        // Show the armorstand and label to the player
        player.showEntity(plugin, armorStand);
        player.showEntity(plugin, getLabel(region));

        // Setup team with color for this player
        setupTeamForPlayer(region, player, armorStand);

        viewers.put(region, player.getUniqueId());
    }

    @Override
    public void update(@NotNull PointRegion region, @NotNull Player player) {
        final ArmorStand removed = displays.remove(region);
        if (removed != null && removed.isValid()) {
            cleanupTeamForPlayer(region, player);
            removed.remove();
        }
        final TextDisplay removedLabel = labels.remove(region);
        if (removedLabel != null && removedLabel.isValid()) {
            removedLabel.remove();
        }
        display(region, player);
    }

    @Override
    public void revalidate(@NotNull PointRegion region, @NotNull Player player) {
        final ArmorStand entity = displays.get(region);
        final TextDisplay label = labels.get(region);
        boolean needsRefresh = (entity != null && !entity.isValid()) || (label != null && !label.isValid());
        if (needsRefresh) {
            if (entity != null && !entity.isValid()) displays.remove(region);
            if (label != null && !label.isValid()) labels.remove(region);
            final ArmorStand armorStand = getEntity(region);
            player.showEntity(plugin, armorStand);
            player.showEntity(plugin, getLabel(region));
            setupTeamForPlayer(region, player, armorStand);
        }
    }

    @Override
    public void hide(@NotNull PointRegion region, @NotNull Player player) {
        final UUID playerUUID = player.getUniqueId();
        if (viewers.remove(region, playerUUID)) {
            // Cleanup the team
            cleanupTeamForPlayer(region, player);

            // Hide entity and label
            final ArmorStand entity = Objects.requireNonNull(displays.get(region));
            player.hideEntity(plugin, entity);

            final TextDisplay label = labels.get(region);
            if (label != null) {
                player.hideEntity(plugin, label);
            }

            // If there are no more viewers, remove the displays
            if (viewers.get(region).isEmpty()) {
                final ArmorStand armorStand = displays.remove(region);
                if (armorStand != null && armorStand.isValid()) {
                    armorStand.remove();
                }
                final TextDisplay removedLabel = labels.remove(region);
                if (removedLabel != null && removedLabel.isValid()) {
                    removedLabel.remove();
                }
            }
        }
    }
}