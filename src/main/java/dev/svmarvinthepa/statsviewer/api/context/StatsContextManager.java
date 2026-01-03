package dev.svmarvinthepa.statsviewer.api.context;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsContextManager {

    private static final Map<UUID, StatsContext> CONTEXTS = new ConcurrentHashMap<>();

    private StatsContextManager() {}

    public static StatsContext get(Player player) {
        return CONTEXTS.get(player.getUniqueId());
    }

    public static void set(Player player, StatsContext context) {
        CONTEXTS.put(player.getUniqueId(), context);
    }

    public static StatsContext remove(Player player) {
        return CONTEXTS.remove(player.getUniqueId());
    }

    public static boolean has(Player player) {
        return CONTEXTS.containsKey(player.getUniqueId());
    }
}
