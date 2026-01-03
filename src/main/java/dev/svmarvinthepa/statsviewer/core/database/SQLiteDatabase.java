package dev.svmarvinthepa.statsviewer.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.svmarvinthepa.statsviewer.core.stats.MCStatCategory;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase implements Database {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public SQLiteDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

	@Override
	public void connect() {
		try {
			File dbFile = new File(plugin.getDataFolder(), "statsviewer.db");
			String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
	
			HikariConfig config = new HikariConfig();
			config.setJdbcUrl(jdbcUrl);
	
			config.setPoolName("StatsViewer-SQLite");
	
			// 🔑 CRUCIAAL
			config.setMaximumPoolSize(4);
			config.setMinimumIdle(1);
			config.setConnectionTimeout(30000);
			config.setIdleTimeout(600000);
			config.setMaxLifetime(1800000);
			config.setAutoCommit(true);
	
			// SQLite pragmas
			config.addDataSourceProperty("journal_mode", "WAL");
			config.addDataSourceProperty("synchronous", "NORMAL");
			config.addDataSourceProperty("busy_timeout", "5000");
	
			dataSource = new HikariDataSource(config);
	
			DebugLogger.info("SQLite (HikariCP + WAL) initialized");
	
		} catch (Exception e) {
			DebugLogger.error("Failed to init SQLite (Hikari)", e);
		}
	}

    @Override
    public void setup() {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS playtime (
                    player_uuid TEXT,
                    player_name TEXT,
                    world TEXT,
                    gamemode TEXT,
                    start_time TEXT,
                    end_time TEXT,
                    duration INTEGER
                );
            """);

            createStatTables(c);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_deaths (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT,
                    player_name TEXT,
                    cause TEXT,
                    death_message TEXT,
                    killer_type TEXT,
                    killer_name TEXT,
                    world TEXT,
                    gamemode TEXT,
                    biome TEXT,
                    x REAL,
                    y REAL,
                    z REAL,
                    timestamp TEXT,
                    damage_source TEXT
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS pvp_kills (
                    killer_uuid TEXT,
                    killer_name TEXT,
                    victim_uuid TEXT,
                    victim_name TEXT,
                    world TEXT,
                    gamemode TEXT,
                    biome TEXT,
                    weapon TEXT,
                    projectile TEXT,
                    timestamp TEXT
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS mob_kills (
                    player_uuid TEXT,
                    player_name TEXT,
                    mob_type TEXT,
                    world TEXT,
                    gamemode TEXT,
                    biome TEXT,
                    weapon TEXT,
                    projectile TEXT,
                    timestamp TEXT
                );
            """);

        } catch (Exception e) {
            DebugLogger.error("SQLite setup failed", e);
        }
    }

    private void createStatTables(Connection c) throws SQLException {
        for (MCStatCategory category : MCStatCategory.values()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid TEXT,
                    stat_name TEXT,
                    stat_count INTEGER,
                    world TEXT,
                    gamemode TEXT,
                    year INTEGER,
                    month INTEGER,
                    PRIMARY KEY (player_uuid, stat_name, world, gamemode, year, month)
                );
            """.formatted(category.getTableName());

            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        }
    }

	@Override
	public Connection getConnection() {
		try {
			return dataSource.getConnection();
		} catch (SQLException e) {
			throw new RuntimeException("Failed to get SQLite connection", e);
		}
	}

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
