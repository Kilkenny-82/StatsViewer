package dev.svmarvinthepa.statsviewer.core.module;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatResolver;
import dev.svmarvinthepa.statsviewer.modules.mobkills.MobKillModule;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class ModuleManager {

    private final Plugin plugin;
    private final Map<String, StatsModule> modules = new HashMap<>();

    public ModuleManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(StatsModule module) {
        modules.put(module.getName(), module);
    }

    public void enableFromConfig(org.bukkit.configuration.file.FileConfiguration config) {
        for (StatsModule module : modules.values()) {
            if (config.getBoolean("modules." + module.getName(), true)) {
                module.onEnable();
            }
        }
    }

    /* =========================
       Session lifecycle
       ========================= */

    public void onSessionStart(StatsContext context) {
        modules.values().forEach(m -> m.onSessionStart(context));
    }

    public void onSessionEnd(SessionSnapshot snapshot) {
        modules.values().forEach(m -> m.onSessionEnd(snapshot));
    }

    public void disableAll() {
        modules.values().forEach(StatsModule::onDisable);
    }

    /* =========================
       Death handling
       ========================= */

    public void onPlayerDeath(PlayerDeathEvent event, StatsContext ctx) {

		Player victim = event.getEntity();
		CombatInfo combat = CombatResolver.resolve(victim);
	
		for (StatsModule module : modules.values()) {
	
			if (module instanceof DeathAwareModule dm) {
				dm.onPlayerDeath(victim, event, combat, ctx);
			}
	
			if (module instanceof PvPAwareModule pm && combat.isPvP()) {
				pm.onPvPDeath(victim, combat, ctx);
			}
		}
	}
 
    public void onExternalDeath(
            Player victim,
            String deathMessage,
            CombatInfo combat,
            StatsContext ctx
    ) {
        for (StatsModule module : modules.values()) {

            if (module instanceof DeathAwareModule dm) {
                dm.onExternalDeath(victim, deathMessage, combat, ctx);
            }

            if (module instanceof PvPAwareModule pm && combat.isPvP()) {
                pm.onPvPDeath(victim, combat, ctx);
            }
        }
    }

    public void onMobKill(EntityDeathEvent event, StatsContext ctx) {

        for (StatsModule module : modules.values()) {
            if (module instanceof MobKillModule mk) {
                mk.onEntityDeath(event, ctx);
            }
        }
    }
}
