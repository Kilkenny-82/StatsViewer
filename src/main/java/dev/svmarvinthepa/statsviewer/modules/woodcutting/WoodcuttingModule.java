package dev.svmarvinthepa.statsviewer.modules.woodcutting;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.WoodcuttingRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.event.HandlerList;

/**
 * Woodcutting statistics: wood cut, trees felled and trees planted. Self-contained:
 * it creates its own tables and registers its own Bukkit listener, so nothing in
 * the core has to know about it.
 */
public class WoodcuttingModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private WoodcuttingRepository repository;
    private WoodcuttingListener listener;

    public WoodcuttingModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "woodcutting";
    }

    @Override
    public void onEnable() {
        repository = new WoodcuttingRepository(plugin.getDatabaseManager());

        if (!repository.ensureTables()) {
            DebugLogger.warn("WoodcuttingModule not started: its tables are unavailable");
            return;
        }

        listener = new WoodcuttingListener(
                plugin,
                repository,
                plugin.getConfig().getLong("woodcutting.tree-window-seconds", 300L)
        );
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.start();

        DebugLogger.info("WoodcuttingModule enabled");
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener.shutdown();
            listener = null;
        }
        DebugLogger.info("WoodcuttingModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {}

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {}
}
