package dev.svmarvinthepa.statsviewer.core;

import dev.svmarvinthepa.statsviewer.api.StatsViewerAPI;
import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
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

public class StatsViewerPlugin extends JavaPlugin {

    private static StatsViewerPlugin instance;

    private DatabaseManager databaseManager;
    private ModuleManager moduleManager;

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

        // listeners (bridge Bukkit → API)
        getServer().getPluginManager()
                .registerEvents(new StatsViewerListener(), this);

        DebugLogger.info("StatsViewer enabled");
    }

    @Override
    public void onDisable() {
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
