package dev.svmarvinthepa.statsviewer.modules.mobkills;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.core.StatsViewerPlugin;
import dev.svmarvinthepa.statsviewer.core.module.StatsModule;
import dev.svmarvinthepa.statsviewer.core.repository.MobKillRepository;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatResolver;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

import java.time.LocalDateTime;

public class MobKillModule implements StatsModule {

    private final StatsViewerPlugin plugin;
    private MobKillRepository repository;

    public MobKillModule(StatsViewerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "mob_kills";
    }

    @Override
    public void onEnable() {
        repository = new MobKillRepository(plugin.getDatabaseManager());
        DebugLogger.info("MobKillModule enabled");
    }

    @Override
    public void onDisable() {
        DebugLogger.info("MobKillModule disabled");
    }

    @Override
    public void onSessionStart(StatsContext ctx) {
        // niets
    }

    @Override
    public void onSessionEnd(dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot snapshot) {
        // niets
    }

    /* =========================
       MOB KILL HANDLING
       ========================= */

	public void onEntityDeath(EntityDeathEvent event, StatsContext ctx) {
	
		LivingEntity victim = event.getEntity();
		Player killer = victim.getKiller();
	
		if (killer == null) return;
		if (victim instanceof Player) return;
	
		Location loc = victim.getLocation();
	
		CombatInfo info = CombatResolver.resolve(victim);
	
		DebugLogger.debug(String.format(
				"MobKill %s killed %s weapon=%s world=%s",
				killer.getName(),
				victim.getType().name(),
				info.weapon(),
				ctx.getWorldName()
		));
	
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
				repository.insert(
						killer.getUniqueId().toString(),
						killer.getName(),
						victim.getType().name().toLowerCase(),
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
