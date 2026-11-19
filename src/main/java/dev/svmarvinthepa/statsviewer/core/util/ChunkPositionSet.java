package dev.svmarvinthepa.statsviewer.core.util;

import java.util.Arrays;

/**
 * Block positions inside one chunk, packed into ints and kept in a small array.
 * The array is what {@link PlacedBlocks} stores in the chunk's persistent data.
 * <p>
 * All methods are pure and never change the array they are given.
 */
public final class ChunkPositionSet {

    private ChunkPositionSet() {}

    /** Packs a block position (any world coordinates) into one int: height, x and z inside the chunk. */
    public static int encode(int x, int y, int z) {
        // y + 2048 stays within 0..4095 for every world height, so the result is never negative
        return ((y + 2048) << 8) | ((x & 15) << 4) | (z & 15);
    }

    public static boolean contains(int[] set, int code) {
        if (set == null) return false;
        for (int value : set) {
            if (value == code) return true;
        }
        return false;
    }

    /**
     * @return the set with {@code code} added; the same array when it was already in it.
     *         At {@code max} entries the oldest one is dropped.
     */
    public static int[] add(int[] set, int code, int max) {
        if (set == null) return new int[] {code};
        if (contains(set, code)) return set;

        if (set.length >= max) {
            int[] next = Arrays.copyOfRange(set, set.length - max + 1, set.length + 1);
            next[next.length - 1] = code;
            return next;
        }

        int[] next = Arrays.copyOf(set, set.length + 1);
        next[set.length] = code;
        return next;
    }

    /** @return the set without {@code code}; the same array when it was not in it, null when it becomes empty */
    public static int[] remove(int[] set, int code) {
        if (set == null || !contains(set, code)) return set;
        if (set.length == 1) return null;

        int[] next = new int[set.length - 1];
        int i = 0;
        for (int value : set) {
            if (value != code) next[i++] = value;
        }
        return next;
    }
}
