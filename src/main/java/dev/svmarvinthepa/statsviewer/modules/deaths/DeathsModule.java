package dev.svmarvinthepa.statsviewer.modules.deaths;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.DeathAwareModule;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.DeathsRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;

public class DeathsModule implements StatsModule, DeathAwareModule {

    private final StatsViewerPlugin plugin;
    private DeathsRepository repository;

    public DeathsModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "deaths";
    }

    @Override
    public void onEnable() {
        repository = new DeathsRepository(plugin.getDatabaseManager());
        DebugLogger.info("DeathsModule enabled");
    }

    @Override
    public void onDisable() {
        DebugLogger.info("DeathsModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {}

    @Override
    public void onSessionEnd(dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot snapshot) {}

    /* =========================
       DeathAwareModule
       ========================= */

    @Override
    public void onPlayerDeath(
			Player victim,
			PlayerDeathEvent event,
			CombatInfo info,
			StatsContext ctx
	) {
        record(victim, event.getDeathMessage(), info, ctx);
    }

    @Override
    public void onExternalDeath(
            Player victim,
            String deathMessage,
            CombatInfo info,
            StatsContext ctx
    ) {
        record(victim, deathMessage, info, ctx);
    }

    private void record(
            Player victim,
            String deathMessage,
            CombatInfo info,
            StatsContext ctx
    ) {
        Location loc = victim.getLocation();
 
        // read everything now, the gamemode can change before the async task runs
        final String world = ctx.getWorldName();
        final String gamemode = ctx.getGameModeName();
        final String biome = loc.getBlock().getBiome().name().toLowerCase();

        DebugLogger.debug(String.format(
                "Death %s cause=%s killer=%s name=%s source=%s world=%s gamemode=%s",
                victim.getName(),
                info.cause(),
                info.killerType(),
                info.killerName(),
                info.weapon(),
                world,
                gamemode
        ));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                repository.insert(
                        victim.getUniqueId().toString(),
                        victim.getName(),
                        info.cause(),
                        deathMessage,
                        info.killerType(),
                        info.killerName(),
                        world,
                        gamemode,
                        biome,
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        LocalDateTime.now(),
                        info.weapon()
                )
        );
    }
}
