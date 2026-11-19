package dev.svmarvinthepa.statsviewer.core.util;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Remembers which blocks a player placed, so a module can leave them out of its
 * statistics (a log put back to climb a tree is not a tree that was cut).
 * <p>
 * The positions are stored in the persistent data of the chunk itself, so they
 * survive restarts and are removed with the chunk's data. At most
 * {@value #MAX_PER_CHUNK} positions are kept per chunk; beyond that the oldest are
 * forgotten. Positions are not cleaned up when a block disappears without a player
 * breaking it (explosion, fire), so a caller should {@link #remove} them when it
 * knows better, for instance when a tree grows there.
 * <p>
 * Use one instance per kind of block ({@code name}), so sets do not mix.
 * Main thread only.
 */
public final class PlacedBlocks {

    private static final int MAX_PER_CHUNK = 8192;

    private final NamespacedKey key;

    public PlacedBlocks(Plugin plugin, String name) {
        this.key = new NamespacedKey(plugin, name);
    }

    public boolean contains(Block block) {
        int[] set = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        return ChunkPositionSet.contains(set, code(block));
    }

    public void add(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();

        int[] set = data.get(key, PersistentDataType.INTEGER_ARRAY);
        int[] updated = ChunkPositionSet.add(set, code(block), MAX_PER_CHUNK);

        if (updated != set) {
            data.set(key, PersistentDataType.INTEGER_ARRAY, updated);
        }
    }

    /** @return true when the block was marked as placed */
    public boolean remove(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();

        int[] set = data.get(key, PersistentDataType.INTEGER_ARRAY);
        if (!ChunkPositionSet.contains(set, code(block))) return false;

        int[] updated = ChunkPositionSet.remove(set, code(block));
        if (updated == null) {
            data.remove(key);
        } else {
            data.set(key, PersistentDataType.INTEGER_ARRAY, updated);
        }
        return true;
    }

    private static int code(Block block) {
        return ChunkPositionSet.encode(block.getX(), block.getY(), block.getZ());
    }
}
