package dev.svmarvinthepa.statsviewer.core.util;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Couples a block a player broke to the items that really dropped from it.
 * <p>
 * {@code BlockBreakEvent} knows who broke what, but not what dropped;
 * {@code BlockDropItemEvent} knows the real drops (fortune, silk touch and
 * plugins included) but fires after it. A module calls {@link #track} from its
 * break handler and {@link #drops} from its drop handler, and gets a single
 * callback with both.
 * <p>
 * When no drop event follows (creative mode, drops disabled, drop event
 * cancelled) the callback still comes, one tick later, with zero drops.
 * Everything runs on the main thread.
 */
public final class BlockDropTracker<T> {

    /** What dropped: the total item count and the count per item name (lowercase). */
    public record Drops(int total, Map<String, Integer> byItem) {

        public static final Drops NONE = new Drops(0, Map.of());

        /** Number of dropped items whose name is in {@code itemNames}. */
        public int countOf(Set<String> itemNames) {
            int sum = 0;
            for (Map.Entry<String, Integer> entry : byItem.entrySet()) {
                if (itemNames.contains(entry.getKey())) sum += entry.getValue();
            }
            return sum;
        }
    }

    @FunctionalInterface
    public interface Completion<T> {
        void complete(T context, Drops drops);
    }

    private record BlockKey(UUID world, int x, int y, int z) {}

    private final Plugin plugin;
    private final Completion<T> completion;
    private final Map<BlockKey, T> pending = new HashMap<>();

    public BlockDropTracker(Plugin plugin, Completion<T> completion) {
        this.plugin = plugin;
        this.completion = completion;
    }

    /** Call from the break handler for a block that should be registered. */
    public void track(Block block, T context) {
        BlockKey key = keyOf(block);

        T replaced = pending.put(key, context);
        if (replaced != null) {
            // the same position broken twice before its drops arrived: don't lose the first
            finish(replaced, Drops.NONE);
        }

        // Fallback for breaks that never produce a BlockDropItemEvent.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.get(key) == context) {
                pending.remove(key);
                finish(context, Drops.NONE);
            }
        });
    }

    /** Call from the drop handler. Ignores blocks that were not tracked. */
    public void drops(BlockDropItemEvent event) {
        T context = pending.remove(keyOf(event.getBlock()));
        if (context == null) return;

        int total = 0;
        Map<String, Integer> byItem = new HashMap<>();

        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            int amount = stack.getAmount();
            if (amount <= 0) continue;

            total += amount;
            byItem.merge(stack.getType().name().toLowerCase(Locale.ROOT), amount, Integer::sum);
        }

        finish(context, new Drops(total, byItem));
    }

    public void clear() {
        pending.clear();
    }

    private void finish(T context, Drops drops) {
        try {
            completion.complete(context, drops);
        } catch (Exception e) {
            DebugLogger.error("Block drop registration failed", e);
        }
    }

    private static BlockKey keyOf(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
