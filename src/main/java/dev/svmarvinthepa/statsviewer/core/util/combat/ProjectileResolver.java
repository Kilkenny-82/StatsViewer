package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.*;
import org.bukkit.potion.PotionType;

public final class ProjectileResolver {

    private ProjectileResolver() {}

    public static String resolve(Projectile proj) {

        // ARROW
        if (proj instanceof Arrow arrow) {
            PotionType type = arrow.getBasePotionType();

            if (type != null && type != PotionType.WATER) {
				String name = type.name().toLowerCase().replaceFirst("^(long|strong)_", "");
                return name + "_arrow";
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
