package dev.svmarvinthepa.statsviewer.modules.woodcutting;

import dev.svmarvinthepa.statsviewer.core.repository.WoodcuttingRepository;
import dev.svmarvinthepa.statsviewer.core.util.BlockDropTracker;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registers wood that players cut, trees they fell and tree seeds they plant.
 *
 * <h3>Per block (player_woodcutting)</h3>
 * Every wood block a player breaks is a CUT row; every sapling, propagule or fungus
 * a player places is a PLANT row. Only blocks that a player breaks count
 * ({@link BlockBreakEvent}), never explosions, fire or pistons.
 *
 * <h3>Per tree (player_trees)</h3>
 * Trees are recognised on the first wood block a player breaks:
 * <ol>
 *   <li>all connected blocks of the same wood type are collected (the whole tree);</li>
 *   <li>it is a natural tree when it touches leaves that are not persistent (placed
 *       leaves are persistent; leaves from world generation or a grown sapling are not),
 *       or nether wart blocks for huge fungi. Logs of a house or a pile are not a tree.</li>
 * </ol>
 * That tree is then one session for this player. While the session is open it collects:
 * further logs of the tree, leaves the player breaks nearby, leaves that decay nearby
 * (and the saplings, sticks and apples they drop), and saplings the player plants
 * nearby. The session ends {@code tree-window-seconds} after the last of these and is
 * written as one row.
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Items that drop by physics when a log is removed (cocoa pods, vines) are not
 *       reported by Bukkit and are not counted.</li>
 *   <li>A tree cut in two sessions further apart than the window counts as two trees;
 *       two touching trees of the same wood type count as one.</li>
 *   <li>A tree that grows back on the same spot is a new tree, even within the window:
 *       the first log broken on a position that was already broken starts a new one.
 *       Saplings, sticks and apples that drop while both are open go to the newest.</li>
 *   <li>Leaves around a tree that decay after the window are not counted.</li>
 * </ul>
 */
public class WoodcuttingListener implements Listener {

    public static final String CUT = "CUT";
    public static final String PLANT = "PLANT";

    /** Upper bound for one tree, bigger than any generated tree. */
    private static final int MAX_TREE_BLOCKS = 600;

    /** Leaves decay within this distance of the nearest log. */
    private static final int LEAF_REACH = 7;

    private static final long MIN_MAX_SESSION_AGE_MS = 15 * 60_000L;
    private static final long SWEEP_TICKS = 100L;

    private final Plugin plugin;
    private final WoodcuttingRepository repository;
    private final long windowMs;
    private final long maxSessionAgeMs;
    private final BlockDropTracker<PendingBreak> tracker;

    private final Map<Material, Optional<WoodClassifier.Wood>> woodCache = new EnumMap<>(Material.class);
    private final Map<Material, Optional<String>> seedCache = new EnumMap<>(Material.class);

    // Open trees, oldest first. Main thread only.
    private final List<TreeSession> sessions = new ArrayList<>();

    // Leaf blocks that are decaying in this very tick, so the items they spawn can be linked.
    private final Map<BlockKey, TreeSession> decaying = new HashMap<>();

    private BukkitTask sweeper;

    public WoodcuttingListener(Plugin plugin, WoodcuttingRepository repository, long windowSeconds) {
        this.plugin = plugin;
        this.repository = repository;
        this.windowMs = Math.max(1, windowSeconds) * 1000L;
        this.maxSessionAgeMs = Math.max(MIN_MAX_SESSION_AGE_MS, windowMs * 3);
        this.tracker = new BlockDropTracker<>(plugin, this::complete);
    }

    public void start() {
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    /**
     * Writes the trees that are still open. Called on shutdown, when the write
     * worker is already stopped, so these rows are written directly.
     */
    public void shutdown() {
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }
        for (TreeSession session : sessions) {
            close(session, true);
        }
        sessions.clear();
        decaying.clear();
        tracker.clear();
    }

