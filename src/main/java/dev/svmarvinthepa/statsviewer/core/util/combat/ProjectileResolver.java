package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.*;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

public final class ProjectileResolver {

    private ProjectileResolver() {}

    public static String resolve(Projectile proj) {

        // ARROW
        if (proj instanceof Arrow arrow) {
            PotionData data = arrow.getBasePotionData();

            if (data != null && data.getType() != PotionType.WATER) {
                return data.getType().name().toLowerCase() + "_arrow";
            }

            return "arrow";
        }

        // TRIDENT (thrown)
        if (proj instanceof Trident) {
            return "trident";
        }

        // FIREBALL
        if (proj instanceof Fireball) {
            return "fireball";
        }

        return proj.getType().name().toLowerCase();
    }
}
