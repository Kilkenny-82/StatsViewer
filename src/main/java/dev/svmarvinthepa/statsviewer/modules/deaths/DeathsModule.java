package dev.svmarvinthepa.statsviewer.modules.deaths;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.DeathsRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatResolver;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.LocalDateTime;

public class DeathsModule implements StatsModule {

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
    public void onSessionStart(StatsContext ctx) {
        // niet nodig
    }

    @Override
    public void onSessionEnd(dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot snapshot) {
        // niet nodig
    }

    /* =========================
       Death handling
       ========================= */

    public void onPlayerDeath(PlayerDeathEvent event, StatsContext ctx) {

        Player player = event.getEntity();
        Location loc = player.getLocation();

        CombatInfo info = CombatResolver.resolve(player);

        DebugLogger.debug(String.format(
                "Death %s cause=%s killer=%s name=%s source=%s world=%s",
                player.getName(),
                info.cause(),
                info.killerType(),
                info.killerName(),
                info.weapon(),
                ctx.getWorldName()
        ));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                repository.insert(
                        player.getUniqueId().toString(),
                        player.getName(),
                        info.cause(),
                        event.getDeathMessage(),
                        info.killerType(),
                        info.killerName(),
                        ctx.getWorldName(),
                        ctx.getGameModeName(),
                        loc.getBlock().getBiome().name().toLowerCase(),
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        LocalDateTime.now(),
                        info.weapon()
                )
        );
    }
}
