package dev.svmarvinthepa.statsviewer.core;

import dev.svmarvinthepa.statsviewer.api.StatsViewerAPI;
import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteWorker;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameModeRegistry;
import dev.svmarvinthepa.statsviewer.core.gamemode.VanillaGameModeProvider;
import dev.svmarvinthepa.statsviewer.core.module.ModuleManager;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.modules.playtime.PlaytimeModule;
import dev.svmarvinthepa.statsviewer.modules.pvp.PvPModule;
import dev.svmarvinthepa.statsviewer.modules.mobkills.MobKillModule;
import dev.svmarvinthepa.statsviewer.modules.deaths.DeathsModule;
import dev.svmarvinthepa.statsviewer.modules.mcstats.MCStatsModule;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class StatsViewerPlugin extends JavaPlugin {

    private static StatsViewerPlugin instance;

    private DatabaseManager databaseManager;
    private ModuleManager moduleManager;

    // 🔴 DB write infrastructure
    private DbWriteWorker dbWriteWorker;
    private Thread dbWriteThread;

    // 🔵 monitoring task
    private BukkitTask dbQueueMonitorTask;

    public static StatsViewerPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // config
        saveDefaultConfig();

        // logging
        DebugLogger.init(this);

        // database
        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        // 🔴 start DB write worker
        dbWriteWorker = new DbWriteWorker(this);
        dbWriteThread = new Thread(dbWriteWorker, "StatsViewer-DB-Worker");
        dbWriteThread.start();

        // 🔵 optional monitoring (every 5 minutes)
        dbQueueMonitorTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> DebugLogger.info("DB queue size = " + DbWriteQueue.size()),
                20L * 60 * 5,
                20L * 60 * 5
        );

        // gamemodes
        CustomGameModeRegistry.register(new VanillaGameModeProvider());

        // modules
        moduleManager = new ModuleManager(this);
        moduleManager.register(new PlaytimeModule(this));
        moduleManager.register(new PvPModule(this));
        moduleManager.register(new MobKillModule(this));
        moduleManager.register(new DeathsModule(this));
        moduleManager.register(new MCStatsModule(this));

        moduleManager.enableFromConfig(getConfig());

        // API
        StatsViewerAPI.init(moduleManager);

        // listeners
        getServer().getPluginManager()
                .registerEvents(new StatsViewerListener(), this);

        DebugLogger.info("StatsViewer enabled");
    }

    @Override
    public void onDisable() {

        // 🔵 stop monitoring
        if (dbQueueMonitorTask != null) {
            dbQueueMonitorTask.cancel();
        }

        // 🔴 stop DB worker
        if (dbWriteWorker != null) {
            dbWriteWorker.shutdown();
        }
        if (dbWriteThread != null) {
            dbWriteThread.interrupt();
        }

        if (moduleManager != null) {
            moduleManager.disableAll();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        DebugLogger.info("StatsViewer disabled");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
