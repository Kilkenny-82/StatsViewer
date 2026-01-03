package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.MariaDBDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MCStatsRepository {

    private final DatabaseManager databaseManager;

    public MCStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void increment(
            String table,
            String playerUuid,
            String statName,
            long amount,
            String world,
            String gamemode,
            int year,
            int month
    ) {

        boolean isMariaDB =
                databaseManager.getDatabase() instanceof MariaDBDatabase;

        String sql = isMariaDB
                ? """
                    INSERT INTO %s
                    (player_uuid, stat_name, stat_count, world, gamemode, year, month)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        stat_count = stat_count + VALUES(stat_count)
                  """.formatted(table)
                : """
                    INSERT INTO %s
                    (player_uuid, stat_name, stat_count, world, gamemode, year, month)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(player_uuid, stat_name, world, gamemode, year, month)
                    DO UPDATE SET stat_count = stat_count + excluded.stat_count
                  """.formatted(table);

        try (Connection c = databaseManager.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, playerUuid);
            ps.setString(2, statName);
            ps.setLong(3, amount);
            ps.setString(4, world);
            ps.setString(5, gamemode);
            ps.setInt(6, year);
            ps.setInt(7, month);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to increment stat: " + statName +
                    " for player=" + playerUuid +
                    " table=" + table +
                    " (db=" + (isMariaDB ? "MariaDB" : "SQLite") + ")",
                    e
            );
        }
    }
}