    /* =========================
       Breaking wood and leaves
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        try {
            Block block = event.getBlock();
            Material type = block.getType();

            WoodClassifier.Wood wood = woodOf(type);
            if (wood != null) {
                onWoodBroken(event.getPlayer(), block, wood);
            } else if (!sessions.isEmpty() && Tag.LEAVES.isTagged(type)) {
                onLeavesBroken(event.getPlayer(), block);
            }
        } catch (Exception e) {
            DebugLogger.error("Woodcutting registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        tracker.drops(event);
    }

    private void onWoodBroken(Player player, Block block, WoodClassifier.Wood wood) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        EventConditions conditions = EventConditions.capture(player, block.getLocation());

        TreeSession session = sessionContaining(block, player.getUniqueId());
        if (session == null) {
            session = startSession(player, block, wood, tool, conditions);
        }
        session.touch();
        session.broken.add(pack(block.getX(), block.getY(), block.getZ()));

        // a structure of placed logs is remembered too, but is not a tree
        TreeSession tree = session.natural ? session : null;
        if (tree != null) {
            tree.logsBroken++;
        }

        tracker.track(block, new PendingBreak(
                tree,
                false,
                player.getUniqueId().toString(),
                player.getName(),
                wood.blockType(),
                wood.woodType(),
                toolName(tool),
                tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0,
                tool.getEnchantmentLevel(Enchantment.FORTUNE),
                conditions,
                block.getX(), block.getY(), block.getZ(),
                LocalDateTime.now()
        ));
    }

    private void onLeavesBroken(Player player, Block block) {
        TreeSession session = sessionNear(block, player.getUniqueId());
        if (session == null) return;

        session.leavesBroken++;
        session.touch();

        tracker.track(block, PendingBreak.forLeaf(session));
    }

    /** Called when the items of a broken block are known. */
    private void complete(PendingBreak pending, BlockDropTracker.Drops drops) {
        TreeSession session = pending.session();

        if (session != null && !session.closed) {
            for (Map.Entry<String, Integer> item : drops.byItem().entrySet()) {
                session.drops.add(item.getKey(), item.getValue());
            }
        }
        if (pending.leaf()) return;

        EventConditions c = pending.conditions();

        DebugLogger.debug(String.format(
                "Woodcutting %s CUT %s tree=%s drops=%d world=%s",
                pending.playerName(), pending.blockType(),
                session == null ? "-" : session.id, drops.total(), c.world()
        ));

        repository.insertBlock(new WoodcuttingRepository.BlockEntry(
                session == null ? null : session.id.toString(),
                pending.playerUuid(),
                pending.playerName(),
                CUT,
                pending.blockType(),
                pending.woodType(),
                pending.tool(),
                pending.silkTouch(),
                pending.fortune(),
                drops.total(),
                c.world(), c.gamemode(), c.biome(), c.weather(), c.timeOfDay(),
                pending.x(), pending.y(), pending.z(),
                pending.at()
        ));
    }

    /* =========================
       Leaves that decay
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (sessions.isEmpty()) return;

        try {
            Block block = event.getBlock();
            TreeSession session = sessionNear(block, null);
            if (session == null) return;

            session.leavesDecayed++;
            session.touch();

            // The decay drops its items right after this event, in the same tick.
            BlockKey key = keyOf(block);
            decaying.put(key, session);
            Bukkit.getScheduler().runTask(plugin, () -> decaying.remove(key, session));
        } catch (Exception e) {
            DebugLogger.error("Woodcutting registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (decaying.isEmpty()) return;

        Location at = event.getLocation();
        World world = at.getWorld();
        if (world == null) return;

        TreeSession session = decaying.get(
                new BlockKey(world.getUID(), at.getBlockX(), at.getBlockY(), at.getBlockZ()));
        if (session == null || session.closed) return;

        ItemStack stack = event.getEntity().getItemStack();
        session.drops.add(stack.getType().name().toLowerCase(Locale.ROOT), stack.getAmount());
    }

    /* =========================
       Planting
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        try {
            Block block = event.getBlockPlaced();
            Material type = block.getType();

            String woodType = seedCache
                    .computeIfAbsent(type, t -> Optional.ofNullable(WoodClassifier.seedType(t.name())))
                    .orElse(null);
            if (woodType == null) return;

            Player player = event.getPlayer();

            // planted near a tree this player felled: that tree was replanted
            TreeSession session = sessionNear(block, player.getUniqueId());
            if (session != null) {
                session.planted++;
                session.touch();
            }

            EventConditions c = EventConditions.capture(player, block.getLocation());

            repository.insertBlock(new WoodcuttingRepository.BlockEntry(
                    session == null ? null : session.id.toString(),
                    player.getUniqueId().toString(),
                    player.getName(),
                    PLANT,
                    type.name().toLowerCase(Locale.ROOT),
                    woodType,
                    null, null, null, null,
                    c.world(), c.gamemode(), c.biome(), c.weather(), c.timeOfDay(),
                    block.getX(), block.getY(), block.getZ(),
                    LocalDateTime.now()
            ));
        } catch (Exception e) {
            DebugLogger.error("Woodcutting registration failed", e);
        }
    }

    /* =========================
       Tree sessions
       ========================= */

