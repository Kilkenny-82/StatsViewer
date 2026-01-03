package dev.svmarvinthepa.statsviewer.core.gamemode;

import org.bukkit.entity.Player;

public interface CustomGameModeProvider {

    /**
     * @return CustomGameMode of null als deze provider niet van toepassing is
     */
    CustomGameMode resolve(Player player);
}
