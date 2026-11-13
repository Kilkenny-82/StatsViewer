package dev.svmarvinthepa.statsviewer.modules.fishing;

/**
 * The three categories of the vanilla fishing loot table, plus UNKNOWN for
 * items that no rule and no table knows (yet). UNKNOWN catches are still
 * stored, so new loot shows up in the data instead of failing the insert.
 */
public enum CatchCategory {
    FISH,
    TREASURE,
    JUNK,
    UNKNOWN
}
