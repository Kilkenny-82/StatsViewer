package dev.svmarvinthepa.statsviewer.modules.farming;

import dev.svmarvinthepa.statsviewer.core.repository.FarmingRepository;
import dev.svmarvinthepa.statsviewer.core.util.BlockDropTracker;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Registers crops that players harvest, in two ways:
 * <ul>
 *   <li>breaking a crop block ({@link BlockBreakEvent}): counted only when it is a
 *       real harvest, so a half-grown wheat plant that is broken is ignored. The
 *       items that really dropped come from {@link BlockDropItemEvent}, see
 *       {@link BlockDropTracker};</li>
 *   <li>right-click harvesting ({@link PlayerHarvestBlockEvent}: sweet berries,
 *       glow berries), which breaks nothing and reports its items itself.</li>
 * </ul>
 * Explosions, pistons, water and other non-player breaks never fire these events.
 * <p>
 * Limitation: a stalk that collapses because the block under it was broken
 * (sugar cane, cactus, bamboo, kelp) drops its items by physics, not through the
 * broken block, so only the block the player broke is counted.
 */
public class CropListener implements Listener {

    public static final String HARVEST = "HARVEST";

    /** Sweet berries can be picked from this growth stage on. */
    private static final int BERRY_MIN_AGE = 2;

    private final FarmingRepository repository;
    private final BlockDropTracker<PendingHarvest> tracker;

    public CropListener(Plugin plugin, FarmingRepository repository) {
        this.repository = repository;
        this.tracker = new BlockDropTracker<>(plugin, this::complete);
    }

    /* =========================
       Breaking crops
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Block block = event.getBlock();

            FarmingCrops.Crop crop = FarmingCrops.forBlock(block.getType().name());
            if (crop == null) return;

            BlockData data = block.getBlockData();
            if (!isHarvestable(crop, data)) return;

            Player player = event.getPlayer();
            ItemStack tool = player.getInventory().getItemInMainHand();

            Integer age = null;
            Integer maxAge = null;
            if (data instanceof org.bukkit.block.data.Ageable ageable
                    && crop.harvest() != FarmingCrops.Harvest.ALWAYS
                    && crop.harvest() != FarmingCrops.Harvest.CAVE_BERRIES) {
                age = ageable.getAge();
                maxAge = ageable.getMaximumAge();
            }

            tracker.track(block, new PendingHarvest(
                    player.getUniqueId().toString(),
                    player.getName(),
                    crop,
                    age,
                    maxAge,
                    toolName(tool),
                    tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0,
                    tool.getEnchantmentLevel(Enchantment.FORTUNE),
                    EventConditions.capture(player, block.getLocation()),
                    block.getLocation(),
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            DebugLogger.error("Farming registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        tracker.drops(event);
    }

    private static boolean isHarvestable(FarmingCrops.Crop crop, BlockData data) {
        return switch (crop.harvest()) {
            case ALWAYS -> true;
            case MATURE -> data instanceof org.bukkit.block.data.Ageable a
                    && a.getAge() >= a.getMaximumAge();
            case BERRIES -> data instanceof org.bukkit.block.data.Ageable a
                    && a.getAge() >= BERRY_MIN_AGE;
            case CAVE_BERRIES -> data instanceof CaveVinesPlant v && v.hasBerries();
        };
    }

    private void complete(PendingHarvest harvest, BlockDropTracker.Drops drops) {
        EventConditions c = harvest.conditions();
        Location at = harvest.location();

        DebugLogger.debug(String.format(
                "Farming %s HARVEST %s total=%d product=%d world=%s",
                harvest.playerName(), harvest.crop().type(), drops.total(),
                drops.countOf(harvest.crop().products()), c.world()
        ));

        repository.insert(new FarmingRepository.Entry(
                harvest.playerUuid(),
                harvest.playerName(),
                HARVEST,
                harvest.crop().type(),
                null,
                drops.total(),
                drops.countOf(harvest.crop().products()),
                harvest.age(),
                harvest.maxAge(),
                harvest.tool(),
                harvest.silkTouch(),
                harvest.fortune(),
                c.world(), c.gamemode(), c.biome(), c.weather(), c.timeOfDay(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                harvest.at()
        ));
    }

    /* =========================
       Right-click harvesting
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        try {
            Player player = event.getPlayer();
            Block block = event.getHarvestedBlock();
            String blockName = block.getType().name();

            FarmingCrops.Crop crop = FarmingCrops.forBlock(blockName);
            String type = crop != null ? crop.type() : FarmingCrops.fallbackType(blockName);
            Set<String> products = crop != null ? crop.products() : Set.of();

            int total = 0;
            int product = 0;
            for (ItemStack stack : event.getItemsHarvested()) {
                total += stack.getAmount();
                if (products.contains(stack.getType().name().toLowerCase(Locale.ROOT))) {
                    product += stack.getAmount();
                }
            }

            Integer age = null;
            Integer maxAge = null;
            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                    && !(block.getBlockData() instanceof CaveVinesPlant)) {
                age = ageable.getAge();
                maxAge = ageable.getMaximumAge();
            }

            ItemStack tool = event.getHand() == null
                    ? player.getInventory().getItemInMainHand()
                    : player.getInventory().getItem(event.getHand());
            Location where = block.getLocation();
            EventConditions c = EventConditions.capture(player, where);

            repository.insert(new FarmingRepository.Entry(
                    player.getUniqueId().toString(),
                    player.getName(),
                    HARVEST,
                    type,
                    null,
                    total,
                    product,
                    age,
                    maxAge,
                    toolName(tool),
                    false,
                    0,
                    c.world(), c.gamemode(), c.biome(), c.weather(), c.timeOfDay(),
                    where.getBlockX(), where.getBlockY(), where.getBlockZ(),
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            DebugLogger.error("Farming registration failed", e);
        }
    }

    public void clear() {
        tracker.clear();
    }

    private static String toolName(ItemStack tool) {
        return tool == null || tool.getType().isAir()
                ? "hand"
                : tool.getType().name().toLowerCase(Locale.ROOT);
    }

    /** Everything known at the moment of the break, waiting for the drops. */
    private record PendingHarvest(
            String playerUuid,
            String playerName,
            FarmingCrops.Crop crop,
            Integer age,
            Integer maxAge,
            String tool,
            boolean silkTouch,
            int fortune,
            EventConditions conditions,
            Location location,
            LocalDateTime at
    ) {}
}