    /**
     * An open tree of this player that contains this exact block. A position that was
     * already broken in a tree does not count: a log there now belongs to a tree that
     * grew back (a replanted sapling, bone meal), which is a new tree.
     */
    private TreeSession sessionContaining(Block block, UUID player) {
        UUID world = block.getWorld().getUID();
        long key = pack(block.getX(), block.getY(), block.getZ());

        for (int i = sessions.size() - 1; i >= 0; i--) {
            TreeSession s = sessions.get(i);
            if (!s.closed && s.playerUuid.equals(player) && s.world.equals(world)
                    && s.positions.contains(key) && !s.broken.contains(key)) {
                return s;
            }
        }
        return null;
    }

    /** The most recent open tree within leaf reach of this block; {@code player} null means anyone's. */
    private TreeSession sessionNear(Block block, UUID player) {
        UUID world = block.getWorld().getUID();

        for (int i = sessions.size() - 1; i >= 0; i--) {
            TreeSession s = sessions.get(i);
            if (s.closed || !s.natural || !s.world.equals(world)) continue;
            if (player != null && !s.playerUuid.equals(player)) continue;
            if (s.near(block.getX(), block.getY(), block.getZ())) return s;
        }
        return null;
    }

    /**
     * Collects the connected wood this block belongs to. The result is a natural tree
     * (a row in player_trees later) or not, so that logs of a house or a pile do not count
     * as a felled tree. Either way it is kept for a while, so breaking the rest of a big
     * structure does not search it again for every block.
     */
    private TreeSession startSession(
            Player player, Block origin, WoodClassifier.Wood wood, ItemStack tool, EventConditions conditions
    ) {
        World world = origin.getWorld();

        Set<Long> positions = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        int[] bounds = {origin.getX(), origin.getX(), origin.getY(), origin.getY(), origin.getZ(), origin.getZ()};
        positions.add(pack(origin.getX(), origin.getY(), origin.getZ()));
        queue.add(new int[] {origin.getX(), origin.getY(), origin.getZ()});

        boolean natural = false;

        while (!queue.isEmpty()) {
            int[] p = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        int x = p[0] + dx;
                        int y = p[1] + dy;
                        int z = p[2] + dz;

                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                        Block neighbour = world.getBlockAt(x, y, z);
                        WoodClassifier.Wood other = woodOf(neighbour.getType());

                        if (other != null) {
                            if (other.woodType().equals(wood.woodType())
                                    && positions.size() < MAX_TREE_BLOCKS
                                    && positions.add(pack(x, y, z))) {
                                queue.add(new int[] {x, y, z});
                                bounds[0] = Math.min(bounds[0], x);
                                bounds[1] = Math.max(bounds[1], x);
                                bounds[2] = Math.min(bounds[2], y);
                                bounds[3] = Math.max(bounds[3], y);
                                bounds[4] = Math.min(bounds[4], z);
                                bounds[5] = Math.max(bounds[5], z);
                            }
                        } else if (!natural && isTreeMarker(neighbour)) {
                            natural = true;
                        }
                    }
                }
            }
        }

        TreeSession session = new TreeSession(
                natural,
                world.getUID(),
                player.getUniqueId(),
                player.getName(),
                wood.woodType(),
                positions,
                bounds,
                toolName(tool),
                conditions,
                origin.getX(), origin.getY(), origin.getZ()
        );
        sessions.add(session);
        return session;
    }

    /** Leaves that grew with the tree, or the wart blocks of a huge fungus. */
    private static boolean isTreeMarker(Block block) {
        Material type = block.getType();

        if (type == Material.NETHER_WART_BLOCK || type == Material.WARPED_WART_BLOCK) return true;

        return Tag.LEAVES.isTagged(type)
                && block.getBlockData() instanceof Leaves leaves
                && !leaves.isPersistent();
    }

    /** Writes the trees whose window has ended. */
    private void sweep() {
        try {
            long now = System.currentTimeMillis();

            Iterator<TreeSession> it = sessions.iterator();
            while (it.hasNext()) {
                TreeSession s = it.next();
                if (now - s.lastActivity >= windowMs || now - s.startedAt >= maxSessionAgeMs) {
                    it.remove();
                    close(s, false);
                }
            }
        } catch (Exception e) {
            DebugLogger.error("Woodcutting sweep failed", e);
        }
    }

    private void close(TreeSession s, boolean blocking) {
        if (s.closed) return;
        s.closed = true;

        if (!s.natural) return; // not a tree: nothing to write

        TreeDrops d = s.drops;

        WoodcuttingRepository.TreeEntry entry = new WoodcuttingRepository.TreeEntry(
                s.id.toString(),
                s.playerUuid.toString(),
                s.playerName,
                s.woodType,
                s.positions.size(),
                s.logsBroken,
                s.leavesBroken,
                s.leavesDecayed,
                d.wood(),
                d.leaves(),
                d.saplings(),
                d.sticks(),
                d.apples(),
                d.other(),
                d.otherDetail(),
                s.planted,
                s.toolType,
                s.conditions.world(),
                s.conditions.gamemode(),
                s.conditions.biome(),
                s.conditions.weather(),
                s.conditions.timeOfDay(),
                s.x, s.y, s.z,
                s.lastActivity - s.startedAt,
                s.startedAtTime
        );

        DebugLogger.debug(String.format(
                "Woodcutting tree %s %s felled by %s: logs=%d/%d leaves=%d+%d saplings=%d planted=%d",
                s.id, s.woodType, s.playerName, s.logsBroken, s.positions.size(),
                s.leavesBroken, s.leavesDecayed, d.saplings(), s.planted
        ));

        if (!blocking) {
            repository.insertTree(entry);
            return;
        }
        try {
            repository.insertTreeBlocking(entry);
        } catch (Exception e) {
            DebugLogger.error("Could not write tree " + s.id + " on shutdown", e);
        }
    }

    /* =========================
       Helpers
       ========================= */

    private WoodClassifier.Wood woodOf(Material type) {
        return woodCache
                .computeIfAbsent(type, t -> Optional.ofNullable(WoodClassifier.classify(t.name())))
                .orElse(null);
    }

    private static String toolName(ItemStack tool) {
        return tool == null || tool.getType().isAir()
                ? "hand"
                : tool.getType().name().toLowerCase(Locale.ROOT);
    }

    private static BlockKey keyOf(Block block) {
        return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    /** Block position in one long (26 bits each for x and z, 12 bits for y). */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) ((y + 2048) & 0xFFF);
    }

    private record BlockKey(UUID world, int x, int y, int z) {}

    /** A broken block waiting for its drops. For leaves only the session is filled in. */
    private record PendingBreak(
            TreeSession session,
            boolean leaf,
            String playerUuid,
            String playerName,
            String blockType,
            String woodType,
            String tool,
            boolean silkTouch,
            int fortune,
            EventConditions conditions,
            int x,
            int y,
            int z,
            LocalDateTime at
    ) {
        static PendingBreak forLeaf(TreeSession session) {
            return new PendingBreak(session, true, null, null, null, null, null,
                    false, 0, null, 0, 0, 0, null);
        }
    }

    /** One tree a player is felling. */
    private static final class TreeSession {
        final UUID id = UUID.randomUUID();
        final boolean natural;
        final UUID world;
        final UUID playerUuid;
        final String playerName;
        final String woodType;
        final Set<Long> positions;
        final Set<Long> broken = new HashSet<>();
        final int minX, maxX, minY, maxY, minZ, maxZ;
        final String toolType;
        final EventConditions conditions;
        final int x, y, z;
        final long startedAt = System.currentTimeMillis();
        final LocalDateTime startedAtTime = LocalDateTime.now();
        final TreeDrops drops = new TreeDrops();

        long lastActivity = startedAt;
        int logsBroken;
        int leavesBroken;
        int leavesDecayed;
        int planted;
        boolean closed;

        TreeSession(
                boolean natural, UUID world, UUID playerUuid, String playerName, String woodType,
                Set<Long> positions, int[] bounds, String toolType, EventConditions conditions,
                int x, int y, int z
        ) {
            this.natural = natural;
            this.world = world;
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.woodType = woodType;
            this.positions = positions;
            this.minX = bounds[0];
            this.maxX = bounds[1];
            this.minY = bounds[2];
            this.maxY = bounds[3];
            this.minZ = bounds[4];
            this.maxZ = bounds[5];
            this.toolType = toolType;
            this.conditions = conditions;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void touch() {
            lastActivity = System.currentTimeMillis();
        }

        /** Within leaf reach of the tree's logs. */
        boolean near(int bx, int by, int bz) {
            return bx >= minX - LEAF_REACH && bx <= maxX + LEAF_REACH
                    && by >= minY - LEAF_REACH && by <= maxY + LEAF_REACH
                    && bz >= minZ - LEAF_REACH && bz <= maxZ + LEAF_REACH;
        }
    }
}
