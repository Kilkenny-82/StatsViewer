package dev.svmarvinthepa.statsviewer.modules.fishing;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.FishingRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.Locale;

/**
 * Fishing statistics. Self-contained: it creates its own table and registers
 * its own Bukkit listener, so nothing in the core has to know about it.
 */
public class FishingModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private FishingRepository repository;
    private FishingListener listener;

    public FishingModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "fishing";
    }

    @Override
    public void onEnable() {
        repository = new FishingRepository(plugin.getDatabaseManager());

        if (!repository.ensureTable()) {
            DebugLogger.warn("FishingModule not started: table " + FishingRepository.TABLE + " is unavailable");
            return;
        }

        FileConfiguration config = plugin.getConfig();

        listener = new FishingListener(
                repository,
                buildClassifier(config),
                config.getBoolean("fishing.record-entity-hooks", true)
        );
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        DebugLogger.info("FishingModule enabled");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener.clear();
            listener = null;
        }
        DebugLogger.info("FishingModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {}

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {}

    /** Built-in loot table, extended by the optional fishing.classification lists in config.yml. */
    private static FishingLootClassifier buildClassifier(FileConfiguration config) {
        FishingLootClassifier classifier = new FishingLootClassifier();

        for (CatchCategory category : List.of(CatchCategory.FISH, CatchCategory.TREASURE, CatchCategory.JUNK)) {
            classifier.override(
                    category,
                    config.getStringList("fishing.classification." + category.name().toLowerCase(Locale.ROOT))
            );
        }
        return classifier;
    }
}
