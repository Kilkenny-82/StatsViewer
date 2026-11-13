package dev.svmarvinthepa.statsviewer.modules.mining;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.MiningRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.event.HandlerList;

/**
 * Mining statistics. Self-contained: it creates its own table and registers
 * its own Bukkit listener, so nothing in the core has to know about it.
 */
public class MiningModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private MiningRepository repository;
    private MiningListener listener;

    public MiningModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "mining";
    }

    @Override
    public void onEnable() {
        repository = new MiningRepository(plugin.getDatabaseManager());

        if (!repository.ensureTable()) {
            DebugLogger.warn("MiningModule not started: table " + MiningRepository.TABLE + " is unavailable");
            return;
        }

        listener = new MiningListener(plugin, repository);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        DebugLogger.info("MiningModule enabled");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener.clear();
            listener = null;
        }
        DebugLogger.info("MiningModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {}

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {}
}
