package dev.brauw.mapper;

import java.util.List;

/**
 * All settings Mapper needs to run, supplied explicitly so an embedded host need not
 * ship a {@code config.yml}. {@link MapperPlugin} builds this from its config; shaded
 * consumers can use {@link #defaults()}.
 */
public record MapperSettings(StorageSettings storage, String defaultMapName, List<String> availableGamemodes) {

    /** Defaults matching Mapper's bundled config.yml. */
    public static MapperSettings defaults() {
        return new MapperSettings(StorageSettings.defaults(), "Unnamed Map", List.of());
    }
}
