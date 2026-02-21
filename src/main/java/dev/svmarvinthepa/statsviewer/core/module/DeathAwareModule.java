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
}
