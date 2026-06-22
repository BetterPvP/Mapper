package dev.brauw.mapper.listener;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.tool.RegionToolManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.PluginManager;

@RequiredArgsConstructor
public class ListenerManager {

    private final Mapper mapper;
    private final RegionToolManager regionToolManager;
    private final SelectionHandler selectionHandler;

    public void registerListeners() {
        PluginManager pm = mapper.getPlugin().getServer().getPluginManager();

        // Register listeners
        pm.registerEvents(new RegionToolListener(mapper, regionToolManager, selectionHandler), mapper.getPlugin());
        pm.registerEvents(new SessionListener(mapper, regionToolManager), mapper.getPlugin());
    }

}