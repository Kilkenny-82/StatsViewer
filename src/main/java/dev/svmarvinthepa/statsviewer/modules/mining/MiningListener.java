package dev.svmarvinthepa.statsviewer.modules.mining;

import dev.svmarvinthepa.statsviewer.core.repository.MiningRepository;
import dev.svmarvinthepa.statsviewer.core.util.BlockDropTracker;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registers minerals that players mine.
 * <p>
 * Only {@link BlockBreakEvent} is used to decide what counts, so only blocks a
 * player breaks are registered: explosions, pistons, fluids and other physics
 * never fire it. The player who breaks the block gets the credit, not whoever
 * picks the drops up.
 * <p>
 * The real drops (fortune, silk touch and plugins included) come from
 * {@link BlockDropItemEvent}; see {@link BlockDropTracker}.
 */
public class MiningListener implements Listener {

    private final MiningRepository repository;
    private final BlockDropTracker<PendingMine> tracker;

    // Material -> classification, filled lazily so a block break costs one map lookup.
    private final Map<Material, Optional<MineralClassifier.Mineral>> classified =
            new EnumMap<>(Material.class);

    public MiningListener(Plugin plugin, MiningRepository repository) {
        this.repository = repository;
        this.tracker = new BlockDropTracker<>(plugin, this::complete);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Block block = event.getBlock();

            MineralClassifier.Mineral mineral = classified
                    .computeIfAbsent(block.getType(),
                            type -> Optional.ofNullable(MineralClassifier.classify(type.name())))
                    .orElse(null);
            if (mineral == null) return;

            Player player = event.getPlayer();
            ItemStack tool = player.getInventory().getItemInMainHand();
            Location where = block.getLocation();

            tracker.track(block, new PendingMine(
                    player.getUniqueId().toString(),
                    player.getName(),
                    mineral,
                    tool.getType().isAir() ? "hand" : tool.getType().name().toLowerCase(Locale.ROOT),
                    tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0,
                    tool.getEnchantmentLevel(Enchantment.FORTUNE),
                    EventConditions.capture(player, where),
                    block.getX(),
                    block.getY(),
                    block.getZ(),
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            DebugLogger.error("Mining registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        tracker.drops(event);
    }

    public void clear() {
        tracker.clear();
        classified.clear();
    }

    private void complete(PendingMine mine, BlockDropTracker.Drops drops) {
        DebugLogger.debug(String.format(
                "Mining %s %s tool=%s silk=%s fortune=%d drops=%d world=%s",
                mine.playerName(), mine.mineral().oreBlock(), mine.tool(), mine.silkTouch(),
                mine.fortune(), drops.total(), mine.conditions().world()
        ));

        EventConditions c = mine.conditions();

        repository.insert(new MiningRepository.Entry(
                mine.playerUuid(),
                mine.playerName(),
                mine.mineral().type(),
                mine.mineral().host(),
                mine.mineral().oreBlock(),
                mine.tool(),
                mine.silkTouch(),
                mine.fortune(),
                drops.total(),
                c.world(),
                c.gamemode(),
                c.biome(),
                c.weather(),
                c.timeOfDay(),
                mine.x(),
                mine.y(),
                mine.z(),
                mine.at()
        ));
    }

    /** Everything known at the moment of the break, waiting for the drops. */
    private record PendingMine(
            String playerUuid,
            String playerName,
            MineralClassifier.Mineral mineral,
            String tool,
            boolean silkTouch,
            int fortune,
            EventConditions conditions,
            int x,
            int y,
            int z,
            LocalDateTime at
    ) {}
}
