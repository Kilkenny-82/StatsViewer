package dev.svmarvinthepa.statsviewer.modules.pvp;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.PvPRepository;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatResolver;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.LocalDateTime;

public class PvPModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private PvPRepository repository;

    public PvPModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "pvp";
    }

    @Override
    public void onEnable() {
        repository = new PvPRepository(plugin.getDatabaseManager());
        DebugLogger.info("PvPModule enabled");
    }

    @Override public void onDisable() {}

    @Override public void onSessionStart(StatsContext ctx) {}
    @Override public void onSessionEnd(dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot snap) {}

    public void onPlayerDeath(PlayerDeathEvent event, StatsContext ctx) {

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return; // alleen PvP

        Location loc = victim.getLocation();
        CombatInfo info = CombatResolver.resolve(victim);

        DebugLogger.debug(String.format(
                "PvP %s killed %s with %s (%s)",
                killer.getName(),
                victim.getName(),
                info.projectile(),
                ctx.getWorldName()
        ));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                repository.insert(
                        killer.getUniqueId().toString(),
                        killer.getName(),
                        victim.getUniqueId().toString(),
                        victim.getName(),
                        ctx.getWorldName(),
                        ctx.getGameModeName(),
                        loc.getBlock().getBiome().name().toLowerCase(),
                        info.weapon(),
                        info.projectile(),
                        LocalDateTime.now()
                )
        );
    }
}
