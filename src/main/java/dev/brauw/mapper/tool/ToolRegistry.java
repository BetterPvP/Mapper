package dev.brauw.mapper.tool;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages the creation and identification of region tools
 */
public class ToolRegistry {

    @Getter private final NamespacedKey toolTypeKey;

    public enum ToolType {
        POINT_REGION_CREATOR(0, Material.BEACON, "Point Region Creator"),
        PERSPECTIVE_REGION_CREATOR(1, Material.ARMOR_STAND, "Perspective Region Creator"),
        CUBOID_WAND(2, Material.BLAZE_ROD, "Cuboid Region Wand"),
        POLYGON_WAND(3, Material.BREEZE_ROD, "Polygon Region Wand"),
        REGION_DELETION_TOOL(4, Material.SHEARS, "Region Deletion Tool"),
        TAG_TOOL(5, Material.NAME_TAG, "Tag Tool");

        @Getter final int slot;
        @Getter final Material material;
        @Getter final String displayName;

        ToolType(int slot, Material material, String displayName) {
            this.slot = slot;
            this.material = material;
            this.displayName = displayName;
        }
    }

    public ToolRegistry(Plugin plugin) {
        this.toolTypeKey = new NamespacedKey(plugin, "region_tool_type");
    }

    public ItemStack createTool(ToolType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(toolTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTool(ItemStack item, ToolType type) {
        if (item == null || !item.hasItemMeta()) return false;
        String toolType = item.getItemMeta().getPersistentDataContainer().get(toolTypeKey, PersistentDataType.STRING);
        return type.name().equals(toolType);
    }
}