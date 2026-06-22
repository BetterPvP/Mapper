package dev.brauw.mapper.listener;

import dev.brauw.mapper.Mapper;
import dev.brauw.mapper.session.event.SessionCreateEvent;
import dev.brauw.mapper.session.event.SessionEndEvent;
import dev.brauw.mapper.tool.RegionToolManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public class SessionListener implements Listener {

    private final Mapper mapper;
    private final RegionToolManager toolManager;
    
    @EventHandler
    public void onSessionCreate(SessionCreateEvent event) {
        Player player = event.getPlayer();
        toolManager.giveTools(player);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Only give tools if they have an active session
        if (mapper.getSessionManager().hasSession(player)) {
            toolManager.giveTools(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Clear the tools from inventory when player quits
        if (mapper.getSessionManager().hasSession(player)) {
            toolManager.removeTools(player);
        }
    }
    
    @EventHandler
    public void onSessionEnd(SessionEndEvent event) {
        Player player = event.getPlayer();
        // Clear the tools from inventory when session ends
        toolManager.removeTools(player);
    }
}