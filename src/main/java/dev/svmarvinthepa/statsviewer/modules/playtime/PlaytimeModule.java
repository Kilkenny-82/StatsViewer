package dev.svmarvinthepa.statsviewer.modules.playtime;

import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.PlaytimeRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaytimeModule implements StatsModule {

    private static final long MIN_DURATION_SECONDS = 10;

    private static final ExecutorService PLAYTIME_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "StatsViewer-Playtime");
                t.setDaemon(true);
                return t;
            });

    private PlaytimeRepository repository;
    private final StatsViewerPlugin plugin;

    public PlaytimeModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "playtime";
    }

    @Override
    public void onEnable() {
        repository = new PlaytimeRepository(plugin.getDatabaseManager());
        DebugLogger.info("PlaytimeModule enabled");
    }

    @Override
    public void onDisable() {
        PLAYTIME_EXECUTOR.shutdown();
        DebugLogger.info("PlaytimeModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {
        DebugLogger.debug(String.format(
                "Playtime START %s world=%s gamemode=%s",
                ctx.getPlayer().getName(),
                ctx.getWorld().getName(),
                ctx.getGameMode().getName()
        ));
    }

    @Override
    public void onSessionEnd(SessionSnapshot snapshot) {

        long durationSeconds = snapshot.durationSeconds();

        if (durationSeconds < MIN_DURATION_SECONDS) {
            DebugLogger.debug(String.format(
                    "Playtime SKIP %s duration=%ds (<%ds)",
                    snapshot.playerName(),
                    durationSeconds,
                    MIN_DURATION_SECONDS
            ));
            return;
        }

        DebugLogger.debug(String.format(
                "Playtime END %s world=%s gamemode=%s duration=%ds",
                snapshot.playerName(),
                snapshot.world(),
                snapshot.gamemode(),
                durationSeconds
        ));

        PLAYTIME_EXECUTOR.execute(() -> {
            try {
                repository.insert(
                        snapshot.playerUuid(),
                        snapshot.playerName(),
                        snapshot.world(),
                        snapshot.gamemode(),
                        snapshot.startTime(),
                        snapshot.endTime(),
                        durationSeconds
                );
            } catch (Exception e) {
                DebugLogger.warn(
                        "Playtime insert skipped (DB busy) for " + snapshot.playerName()
                );
            }
        });
    }
}
