package dev.svmarvinthepa.statsviewer.core.util.combat;

public record CombatInfo(
        String cause,
        String killerType,
        String killerName,
        String weapon,
        String projectile
) {}