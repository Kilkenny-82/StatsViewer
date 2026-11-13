package dev.svmarvinthepa.statsviewer.modules.fishing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central place that decides whether a catch is FISH, TREASURE or JUNK.
 * <p>
 * Mirrors the vanilla fishing loot table and is meant to be extended:
 * <ul>
 *   <li>{@link #register} adds items to the built-in table (in code),</li>
 *   <li>{@link #addRule} adds a contextual rule for items whose category
 *       depends on more than their type (the fishing rod, see below),</li>
 *   <li>{@link #override} is what the {@code fishing.classification} section of
 *       config.yml feeds, and wins over everything else.</li>
 * </ul>
 * Items are matched on their lowercase Minecraft name ("cod", "enchanted_book"),
 * the same value that is stored in the database. Anything unmatched becomes
 * {@link CatchCategory#UNKNOWN}.
 * <p>
 * This class deliberately has no Bukkit dependency.
 */
public final class FishingLootClassifier {

    /** Contextual rule. Return null to have no opinion. */
    @FunctionalInterface
    public interface Rule {
        CatchCategory classify(String itemName, boolean enchanted);
    }

    private final Map<String, CatchCategory> table = new HashMap<>();
    private final Map<String, CatchCategory> overrides = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public FishingLootClassifier() {
        // vanilla gameplay/fishing loot table
        register(CatchCategory.FISH,
                "cod", "salmon", "tropical_fish", "pufferfish");

        register(CatchCategory.TREASURE,
                "bow", "enchanted_book", "name_tag", "nautilus_shell", "saddle");

        register(CatchCategory.JUNK,
                "lily_pad", "bowl", "leather", "leather_boots", "rotten_flesh", "stick",
                "string", "potion", "bone", "ink_sac", "tripwire_hook");

        // A fishing rod is in both loot tables: the treasure one is enchanted,
        // the junk one is damaged but never enchanted.
        addRule((item, enchanted) ->
                item.equals("fishing_rod")
                        ? (enchanted ? CatchCategory.TREASURE : CatchCategory.JUNK)
                        : null);
    }

    public FishingLootClassifier register(CatchCategory category, String... itemNames) {
        for (String name : itemNames) {
            table.put(normalise(name), category);
        }
        return this;
    }

    public FishingLootClassifier addRule(Rule rule) {
        rules.add(rule);
        return this;
    }

    /** Forces these items into a category, beating the built-in table and rules. */
    public FishingLootClassifier override(CatchCategory category, Collection<String> itemNames) {
        for (String name : itemNames) {
            String key = normalise(name);
            if (!key.isEmpty()) overrides.put(key, category);
        }
        return this;
    }

    public CatchCategory classify(String itemName, boolean enchanted) {
        String key = normalise(itemName);

        CatchCategory forced = overrides.get(key);
        if (forced != null) return forced;

        for (Rule rule : rules) {
            CatchCategory category = rule.classify(key, enchanted);
            if (category != null) return category;
        }

        return table.getOrDefault(key, CatchCategory.UNKNOWN);
    }

    /** Lowercase and without the "minecraft:" prefix, so config entries can use either form. */
    private static String normalise(String name) {
        if (name == null) return "";
        String key = name.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
    }
}
