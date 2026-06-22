package dev.brauw.mapper;

import dev.brauw.mapper.command.MapperCommand;
import dev.brauw.mapper.export.ExportManager;
import dev.brauw.mapper.gui.GuiManager;
import dev.brauw.mapper.listener.ListenerManager;
import dev.brauw.mapper.logger.BukkitLoggerFactory;
import dev.brauw.mapper.metadata.MetadataManager;
import dev.brauw.mapper.selection.SelectionHandler;
import dev.brauw.mapper.session.SessionManager;
import dev.brauw.mapper.storage.StorageManager;
import dev.brauw.mapper.tag.TagRegistry;
import dev.brauw.mapper.tool.RegionToolManager;
import dev.brauw.mapper.util.BukkitTaskScheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;

import java.util.List;

@Getter
@CustomLog
public class MapperPlugin extends JavaPlugin {

    @Getter
    private static MapperPlugin instance;
    private SessionManager sessionManager;
    private ExportManager exportManager;
    private PaperCommandManager<CommandSourceStack> commandManager;
    private ListenerManager listenerManager;
    private MetadataManager metadataManager;
    private StorageManager storageManager;
    private RegionToolManager regionToolManager;
    private SelectionHandler selectionHandler;
    private GuiManager guiManager;
    private TagRegistry tagRegistry;
    private BukkitTaskScheduler taskScheduler;
    /**
     * Key used to stamp a region's UUID onto its display entities, so a clicked
     * entity can be traced back to the region it represents.
     */
    private NamespacedKey regionIdKey;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        BukkitLoggerFactory.initialize(this);

        this.regionIdKey = new NamespacedKey(this, "region_id");

        this.taskScheduler = new BukkitTaskScheduler(this);
        this.regionToolManager = new RegionToolManager(this);
        this.tagRegistry = new TagRegistry();
        this.guiManager = new GuiManager(this);
        this.selectionHandler = new SelectionHandler(guiManager, tagRegistry);
        this.listenerManager = new ListenerManager(this, regionToolManager, selectionHandler);
        this.listenerManager.registerListeners();
        this.sessionManager = new SessionManager(5 * 60 * 1000, this);
        this.exportManager = new ExportManager(this);
        this.commandManager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(this);

        // storage
        String dataDirectory = getConfig().getString("storage.data-directory", "%world%");
        String regionsFile = getConfig().getString("storage.regions-file", "dataPoints.json");
        String metadataFile = getConfig().getString("storage.metadata-file", "metadata.json");
        String exportDirectory = getConfig().getString("storage.export-directory", "%plugin%/exports");
        this.storageManager = new StorageManager(this, dataDirectory, regionsFile, metadataFile, exportDirectory);

        // metadata
        String defaultName = getConfig().getString("metadata.default-map-name", "Unnamed Map");
        List<String> gamemodes = getConfig().getStringList("metadata.available-gamemodes");
        this.metadataManager = new MetadataManager(defaultName, gamemodes, storageManager);

        this.setupCommands();
        log.info("Mapper plugin enabled!");
    }

    private void setupCommands() {
        AnnotationParser<CommandSourceStack> parser = new AnnotationParser<>(this.commandManager, CommandSourceStack.class);
        parser.parse(new MapperCommand(this));
    }

    @Override
    public void onDisable() {
        log.info("Mapper plugin disabled!");
    }
}