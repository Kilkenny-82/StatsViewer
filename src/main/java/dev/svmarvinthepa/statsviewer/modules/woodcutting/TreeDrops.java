package dev.svmarvinthepa.statsviewer.modules.woodcutting;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything one felled tree yielded, sorted into the categories the statistics
 * care about. Items that fit no category are kept per item in {@link #otherDetail()},
 * so unexpected drops (bee nests, cocoa, anything new) are not lost.
 * <p>
 * This class deliberately has no Bukkit dependency.
 */
public final class TreeDrops {

    /** Max length of {@link #otherDetail()}: the column is limited, so it is cut at a whole entry. */
    private static final int MAX_DETAIL_LENGTH = 250;

    private int wood;
    private int leaves;
    private int saplings;
    private int sticks;
    private int apples;
    private int other;
    private final Map<String, Integer> otherByItem = new TreeMap<>();

    /** @param itemName lowercase Minecraft item name, e.g. "oak_sapling" */
    public void add(String itemName, int amount) {
        if (itemName == null || amount <= 0) return;

        String item = itemName.toLowerCase(Locale.ROOT);

        if (item.endsWith("_sapling") || item.endsWith("_propagule")) {
            saplings += amount;
        } else if (item.equals("stick")) {
            sticks += amount;
        } else if (item.equals("apple")) {
            apples += amount;
        } else if (item.endsWith("_leaves")) {
            leaves += amount;
        } else if (WoodClassifier.isWood(item.toUpperCase(Locale.ROOT))) {
            wood += amount;
        } else {
            other += amount;
            otherByItem.merge(item, amount, Integer::sum);
        }
    }

    public int wood() { return wood; }
    public int leaves() { return leaves; }
    public int saplings() { return saplings; }
    public int sticks() { return sticks; }
    public int apples() { return apples; }
    public int other() { return other; }

    /** "bee_nest=1,cocoa_beans=3", or null when nothing unexpected dropped. */
    public String otherDetail() {
        if (otherByItem.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : otherByItem.entrySet()) {
            String part = e.getKey() + "=" + e.getValue();
            if (sb.length() + part.length() + 1 > MAX_DETAIL_LENGTH) break;
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        return sb.toString();
    }
}
