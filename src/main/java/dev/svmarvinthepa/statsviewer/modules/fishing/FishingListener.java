package dev.svmarvinthepa.statsviewer.modules.fishing;

import dev.svmarvinthepa.statsviewer.core.repository.FishingRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers fishing from {@link PlayerFishEvent}.
 * <p>
 * Every cast (one bobber) gets a {@code cast_id}. The rows of one cast share
 * it, so a cast can be followed from CAST to its outcome:
 * <ul>
 *   <li>CAST       the line was cast</li>
 *   <li>CAUGHT     an item came out of the water</li>
 *   <li>FAILED     a bite was missed (the bobber stays in the water: more rows can follow)</li>
 *   <li>REELED_IN  the line was reeled in without a catch</li>
 *   <li>IN_GROUND  the bobber landed on solid ground (can never catch anything;
 *                  the cast stays open until the line is reeled in)</li>
 *   <li>ENTITY     the bobber hooked an entity (mob, player, boat...): not fishing,
 *                  kept apart so it can be filtered out</li>
 *   <li>CANCELLED  another plugin cancelled the event</li>
 * </ul>
 * CAUGHT, ENTITY and REELED_IN end a cast. A cast without such a row was
 * abandoned (item switched, logged off, teleported away): vanilla removes the
 * bobber without an event.
 * <p>
 * Duplicates are prevented per bobber: a cast is registered once, and once a
 * cast has ended further outcome events for the same bobber are ignored. The one
 * exception is a loot table that drops several items in one catch, which fires
 * one CAUGHT_FISH per item; each distinct item gets its own CAUGHT row.
 */
public class FishingListener implements Listener {

    public static final String CAST = "CAST";
    public static final String CAUGHT = "CAUGHT";
    public static final String ENTITY = "ENTITY";
    public static final String FAILED = "FAILED";
    public static final String REELED_IN = "REELED_IN";
    public static final String IN_GROUND = "IN_GROUND";
    public static final String CANCELLED = "CANCELLED";

    private static final String ROD_FALLBACK = "fishing_rod";

    private final FishingRepository repository;
    private final FishingLootClassifier classifier;
    private final boolean recordEntityHooks;

    // Player UUID -> latest cast. Touched on the main thread only.
    private final Map<UUID, Cast> casts = new HashMap<>();

    public FishingListener(
            FishingRepository repository,
            FishingLootClassifier classifier,
            boolean recordEntityHooks
    ) {
        this.repository = repository;
        this.classifier = classifier;
        this.recordEntityHooks = recordEntityHooks;
    }

    /* =========================
       Events
       ========================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        try {
            switch (event.getState()) {
                case FISHING -> onCast(event);
                case CAUGHT_FISH -> onCaughtItem(event);
                case CAUGHT_ENTITY -> onCaughtEntity(event);
                case FAILED_ATTEMPT -> onFailedAttempt(event);
                case REEL_IN -> onReelIn(event);
                case IN_GROUND -> onInGround(event);
                default -> {
                    // BITE and LURED carry no outcome; states added later are ignored
                }
            }
        } catch (Exception e) {
            DebugLogger.error("Fishing registration failed", e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        casts.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        casts.clear();
    }

    /* =========================
       State handlers
       ========================= */

    private void onCast(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();
        Location where = hook.getLocation();

        if (event.isCancelled()) {
            // no bobber was launched, so there is no cast to track
            Cast blocked = newCast(player, hook, event.getHand(), -1);
            record(player, blocked, CANCELLED, where, Outcome.NONE);
            return;
        }

        Cast current = casts.get(player.getUniqueId());
        if (current != null && current.hookId.equals(hook.getUniqueId())) {
            return; // this bobber was registered already
        }

        Cast cast = newCast(player, hook, event.getHand(), System.currentTimeMillis());
        casts.put(player.getUniqueId(), cast);

        record(player, cast, CAST, where, Outcome.NONE);
    }

    private void onCaughtItem(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Cast cast = castFor(event);
        Location where = event.getHook().getLocation();

        if (event.isCancelled()) {
            if (!cast.closed) {
                cast.closed = true;
                record(player, cast, CANCELLED, where, Outcome.NONE);
            }
            return;
        }

        if (!(event.getCaught() instanceof Item item)) {
            // CAUGHT_FISH should always carry an item; register the catch anyway
            if (!cast.closed) {
                cast.closed = true;
                record(player, cast, CAUGHT, where, Outcome.NONE);
            }
            return;
        }

        if (!cast.caughtItems.add(item.getUniqueId())) {
            return; // this exact item was registered already
        }

        ItemStack stack = item.getItemStack();
        String itemType = stack.getType().name().toLowerCase(Locale.ROOT);
        CatchCategory category = classifier.classify(itemType, !stack.getEnchantments().isEmpty());

        cast.closed = true;
        record(player, cast, CAUGHT, where,
                new Outcome(itemType, category.name(), stack.getAmount(), null));
    }

