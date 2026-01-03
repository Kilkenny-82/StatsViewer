package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class MobKillRepository {

    private final DatabaseManager databaseManager;

    public MobKillRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(
            String playerUuid,
            String playerName,
            String mobType,
            String world,
            String gamemode,
            String biome,
            String weapon,
            String projectile,
            LocalDateTime timestamp
    ) {
        String sql = """
            INSERT INTO mob_kills
            (player_uuid, player_name, mob_type, world, gamemode, biome, weapon, projectile, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection c = databaseManager.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, playerUuid);
            ps.setString(2, playerName);
            ps.setString(3, mobType);
            ps.setString(4, world);
            ps.setString(5, gamemode);
            ps.setString(6, biome);
            ps.setString(7, weapon);
            ps.setString(8, projectile);
            ps.setString(9, timestamp.toString());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert mob kill", e);
        }
    }
}
