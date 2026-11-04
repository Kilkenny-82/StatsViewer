package dev.svmarvinthepa.statsviewer.core.module;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Player;

public interface DeathAwareModule {
    void onPlayerDeath(
        Player victim,
        PlayerDeathEvent event,
        CombatInfo combat,
        StatsContext ctx
    );

    /**
     * Death that did not go through a PlayerDeathEvent (for example a
     * "soft" death in a minigame plugin that teleports the player back).
     * Modules that don't care can ignore it.
     */
    default void onExternalDeath(
        Player victim,
        String deathMessage,
        CombatInfo combat,
        StatsContext ctx
    ) {}
}
