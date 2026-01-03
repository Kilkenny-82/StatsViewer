package dev.svmarvinthepa.statsviewer.modules.mcstats;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.MCStatsRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.util.Map;

public class MCStatsModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private MCStatsRepository repository;

    public MCStatsModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "mc_stats";
    }

    @Override
    public void onEnable() {
        this.repository = new MCStatsRepository(
                plugin.getDatabaseManager()
        );
        DebugLogger.info("MCStatsModule enabled");
    }

    @Override
    public void onDisable() {
        DebugLogger.info("MCStatsModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {
        // niets nodig
    }

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {

        if (snapshot.stats().isEmpty()) {
            DebugLogger.debug("MCStats SKIP no stats for " + snapshot.playerName());
            return;
        }

        LocalDate date = snapshot.endTime().toLocalDate();
        int year = date.getYear();
        int month = date.getMonthValue();

        DebugLogger.debug(String.format(
                "MCStats END %s world=%s gamemode=%s",
                snapshot.playerName(),
                snapshot.world(),
                snapshot.gamemode()
        ));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            snapshot.stats().forEach((category, stats) -> {
                String table = "stats_" + category;

                for (Map.Entry<String, Long> entry : stats.entrySet()) {
                    long delta = entry.getValue();
                    if (delta <= 0) continue;

                    DebugLogger.debug(String.format(
                            "MCStats %s %s +%d (%s/%s %d-%02d)",
                            snapshot.playerName(),
                            entry.getKey(),
                            delta,
                            snapshot.world(),
                            snapshot.gamemode(),
                            year,
                            month
                    ));

                    repository.increment(
                            table,
                            snapshot.playerUuid().toString(),
                            entry.getKey(),
                            delta,
                            snapshot.world(),
                            snapshot.gamemode(),
                            year,
                            month
                    );
                }
            });
        });
    }
}
