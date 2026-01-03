package dev.svmarvinthepa.statsviewer.core.database;

import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private Database database;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String type = plugin.getConfig().getString("database.type", "SQLITE");

        if (type.equalsIgnoreCase("MARIADB")) {
            database = new MariaDBDatabase(plugin);
        } else {
            database = new SQLiteDatabase(plugin);
        }

        database.connect();
        database.setup();
        DebugLogger.info("Database initialized: " + type);
    }

    public Database getDatabase() {
        return database;
    }

    public void shutdown() {
        if (database != null) {
            database.close();
        }
    }
}
