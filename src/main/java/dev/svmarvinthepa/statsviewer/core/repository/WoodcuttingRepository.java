package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.SqlSupport;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

/**
 * Two tables:
 * <ul>
 *   <li>{@code player_woodcutting}: one row per wood block a player cut (CUT) and per
 *       tree seed a player planted (PLANT);</li>
 *   <li>{@code player_trees}: one row per tree a player felled, with what the tree
 *       yielded. Rows in player_woodcutting point to it through {@code tree_id}.</li>
 * </ul>
 */
public class WoodcuttingRepository {

    public static final String BLOCK_TABLE = "player_woodcutting";
    public static final String TREE_TABLE = "player_trees";

    /* =========================
       player_woodcutting
       ========================= */

    private static final String BLOCK_SQLITE_DDL = """
        CREATE TABLE IF NOT EXISTS player_woodcutting (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tree_id TEXT,
            player_uuid TEXT,
            player_name TEXT,
            action TEXT,
            block_type TEXT,
            wood_type TEXT,
            tool_type TEXT,
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

    private static final String BLOCK_MARIADB_DDL = """
        CREATE TABLE IF NOT EXISTS player_woodcutting (
            id INT AUTO_INCREMENT PRIMARY KEY,
            tree_id VARCHAR(36),
            player_uuid VARCHAR(36),
            player_name VARCHAR(16),
            action VARCHAR(16),
            block_type VARCHAR(64),
            wood_type VARCHAR(64),
            tool_type VARCHAR(64),
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

    private static final String BLOCK_INSERT_SQL = """
        INSERT INTO player_woodcutting (
            tree_id, player_uuid, player_name, action,
            block_type, wood_type, tool_type, silk_touch, fortune_level, drops,
            world, gamemode, biome, weather, time_of_day,
            x, y, z, timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    /* =========================
       player_trees
       ========================= */

    private static final String TREE_SQLITE_DDL = """
        CREATE TABLE IF NOT EXISTS player_trees (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tree_id TEXT,
            player_uuid TEXT,
            player_name TEXT,
            wood_type TEXT,
            tree_logs INTEGER,
            logs_broken INTEGER,
            leaves_broken INTEGER,
            leaves_decayed INTEGER,
            wood_items INTEGER,
            leaf_items INTEGER,
            sapling_items INTEGER,
            stick_items INTEGER,
            apple_items INTEGER,
            other_items INTEGER,
            other_items_detail TEXT,
            saplings_planted INTEGER,
            tool_type TEXT,
            world TEXT,
            gamemode TEXT,
            biome TEXT,
            weather TEXT,
            time_of_day TEXT,
            x INTEGER,
            y INTEGER,
            z INTEGER,
            duration_ms INTEGER,
            timestamp TEXT
        );
    """;

    private static final String TREE_MARIADB_DDL = """
        CREATE TABLE IF NOT EXISTS player_trees (
            id INT AUTO_INCREMENT PRIMARY KEY,
            tree_id VARCHAR(36),
            player_uuid VARCHAR(36),
            player_name VARCHAR(16),
            wood_type VARCHAR(64),
            tree_logs INT,
            logs_broken INT,
            leaves_broken INT,
            leaves_decayed INT,
            wood_items INT,
            leaf_items INT,
            sapling_items INT,
            stick_items INT,
            apple_items INT,
            other_items INT,
            other_items_detail VARCHAR(255),
            saplings_planted INT,
            tool_type VARCHAR(64),
            world VARCHAR(64),
            gamemode VARCHAR(32),
            biome VARCHAR(64),
            weather VARCHAR(16),
            time_of_day VARCHAR(16),
            x INT,
            y INT,
            z INT,
            duration_ms BIGINT,
            timestamp DATETIME
        );
    """;

    private static final String TREE_INSERT_SQL = """
        INSERT INTO player_trees (
            tree_id, player_uuid, player_name, wood_type,
            tree_logs, logs_broken, leaves_broken, leaves_decayed,
            wood_items, leaf_items, sapling_items, stick_items, apple_items,
            other_items, other_items_detail, saplings_planted, tool_type,
            world, gamemode, biome, weather, time_of_day,
            x, y, z, duration_ms, timestamp
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private final DatabaseManager databaseManager;

    public WoodcuttingRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * A wood block that was cut (CUT) or a tree seed that was planted (PLANT).
     * {@code treeId} is null when the block was not part of a natural tree.
     * For PLANT, tool_type, silk_touch, fortune_level and drops are null.
     */
    public record BlockEntry(
            String treeId,
            String playerUuid,
            String playerName,
            String action,
            String blockType,
            String woodType,
            String toolType,
            Boolean silkTouch,
            Integer fortuneLevel,
            Integer drops,
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

    /** One felled tree and what it yielded. {@code timestamp} is the first cut. */
    public record TreeEntry(
            String treeId,
            String playerUuid,
            String playerName,
            String woodType,
            int treeLogs,
            int logsBroken,
            int leavesBroken,
            int leavesDecayed,
            int woodItems,
            int leafItems,
            int saplingItems,
            int stickItems,
            int appleItems,
            int otherItems,
            String otherItemsDetail,
            int saplingsPlanted,
            String toolType,
            String world,
            String gamemode,
            String biome,
            String weather,
            String timeOfDay,
            int x,
            int y,
            int z,
            long durationMs,
            LocalDateTime timestamp
    ) {}

    /** Creates both tables when they do not exist yet. */
    public boolean ensureTables() {
        boolean blocks = SqlSupport.createTable(
                databaseManager, BLOCK_TABLE, BLOCK_SQLITE_DDL, BLOCK_MARIADB_DDL,
                "player_uuid", "tree_id", "action", "timestamp"
        );
        boolean trees = SqlSupport.createTable(
                databaseManager, TREE_TABLE, TREE_SQLITE_DDL, TREE_MARIADB_DDL,
                "player_uuid", "tree_id", "timestamp"
        );
        return blocks && trees;
    }

    public void insertBlock(BlockEntry e) {

        // 🔵 enqueue write task
        DbWriteQueue.submit(() -> {
            try {
                executeBlockInsert(e);
            } catch (Exception ex) {
                // ⚠️ NO rethrow of the raw exception — worker must stay alive
                throw new RuntimeException("Failed to insert woodcutting record", ex);
            }
        });
    }

    public void insertTree(TreeEntry e) {

        // 🔵 enqueue write task
        DbWriteQueue.submit(() -> {
            try {
                executeTreeInsert(e);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to insert tree record", ex);
            }
        });
    }

    /**
     * Writes a tree right now on the calling thread. Only for shutdown: the write
     * worker is already stopped by then, so a queued write would never be executed.
     */
    public void insertTreeBlocking(TreeEntry e) throws Exception {
        executeTreeInsert(e);
    }

    private void executeBlockInsert(BlockEntry e) throws Exception {
        try (Connection c = databaseManager.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(BLOCK_INSERT_SQL)) {

            int i = 1;
            SqlSupport.setString(ps, i++, e.treeId(), 36);
            SqlSupport.setString(ps, i++, e.playerUuid(), 36);
            SqlSupport.setString(ps, i++, e.playerName(), 16);
            SqlSupport.setString(ps, i++, e.action(), 16);
            SqlSupport.setString(ps, i++, e.blockType(), 64);
            SqlSupport.setString(ps, i++, e.woodType(), 64);
            SqlSupport.setString(ps, i++, e.toolType(), 64);
            SqlSupport.setBool(ps, i++, e.silkTouch());
            SqlSupport.setInt(ps, i++, e.fortuneLevel());
            SqlSupport.setInt(ps, i++, e.drops());
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
        }
    }

    private void executeTreeInsert(TreeEntry e) throws Exception {
        try (Connection c = databaseManager.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(TREE_INSERT_SQL)) {

            int i = 1;
            SqlSupport.setString(ps, i++, e.treeId(), 36);
            SqlSupport.setString(ps, i++, e.playerUuid(), 36);
            SqlSupport.setString(ps, i++, e.playerName(), 16);
            SqlSupport.setString(ps, i++, e.woodType(), 64);
            ps.setInt(i++, e.treeLogs());
            ps.setInt(i++, e.logsBroken());
            ps.setInt(i++, e.leavesBroken());
            ps.setInt(i++, e.leavesDecayed());
            ps.setInt(i++, e.woodItems());
            ps.setInt(i++, e.leafItems());
            ps.setInt(i++, e.saplingItems());
            ps.setInt(i++, e.stickItems());
            ps.setInt(i++, e.appleItems());
            ps.setInt(i++, e.otherItems());
            SqlSupport.setString(ps, i++, e.otherItemsDetail(), 255);
            ps.setInt(i++, e.saplingsPlanted());
            SqlSupport.setString(ps, i++, e.toolType(), 64);
            SqlSupport.setString(ps, i++, e.world(), 64);
            SqlSupport.setString(ps, i++, e.gamemode(), 32);
            SqlSupport.setString(ps, i++, e.biome(), 64);
            SqlSupport.setString(ps, i++, e.weather(), 16);
            SqlSupport.setString(ps, i++, e.timeOfDay(), 16);
            ps.setInt(i++, e.x());
            ps.setInt(i++, e.y());
            ps.setInt(i++, e.z());
            ps.setLong(i++, e.durationMs());
            ps.setString(i, e.timestamp().toString());

            ps.executeUpdate();
        }
    }
}
