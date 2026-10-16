package dev.brauw.mapper.listener;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.region.Region;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.session.EditSession;
import dev.brauw.mapper.tool.RegionToolManager;
import dev.brauw.mapper.tool.ToolRegistry.ToolType;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor
public class RegionToolListener implements Listener {

    private final Mapper mapper;
    private final RegionToolManager toolManager;
    private final SelectionHandler selectionHandler;

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        if (item == null) return;

        final EditSession session = editableSession(player);
        if (session == null) return;

        if (toolManager.isTool(item, ToolType.CUBOID_WAND)) {
            event.setCancelled(true);
            handleCuboidWand(event, session, player);
        }
        else if (toolManager.isTool(item, ToolType.POINT_REGION_CREATOR) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.createPointRegion(session, player, event.getInteractionPoint(), null);
        }
        else if (toolManager.isTool(item, ToolType.PERSPECTIVE_REGION_CREATOR) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.createPerspectiveRegion(session, player, event.getInteractionPoint(), null);
        }
        else if (toolManager.isTool(item, ToolType.REGION_DELETION_TOOL) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.handleRegionDeletion(session, player);
        }
        else if (toolManager.isTool(item, ToolType.POLYGON_WAND)) {
            event.setCancelled(true);
            handlePolygonWand(event, session, player);
        }
        else if (toolManager.isTool(item, ToolType.TAG_TOOL) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            selectionHandler.handleTagEditor(session, player);
        }
        else if (toolManager.isTool(item, ToolType.PATH_WAND)) {
            event.setCancelled(true);
            handlePathWand(event, session, player);
        }
        else if (toolManager.isTool(item, ToolType.CLIPBOARD_TOOL) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            handleClipboard(session, player);
        }
        else if (toolManager.isTool(item, ToolType.RESIZE_TOOL)) {
            event.setCancelled(true);
            if (event.getAction().isRightClick()) {
                mapper.getCuboidResizer().use(session, player);
            } else if (event.getAction().isLeftClick()) {
                mapper.getCuboidResizer().cancel(player);
            }
        }
    }

    /**
     * Right-click copies what you are looking at; sneak + right-click pastes it where you stand.
     * <p>
     * Both are right-clicks, so sneaking is the whole distinction - which also means the paste never
     * needs anything under the crosshair, and works while looking at open air.
     */
    private void handleClipboard(EditSession session, Player player) {
        if (player.isSneaking()) {
            selectionHandler.handlePaste(session, player, null);
        } else {
            selectionHandler.handleCopy(session, player);
        }
    }

    /**
     * Handles right-clicking an entity-backed region display (e.g. the armor stand of a
     * PerspectiveRegion). We listen to {@link PlayerInteractAtEntityEvent} because armor stands fire
     * that variant rather than the base interact-entity event. The clicked entity is traced back to
     * its region via the UUID stamped on its persistent data container, then the held tool decides
     * the action.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        // Only react to the main hand so the handler doesn't fire twice (main + off hand).
        if (event.getHand() != EquipmentSlot.HAND) return;

        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) return;

        final EditSession session = editableSession(player);
        if (session == null) return;

        // Paste needs no target, so it is handled before the entity is resolved - otherwise sneaking
        // while facing an NPC marker would swallow the click and paste nothing.
        if (toolManager.isTool(item, ToolType.CLIPBOARD_TOOL) && player.isSneaking()) {
            event.setCancelled(true);
            selectionHandler.handlePaste(session, player, null);
            return;
        }

        final Region region = resolveRegion(session, event.getRightClicked());
        if (region == null) return;

        // The same tools that act on a raytraced region also act on its display entity.
        if (toolManager.isTool(item, ToolType.REGION_DELETION_TOOL)) {
            event.setCancelled(true);
            selectionHandler.deleteConfirmed(session, player, region);
        } else if (toolManager.isTool(item, ToolType.TAG_TOOL)) {
            event.setCancelled(true);
            selectionHandler.openTagEditor(player, region);
        } else if (toolManager.isTool(item, ToolType.CLIPBOARD_TOOL)) {
            event.setCancelled(true);
            selectionHandler.copyRegion(player, region);
        }
    }

    /**
     * Scrolling while a resize face is grabbed nudges it a block instead of changing the held slot.
     * Sneak + scroll while holding the deletion, tag or clipboard tool steps through the regions
     * under the crosshair instead.
     */
    @EventHandler
    public void onScroll(PlayerItemHeldEvent event) {
        final Player player = event.getPlayer();
        final int forward = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        if (mapper.getCuboidResizer().isGrabbing(player)) {
            event.setCancelled(true);
            mapper.getCuboidResizer().nudge(player, forward <= 4);
            return;
        }

        if (!player.isSneaking()
                || mapper.getTargetTracker().heldTool(player.getInventory().getItem(event.getPreviousSlot())) == null) {
            return;
        }

        if (mapper.getTargetTracker().cycle(player, forward <= 4 ? 1 : -1)) {
            event.setCancelled(true);
        }
    }

    /**
     * Resolves the session a player is allowed to change with a tool.
     * <p>
     * A viewer can be holding tools - they may have been demoted mid-session, or picked one up - so
     * the role is checked at use time rather than inferred from the fact that they hold the item.
     *
     * @param player the acting player
     * @return their session, or {@code null} if they have none here or may not edit it
     */
    private @Nullable EditSession editableSession(Player player) {
        final EditSession session = mapper.getSessionManager().getSession(player);
        if (session == null || !session.getWorld().equals(player.getWorld())) {
            return null;
        }

        if (!session.canEdit(player)) {
            player.sendActionBar(Component.text("You are viewing this session, not editing it.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return null;
        }
        return session;
    }

    /**
     * Resolves the region that a clicked display entity represents, by reading the region UUID
     * stamped on the entity and matching it against the session.
     *
     * @param session the player's edit session
     * @param entity  the clicked entity
     * @return the matching region, or {@code null} if the entity isn't a region display
     */
    private @Nullable Region resolveRegion(EditSession session, Entity entity) {
        final String rawId = entity.getPersistentDataContainer()
                .get(mapper.getRegionIdKey(), PersistentDataType.STRING);
        if (rawId == null) return null;

        final UUID regionId = UUID.fromString(rawId);
        return session.getRegions().stream()
                .filter(region -> region.getId().equals(regionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Left-click sets the first corner and right-click the second, at the clicked block, or at the
     * block the player stands in when the click hits air. Sneak + right-click into the air finishes
     * the region, so a sneaking right-click never sets a corner there.
     */
    private void handleCuboidWand(PlayerInteractEvent event, EditSession session, Player player) {
        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> selectionHandler.setFirstPosition(player);
            case RIGHT_CLICK_BLOCK -> selectionHandler.setSecondPosition(player);
            case LEFT_CLICK_AIR -> selectionHandler.setFirstPositionHere(player);
            case RIGHT_CLICK_AIR -> {
                if (player.isSneaking()) {
                    selectionHandler.createCuboidRegion(session, player, null);
                } else {
                    selectionHandler.setSecondPositionHere(player);
                }
            }
            default -> { }
        }
    }

    /**
     * Right-click appends a waypoint, left-click undoes the last one, and sneak + right-click
     * finishes the path. Sneaking is reserved for finishing, so unlike the point tools it does not
     * also mean "use my standing position" - {@code addPathPoint} falls back to that on its own when
     * a click yields no interaction point.
     */
    private void handlePathWand(PlayerInteractEvent event, EditSession session, Player player) {
        if (event.getAction().isRightClick() && player.isSneaking()) {
            selectionHandler.createPathRegion(session, player, null);
        } else if (event.getAction().isRightClick()) {
            selectionHandler.addPathPoint(player, player.getLocation());
        } else if (event.getAction().isLeftClick()) {
            selectionHandler.undoPathPoint(player);
        }
    }

    private void handlePolygonWand(PlayerInteractEvent event, EditSession session, Player player) {
        if (event.getAction().isRightClick() && player.isSneaking()) {
            if (selectionHandler.hasCompleteSelection(player)) {
                selectionHandler.addPolygonChild(player);
            } else {
                selectionHandler.createPolygonRegion(session, player, null);
            }
            return;
        }

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> selectionHandler.setFirstPosition(player);
            case RIGHT_CLICK_BLOCK -> selectionHandler.setSecondPosition(player);
            case LEFT_CLICK_AIR -> selectionHandler.setFirstPositionHere(player);
            case RIGHT_CLICK_AIR -> selectionHandler.setSecondPositionHere(player);
            default -> { }
        }
    }
}
