package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.UUID;

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

        DbWriteQueue.submit(() -> {
            String sql = """
                INSERT INTO playtime
                (player_uuid, player_name, world, gamemode, start_time, end_time, duration)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

            try (Connection c = databaseManager.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, world);
                ps.setString(4, gamemode);
                ps.setString(5, start.toString());
                ps.setString(6, end.toString());
                ps.setLong(7, durationSeconds);

                ps.executeUpdate();

            } catch (Exception e) {
                // ⚠️ nooit laten ontsnappen naar Bukkit
                throw new RuntimeException(
                        "Failed to insert playtime for player=" + playerUuid +
                        " world=" + world +
                        " gamemode=" + gamemode,
                        e
                );
            }
        });
    }
}
