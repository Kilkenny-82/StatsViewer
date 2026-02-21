package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatResolver {

    private CombatResolver() {}

    public static CombatInfo resolve(LivingEntity victim) {

        EntityDamageEvent event = victim.getLastDamageCause();
        if (event == null) {
            return CombatInfo.environment("unknown");
        }

        String cause = event.getCause().name().toLowerCase();

        // =====================
        // ENTITY DAMAGE
        // =====================
        if (event instanceof EntityDamageByEntityEvent ede) {
            return resolveFromEntityDamage(ede, cause);
        }

        // =====================
        // ENVIRONMENT (fire, fall, poison, etc.)
        // =====================
        return CombatInfo.environment(cause);
    }

    private static CombatInfo resolveFromEntityDamage(
            EntityDamageByEntityEvent ede,
            String finalCause
    ) {

        Entity damager = ede.getDamager();

        // -------- PROJECTILES --------
        if (damager instanceof Projectile proj) {

            if (proj.getShooter() instanceof Player player) {
                return new CombatInfo(
                        finalCause,
                        "player",
                        player.getName(),
                        WeaponResolver.resolve(player),
                        ProjectileResolver.resolve(proj)
                );
            }

            if (proj.getShooter() instanceof LivingEntity le) {
                return new CombatInfo(
                        finalCause,
                        le.getType().name().toLowerCase(),
                        le.getCustomName(),
                        le.getType().name().toLowerCase(),
                        ProjectileResolver.resolve(proj)
                );
            }
        }

        // -------- WOLF (named support) --------
        if (damager instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
            return new CombatInfo(
                    finalCause,
                    "wolf",
                    owner.getName(),
                    "wolf",
                    wolf.getCustomName()
            );
        }

        // -------- PLAYER MELEE --------
        if (damager instanceof Player player) {
            return new CombatInfo(
                    finalCause,
                    "player",
                    player.getName(),
                    WeaponResolver.resolve(player),
                    null
            );
        }

        // -------- MOB MELEE --------
        if (damager instanceof LivingEntity le) {
            return new CombatInfo(
                    finalCause,
                    le.getType().name().toLowerCase(),
                    le.getCustomName(),
                    le.getType().name().toLowerCase(),
                    null
            );
        }

        // -------- FALLBACK --------
        return CombatInfo.environment(finalCause);
    }
}
