package dev.svmarvinthepa.statsviewer.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.svmarvinthepa.statsviewer.core.stats.MCStatCategory;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MariaDBDatabase implements Database {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public MariaDBDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void connect() {
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.database");
        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");
        boolean useSSL = plugin.getConfig().getBoolean("database.useSSL", false);

        String jdbcUrl = String.format(
                "jdbc:mariadb://%s:%d/%s?useSSL=%s&characterEncoding=utf8",
                host, port, database, useSSL
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("StatsViewer-MariaDB");
		config.setDriverClassName("org.mariadb.jdbc.Driver");

        // Pool tuning (veilig voor async)
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(15000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);

        DebugLogger.info("MariaDB (HikariCP) initialized");
    }

    @Override
    public void setup() {
        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS playtime (
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(16),
                    world VARCHAR(64),
                    gamemode VARCHAR(32),
                    start_time DATETIME,
                    end_time DATETIME,
                    duration BIGINT
                );
            """);

            createStatTables(c);

            st.execute("""
                CREATE TABLE IF NOT EXISTS player_deaths (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(16),
                    cause VARCHAR(64),
                    death_message TEXT,
                    killer_type VARCHAR(64),
                    killer_name VARCHAR(64),
                    world VARCHAR(64),
                    gamemode VARCHAR(32),
                    biome VARCHAR(64),
                    x DOUBLE,
                    y DOUBLE,
                    z DOUBLE,
                    timestamp DATETIME,
                    damage_source VARCHAR(64)
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS pvp_kills (
                    killer_uuid VARCHAR(36),
                    killer_name VARCHAR(16),
                    victim_uuid VARCHAR(36),
                    victim_name VARCHAR(16),
                    world VARCHAR(64),
                    gamemode VARCHAR(32),
                    biome VARCHAR(64),
                    weapon VARCHAR(64),
                    projectile VARCHAR(64),
                    timestamp DATETIME
                );
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS mob_kills (
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(16),
                    mob_type VARCHAR(64),
                    world VARCHAR(64),
                    gamemode VARCHAR(32),
                    biome VARCHAR(64),
                    weapon VARCHAR(64),
                    projectile VARCHAR(64),
                    timestamp DATETIME
                );
            """);

        } catch (SQLException e) {
            DebugLogger.error("MariaDB setup failed", e);
        }
    }

    private void createStatTables(Connection c) throws SQLException {
        for (MCStatCategory category : MCStatCategory.values()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS %s (
                    player_uuid VARCHAR(36),
                    stat_name VARCHAR(64),
                    stat_count BIGINT,
                    world VARCHAR(64),
                    gamemode VARCHAR(32),
                    year INT,
                    month INT,
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
			throw new RuntimeException("Failed to get MariaDB connection", e);
		}
	}

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
