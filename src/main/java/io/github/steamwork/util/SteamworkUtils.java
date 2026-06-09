package io.github.steamwork.util;

import io.github.steamwork.Steamwork;
import org.bukkit.NamespacedKey;

public final class SteamworkUtils {

    private SteamworkUtils() {
        throw new AssertionError("Utility class");
    }

    public static NamespacedKey steamworkKey(String key) {
        return new NamespacedKey(Steamwork.getInstance(), key);
    }
}
