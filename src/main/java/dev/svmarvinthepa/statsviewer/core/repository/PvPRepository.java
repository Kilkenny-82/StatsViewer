package dev.svmarvinthepa.statsviewer.core.repository;

import dev.svmarvinthepa.statsviewer.core.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

public class PvPRepository {

    private final DatabaseManager databaseManager;

    public PvPRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void insert(
            String killerUuid,
            String killerName,
            String victimUuid,
            String victimName,
            String world,
            String gamemode,
            String biome,
            String weapon,
            String projectile,
            LocalDateTime timestamp
    ) {
        String sql = """
            INSERT INTO pvp_kills
            (killer_uuid, killer_name, victim_uuid, victim_name,
             world, gamemode, biome, weapon, projectile, timestamp)
            VALUES (?,?,?,?,?,?,?,?,?,?)
        """;

        try (Connection c = databaseManager.getDatabase().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, killerUuid);
            ps.setString(2, killerName);
            ps.setString(3, victimUuid);
            ps.setString(4, victimName);
            ps.setString(5, world);
            ps.setString(6, gamemode);
            ps.setString(7, biome);
            ps.setString(8, weapon);
            ps.setString(9, projectile);
            ps.setString(10, timestamp.toString());

            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
