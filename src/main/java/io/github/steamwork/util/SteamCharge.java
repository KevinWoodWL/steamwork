package io.github.steamwork.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SteamCharge {

    private SteamCharge() {
        throw new AssertionError("Utility class");
    }

    private static final int BAR_SEGMENTS = 16;

    private static final NamespacedKey AMOUNT_KEY = SteamworkUtils.steamworkKey("steam_amount");
    private static final NamespacedKey CAPACITY_KEY = SteamworkUtils.steamworkKey("steam_capacity");
    private static final NamespacedKey SOCKET_KEY = SteamworkUtils.steamworkKey("steam_socket");
    private static final NamespacedKey LORE_LINE_COUNT_KEY = SteamworkUtils.steamworkKey("steam_lore_lines");

    public static final String SOCKET_NONE = "none";

    public static void initIfMissing(@NotNull ItemStack stack, double capacity) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CAPACITY_KEY, PersistentDataType.DOUBLE)) {
            pdc.set(CAPACITY_KEY, PersistentDataType.DOUBLE, capacity);
        }
        if (!pdc.has(AMOUNT_KEY, PersistentDataType.DOUBLE)) {
            pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, 0.0);
        }
        if (!pdc.has(SOCKET_KEY, PersistentDataType.STRING)) {
            pdc.set(SOCKET_KEY, PersistentDataType.STRING, SOCKET_NONE);
        }
        saveWithDisplay(stack, meta);
    }

    public static double getAmount(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        Double value = meta.getPersistentDataContainer().get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    public static double getCapacity(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        Double value = meta.getPersistentDataContainer().get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }

    public static boolean hasSteamStorage(@NotNull ItemStack stack) {
        return getCapacity(stack) > 0.0;
    }

    public static boolean hasAtLeast(@NotNull ItemStack stack, double amount) {
        return amount <= 0.0 || getAmount(stack) >= amount;
    }

    public static void setAmount(@NotNull ItemStack stack, double amount) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double capacity = pdc.get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        if (capacity == null) return;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, clamp(amount, 0.0, capacity));
        saveWithDisplay(stack, meta);
    }

    public static double addAmount(@NotNull ItemStack stack, double delta) {
        if (delta <= 0.0) return 0.0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double capacity = pdc.get(CAPACITY_KEY, PersistentDataType.DOUBLE);
        if (capacity == null) return 0.0;
        double current = pdc.getOrDefault(AMOUNT_KEY, PersistentDataType.DOUBLE, 0.0);
        double added = Math.min(capacity - current, delta);
        if (added <= 0.0) return 0.0;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, current + added);
        saveWithDisplay(stack, meta);
        return added;
    }

    public static double removeAmount(@NotNull ItemStack stack, double delta) {
        if (delta <= 0.0) return 0.0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double current = pdc.get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        if (current == null) return 0.0;
        double removed = Math.min(current, delta);
        if (removed <= 0.0) return 0.0;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, current - removed);
        saveWithDisplay(stack, meta);
        return removed;
    }

    public static boolean tryRemoveAmount(@NotNull ItemStack stack, double amount) {
        if (amount <= 0.0) return true;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Double current = pdc.get(AMOUNT_KEY, PersistentDataType.DOUBLE);
        if (current == null || current < amount) return false;
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, current - amount);
        saveWithDisplay(stack, meta);
        return true;
    }

    public static @NotNull String getSocket(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return SOCKET_NONE;
        String value = meta.getPersistentDataContainer().get(SOCKET_KEY, PersistentDataType.STRING);
        return value == null ? SOCKET_NONE : value;
    }

    public static boolean hasCanister(@NotNull ItemStack stack) {
        return !SOCKET_NONE.equals(getSocket(stack));
    }

    public static boolean isPowered(@NotNull ItemStack stack) {
        return hasCanister(stack) && getAmount(stack) > 0.0;
    }

    public static void installCanister(
            @NotNull ItemStack stack,
            @NotNull String socketType,
            double capacity,
            double transferAmount
    ) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(SOCKET_KEY, PersistentDataType.STRING, socketType);
        pdc.set(CAPACITY_KEY, PersistentDataType.DOUBLE, capacity);
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, clamp(transferAmount, 0.0, capacity));
        saveWithDisplay(stack, meta);
    }

    public static void clearCanister(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(SOCKET_KEY, PersistentDataType.STRING, SOCKET_NONE);
        pdc.set(CAPACITY_KEY, PersistentDataType.DOUBLE, 0.0);
        pdc.set(AMOUNT_KEY, PersistentDataType.DOUBLE, 0.0);
        saveWithDisplay(stack, meta);
    }

    public static void refreshDisplay(@NotNull ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        saveWithDisplay(stack, meta);
    }

    private static void saveWithDisplay(@NotNull ItemStack stack, @NotNull ItemMeta meta) {
        refreshDisplay(meta);
        stack.setItemMeta(meta);
    }

    private static void refreshDisplay(@NotNull ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<Component> lore = meta.lore();
        List<Component> updatedLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);

        Integer oldLineCount = pdc.get(LORE_LINE_COUNT_KEY, PersistentDataType.INTEGER);
        if (oldLineCount != null && oldLineCount > 0) {
            int linesToRemove = Math.min(oldLineCount, updatedLore.size());
            for (int i = 0; i < linesToRemove; i++) {
                updatedLore.remove(updatedLore.size() - 1);
            }
        }
        pdc.remove(LORE_LINE_COUNT_KEY);

        double capacity = pdc.getOrDefault(CAPACITY_KEY, PersistentDataType.DOUBLE, 0.0);
        if (capacity <= 0.0) {
            meta.lore(updatedLore.isEmpty() ? null : updatedLore);
            return;
        }

        double amount = clamp(pdc.getOrDefault(AMOUNT_KEY, PersistentDataType.DOUBLE, 0.0), 0.0, capacity);
        String socket = pdc.getOrDefault(SOCKET_KEY, PersistentDataType.STRING, SOCKET_NONE);

        List<Component> dynamicLore = new ArrayList<>();
        if (!SOCKET_NONE.equals(socket)) {
            dynamicLore.add(noItalic(Component.text("\u7f50\u4f53 ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(socketDisplay(socket), NamedTextColor.GRAY))));
        }
        dynamicLore.add(noItalic(Component.text("\u84b8\u6c7d ", NamedTextColor.GRAY)
                .append(Component.text(formatAmount(amount), amountColor(amount / capacity)))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text(formatAmount(capacity), NamedTextColor.AQUA))
                .append(Component.text(" mB", NamedTextColor.DARK_GRAY))));
        dynamicLore.add(noItalic(barComponent(amount, capacity)));

        updatedLore.addAll(dynamicLore);
        pdc.set(LORE_LINE_COUNT_KEY, PersistentDataType.INTEGER, dynamicLore.size());
        meta.lore(updatedLore);
    }

    private static @NotNull Component barComponent(double amount, double capacity) {
        double ratio = capacity <= 0.0 ? 0.0 : clamp(amount / capacity, 0.0, 1.0);
        int filled = (int) Math.round(ratio * BAR_SEGMENTS);
        NamedTextColor color = amountColor(ratio);

        return Component.text("\u84b8\u6c7d\u6761 ", NamedTextColor.DARK_GRAY)
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text("|".repeat(filled), color))
                .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text((int) Math.round(ratio * 100.0) + "%", color));
    }

    private static @NotNull NamedTextColor amountColor(double ratio) {
        if (ratio <= 0.0) return NamedTextColor.RED;
        if (ratio < 0.25) return NamedTextColor.GOLD;
        if (ratio < 0.60) return NamedTextColor.YELLOW;
        return NamedTextColor.AQUA;
    }

    private static @NotNull String socketDisplay(@NotNull String socket) {
        return switch (socket) {
            case "brass" -> "\u9ec4\u94dc";
            case "invar" -> "\u56e0\u74e6";
            case "tungsten" -> "\u94a8";
            default -> socket;
        };
    }

    private static @NotNull String formatAmount(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value - Math.rint(value)) < 0.01) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
