package dev.svmarvinthepa.statsviewer.core.module;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import org.bukkit.entity.Player;

public interface PvPAwareModule {
    void onPvPDeath(Player victim, CombatInfo combat, StatsContext ctx);
}
