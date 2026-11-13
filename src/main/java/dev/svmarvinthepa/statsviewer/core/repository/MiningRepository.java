package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.SqlSupport;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

public class MiningRepository {

    public static final String TABLE = "player_mining";

    private static final String SQLITE_DDL = """
        CREATE TABLE IF NOT EXISTS player_mining (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_uuid TEXT,
            player_name TEXT,
            mineral_type TEXT,
            block_type TEXT,
            ore_block TEXT,
            tool TEXT,
            silk_touch INTEGER,
            fortune_level INTEGER,
            drops INTEGER,
            world TEXT,
            gamemode TEXT,
            biome TEXT,
            weather TEXT,
            time_of_day TEXT,
            x INTEGER,
            y INTEGER,
            z INTEGER,
            timestamp TEXT
        );
    """;

    private static final String MARIADB_DDL = """
        CREATE TABLE IF NOT EXISTS player_mining (
            id INT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36),
            player_name VARCHAR(16),
            mineral_type VARCHAR(64),
            block_type VARCHAR(64),
            ore_block VARCHAR(64),
            tool VARCHAR(64),
            silk_touch TINYINT(1),
            fortune_level INT,
            drops INT,
            world VARCHAR(64),
            gamemode VARCHAR(32),
            biome VARCHAR(64),
            weather VARCHAR(16),
            time_of_day VARCHAR(16),
            x INT,
            y INT,
            z INT,
            timestamp DATETIME
        );
    """;

    private static final String INSERT_SQL = """
        INSERT INTO player_mining (
            player_uuid, player_name,
            mineral_type, block_type, ore_block,
            tool, silk_touch, fortune_level, drops,
            world, gamemode, biome, weather, time_of_day,
            x, y, z, timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private final DatabaseManager databaseManager;

    public MiningRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** One mined mineral block. {@code drops} is what really dropped (0 when nothing did). */
    public record Entry(
            String playerUuid,
            String playerName,
            String mineralType,
            String blockType,
            String oreBlock,
            String tool,
            boolean silkTouch,
            int fortuneLevel,
            int drops,
            String world,
            String gamemode,
            String biome,
            String weather,
            String timeOfDay,
            int x,
            int y,
            int z,
            LocalDateTime timestamp
    ) {}

    /** Creates the table when it does not exist yet. */
    public boolean ensureTable() {
        return SqlSupport.createTable(
                databaseManager, TABLE, SQLITE_DDL, MARIADB_DDL,
                "player_uuid", "mineral_type", "timestamp"
        );
    }

    public void insert(Entry e) {

        // 🔵 enqueue write task
        DbWriteQueue.submit(() -> {
            try (Connection c = databaseManager.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {

                int i = 1;
                SqlSupport.setString(ps, i++, e.playerUuid(), 36);
                SqlSupport.setString(ps, i++, e.playerName(), 16);
                SqlSupport.setString(ps, i++, e.mineralType(), 64);
                SqlSupport.setString(ps, i++, e.blockType(), 64);
                SqlSupport.setString(ps, i++, e.oreBlock(), 64);
                SqlSupport.setString(ps, i++, e.tool(), 64);
                SqlSupport.setBool(ps, i++, e.silkTouch());
                ps.setInt(i++, e.fortuneLevel());
                ps.setInt(i++, e.drops());
                SqlSupport.setString(ps, i++, e.world(), 64);
                SqlSupport.setString(ps, i++, e.gamemode(), 32);
                SqlSupport.setString(ps, i++, e.biome(), 64);
                SqlSupport.setString(ps, i++, e.weather(), 16);
                SqlSupport.setString(ps, i++, e.timeOfDay(), 16);
                ps.setInt(i++, e.x());
                ps.setInt(i++, e.y());
                ps.setInt(i++, e.z());
                ps.setString(i, e.timestamp().toString());

                ps.executeUpdate();

            } catch (Exception ex) {
                // ⚠️ NO rethrow of the raw exception — worker must stay alive
                throw new RuntimeException("Failed to insert mining record", ex);
            }
        });
    }
}
