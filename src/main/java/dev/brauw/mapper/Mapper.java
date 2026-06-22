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

/**
 * The Mapper runtime: owns every manager (storage, export, sessions, GUI, tools, commands,
 * listeners) and the host plugin they hang off. This is the single source of truth, whether
 * Mapper runs as its own {@link MapperPlugin} or is shaded into another plugin and started via
 * {@link #initialize(JavaPlugin, MapperSettings)}.
 * <p>
 * Embedded mode is full parity with the standalone plugin — commands, edit sessions, selection
 * tools and listeners are all registered against the host plugin. The only thing it skips is
 * reading a bundled {@code config.yml}; settings are passed in instead.
 */
@Getter
@CustomLog
public final class Mapper {

    private static final long SESSION_TIMEOUT_MILLIS = 5 * 60 * 1000;

    private static Mapper instance;

    private final JavaPlugin plugin;
    private final NamespacedKey regionIdKey;
    private final BukkitTaskScheduler taskScheduler;
    private final RegionToolManager regionToolManager;
    private final TagRegistry tagRegistry;
    private final GuiManager guiManager;
    private final SelectionHandler selectionHandler;
    private final ListenerManager listenerManager;
    private final SessionManager sessionManager;
    private final ExportManager exportManager;
    private final PaperCommandManager<CommandSourceStack> commandManager;
    private final StorageManager storageManager;
    private final MetadataManager metadataManager;

    private Mapper(JavaPlugin plugin, MapperSettings settings) {
        this.plugin = plugin;
        this.regionIdKey = new NamespacedKey(plugin, "region_id");
        this.taskScheduler = new BukkitTaskScheduler(plugin);
        this.regionToolManager = new RegionToolManager(plugin);
        this.tagRegistry = new TagRegistry();
        this.guiManager = new GuiManager(this);
        this.selectionHandler = new SelectionHandler(guiManager, tagRegistry);
        this.listenerManager = new ListenerManager(this, regionToolManager, selectionHandler);
        this.listenerManager.registerListeners();
        this.sessionManager = new SessionManager(SESSION_TIMEOUT_MILLIS, this);
        this.exportManager = new ExportManager(plugin);
        this.commandManager = PaperCommandManager.builder()
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);

        final StorageSettings storage = settings.storage();
        this.storageManager = new StorageManager(
                plugin,
                storage.dataDirectory(),
                storage.regionsFileName(),
                storage.metadataFileName(),
                storage.exportDirectory());
        this.metadataManager =
                new MetadataManager(settings.defaultMapName(), settings.availableGamemodes(), storageManager);

        setupCommands();
    }

    private void setupCommands() {
        final AnnotationParser<CommandSourceStack> parser =
                new AnnotationParser<>(this.commandManager, CommandSourceStack.class);
        parser.parse(new MapperCommand(this));
    }

    /** Initializes the runtime with default settings (no config.yml needed). */
    public static Mapper initialize(JavaPlugin plugin) {
        return initialize(plugin, MapperSettings.defaults());
    }

    /** Initializes the runtime; idempotent — the first call wins. */
    public static synchronized Mapper initialize(JavaPlugin plugin, MapperSettings settings) {
        if (instance != null) {
            return instance;
        }
        // Route Mapper's logs to the host plugin (no-op if the plugin already initialized it).
        BukkitLoggerFactory.initialize(plugin);
        instance = new Mapper(plugin, settings);
        log.info("Mapper initialized (embedded=" + !(plugin instanceof MapperPlugin) + ")");
        return instance;
    }

    /** @return the initialized runtime; throws if {@link #initialize} was never called. */
    public static Mapper get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "Mapper has not been initialized. Call Mapper.initialize(plugin) before use.");
        }
        return instance;
    }

    /** @return whether the runtime has been initialized. */
    public static boolean isInitialized() {
        return instance != null;
    }
}
