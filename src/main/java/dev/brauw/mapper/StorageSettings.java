package dev.brauw.mapper;

/**
 * On-disk path settings for region/metadata/export files. Values may contain the
 * {@code %world%} and {@code %plugin%} placeholders resolved by the storage layer.
 */
public record StorageSettings(
        String dataDirectory,
        String regionsFileName,
        String metadataFileName,
        String exportDirectory) {

    /** Defaults matching Mapper's bundled config.yml. */
    public static StorageSettings defaults() {
        return new StorageSettings("%world%", "dataPoints.json", "metadata.json", "%plugin%/exports");
    }
}
