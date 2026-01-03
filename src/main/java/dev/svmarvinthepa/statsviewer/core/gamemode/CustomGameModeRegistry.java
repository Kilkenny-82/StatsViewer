package dev.svmarvinthepa.statsviewer.core.gamemode;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class CustomGameModeRegistry {

    private static final List<CustomGameModeProvider> providers = new ArrayList<>();

    private CustomGameModeRegistry() {}

    public static void register(CustomGameModeProvider provider) {
        providers.add(provider);
    }

    public static CustomGameMode resolve(Player player) {
        for (CustomGameModeProvider provider : providers) {
            CustomGameMode mode = provider.resolve(player);
            if (mode != null) {
                return mode;
            }
        }

        return () -> "UNKNOWN";
    }
}
