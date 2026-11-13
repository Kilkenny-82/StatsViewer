package dev.svmarvinthepa.statsviewer.modules.mining;

import java.util.Locale;

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
     */
    public record Mineral(String type, String host, String oreBlock) {}

    private static final String ORE_SUFFIX = "_ORE";

    private MineralClassifier() {}

    /** @param materialName the Material name, e.g. DEEPSLATE_IRON_ORE; returns null for non-minerals */
    public static Mineral classify(String materialName) {
        if (materialName == null || materialName.startsWith("LEGACY_")) return null;

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
