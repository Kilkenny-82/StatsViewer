package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.time.LocalDateTime;

public class PlaytimeRepository {

    private final DatabaseManager databaseManager;

    public PlaytimeRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(
            UUID playerUuid,
            String playerName,
            String world,
            String gamemode,
            LocalDateTime start,
            LocalDateTime end,
            long durationSeconds
    ) {
        try (PreparedStatement ps = databaseManager
                .getDatabase()
                .getConnection()
                .prepareStatement("""
                    INSERT INTO playtime
                    (player_uuid, player_name, world, gamemode, start_time, end_time, duration)
                    VALUES (?,?,?,?,?,?,?)
                """)) {

            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, world);
            ps.setString(4, gamemode);
            ps.setString(5, start.toString());
            ps.setString(6, end.toString());
            ps.setLong(7, durationSeconds);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert playtime", e);
        }
    }
}
