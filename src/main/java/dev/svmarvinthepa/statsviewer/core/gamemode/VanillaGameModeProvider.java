package dev.svmarvinthepa.statsviewer.core.gamemode;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class VanillaGameModeProvider implements CustomGameModeProvider {

    @Override
	public CustomGameMode resolve(Player player) {
        GameMode gm = player.getGameMode();

        return () -> gm.name();
    }
}
