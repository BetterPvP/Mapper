package dev.brauw.mapper;

import dev.brauw.mapper.logger.BukkitLoggerFactory;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Standalone entry point. Reads {@code config.yml} and hands the resulting settings to the shared
 * {@link Mapper} runtime, which owns all the actual functionality. When Mapper is shaded into
 * another plugin instead, that host calls {@link Mapper#initialize(JavaPlugin, MapperSettings)}
 * directly and this class is never loaded.
 */
@CustomLog
public class MapperPlugin extends JavaPlugin {

    @Getter
    private static MapperPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        BukkitLoggerFactory.initialize(this);

        final StorageSettings storage = new StorageSettings(
                getConfig().getString("storage.data-directory", "%world%"),
                getConfig().getString("storage.regions-file", "dataPoints.json"),
                getConfig().getString("storage.metadata-file", "metadata.json"),
                getConfig().getString("storage.export-directory", "%plugin%/exports"));
        final String defaultName = getConfig().getString("metadata.default-map-name", "Unnamed Map");
        final List<String> gamemodes = getConfig().getStringList("metadata.available-gamemodes");

        Mapper.initialize(this, new MapperSettings(storage, defaultName, gamemodes));
        log.info("Mapper plugin enabled!");
    }

    @Override
    public void onDisable() {
        log.info("Mapper plugin disabled!");
    }
}
