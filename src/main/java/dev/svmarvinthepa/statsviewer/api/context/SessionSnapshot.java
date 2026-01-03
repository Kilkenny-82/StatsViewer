package dev.svmarvinthepa.statsviewer.api.context;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record SessionSnapshot(
        UUID playerUuid,
        String playerName,
        String world,
        String gamemode,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long durationSeconds,

		Map<String, Map<String, Long>> stats
) {}
