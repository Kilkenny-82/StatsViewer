package dev.svmarvinthepa.statsviewer.modules.farming;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.FarmingRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.event.HandlerList;

/**
 * Farming statistics: crop harvests and livestock actions (breeding, shearing,
 * milking). Self-contained: it creates its own table and registers its own
 * Bukkit listeners, so nothing in the core has to know about it.
 */
public class FarmingModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private FarmingRepository repository;
    private CropListener cropListener;
    private LivestockListener livestockListener;

    public FarmingModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "farming";
    }

    @Override
    public void onEnable() {
        repository = new FarmingRepository(plugin.getDatabaseManager());

        if (!repository.ensureTable()) {
            DebugLogger.warn("FarmingModule not started: table " + FarmingRepository.TABLE + " is unavailable");
            return;
        }

        cropListener = new CropListener(plugin, repository);
        plugin.getServer().getPluginManager().registerEvents(cropListener, plugin);

        if (plugin.getConfig().getBoolean("farming.livestock", true)) {
            livestockListener = new LivestockListener(repository);
            plugin.getServer().getPluginManager().registerEvents(livestockListener, plugin);
        }

        DebugLogger.info("FarmingModule enabled");
    }

    @Override
    public void onDisable() {
        if (cropListener != null) {
            HandlerList.unregisterAll(cropListener);
            cropListener.clear();
            cropListener = null;
        }
        if (livestockListener != null) {
            HandlerList.unregisterAll(livestockListener);
            livestockListener.clear();
            livestockListener = null;
        }
        DebugLogger.info("FarmingModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {}

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {}
}
