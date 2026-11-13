package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.SqlSupport;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

public class FishingRepository {

    public static final String TABLE = "player_fishing";

    private static final String SQLITE_DDL = """
        CREATE TABLE IF NOT EXISTS player_fishing (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            cast_id TEXT,
            player_uuid TEXT,
            player_name TEXT,
            result TEXT,
            item_type TEXT,
            catch_category TEXT,
            amount INTEGER,
            entity_type TEXT,
            fishing_rod TEXT,
            lure_level INTEGER,
            luck_of_the_sea_level INTEGER,
            wait_time_ms INTEGER,
            world TEXT,
            gamemode TEXT,
            biome TEXT,
            weather TEXT,
            time_of_day TEXT,
            x REAL,
            y REAL,
            z REAL,
            timestamp TEXT
        );
    """;

    private static final String MARIADB_DDL = """
        CREATE TABLE IF NOT EXISTS player_fishing (
            id INT AUTO_INCREMENT PRIMARY KEY,
            cast_id VARCHAR(36),
            player_uuid VARCHAR(36),
            player_name VARCHAR(16),
            result VARCHAR(16),
            item_type VARCHAR(64),
            catch_category VARCHAR(16),
            amount INT,
            entity_type VARCHAR(64),
            fishing_rod VARCHAR(64),
            lure_level INT,
            luck_of_the_sea_level INT,
            wait_time_ms BIGINT,
            world VARCHAR(64),
            gamemode VARCHAR(32),
            biome VARCHAR(64),
            weather VARCHAR(16),
            time_of_day VARCHAR(16),
            x DOUBLE,
            y DOUBLE,
            z DOUBLE,
            timestamp DATETIME
        );
    """;

    private static final String INSERT_SQL = """
        INSERT INTO player_fishing (
            cast_id, player_uuid, player_name, result,
            item_type, catch_category, amount, entity_type,
            fishing_rod, lure_level, luck_of_the_sea_level, wait_time_ms,
            world, gamemode, biome, weather, time_of_day,
            x, y, z, timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private final DatabaseManager databaseManager;

    public FishingRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * One registration. Fields that do not apply to the result are null
     * (item_type, catch_category and amount only for CAUGHT, entity_type only
     * for ENTITY, wait_time_ms when the moment of the cast is unknown).
     */
    public record Entry(
            String castId,
            String playerUuid,
            String playerName,
            String result,
            String itemType,
            String catchCategory,
            Integer amount,
            String entityType,
            String fishingRod,
            Integer lureLevel,
            Integer luckOfTheSeaLevel,
            Long waitTimeMs,
            String world,
            String gamemode,
            String biome,
            String weather,
            String timeOfDay,
            double x,
            double y,
            double z,
            LocalDateTime timestamp
    ) {}

    /** Creates the table when it does not exist yet. */
    public boolean ensureTable() {
        return SqlSupport.createTable(
                databaseManager, TABLE, SQLITE_DDL, MARIADB_DDL,
                "player_uuid", "cast_id", "timestamp"
        );
    }

    public void insert(Entry e) {

        // 🔵 enqueue write task
        DbWriteQueue.submit(() -> {
            try (Connection c = databaseManager.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {

                int i = 1;
                SqlSupport.setString(ps, i++, e.castId(), 36);
                SqlSupport.setString(ps, i++, e.playerUuid(), 36);
                SqlSupport.setString(ps, i++, e.playerName(), 16);
                SqlSupport.setString(ps, i++, e.result(), 16);
                SqlSupport.setString(ps, i++, e.itemType(), 64);
                SqlSupport.setString(ps, i++, e.catchCategory(), 16);
                SqlSupport.setInt(ps, i++, e.amount());
                SqlSupport.setString(ps, i++, e.entityType(), 64);
                SqlSupport.setString(ps, i++, e.fishingRod(), 64);
                SqlSupport.setInt(ps, i++, e.lureLevel());
                SqlSupport.setInt(ps, i++, e.luckOfTheSeaLevel());
                SqlSupport.setLong(ps, i++, e.waitTimeMs());
                SqlSupport.setString(ps, i++, e.world(), 64);
                SqlSupport.setString(ps, i++, e.gamemode(), 32);
                SqlSupport.setString(ps, i++, e.biome(), 64);
                SqlSupport.setString(ps, i++, e.weather(), 16);
                SqlSupport.setString(ps, i++, e.timeOfDay(), 16);
                ps.setDouble(i++, e.x());
                ps.setDouble(i++, e.y());
                ps.setDouble(i++, e.z());
                ps.setString(i, e.timestamp().toString());

                ps.executeUpdate();

            } catch (Exception ex) {
                // ⚠️ NO rethrow of the raw exception — worker must stay alive
                throw new RuntimeException("Failed to insert fishing record", ex);
            }
        });
    }
}
