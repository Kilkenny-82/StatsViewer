package dev.svmarvinthepa.statsviewer.core.gamemode;

import org.bukkit.entity.Player;

public interface GameModeProvider {
    boolean applies(Player player);
    String getGameMode(Player player);
}
