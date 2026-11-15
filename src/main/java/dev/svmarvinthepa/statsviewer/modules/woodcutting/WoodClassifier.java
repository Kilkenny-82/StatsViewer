package dev.svmarvinthepa.statsviewer.modules.woodcutting;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a block is wood, which wood type it is, and whether a block
 * is a tree seed (sapling, propagule, fungus).
 * <p>
 * Works on the Minecraft block name instead of a fixed list, so wood types that
 * Minecraft adds later (anything named {@code <name>_LOG}, {@code <name>_WOOD},
 * {@code <name>_STEM} or {@code <name>_HYPHAE}, and {@code <name>_SAPLING}) are
 * recognised without a code change and stored under the name taken from the block.
 * <p>
 * Wood is the logs and everything made from them in the world: log, wood (bark on
 * all sides), the stripped variants, nether stems and hyphae, bamboo blocks and
 * mangrove roots. Planks and other crafted blocks are not wood in this sense.
 * <p>
 * This class deliberately has no Bukkit dependency.
 */
public final class WoodClassifier {

    /**
     * @param woodType  oak, spruce, birch, pale_oak, crimson, mangrove, bamboo, ...
     * @param blockType the exact block, e.g. stripped_pale_oak_log
     */
    public record Wood(String woodType, String blockType) {}

    private static final String STRIPPED = "STRIPPED_";

    private static final String[] SUFFIXES = {"_LOG", "_WOOD", "_STEM", "_HYPHAE"};

    /** Blocks that match a wood suffix but are not wood. */
    private static final Set<String> NOT_WOOD = Set.of("MELON_STEM", "PUMPKIN_STEM", "MUSHROOM_STEM");

    private static final Set<String> MANGROVE_ROOTS = Set.of("MANGROVE_ROOTS", "MUDDY_MANGROVE_ROOTS");

    private static final Set<String> SEEDS_WITHOUT_SUFFIX = Set.of(
            "CRIMSON_FUNGUS", "WARPED_FUNGUS", "AZALEA", "FLOWERING_AZALEA");

    private WoodClassifier() {}

    /** @param materialName e.g. STRIPPED_OAK_LOG; returns null when it is not wood */
    public static Wood classify(String materialName) {
        if (materialName == null || materialName.startsWith("LEGACY_")) return null;
        if (materialName.startsWith("ATTACHED_") || NOT_WOOD.contains(materialName)) return null;

        String blockType = materialName.toLowerCase(Locale.ROOT);

        if (MANGROVE_ROOTS.contains(materialName)) {
            return new Wood("mangrove", blockType);
        }

        String base = materialName.startsWith(STRIPPED)
                ? materialName.substring(STRIPPED.length())
                : materialName;

        if (base.equals("BAMBOO_BLOCK")) {
            return new Wood("bamboo", blockType);
        }

        for (String suffix : SUFFIXES) {
            if (base.endsWith(suffix) && base.length() > suffix.length()) {
                String type = base.substring(0, base.length() - suffix.length());
                return new Wood(type.toLowerCase(Locale.ROOT), blockType);
            }
        }
        return null;
    }

    public static boolean isWood(String materialName) {
        return classify(materialName) != null;
    }

    /**
     * @param materialName e.g. DARK_OAK_SAPLING
     * @return the wood type this seed grows into (dark_oak), or null when it is not a tree seed
     */
    public static String seedType(String materialName) {
        if (materialName == null || materialName.startsWith("LEGACY_")) return null;

        if (SEEDS_WITHOUT_SUFFIX.contains(materialName)) {
            return materialName.startsWith("FLOWERING_")
                    ? "azalea"
                    : materialName.replace("_FUNGUS", "").toLowerCase(Locale.ROOT);
        }
        for (String suffix : new String[] {"_SAPLING", "_PROPAGULE"}) {
            if (materialName.endsWith(suffix) && materialName.length() > suffix.length()) {
                return materialName.substring(0, materialName.length() - suffix.length())
                        .toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
