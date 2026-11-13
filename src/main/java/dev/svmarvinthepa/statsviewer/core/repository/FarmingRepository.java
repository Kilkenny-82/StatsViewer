package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.SqlSupport;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

public class FarmingRepository {

    public static final String TABLE = "player_farming";

    private static final String SQLITE_DDL = """
        CREATE TABLE IF NOT EXISTS player_farming (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_uuid TEXT,
            player_name TEXT,
            action TEXT,
            crop_type TEXT,
            animal_type TEXT,
            harvested_amount INTEGER,
            product_amount INTEGER,
            age INTEGER,
            max_age INTEGER,
            tool TEXT,
            silk_touch INTEGER,
            fortune_level INTEGER,
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
        CREATE TABLE IF NOT EXISTS player_farming (
            id INT AUTO_INCREMENT PRIMARY KEY,
            player_uuid VARCHAR(36),
            player_name VARCHAR(16),
            action VARCHAR(16),
            crop_type VARCHAR(64),
            animal_type VARCHAR(64),
            harvested_amount INT,
            product_amount INT,
            age INT,
            max_age INT,
            tool VARCHAR(64),
            silk_touch TINYINT(1),
            fortune_level INT,
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
        INSERT INTO player_farming (
            player_uuid, player_name, action,
            crop_type, animal_type,
            harvested_amount, product_amount, age, max_age,
            tool, silk_touch, fortune_level,
            world, gamemode, biome, weather, time_of_day,
            x, y, z, timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private final DatabaseManager databaseManager;

    public FarmingRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * One farming action: HARVEST, BREED, SHEAR or MILK.
     * Fields that do not apply are null (crop_type, age and max_age only for crops,
     * animal_type only for livestock actions).
     */
    public record Entry(
            String playerUuid,
            String playerName,
            String action,
            String cropType,
            String animalType,
            Integer harvestedAmount,
            Integer productAmount,
            Integer age,
            Integer maxAge,
            String tool,
            Boolean silkTouch,
            Integer fortuneLevel,
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
                "player_uuid", "action", "crop_type", "timestamp"
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
                SqlSupport.setString(ps, i++, e.action(), 16);
                SqlSupport.setString(ps, i++, e.cropType(), 64);
                SqlSupport.setString(ps, i++, e.animalType(), 64);
                SqlSupport.setInt(ps, i++, e.harvestedAmount());
                SqlSupport.setInt(ps, i++, e.productAmount());
                SqlSupport.setInt(ps, i++, e.age());
                SqlSupport.setInt(ps, i++, e.maxAge());
                SqlSupport.setString(ps, i++, e.tool(), 64);
                SqlSupport.setBool(ps, i++, e.silkTouch());
                SqlSupport.setInt(ps, i++, e.fortuneLevel());
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
                throw new RuntimeException("Failed to insert farming record", ex);
            }
        });
    }
}
