package dev.svmarvinthepa.statsviewer.modules.farming;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which blocks count as a crop, when breaking them is a real harvest, and which
 * dropped items are the crop itself (as opposed to seeds and other by-products).
 * <p>
 * Extend it with {@link #register}. This class deliberately has no Bukkit
 * dependency.
 */
public final class FarmingCrops {

    /** When breaking the block counts as a harvest. */
    public enum Harvest {
        /** Always: the block has no growth stages (melon, pumpkin, cactus...). */
        ALWAYS,
        /** Only when fully grown (wheat, carrots, nether wart...). */
        MATURE,
        /** Sweet berry bush: from age 2, when it carries berries. */
        BERRIES,
        /** Cave vines: only when they carry glow berries. */
        CAVE_BERRIES
    }

    /**
     * @param type     name stored as crop_type
     * @param harvest  when breaking it counts
     * @param products dropped items that are the crop itself, lowercase
     * @param stalk    grows as a column (sugar cane, bamboo, cactus, kelp): breaking a block
     *                 makes everything above it collapse and drop its items
     */
    public record Crop(String type, Harvest harvest, Set<String> products, boolean stalk) {}

    private static final Map<String, Crop> BY_BLOCK = new HashMap<>();

    static {
        register("WHEAT", "wheat", Harvest.MATURE, "wheat");
        register("CARROTS", "carrots", Harvest.MATURE, "carrot");
        register("POTATOES", "potatoes", Harvest.MATURE, "potato");
        register("BEETROOTS", "beetroot", Harvest.MATURE, "beetroot");
        register("NETHER_WART", "nether_wart", Harvest.MATURE, "nether_wart");
        register("COCOA", "cocoa_beans", Harvest.MATURE, "cocoa_beans");

        register("SWEET_BERRY_BUSH", "sweet_berries", Harvest.BERRIES, "sweet_berries");
        register("CAVE_VINES", "glow_berries", Harvest.CAVE_BERRIES, "glow_berries");
        register("CAVE_VINES_PLANT", "glow_berries", Harvest.CAVE_BERRIES, "glow_berries");

        register("MELON", "melon", Harvest.ALWAYS, "melon_slice");
        register("PUMPKIN", "pumpkin", Harvest.ALWAYS, "pumpkin");
        registerStalk("SUGAR_CANE", "sugar_cane", "sugar_cane");
        registerStalk("BAMBOO", "bamboo", "bamboo");
        registerStalk("CACTUS", "cactus", "cactus");
        registerStalk("KELP", "kelp", "kelp");
        registerStalk("KELP_PLANT", "kelp", "kelp");
        register("BROWN_MUSHROOM", "mushrooms", Harvest.ALWAYS, "brown_mushroom");
        register("RED_MUSHROOM", "mushrooms", Harvest.ALWAYS, "red_mushroom");
    }

    private FarmingCrops() {}

    /** Adds or replaces a crop. {@code blockName} is the Material name, e.g. "SWEET_BERRY_BUSH". */
    public static void register(String blockName, String type, Harvest harvest, String... products) {
        BY_BLOCK.put(blockName.toUpperCase(Locale.ROOT), new Crop(type, harvest, Set.of(products), false));
    }

    /**
     * Adds or replaces a crop that grows as a column. All blocks of the same {@code type}
     * stacked above a broken block count as part of that harvest.
     */
    public static void registerStalk(String blockName, String type, String... products) {
        BY_BLOCK.put(blockName.toUpperCase(Locale.ROOT),
                new Crop(type, Harvest.ALWAYS, Set.of(products), true));
    }

    /** @return the crop for this block (Material name), or null when it is not a crop */
    public static Crop forBlock(String materialName) {
        return materialName == null ? null : BY_BLOCK.get(materialName);
    }

    /** Fallback crop_type for a block that is not in the table but was harvested by a player. */
    public static String fallbackType(String materialName) {
        return materialName.toLowerCase(Locale.ROOT);
    }
}
