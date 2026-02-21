package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteQueue;
import dev.svmarvinthepa.statsviewer.core.database.write.DbWriteTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

public class DeathsRepository {

    private final DatabaseManager databaseManager;

    public DeathsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(
            String playerUuid,
            String playerName,
            String cause,
            String deathMessage,
            String killerType,
            String killerName,
            String world,
            String gamemode,
            String biome,
            double x,
            double y,
            double z,
            LocalDateTime timestamp,
            String damageSource
    ) {

        String sql = """
            INSERT INTO player_deaths (
                player_uuid, player_name, cause, death_message,
                killer_type, killer_name,
                world, gamemode, biome,
                x, y, z, timestamp, damage_source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        // 🔵 enqueue write task
        DbWriteQueue.submit(() -> {
            try (Connection c = databaseManager.getDatabase().getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                ps.setString(1, playerUuid);
                ps.setString(2, playerName);
                ps.setString(3, cause);
                ps.setString(4, deathMessage);
                ps.setString(5, killerType);
                ps.setString(6, killerName);
                ps.setString(7, world);
                ps.setString(8, gamemode);
                ps.setString(9, biome);
                ps.setDouble(10, x);
                ps.setDouble(11, y);
                ps.setDouble(12, z);
                ps.setString(13, timestamp.toString());
                ps.setString(14, damageSource);

                ps.executeUpdate();

            } catch (Exception e) {
                // ⚠️ NO rethrow — worker must stay alive
                throw new RuntimeException("Failed to insert player death", e);
            }
        });
    }
}
