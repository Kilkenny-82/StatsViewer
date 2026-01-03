package dev.svmarvinthepa.statsviewer.core.util.combat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class WeaponResolver {

    private WeaponResolver() {}

    public static String resolve(Player player) {

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            return "hand";
        }

        return item.getType().name().toLowerCase();
    }
}
