package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatResolver {

    private CombatResolver() {}

    public static CombatInfo resolve(LivingEntity victim) {

        EntityDamageEvent event = victim.getLastDamageCause();
        if (event == null) {
            return new CombatInfo("unknown", "environment", null, "unknown", null);
        }

        String cause = event.getCause().name().toLowerCase();

        // =====================
        // ENTITY DAMAGE
        // =====================
        if (event instanceof EntityDamageByEntityEvent ede) {

            Entity damager = ede.getDamager();

            // -------- PROJECTILE --------
            if (damager instanceof Projectile proj) {

                if (proj.getShooter() instanceof Player player) {
                    return new CombatInfo(
                            cause,
                            "player",
                            player.getName(),
                            WeaponResolver.resolve(player),
                            ProjectileResolver.resolve(proj)
                    );
                }

                if (proj.getShooter() instanceof LivingEntity le) {
                    return new CombatInfo(
                            cause,
                            le.getType().name().toLowerCase(),
                            le.getCustomName(),
                            le.getType().name().toLowerCase(),
                            ProjectileResolver.resolve(proj)
                    );
                }
            }

            // -------- DIRECT MELEE (ZOMBIE FIX) --------
            if (damager instanceof LivingEntity le) {
                return new CombatInfo(
                        cause,
                        le.getType().name().toLowerCase(),
                        le.getCustomName(),
                        le.getType().name().toLowerCase(),
                        null
                );
            }
        }

        // =====================
        // NON-ENTITY DAMAGE (FIRE, POISON, ETC.)
        // =====================
        Entity lastAttacker = victim.getLastDamageCause() instanceof EntityDamageByEntityEvent e
                ? e.getDamager()
                : null;

        if (lastAttacker instanceof LivingEntity le) {
            return new CombatInfo(
                    cause,
                    le.getType().name().toLowerCase(),
                    le.getCustomName(),
                    cause,
                    null
            );
        }

        // =====================
        // ENVIRONMENT
        // =====================
        return new CombatInfo(
                cause,
                "environment",
                null,
                cause,
                null
        );
    }
}