    private void onCaughtEntity(PlayerFishEvent event) {
        Player player = event.getPlayer();
        Cast cast = castFor(event);
        if (cast.closed) return;

        cast.closed = true;
        Location where = event.getHook().getLocation();

        if (event.isCancelled()) {
            record(player, cast, CANCELLED, where, Outcome.NONE);
            return;
        }
        if (!recordEntityHooks) return;

        Entity caught = event.getCaught();
        String entityType = caught == null ? null : caught.getType().name().toLowerCase(Locale.ROOT);

        record(player, cast, ENTITY, where, new Outcome(null, null, null, entityType));
    }

    private void onFailedAttempt(PlayerFishEvent event) {
        if (event.isCancelled()) return;

        Cast cast = castFor(event);
        if (cast.closed) return;

        // A missed bite does not end the cast: the bobber stays in the water.
        record(event.getPlayer(), cast, FAILED, event.getHook().getLocation(), Outcome.NONE);
    }

    /**
     * The bobber landed on solid ground: this cast can never catch anything.
     * Registered once, and the cast stays open because the player still has to
     * reel in (which then gives a REELED_IN row).
     */
    private void onInGround(PlayerFishEvent event) {
        if (event.isCancelled()) return;

        Cast cast = castFor(event);
        if (cast.closed || cast.groundRecorded) return;

        cast.groundRecorded = true;
        record(event.getPlayer(), cast, IN_GROUND, event.getHook().getLocation(), Outcome.NONE);
    }

    private void onReelIn(PlayerFishEvent event) {
        Cast cast = castFor(event);
        if (cast.closed) return;

        cast.closed = true;
        record(event.getPlayer(), cast, event.isCancelled() ? CANCELLED : REELED_IN,
                event.getHook().getLocation(), Outcome.NONE);
    }

    /* =========================
       Cast tracking
       ========================= */

    /** The tracked cast of this bobber, or a new one when this bobber was never seen being cast. */
    private Cast castFor(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();

        Cast cast = casts.get(player.getUniqueId());
        if (cast != null && cast.hookId.equals(hook.getUniqueId())) {
            return cast;
        }

        // cast before the module was enabled/reloaded, or a bobber another plugin spawned
        Cast orphan = newCast(player, hook, event.getHand(), -1);
        casts.put(player.getUniqueId(), orphan);
        return orphan;
    }

    /** The rod is read once per cast: FAILED_ATTEMPT has no hand, and a swap mid-cast ends the bobber anyway. */
    private Cast newCast(Player player, FishHook hook, EquipmentSlot hand, long startedAt) {
        ItemStack rod = rodOf(player, hand);

        return new Cast(
                hook.getUniqueId(),
                startedAt,
                rod == null ? ROD_FALLBACK : rod.getType().name().toLowerCase(Locale.ROOT),
                rod == null ? 0 : rod.getEnchantmentLevel(Enchantment.LURE),
                rod == null ? 0 : rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA)
        );
    }

    private static ItemStack rodOf(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();

        if (hand != null) {
            ItemStack stack = inventory.getItem(hand);
            if (isRod(stack)) return stack;
        }
        if (isRod(inventory.getItemInMainHand())) return inventory.getItemInMainHand();
        if (isRod(inventory.getItemInOffHand())) return inventory.getItemInOffHand();
        return null;
    }

    private static boolean isRod(ItemStack stack) {
        return stack != null && stack.getType() == Material.FISHING_ROD;
    }

    /* =========================
       Registration
       ========================= */

    private void record(Player player, Cast cast, String result, Location where, Outcome outcome) {
        // read everything now, the world and the player can change before the async write runs
        EventConditions conditions = EventConditions.capture(player, where);

        Long waitTimeMs = (cast.startedAt > 0 && !CAST.equals(result))
                ? Long.valueOf(System.currentTimeMillis() - cast.startedAt)
                : null;

        DebugLogger.debug(String.format(
                "Fishing %s %s cast=%s item=%s entity=%s world=%s",
                player.getName(), result, cast.id, outcome.itemType(), outcome.entityType(),
                conditions.world()
        ));

        repository.insert(new FishingRepository.Entry(
                cast.id.toString(),
                player.getUniqueId().toString(),
                player.getName(),
                result,
                outcome.itemType(),
                outcome.category(),
                outcome.amount(),
                outcome.entityType(),
                cast.rodType,
                cast.lureLevel,
                cast.luckLevel,
                waitTimeMs,
                conditions.world(),
                conditions.gamemode(),
                conditions.biome(),
                conditions.weather(),
                conditions.timeOfDay(),
                where.getX(),
                where.getY(),
                where.getZ(),
                LocalDateTime.now()
        ));
    }

    /** What came out of the water; every field is null when it does not apply. */
    private record Outcome(String itemType, String category, Integer amount, String entityType) {
        static final Outcome NONE = new Outcome(null, null, null, null);
    }

    private static final class Cast {
        final UUID id = UUID.randomUUID();
        final UUID hookId;
        final long startedAt;          // -1 when the moment of the cast is unknown
        final String rodType;
        final int lureLevel;
        final int luckLevel;
        final Set<UUID> caughtItems = new HashSet<>();
        boolean closed;
        boolean groundRecorded;

        Cast(UUID hookId, long startedAt, String rodType, int lureLevel, int luckLevel) {
            this.hookId = hookId;
            this.startedAt = startedAt;
            this.rodType = rodType;
            this.lureLevel = lureLevel;
            this.luckLevel = luckLevel;
        }
    }
}
