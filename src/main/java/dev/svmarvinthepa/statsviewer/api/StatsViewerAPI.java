package dev.svmarvinthepa.statsviewer.api;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.StatsContextManager;
import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameMode;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameModeRegistry;
import dev.svmarvinthepa.statsviewer.core.module.ModuleManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class StatsViewerAPI {

    private static ModuleManager moduleManager;

    private StatsViewerAPI() {}

    public static void init(ModuleManager manager) {
        moduleManager = manager;
    }

    /* =========================
       Lifecycle hooks
       ========================= */

    public static void handleJoin(Player player) {
        World world = player.getWorld();
        CustomGameMode gm = CustomGameModeRegistry.resolve(player);

        StatsContext ctx = new StatsContext(player, world, gm);
        StatsContextManager.set(player, ctx);

        moduleManager.onSessionStart(ctx);
    }

	public static void handleSwitch(Player player, World newWorld, CustomGameMode newGameMode) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		if (ctx.isSameContext(newWorld, newGameMode)) return;
	
		SessionSnapshot snapshot = ctx.closeSession();
	
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
	
		// 3️⃣ Nieuwe sessie starten
		ctx.startNewSession(newWorld, newGameMode);
		moduleManager.onSessionStart(ctx);
	}
	
    public static void handleQuit(Player player) {
        StatsContext ctx = StatsContextManager.remove(player);
        if (ctx == null) return;

        SessionSnapshot snapshot = ctx.closeSession();
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
    }
	
	public static void handleDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		moduleManager.onPlayerDeath(event, ctx);
	}
	
	public static void handleEntityDeath(EntityDeathEvent event) {
	
		if (!(event.getEntity().getKiller() instanceof Player killer)) {
			return; // geen player kill
		}
	
		StatsContext ctx = StatsContextManager.get(killer);
		if (ctx == null) return;
	
		moduleManager.onMobKill(event, ctx);
	}
}
