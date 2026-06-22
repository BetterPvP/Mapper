package dev.brauw.mapper;

import dev.brauw.mapper.export.ExportManager;
import dev.brauw.mapper.logger.BukkitLoggerFactory;
import dev.brauw.mapper.storage.StorageManager;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.plugin.Plugin;

/**
 * Headless entry point to Mapper's data layer (storage + export), usable without
 * running Mapper as a standalone plugin.
 * <p>
 * Two ways in:
 * <ul>
 *   <li>{@link MapperPlugin} initializes this automatically on enable.</li>
 *   <li>A host plugin that shades Mapper calls {@link #initialize(Plugin)} once,
 *       then reads regions through {@link #get()} — no Bukkit plugin of its own.</li>
 * </ul>
 * The editor (GUI, commands, tools, live edit sessions) only exists in plugin mode;
 * embedded mode is read/write of region files only.
 */
@Getter
@CustomLog
public final class Mapper {

    private static Mapper instance;

    private final Plugin plugin;
    private final StorageManager storageManager;
    private final ExportManager exportManager;

    private Mapper(Plugin plugin, StorageSettings settings) {
        this.plugin = plugin;
        this.exportManager = new ExportManager(plugin);
        this.storageManager = new StorageManager(
                plugin,
                settings.dataDirectory(),
                settings.regionsFileName(),
                settings.metadataFileName(),
                settings.exportDirectory());
    }

    /** Initializes the data layer with default storage settings. */
    public static Mapper initialize(Plugin plugin) {
        return initialize(plugin, StorageSettings.defaults());
    }

    /** Initializes the data layer; idempotent — the first call wins. */
    public static synchronized Mapper initialize(Plugin plugin, StorageSettings settings) {
        if (instance != null) {
            return instance;
        }
        // Route Mapper's logs to the host plugin (no-op if already initialized by the plugin).
        BukkitLoggerFactory.initialize(plugin);
        instance = new Mapper(plugin, settings);
        log.info("Mapper data layer initialized (embedded=" + !(plugin instanceof MapperPlugin) + ")");
        return instance;
    }

    /** @return the initialized instance; throws if {@link #initialize} was never called. */
    public static Mapper get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "Mapper has not been initialized. Call Mapper.initialize(plugin) before use.");
        }
        return instance;
    }

    /** @return whether the data layer has been initialized. */
    public static boolean isInitialized() {
        return instance != null;
    }
}
