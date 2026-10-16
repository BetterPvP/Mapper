package dev.brauw.mapper.session;

import dev.brauw.mapper.region.Region;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * One participant in an {@link EditSession}.
 * <p>
 * Identity is the player's UUID rather than the {@link Player} object, so a member survives a
 * reconnect: the session keeps their role and their place in the member list while they are offline,
 * and re-shows every region when they come back.
 */
@Getter
public class SessionMember {

    private final UUID playerId;
    private final String name;
    private final long joinedAt;

    @Setter
    private SessionRole role;

    /** Shows this member only the regions whose name or tags contain it, or every region when null. */
    @Setter
    private @Nullable String filter;

    public SessionMember(Player player, SessionRole role) {
        this.playerId = player.getUniqueId();
        this.name = player.getName();
        this.role = role;
        this.joinedAt = System.currentTimeMillis();
    }

    /**
     * @return the online player behind this member, or {@code null} if they have disconnected
     */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public boolean canEdit() {
        return role.isCanEdit();
    }

    /** @return whether this member's filter lets them see the region */
    public boolean shows(Region region) {
        return filter == null || matches(region, filter);
    }

    /**
     * @param region the region
     * @param filter the text to look for
     * @return whether the region's name or one of its tags contains the text, ignoring case
     */
    public static boolean matches(Region region, String filter) {
        final String needle = filter.toLowerCase(Locale.ROOT);
        return region.getName().toLowerCase(Locale.ROOT).contains(needle)
                || region.getOptions().getTags().stream()
                .anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
    }
}
