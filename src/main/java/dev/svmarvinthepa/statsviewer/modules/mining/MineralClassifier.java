package dev.svmarvinthepa.statsviewer.modules.mining;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Decides whether a block is a mineral and which one.
 * <p>
 * Works on the Minecraft block name, so every {@code *_ORE} block is picked up
 * automatically, including ores added by later Minecraft versions: those are
 * stored under the name derived from the block instead of being missed.
 * <p>
 * This class deliberately has no Bukkit dependency.
 */
public final class MineralClassifier {

    /**
     * @param type     the mineral: iron, gold, copper, coal, redstone, lapis, diamond,
     *                 emerald, quartz, ancient_debris, amethyst...
     * @param host     the rock the ore variant sits in: stone, deepslate, netherrack or other
     * @param oreBlock the exact block that was mined, e.g. deepslate_iron_ore
     * @param requiredDrop when not null, the block only counts as this mineral when this item
     *                 really dropped (lowercase item name). Used for blocks that are mined all
     *                 the time but only sometimes yield the mineral, like gravel and flint.
     */
    public record Mineral(String type, String host, String oreBlock, String requiredDrop) {

        public Mineral(String type, String host, String oreBlock) {
            this(type, host, oreBlock, null);
        }
    }

    private static final String ORE_SUFFIX = "_ORE";

    /** Blocks that are no ore but yield a mineral as a drop. Extend with {@link #registerDropMineral}. */
    private static final Map<String, Mineral> DROP_MINERALS = new HashMap<>();

    static {
        registerDropMineral("GRAVEL", "flint", "gravel", "flint");
    }

    private MineralClassifier() {}

    /**
     * Registers a block that counts as a mineral only when {@code requiredDrop} dropped from it.
     *
     * @param blockName the Material name, e.g. "GRAVEL"
     */
    public static void registerDropMineral(String blockName, String type, String host, String requiredDrop) {
        String name = blockName.toUpperCase(Locale.ROOT);
        DROP_MINERALS.put(name, new Mineral(type, host, name.toLowerCase(Locale.ROOT),
                requiredDrop.toLowerCase(Locale.ROOT)));
    }

    /** @param materialName the Material name, e.g. DEEPSLATE_IRON_ORE; returns null for non-minerals */
    public static Mineral classify(String materialName) {
        if (materialName == null || materialName.startsWith("LEGACY_")) return null;

        Mineral viaDrop = DROP_MINERALS.get(materialName);
        if (viaDrop != null) return viaDrop;

        String oreBlock = materialName.toLowerCase(Locale.ROOT);

        if (materialName.equals("ANCIENT_DEBRIS")) {
            return new Mineral("ancient_debris", "netherrack", oreBlock);
        }
        if (materialName.equals("AMETHYST_CLUSTER")) {
            return new Mineral("amethyst", "other", oreBlock);
        }
        if (!materialName.endsWith(ORE_SUFFIX)) return null;

        String base = materialName.substring(0, materialName.length() - ORE_SUFFIX.length());
        String host = "stone";

        if (base.startsWith("DEEPSLATE_")) {
            host = "deepslate";
            base = base.substring("DEEPSLATE_".length());
        } else if (base.startsWith("NETHER_")) {
            host = "netherrack";
            base = base.substring("NETHER_".length());
        }

        if (base.isEmpty()) return null;

        return new Mineral(base.toLowerCase(Locale.ROOT), host, oreBlock);
    }
}
