package dev.svmarvinthepa.statsviewer.api;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.StatsContextManager;
import dev.svmarvinthepa.statsviewer.api.context.SessionSnapshot;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameMode;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameModeRegistry;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatResolver;
import dev.svmarvinthepa.statsviewer.core.util.combat.CombatInfo;
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
		CustomGameMode vanilla = CustomGameModeRegistry.resolve(player);
	
		StatsContext ctx = new StatsContext(player, world, vanilla);
		StatsContextManager.set(player, ctx);
	
		moduleManager.onSessionStart(ctx);
	}

	public static void handleSwitch(Player player, World newWorld) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		boolean worldChanged = !ctx.getWorld().equals(newWorld);
		if (!worldChanged) return;
	
		// 🔴 sessie altijd sluiten bij world switch
		SessionSnapshot snapshot = ctx.closeSession();
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
	
		// 🔵 vanilla gamemode opnieuw bepalen
		CustomGameMode resolvedVanilla = CustomGameModeRegistry.resolve(player);
	
		// 🔑 alleen toepassen als géén externe plugin eigenaar is
		if (!ctx.isPluginActive()) {
			ctx.setVanillaGameMode(resolvedVanilla);
		}
	
		ctx.startNewSession(newWorld);
		moduleManager.onSessionStart(ctx);
	}
	
	public static void handleVanillaGameModeChange(Player player, CustomGameMode newVanilla) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		// als plugin eigenaar is → negeren
		if (ctx.isPluginActive()) return;
	
		// als niets verandert → niks doen
		if (ctx.getEffectiveGameMode().equals(newVanilla)) return;
	
		// 🔴 sessie sluiten
		SessionSnapshot snapshot = ctx.closeSession();
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
	
		// 🔵 vanilla bijwerken
		ctx.setVanillaGameMode(newVanilla);
	
		// 🟢 nieuwe sessie starten (zelfde world)
		ctx.startNewSession(ctx.getWorld());
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
 	
	/**
	 * Register a death that Bukkit never reported as a PlayerDeathEvent
	 * (e.g. Parkour teleports the player to a checkpoint instead of killing them).
	 * Counted in the player's current context, so it lands on the active gamemode.
	 */
	public static void handleExternalDeath(Player player, String deathMessage, CombatInfo combat) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;

		moduleManager.onExternalDeath(player, deathMessage, combat, ctx);
	}
	
	public static void handleEntityDeath(EntityDeathEvent event) {

		if (!(event.getEntity().getKiller() instanceof Player killer)) {
			return;
		}
	
		StatsContext ctx = StatsContextManager.get(killer);
		if (ctx == null) return;
	
		moduleManager.onMobKill(event, ctx);
	}
	
	public static void activatePluginGameMode(Player player, CustomGameMode pluginMode) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		// sessie afsluiten
		SessionSnapshot snapshot = ctx.closeSession();
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
	
		ctx.setPluginGameMode(pluginMode);
	
		ctx.startNewSession(ctx.getWorld());
		moduleManager.onSessionStart(ctx);
	}
	
	public static void deactivatePluginGameMode(Player player) {
		StatsContext ctx = StatsContextManager.get(player);
		if (ctx == null) return;
	
		SessionSnapshot snapshot = ctx.closeSession();
		if (snapshot != null) {
			moduleManager.onSessionEnd(snapshot);
		}
	
		ctx.clearPluginGameMode();
	
		// vanilla opnieuw bepalen
		CustomGameMode vanilla = CustomGameModeRegistry.resolve(player);
		ctx.setVanillaGameMode(vanilla);
	
		ctx.startNewSession(ctx.getWorld());
		moduleManager.onSessionStart(ctx);
	}
}
