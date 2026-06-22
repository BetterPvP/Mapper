package dev.brauw.mapper.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.brauw.mapper.storage.StorageManager;
import lombok.CustomLog;
import org.bukkit.World;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;

@CustomLog
public class MetadataManager {
    private final ObjectMapper objectMapper;
    private final String defaultName;
    private final List<String> gameModes;
    private final StorageManager storageManager;

    public MetadataManager(String defaultName, List<String> gameModes, StorageManager storageManager) {
        this.defaultName = defaultName;
        this.gameModes = gameModes;
        this.storageManager = storageManager;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public MapMetadata loadMetadata(InputStream metadataFile) throws IllegalArgumentException {
        if (metadataFile != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(metadataFile))) {
                return objectMapper.readValue(reader, MapMetadata.class);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to load metadata from input stream", e);
            }
        }
        throw new IllegalArgumentException("Metadata file not found");
    }

    public MapMetadata loadMetadata(File metadataFile) throws IllegalArgumentException {
        if (metadataFile != null && metadataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
                return objectMapper.readValue(reader, MapMetadata.class);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Failed to load metadata from file " + metadataFile.getName(), e);
            }
        }
        throw new IllegalArgumentException("Metadata file not found");
    }

    public MapMetadata loadMetadata(World world) {
        File metadataFile = storageManager.getMetadataFile(world);
        if (metadataFile.exists()) {
            return loadMetadata(metadataFile);
        }
        throw new IllegalArgumentException("Metadata file not found in world " + world.getName());
    }

    public MapMetadata loadOrCreateMetadata(World world) {
        File metadataFile = storageManager.getMetadataFile(world);
        if (metadataFile.exists()) {
            return loadMetadata(metadataFile);
        }

        return createDefaultMetadata();
    }

    public void saveMetadata(File worldFolder, MapMetadata metadata) {
        File metadataFile = new File(worldFolder, "metadata.json");
        try (FileWriter writer = new FileWriter(metadataFile)) {
            objectMapper.writeValue(writer, metadata);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to save metadata for world " + worldFolder.getName(), e);
        }
    }

    public void saveMetadata(World world, MapMetadata metadata) {
        File metadataFile = storageManager.getMetadataFile(world);
        try (FileWriter writer = new FileWriter(metadataFile)) {
            objectMapper.writeValue(writer, metadata);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to save metadata for world " + world.getName(), e);
        }
    }

    public MapMetadata createDefaultMetadata() {
        return new MapMetadata(defaultName,
                new HashSet<>(),
                gameModes.isEmpty() ? "Unknown" : gameModes.getFirst(),
                100);
    }

    public List<String> getGameModes() {
        return Collections.unmodifiableList(gameModes);
    }
}
