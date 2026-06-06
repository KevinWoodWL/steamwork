package io.github.steamwork.util;

import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.steamwork.SteamworkFluids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class SteamLogicSupport {

    private SteamLogicSupport() {}

    public enum SteamKind {
        STEAM(SteamworkFluids.STEAM, "steamwork.gui.common.steam_kind.steam", TextColor.fromHexString("#d8edf0")),
        SUPERHEATED(SteamworkFluids.SUPERHEATED_STEAM, "steamwork.gui.common.steam_kind.superheated", TextColor.fromHexString("#ff8c00")),
        PRESSURIZED(SteamworkFluids.PRESSURIZED_STEAM, "steamwork.gui.common.steam_kind.pressurized", TextColor.fromHexString("#18c0d8"));

        private final RebarFluid fluid;
        private final String translationKey;
        private final TextColor color;

        SteamKind(@NotNull RebarFluid fluid, @NotNull String translationKey, @Nullable TextColor color) {
            this.fluid = fluid;
            this.translationKey = translationKey;
            this.color = color != null ? color : NamedTextColor.WHITE;
        }

        public @NotNull RebarFluid fluid() {
            return fluid;
        }

        public @NotNull String translationKey() {
            return translationKey;
        }

        public @NotNull TextColor color() {
            return color;
        }

        public @NotNull Component component() {
            return Component.translatable(translationKey).color(color);
        }

        public @NotNull SteamKind next() {
            SteamKind[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static @NotNull SteamKind fromOrdinal(int ordinal) {
            SteamKind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : STEAM;
        }
    }

    public static @Nullable FluidBufferRebarBlock fluidNeighbor(@NotNull Block block, @Nullable BlockFace face) {
        if (face == null) return null;
        return PneumaticEndpointSupport.loadedRebarBlock(block.getRelative(face)) instanceof FluidBufferRebarBlock fb ? fb : null;
    }

    public static double amount(@NotNull FluidBufferRebarBlock block, @NotNull SteamKind kind) {
        if (!block.hasFluid(kind.fluid())) return 0.0;
        return block.fluidAmount(kind.fluid());
    }

    public static double capacity(@NotNull FluidBufferRebarBlock block, @NotNull SteamKind kind) {
        if (!block.hasFluid(kind.fluid())) return 0.0;
        return block.fluidCapacity(kind.fluid());
    }

    public static double fillRatio(@Nullable FluidBufferRebarBlock block, @NotNull SteamKind kind) {
        if (block == null) return 0.0;
        if (!block.hasFluid(kind.fluid())) return 0.0;
        double capacity = capacity(block, kind);
        return capacity <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, amount(block, kind) / capacity));
    }

    public static double transfer(@NotNull FluidBufferRebarBlock src,
                                  @NotNull FluidBufferRebarBlock dst,
                                  @NotNull SteamKind kind,
                                  double maxAmount) {
        if (!src.hasFluid(kind.fluid()) || !dst.hasFluid(kind.fluid())) return 0.0;
        double amount = Math.min(maxAmount, Math.min(src.fluidAmount(kind.fluid()), dst.fluidSpaceRemaining(kind.fluid())));
        if (amount <= 0.0) return 0.0;
        src.removeFluid(kind.fluid(), amount);
        dst.addFluid(kind.fluid(), amount);
        return amount;
    }

    public static @NotNull String faceName(@Nullable BlockFace face) {
        if (face == null) return "unset";
        return face.name().toLowerCase(Locale.ROOT);
    }

    public static @NotNull Component faceComponent(@Nullable BlockFace face) {
        return Component.translatable("steamwork.gui.common.face." + faceName(face));
    }

    public static @Nullable BlockFace nextFace(@Nullable BlockFace current) {
        BlockFace[] faces = {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        };
        if (current == null) return faces[0];
        for (int i = 0; i < faces.length; i++) {
            if (faces[i] == current) return i + 1 < faces.length ? faces[i + 1] : null;
        }
        return null;
    }

    /** 去除斜体（GUI 标准用法）。 */
    public static @NotNull Component ni(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** 从 PDC 读取 BlockFace，键不存在或非法时返回 {@code fallback}（不可为 null）。 */
    public static @NotNull BlockFace loadFace(@NotNull PersistentDataContainer pdc,
                                              @NotNull NamespacedKey key,
                                              @NotNull BlockFace fallback) {
        String value = pdc.get(key, PersistentDataType.STRING);
        if (value == null) return fallback;
        try {
            return BlockFace.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /**
     * 从 PDC 读取可为 null 的 BlockFace，"NONE" 字符串映射为 null，
     * 键不存在时返回 {@code fallback}。
     */
    public static @Nullable BlockFace loadNullableFace(@NotNull PersistentDataContainer pdc,
                                                       @NotNull NamespacedKey key,
                                                       @Nullable BlockFace fallback) {
        String value = pdc.get(key, PersistentDataType.STRING);
        if (value == null) return fallback;
        if ("NONE".equals(value)) return null;
        try {
            return BlockFace.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 保存可为 null 的 BlockFace 到 PDC，null 写为 "NONE"。 */
    public static void saveNullableFace(@NotNull PersistentDataContainer pdc,
                                        @NotNull NamespacedKey key,
                                        @Nullable BlockFace face) {
        pdc.set(key, PersistentDataType.STRING, face != null ? face.name() : "NONE");
    }

    /** 构造压力显示行：当前量 / 容量。 */
    public static @NotNull Component pressureLine(double amount, double capacity) {
        return Component.translatable("steamwork.gui.common.pressure",
                RebarArgument.of("amount", UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0)));
    }
}
