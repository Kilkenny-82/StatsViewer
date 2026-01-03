package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CombatResolver {

    private CombatResolver() {}

    public static CombatInfo resolve(LivingEntity victim) {

        EntityDamageEvent event = victim.getLastDamageCause();
        if (event == null) {
            return new CombatInfo("unknown", "unknown", null, "unknown", null);
        }

        String cause = event.getCause().name().toLowerCase();

        if (!(event instanceof EntityDamageByEntityEvent ede)) {
            return new CombatInfo(cause, cause, null, cause, null);
        }

        Entity damager = ede.getDamager();

        // =====================
        // PROJECTILES
        // =====================
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
                        proj.getType().name().toLowerCase()
                );
            }
        }

        // =====================
        // DIRECT MELEE
        // =====================
        if (damager instanceof Player player) {
            return new CombatInfo(
                    cause,
                    "player",
                    player.getName(),
                    WeaponResolver.resolve(player),
                    null
            );
        }

        // =====================
        // WOLF
        // =====================
        if (damager instanceof Wolf wolf && wolf.getOwner() instanceof Player owner) {
            return new CombatInfo(
                    cause,
                    "wolf",
                    owner.getName(),
                    "wolf",
                    wolf.getCustomName()
            );
        }

        // =====================
        // TNT
        // =====================
        if (damager instanceof TNTPrimed) {
            return new CombatInfo(
                    cause,
                    "player",
                    null,
                    "tnt",
                    null
            );
        }

        return new CombatInfo(cause, cause, null, cause, null);
    }
}
