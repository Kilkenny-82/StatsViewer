package dev.svmarvinthepa.statsviewer.core.util;

import dev.svmarvinthepa.statsviewer.api.context.StatsContext;
import dev.svmarvinthepa.statsviewer.api.context.StatsContextManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Snapshot of the circumstances an activity took place in.
 * <p>
 * Shared by the activity modules (fishing, mining, farming) so every table
 * stores world, gamemode, biome, weather and time of day the same way.
 * Capture it on the main thread, right when the event happens: the values
 * can change before an async database write runs.
 * <p>
 * Weather and time of day only mean something in dimensions with a sky and a
 * day cycle. In the Nether and the End they are stored as {@value #NONE}
 * instead of a misleading CLEAR / DAY.
 */
public record EventConditions(
        String world,
        String gamemode,
        String biome,
        String weather,
        String timeOfDay
) {

    public static final String NONE = "NONE";

    public static final String CLEAR = "CLEAR";
    public static final String RAIN = "RAIN";
    public static final String THUNDER = "THUNDER";

    public static final String DAY = "DAY";
    public static final String NIGHT = "NIGHT";

    /** Ticks 0..12999 are day, 13000..23999 are night. */
    private static final long TICKS_PER_DAY = 24000L;
    private static final long NIGHT_STARTS_AT = 13000L;

    public static EventConditions capture(Player player, Location where) {
        World world = where.getWorld() != null ? where.getWorld() : player.getWorld();

        // Same source as the existing modules, so a custom (plugin) gamemode is honoured.
        StatsContext ctx = StatsContextManager.get(player);
        String gamemode = ctx != null ? ctx.getGameModeName() : player.getGameMode().name();

        String biome = world.getBlockAt(where).getBiome().name().toLowerCase(Locale.ROOT);

        boolean hasSkyCycle = world.getEnvironment() == World.Environment.NORMAL;

        return new EventConditions(
                world.getName(),
                gamemode,
                biome,
                weatherOf(hasSkyCycle, world.isThundering(), world.hasStorm()),
                timeOfDayOf(hasSkyCycle, world.getTime())
        );
    }

    /** CLEAR / RAIN / THUNDER, or NONE when the dimension has no weather. */
    public static String weatherOf(boolean hasSkyCycle, boolean thundering, boolean storm) {
        if (!hasSkyCycle) return NONE;
        if (thundering) return THUNDER;
        if (storm) return RAIN;
        return CLEAR;
    }

    /** DAY / NIGHT, or NONE when the dimension has no day cycle. */
    public static String timeOfDayOf(boolean hasSkyCycle, long worldTicks) {
        if (!hasSkyCycle) return NONE;
        long time = Math.floorMod(worldTicks, TICKS_PER_DAY);
        return time < NIGHT_STARTS_AT ? DAY : NIGHT;
    }
}
