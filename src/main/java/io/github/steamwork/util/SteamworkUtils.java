package io.github.steamwork.util;

import io.github.steamwork.Steamwork;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;

public final class SteamworkUtils {

    private SteamworkUtils() {
        throw new AssertionError("Utility class");
    }

    public static NamespacedKey steamworkKey(String key) {
        return new NamespacedKey(Steamwork.getInstance(), key);
    }

    public static Component createFluidAmountBar(double amount, double capacity, int bars, TextColor color) {
        double proportion = capacity <= 0.0 ? 0.0 : amount / capacity;
        return new ProgressBar()
                .bars(bars)
                .barColor(color)
                .proportion(Math.max(0.0, Math.min(1.0, proportion)))
                .suffix(Component.text(" ")
                        .append(Component.text((int) Math.round(amount)))
                        .append(Component.text("/"))
                        .append(UnitFormat.MILLIBUCKETS.format(capacity)))
                .asComponent();
    }
}
