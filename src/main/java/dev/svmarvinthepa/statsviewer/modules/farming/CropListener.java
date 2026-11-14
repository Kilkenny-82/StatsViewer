package dev.svmarvinthepa.statsviewer.modules.farming;

import dev.svmarvinthepa.statsviewer.core.repository.FarmingRepository;
import dev.svmarvinthepa.statsviewer.core.util.BlockDropTracker;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 *   <li>breaking a crop block ({@link BlockBreakEvent}). Young plants are registered
 *       too, with their growth stage in age / max_age (switch off with
 *       {@code farming.include-immature: false} to count only full-grown crops).
 *       The items that really dropped come from {@link BlockDropItemEvent}, see
 *       {@link BlockDropTracker};</li>
 *   <li>right-click harvesting ({@link PlayerHarvestBlockEvent}: sweet berries,
 *       glow berries), which breaks nothing and reports its items itself.</li>
 * </ul>
 * Explosions, pistons, water and other non-player breaks never fire these events.
 * <p>
 * Stalks (sugar cane, cactus, bamboo, kelp): when a block is broken, everything above
 * it collapses and drops its items by physics, without any event. Those items are added
 * to the harvest by counting the blocks of the same crop stacked above the broken block
 * (one item each), so the row holds the total of the whole stalk. It is a count of
 * blocks, not of dropped items: nothing is added in creative mode.
 */
public class CropListener implements Listener {

    public static final String HARVEST = "HARVEST";

    /** Sweet berries can be picked from this growth stage on. */
    private static final int BERRY_MIN_AGE = 2;

    /** Upper bound when walking up a stalk, above the world height of any dimension. */
    private static final int MAX_STALK_HEIGHT = 512;

    private final FarmingRepository repository;
    private final boolean includeImmature;
    private final BlockDropTracker<PendingHarvest> tracker;

    public CropListener(Plugin plugin, FarmingRepository repository, boolean includeImmature) {
        this.repository = repository;
        this.includeImmature = includeImmature;
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

            int collapsed = crop.stalk() && player.getGameMode() != GameMode.CREATIVE
                    ? stackedAbove(block, crop)
                    : 0;

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
                    collapsed,
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

    private boolean isHarvestable(FarmingCrops.Crop crop, BlockData data) {
        return switch (crop.harvest()) {
            case ALWAYS -> true;
            case MATURE -> includeImmature || (data instanceof org.bukkit.block.data.Ageable a
                    && a.getAge() >= a.getMaximumAge());
            case BERRIES -> includeImmature || (data instanceof org.bukkit.block.data.Ageable a
                    && a.getAge() >= BERRY_MIN_AGE);
            case CAVE_BERRIES -> data instanceof CaveVinesPlant v && v.hasBerries();
        };
    }

    /** Number of blocks of the same crop stacked directly above this block. */
    private static int stackedAbove(Block block, FarmingCrops.Crop crop) {
        int count = 0;
        Block above = block.getRelative(BlockFace.UP);

        while (count < MAX_STALK_HEIGHT) {
            FarmingCrops.Crop other = FarmingCrops.forBlock(above.getType().name());
            if (other == null || !other.type().equals(crop.type())) break;

            count++;
            above = above.getRelative(BlockFace.UP);
        }
        return count;
    }

    private void complete(PendingHarvest harvest, BlockDropTracker.Drops drops) {
        EventConditions c = harvest.conditions();
        Location at = harvest.location();

        // the broken block's real drops plus the collapsed part of a stalk (one item per block)
        int harvested = drops.total() + harvest.collapsed();
        int product = drops.countOf(harvest.crop().products()) + harvest.collapsed();

        DebugLogger.debug(String.format(
                "Farming %s HARVEST %s total=%d product=%d collapsed=%d world=%s",
                harvest.playerName(), harvest.crop().type(), harvested, product,
                harvest.collapsed(), c.world()
        ));

        repository.insert(new FarmingRepository.Entry(
                harvest.playerUuid(),
                harvest.playerName(),
                HARVEST,
                harvest.crop().type(),
                null,
                harvested,
                product,
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
            int collapsed,
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
