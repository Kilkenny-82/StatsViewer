package dev.svmarvinthepa.statsviewer.modules.farming;

import dev.svmarvinthepa.statsviewer.core.repository.FarmingRepository;
import dev.svmarvinthepa.statsviewer.core.util.DebugLogger;
import dev.svmarvinthepa.statsviewer.core.util.EventConditions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Registers livestock actions of players: breeding, shearing and milking.
 * <ul>
 *   <li>BREED  only when a player did the breeding ({@link EntityBreedEvent#getBreeder()});
 *              animals that breed on their own are not a player action. The animal
 *              type is the baby that was born.</li>
 *   <li>SHEAR  by a player (dispensers do not count). Amount = items the shearing dropped.</li>
 *   <li>MILK   an empty bucket used on an adult cow, mooshroom or goat; amount = 1 milk bucket.
 *              A bowl on an adult mooshroom is registered as MILK too (mushroom stew).</li>
 * </ul>
 * Not covered: honey/honeycomb from beehives and eggs, which have no player action
 * to hook onto.
 */
public class LivestockListener implements Listener {

    public static final String BREED = "BREED";
    public static final String SHEAR = "SHEAR";
    public static final String MILK = "MILK";

    private final FarmingRepository repository;

    // Player -> entity and tick of the last registered milking. Main thread only.
    private final Map<UUID, MilkMark> lastMilk = new HashMap<>();

    public LivestockListener(FarmingRepository repository) {
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        try {
            if (!(event.getBreeder() instanceof Player player)) return;

            Entity baby = event.getEntity();
            record(player, BREED, baby.getType().name().toLowerCase(Locale.ROOT),
                    null, null, null, baby.getLocation());
        } catch (Exception e) {
            DebugLogger.error("Livestock registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        try {
            int items = 0;
            for (ItemStack drop : event.getDrops()) {
                items += drop.getAmount();
            }

            Entity entity = event.getEntity();
            record(event.getPlayer(), SHEAR, entity.getType().name().toLowerCase(Locale.ROOT),
                    items, items, event.getItem(), entity.getLocation());
        } catch (Exception e) {
            DebugLogger.error("Livestock registration failed", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        try {
            Player player = event.getPlayer();
            EquipmentSlot hand = event.getHand();
            ItemStack held = player.getInventory().getItem(hand);
            if (held == null) return;

            Entity entity = event.getRightClicked();
            if (!(entity instanceof org.bukkit.entity.Ageable animal) || !animal.isAdult()) return;

            boolean milking = held.getType() == Material.BUCKET
                    && (entity instanceof Cow || entity instanceof Goat);
            boolean stew = held.getType() == Material.BOWL && entity instanceof MushroomCow;
            if (!milking && !stew) return;

            // One click can arrive as more than one interact event: count it once.
            int tick = Bukkit.getCurrentTick();
            MilkMark previous = lastMilk.get(player.getUniqueId());
            if (previous != null && previous.tick == tick && previous.entity.equals(entity.getUniqueId())) {
                return;
            }
            lastMilk.put(player.getUniqueId(), new MilkMark(entity.getUniqueId(), tick));

            record(player, MILK, entity.getType().name().toLowerCase(Locale.ROOT),
                    1, 1, held, entity.getLocation());
        } catch (Exception e) {
            DebugLogger.error("Livestock registration failed", e);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMilk.remove(event.getPlayer().getUniqueId());
    }

    public void clear() {
        lastMilk.clear();
    }

    private void record(
            Player player, String action, String animalType,
            Integer harvested, Integer product, ItemStack tool, Location where
    ) {
        EventConditions c = EventConditions.capture(player, where);

        DebugLogger.debug(String.format(
                "Farming %s %s %s amount=%s world=%s",
                player.getName(), action, animalType, harvested, c.world()
        ));

        repository.insert(new FarmingRepository.Entry(
                player.getUniqueId().toString(),
                player.getName(),
                action,
                null,
                animalType,
                harvested,
                product,
                null,
                null,
                tool == null || tool.getType().isAir() ? null : tool.getType().name().toLowerCase(Locale.ROOT),
                null,
                null,
                c.world(), c.gamemode(), c.biome(), c.weather(), c.timeOfDay(),
                where.getX(), where.getY(), where.getZ(),
                LocalDateTime.now()
        ));
    }

    private record MilkMark(UUID entity, int tick) {}
}
