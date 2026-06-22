package dev.brauw.mapper.storage;

import dev.brauw.mapper.MapperPlugin;
import org.bukkit.World;

import java.io.File;

/**
 * Resolves the on-disk locations used for saving and loading region data and
 * metadata, based on the {@code storage} section of the plugin config.
 * <p>
 * Centralising path resolution here means {@code /mapper save}, {@code /mapper edit}
 * and {@code /mapper metadata} all agree on where files live, instead of each
 * constructing {@link File} paths inline.
 */
public class StorageManager {

    /** Placeholder replaced with the target world's own folder. */
    private static final String WORLD_PLACEHOLDER = "%world%";
    /** Placeholder replaced with the plugin's data folder. */
    private static final String PLUGIN_PLACEHOLDER = "%plugin%";

    private final MapperPlugin plugin;
    private final String dataDirectory;
    private final String regionsFileName;
    private final String metadataFileName;
    private final String exportDirectory;

    public StorageManager(MapperPlugin plugin, String dataDirectory, String regionsFileName,
                          String metadataFileName, String exportDirectory) {
        this.plugin = plugin;
        this.dataDirectory = dataDirectory;
        this.regionsFileName = regionsFileName;
        this.metadataFileName = metadataFileName;
        this.exportDirectory = exportDirectory;
    }

    /**
     * Resolves the file region data is saved to / loaded from for the given world.
     */
    public File getRegionsFile(World world) {
        return new File(getDataDirectory(world), regionsFileName);
    }

    /**
     * Resolves the file metadata is saved to / loaded from for the given world.
     */
    public File getMetadataFile(World world) {
        return new File(getDataDirectory(world), metadataFileName);
    }

    /**
     * Resolves (and creates) the base data directory for the given world.
     */
    public File getDataDirectory(World world) {
        File directory = resolveDirectory(dataDirectory, world);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /**
     * Resolves (and creates) the directory used for timestamped exports.
     */
    public File getExportDirectory(World world) {
        File directory = resolveDirectory(exportDirectory, world);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    /**
     * Turns a configured path string into a real {@link File}.
     * <p>
     * The string may contain the placeholders {@code %world%} (the world's own
     * folder, see {@link World#getWorldFolder()}) and {@code %plugin%} (the
     * plugin data folder, see {@link MapperPlugin#getDataFolder()}). After
     * substitution, the path may be absolute or relative; relative paths are
     * resolved against the server root.
     *
     * @param configuredPath the raw value from config (e.g. {@code "%world%"})
     * @param world          the world the operation is acting on
     * @return the resolved directory
     */
    private File resolveDirectory(String configuredPath, World world) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return world.getWorldFolder();
        }

        String path = configuredPath
                .replace(WORLD_PLACEHOLDER, world.getWorldFolder().getAbsolutePath())
                .replace(PLUGIN_PLACEHOLDER, plugin.getDataFolder().getAbsolutePath());

        // Placeholders expand to absolute paths; a bare relative path resolves
        // against the JVM working directory (the server root).
        return new File(path);
    }
}
