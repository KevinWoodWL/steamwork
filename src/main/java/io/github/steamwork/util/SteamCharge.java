package io.github.steamwork.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * 给任意 ItemStack 读写"蒸汽储能"状态的工具。
 * 使用两个 PDC 字段：amount（当前蒸汽量，单位 mB）+ capacity（最大蒸汽量，单位 mB）。
 * <p>
 * 这是装备/蒸汽罐/未来便携能源共用的统一存储接口。机器/充电舱通过这里读写，
 * 不依赖具体物品类型。
 */
public final class SteamCharge {

    private SteamCharge() {
        throw new AssertionError("Utility class");
    }

    private static final NamespacedKey AMOUNT_KEY = SteamworkUtils.steamworkKey("steam_amount");
    private static final NamespacedKey CAPACITY_KEY = SteamworkUtils.steamworkKey("steam_capacity");

    /** 给一个空白 ItemStack 写入蒸汽容量（amount 初始化为 0）。仅当还没有容量时写入。 */
    public static void initIfMissing(@NotNull ItemStack stack, double capacity) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(CAPACITY_KEY, PersistentDataType.DOUBLE)) return;
        pdc.set(CAPACITY_KEY, PersistentDataType.DOUBLE, capacity);
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, 0.0);
        stack.setItemMeta(meta);
    }

    /** 返回当前蒸汽量。没有储能字段返回 0。 */
    public static double getAmount(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        Double v = meta.getPersistentDataContainer().get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        return v == null ? 0.0 : v;
    }

    /** 返回最大蒸汽容量。没有储能字段返回 0（视为不可充气物品）。 */
    public static double getCapacity(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        Double v = meta.getPersistentDataContainer().get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        return v == null ? 0.0 : v;
    }

    /** 物品是否声明了蒸汽储能（capacity > 0）。 */
    public static boolean hasSteamStorage(@NotNull ItemStack stack) {
        return getCapacity(stack) > 0.0;
    }

    /** 直接覆盖当前蒸汽量。会自动 clamp 到 [0, capacity]。 */
    public static void setAmount(@NotNull ItemStack stack, double amount) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double cap = pdc.get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        if (cap == null) return;
        double clamped = Math.max(0.0, Math.min(cap, amount));
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, clamped);
        stack.setItemMeta(meta);
    }

    /**
     * 给物品加蒸汽。返回实际加进去的量（受剩余空间限制）。
     * 没有储能字段返回 0。
     */
    public static double addAmount(@NotNull ItemStack stack, double delta) {
        if (delta <= 0) return 0.0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double cap = pdc.get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        if (cap == null) return 0.0;
        Double cur = pdc.get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        double current = cur == null ? 0.0 : cur;
        double space = cap - current;
        double added = Math.min(space, delta);
        if (added <= 0) return 0.0;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, current + added);
        stack.setItemMeta(meta);
        return added;
    }

    /**
     * 从物品扣蒸汽。返回实际扣出来的量（受当前余量限制）。
     * 没有储能字段返回 0。
     */
    public static double removeAmount(@NotNull ItemStack stack, double delta) {
        if (delta <= 0) return 0.0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double cur = pdc.get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        if (cur == null) return 0.0;
        double removed = Math.min(cur, delta);
        if (removed <= 0) return 0.0;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, cur - removed);
        stack.setItemMeta(meta);
        return removed;
    }
}
