package dev.svmarvinthepa.statsviewer.api.context;

import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameMode;
import org.bukkit.Statistic;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsContext {

    /* =========================
       Player info
       ========================= */

    private final Player player;
    private final UUID playerUuid;
    private final String playerName;

    /* =========================
       Context info
       ========================= */

    private World world;
    private CustomGameMode gameMode;

    private final LocalDate date;
    private final LocalDateTime createdAt;

    /* =========================
       Session timing
       ========================= */

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean closed = false;

    /* =========================
       Stat snapshots (start)
       ========================= */

    private final Map<Statistic, Integer> customStatsStart = new EnumMap<>(Statistic.class);
    private final Map<String, Integer> pickedUpStart = new HashMap<>();
    private final Map<String, Integer> minedStart = new HashMap<>();
    private final Map<String, Integer> usedStart = new HashMap<>();
    private final Map<String, Integer> craftedStart = new HashMap<>();
    private final Map<String, Integer> droppedStart = new HashMap<>();
    private final Map<String, Integer> killedStart = new HashMap<>();
    private final Map<String, Integer> killedByStart = new HashMap<>();

    public StatsContext(Player player, World world, CustomGameMode gameMode) {
        this.player = player;
        this.playerUuid = player.getUniqueId();
        this.playerName = player.getName();

        this.world = world;
        this.gameMode = gameMode;

        this.date = LocalDate.now();
        this.createdAt = LocalDateTime.now();
        this.startTime = this.createdAt;

        captureStartStats();
    }

    /* =========================
       Getters
       ========================= */

    public Player getPlayer() { return player; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public World getWorld() { return world; }
    public String getWorldName() { return world.getName(); }
    public CustomGameMode getGameMode() { return gameMode; }
    public String getGameModeName() { return gameMode.getName(); }
    public LocalDate getDate() { return date; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isClosed() { return closed; }

    /* =========================
       Session lifecycle
       ========================= */

    public SessionSnapshot closeSession() {
        if (closed) return null;

        this.endTime = LocalDateTime.now();
        this.closed = true;

        long durationSeconds =
                Duration.between(startTime, endTime).getSeconds();

        Map<String, Map<String, Long>> statsDelta = collectStatsDelta();

        return new SessionSnapshot(
                playerUuid,
                playerName,
                world.getName(),
                gameMode.getName(),
                startTime,
                endTime,
                durationSeconds,
                statsDelta
        );
    }

    public void startNewSession(World newWorld, CustomGameMode newGameMode) {
        this.world = newWorld;
        this.gameMode = newGameMode;
        this.startTime = LocalDateTime.now();
        this.endTime = null;
        this.closed = false;

        clearStartStats();
        captureStartStats();
    }

    public boolean isSameContext(World world, CustomGameMode gameMode) {
        return this.world.equals(world) && this.gameMode.equals(gameMode);
    }

    /* =========================
       Stats handling
       ========================= */

    private void captureStartStats() {

        // CUSTOM
        for (Statistic stat : Statistic.values()) {
            if (stat.getType() == Statistic.Type.UNTYPED) {
                customStatsStart.put(stat, player.getStatistic(stat));
            }
        }

        // ITEM / BLOCK BASED
        for (Material mat : Material.values()) {

            if (mat.isItem()) {
                pickedUpStart.put(mat.name(), player.getStatistic(Statistic.PICKUP, mat));
                usedStart.put(mat.name(), player.getStatistic(Statistic.USE_ITEM, mat));
                craftedStart.put(mat.name(), player.getStatistic(Statistic.CRAFT_ITEM, mat));
                droppedStart.put(mat.name(), player.getStatistic(Statistic.DROP, mat));
            }

            if (mat.isBlock()) {
                minedStart.put(mat.name(), player.getStatistic(Statistic.MINE_BLOCK, mat));
            }
        }

        // ENTITY BASED
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive()) continue;

            killedStart.put(type.name(), player.getStatistic(Statistic.KILL_ENTITY, type));
            killedByStart.put(type.name(), player.getStatistic(Statistic.ENTITY_KILLED_BY, type));
        }
    }

    private void clearStartStats() {
        customStatsStart.clear();
        pickedUpStart.clear();
        minedStart.clear();
        usedStart.clear();
        craftedStart.clear();
        droppedStart.clear();
        killedStart.clear();
        killedByStart.clear();
    }

    private Map<String, Map<String, Long>> collectStatsDelta() {
        Map<String, Map<String, Long>> result = new HashMap<>();

        collectDelta(result, "custom", customStatsStart,
                stat -> player.getStatistic(stat)
        );

        collectDelta(result, "picked_up", pickedUpStart,
                key -> safeItemStat(Statistic.PICKUP, key)
        );

        collectDelta(result, "mined", minedStart,
                key -> safeBlockStat(Statistic.MINE_BLOCK, key)
        );

        collectDelta(result, "used", usedStart,
                key -> safeItemStat(Statistic.USE_ITEM, key)
        );

        collectDelta(result, "crafted", craftedStart,
                key -> safeItemStat(Statistic.CRAFT_ITEM, key)
        );

        collectDelta(result, "dropped", droppedStart,
                key -> safeItemStat(Statistic.DROP, key)
        );

        collectDelta(result, "killed", killedStart,
                key -> safeEntityStat(Statistic.KILL_ENTITY, key)
        );

        collectDelta(result, "killed_by", killedByStart,
                key -> safeEntityStat(Statistic.ENTITY_KILLED_BY, key)
        );

        return result;
    }

    /* =========================
       Safe readers
       ========================= */

    private int safeItemStat(Statistic stat, String key) {
        Material mat = Material.matchMaterial(key);
        if (mat == null || !mat.isItem()) return 0;
        return player.getStatistic(stat, mat);
    }

    private int safeBlockStat(Statistic stat, String key) {
        Material mat = Material.matchMaterial(key);
        if (mat == null || !mat.isBlock()) return 0;
        return player.getStatistic(stat, mat);
    }

    private int safeEntityStat(Statistic stat, String key) {
        try {
            EntityType type = EntityType.valueOf(key);
            return player.getStatistic(stat, type);
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    /* =========================
       Helpers
       ========================= */

    private <K> void collectDelta(
            Map<String, Map<String, Long>> target,
            String category,
            Map<K, Integer> start,
            StatReader<K> reader
    ) {
        Map<String, Long> delta = new HashMap<>();

        start.forEach((key, startValue) -> {
            int endValue = reader.read(key);
            int diff = endValue - startValue;

            if (diff > 0) {
                delta.put(key.toString().toLowerCase(), (long) diff);
            }
        });

        if (!delta.isEmpty()) {
            target.put(category, delta);
        }
    }

    @FunctionalInterface
    private interface StatReader<K> {
        int read(K key);
    }
}
