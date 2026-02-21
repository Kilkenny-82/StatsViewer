package dev.svmarvinthepa.statsviewer.core;

import dev.svmarvinthepa.statsviewer.api.StatsViewerAPI;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameMode;
import dev.svmarvinthepa.statsviewer.core.gamemode.CustomGameModeRegistry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class StatsViewerListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        StatsViewerAPI.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        StatsViewerAPI.handleQuit(event.getPlayer());
    }

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		World newWorld = player.getWorld();
	
		StatsViewerAPI.handleSwitch(player, newWorld);
	}
	
	@EventHandler
	public void onVanillaGamemodeChange(PlayerGameModeChangeEvent event) {
		Player player = event.getPlayer();
	
		CustomGameMode vanilla = () -> event.getNewGameMode().name();
	
		StatsViewerAPI.handleVanillaGameModeChange(player, vanilla);
	}
	
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		StatsViewerAPI.handleDeath(event);
	}
	
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		StatsViewerAPI.handleEntityDeath(event);
	}
	
}
