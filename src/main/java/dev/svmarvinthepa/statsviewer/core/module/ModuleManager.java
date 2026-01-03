package dev.svmarvinthepa.statsviewer.core.module;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.modules.deaths.DeathsModule;
import dev.svmarvinthepa.statsviewer.modules.mobkills.MobKillModule;
import org.bukkit.configuration.file.FileConfiguration;
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

    public void enableFromConfig(FileConfiguration config) {
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
       Event dispatching
       ========================= */

    public void onPlayerDeath(PlayerDeathEvent event, StatsContext ctx) {
        modules.values().forEach(module -> {
            if (module instanceof DeathsModule dm) {
                dm.onPlayerDeath(event, ctx);
            }
        });
    }

	public void onMobKill(EntityDeathEvent event, StatsContext ctx) {
		for (StatsModule module : modules.values()) {
			if (module instanceof dev.svmarvinthepa.statsviewer.modules.mobkills.MobKillModule mk) {
				mk.onEntityDeath(event, ctx);
			}
		}
    }
}
