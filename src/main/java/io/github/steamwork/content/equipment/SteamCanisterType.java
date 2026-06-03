package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.item.RebarItem;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.SteamCharge;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 蒸汽罐三档类型，统一「插槽字符串 ↔ 容量 ↔ 罐物品」的双向映射。
 *
 * <p>容量为设计权威值，与各 {@code steam_canister_*.yml} 的 {@code steam-capacity} 保持一致。</p>
 */
public enum SteamCanisterType {
    BRASS("brass", 2000.0, SteamworkKeys.STEAM_CANISTER_BRASS),
    INVAR("invar", 5000.0, SteamworkKeys.STEAM_CANISTER_INVAR),
    TUNGSTEN("tungsten", 12000.0, SteamworkKeys.STEAM_CANISTER_TUNGSTEN);

    /** 写入装备 {@link SteamCharge} 插槽的字符串标识。 */
    public final String socketKey;
    /** 该罐赋予装备的蒸汽容量上限（mB）。 */
    public final double capacity;
    /** 对应罐物品的 Rebar key。 */
    public final NamespacedKey itemKey;

    SteamCanisterType(String socketKey, double capacity, NamespacedKey itemKey) {
        this.socketKey = socketKey;
        this.capacity = capacity;
        this.itemKey = itemKey;
    }

    /** 由插槽字符串还原类型；未知返回 {@code null}。 */
    public static @Nullable SteamCanisterType fromSocketKey(@NotNull String key) {
        for (SteamCanisterType t : values()) {
            if (t.socketKey.equals(key)) return t;
        }
        return null;
    }

    /** 由罐物品识别类型；非蒸汽罐返回 {@code null}。 */
    public static @Nullable SteamCanisterType fromCanisterStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        RebarItem item = RebarItem.fromStack(stack);
        if (item == null) return null;
        NamespacedKey key = item.getKey();
        for (SteamCanisterType t : values()) {
            if (t.itemKey.equals(key)) return t;
        }
        return null;
    }

    /** 生成一只对应的空罐物品（拆罐吐回用，蒸汽量 0）。 */
    public @NotNull ItemStack makeCanisterItem() {
        return switch (this) {
            case BRASS -> SteamworkItems.STEAM_CANISTER_BRASS.clone();
            case INVAR -> SteamworkItems.STEAM_CANISTER_INVAR.clone();
            case TUNGSTEN -> SteamworkItems.STEAM_CANISTER_TUNGSTEN.clone();
        };
    }
}
