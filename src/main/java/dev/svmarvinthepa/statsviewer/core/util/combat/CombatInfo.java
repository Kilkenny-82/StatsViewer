package dev.svmarvinthepa.statsviewer.core.util.combat;

public record CombatInfo(
        String cause,
        String killerType,
        String killerName,
        String weapon,
        String projectile
) {

    public boolean isPvP() {
        return "player".equals(killerType);
    }

    public static CombatInfo environment(String cause) {
        return new CombatInfo(
                cause,
                "environment",
                null,
                cause,
                null
        );
    }
}
