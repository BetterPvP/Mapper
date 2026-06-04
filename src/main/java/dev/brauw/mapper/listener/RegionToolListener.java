package dev.brauw.mapper.listener;

import dev.brauw.mapper.MapperPlugin;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tool.RegionToolManager;
import dev.brauw.mapper.tool.ToolRegistry.ToolType;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor
public class RegionToolListener implements Listener {

    private final MapperPlugin plugin;
    private final RegionToolManager toolManager;
    private final SelectionHandler selectionHandler;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        // Check if player has active session
        if (!plugin.getSessionManager().hasSession(player)) return;
        EditSession session = plugin.getSessionManager().getSession(player);

        // Process tool interactions
        if (toolManager.isTool(item, ToolType.CUBOID_WAND)) {
            event.setCancelled(true);
            handleCuboidWand(event, session);
        }
        else if (toolManager.isTool(item, ToolType.POINT_REGION_CREATOR) &&
                event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.createPointRegion(session, event.getInteractionPoint());
        }
        else if (toolManager.isTool(item, ToolType.PERSPECTIVE_REGION_CREATOR) &&
                event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.createPerspectiveRegion(session, event.getInteractionPoint());
        }
        else if (toolManager.isTool(item, ToolType.REGION_DELETION_TOOL) &&
                event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.handleRegionDeletion(session, event);
        }
        else if (toolManager.isTool(item, ToolType.POLYGON_WAND)) {
            event.setCancelled(true);
            handlePolygonWand(event, session);
        }
        else if (toolManager.isTool(item, ToolType.TAG_TOOL) &&
                event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.handleTagEditor(session, event);
        }
    }

    /**
     * Handles right-clicking an entity-backed region display (e.g. the armor stand
     * of a PerspectiveRegion). We listen to {@link PlayerInteractAtEntityEvent}
     * because armor stands fire that variant rather than the base interact-entity
     * event. The clicked entity is traced back to its region via the UUID stamped
     * on its persistent data container, then the held tool decides the action.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        // Only react to the main hand so the handler doesn't fire twice (main + off hand).
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) return;

        if (!plugin.getSessionManager().hasSession(player)) return;
        EditSession session = plugin.getSessionManager().getSession(player);

        Region region = resolveRegion(session, event.getRightClicked());
        if (region == null) return;

        // The same tools that act on a raytraced region also act on its display entity.
        if (toolManager.isTool(item, ToolType.REGION_DELETION_TOOL)) {
            event.setCancelled(true);
            selectionHandler.deleteRegion(session, region);
        } else if (toolManager.isTool(item, ToolType.TAG_TOOL)) {
            event.setCancelled(true);
            selectionHandler.openTagEditor(session, region);
        }
    }

    /**
     * Resolves the region that a clicked display entity represents, by reading the
     * region UUID stamped on the entity and matching it against the session.
     *
     * @param session the player's edit session
     * @param entity  the clicked entity
     * @return the matching region, or {@code null} if the entity isn't a region display
     */
    private @Nullable Region resolveRegion(EditSession session, Entity entity) {
        String rawId = entity.getPersistentDataContainer()
                .get(plugin.getRegionIdKey(), PersistentDataType.STRING);
        if (rawId == null) return null;

        final UUID regionId = UUID.fromString(rawId);
        return session.getRegions().stream()
                .filter(region -> region.getId().equals(regionId))
                .findFirst()
                .orElse(null);
    }

    private void handleCuboidWand(PlayerInteractEvent event, EditSession session) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionHandler.setFirstPosition(session, event);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionHandler.setSecondPosition(session, event);
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR && event.getPlayer().isSneaking()) {
            selectionHandler.createCuboidRegion(session);
        }
    }

    private void handlePolygonWand(PlayerInteractEvent event, EditSession session) {
        if (event.getAction().isRightClick() && event.getPlayer().isSneaking()) {
            if (selectionHandler.hasCompleteSelection(session)) {
                selectionHandler.addPolygonChild(session);
            } else {
                selectionHandler.createPolygonRegion(session);
            }
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selectionHandler.setFirstPosition(session, event);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            selectionHandler.setSecondPosition(session, event);
        }
    }
}
