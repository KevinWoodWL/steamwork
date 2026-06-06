package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.SteamLogicSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 汽动逻辑门 —— 借鉴 PneumaticCraft 的压力逻辑，但「信号即蒸汽」。
 *
 * <p>读取两个输入面相邻机器的蒸汽压力作为布尔信号（有汽=HIGH），按门类型判定后，
 * 把真实蒸汽从输入相邻机器搬运到输出面相邻机器——逻辑输出的就是能直接做功的蒸汽，
 * 无需「信号→执行器」转换。对普通 / 过热 / 加压三种蒸汽各自独立处理、互不干扰。</p>
 *
 * <ul>
 *   <li>AND：A、B 两路都有汽 → 合流搬往输出</li>
 *   <li>OR：任一路有汽 → 把有汽的路搬往输出</li>
 *   <li>XOR：恰好一路有汽 → 搬那一路（两路都有则对冲截断）</li>
 *   <li>NOT：A 为主进汽、B 为控制；B 无汽压时把 A 搬往输出，B 一旦加压立即截断</li>
 * </ul>
 */
public class PneumaticLogicGate extends RebarBlock implements
        GuiRebarBlock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock {

    public enum GateMode {
        AND("steamwork.gui.pneumatic_logic_gate.mode.and"),
        OR("steamwork.gui.pneumatic_logic_gate.mode.or"),
        XOR("steamwork.gui.pneumatic_logic_gate.mode.xor"),
        NOT("steamwork.gui.pneumatic_logic_gate.mode.not");

        private final String translationKey;
        GateMode(@NotNull String translationKey) { this.translationKey = translationKey; }

        public @NotNull Component component() {
            return Component.translatable(translationKey);
        }
    }

    private static final RebarFluid[] STEAMS = {
        SteamworkFluids.STEAM, SteamworkFluids.SUPERHEATED_STEAM, SteamworkFluids.PRESSURIZED_STEAM
    };

    private static final String KEY_MODE = "plg_mode";
    private static final String KEY_A    = "plg_a";
    private static final String KEY_B    = "plg_b";
    private static final String KEY_OUT  = "plg_out";
    private static final String KEY_THRESHOLD = "plg_threshold";

    private final int tickInterval        = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 2);
    private final double transferPerTick  = getSettings().get("transfer-per-tick", ConfigAdapter.DOUBLE, 50.0);
    private final double defaultThreshold = getSettings().get("signal-threshold", ConfigAdapter.DOUBLE, 1.0);

    private @NotNull GateMode mode = GateMode.AND;
    private @Nullable BlockFace faceA = BlockFace.NORTH;
    private @Nullable BlockFace faceB = BlockFace.WEST;
    private @Nullable BlockFace faceOut = BlockFace.SOUTH;
    private double signalThreshold;
    private boolean lastActive = false;

    private final ModeItem      modeItem      = new ModeItem();
    private final ThresholdItem thresholdItem = new ThresholdItem();
    private final FaceAItem     faceAItem     = new FaceAItem();
    private final FaceBItem     faceBItem     = new FaceBItem();
    private final OutItem       outItem       = new OutItem();
    private final StatusItem    statusItem    = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }
    }

    @SuppressWarnings("unused")
    public PneumaticLogicGate(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        signalThreshold = defaultThreshold;
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public PneumaticLogicGate(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        int ord = pdc.getOrDefault(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, 0);
        GateMode[] ms = GateMode.values();
        mode = (ord >= 0 && ord < ms.length) ? ms[ord] : GateMode.AND;
        faceA   = SteamLogicSupport.loadNullableFace(pdc, steamworkKey(KEY_A), BlockFace.NORTH);
        faceB   = SteamLogicSupport.loadNullableFace(pdc, steamworkKey(KEY_B), BlockFace.WEST);
        faceOut = SteamLogicSupport.loadNullableFace(pdc, steamworkKey(KEY_OUT), BlockFace.SOUTH);
        signalThreshold = pdc.getOrDefault(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, defaultThreshold);
        setTickInterval(tickInterval);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, mode.ordinal());
        SteamLogicSupport.saveNullableFace(pdc, steamworkKey(KEY_A), faceA);
        SteamLogicSupport.saveNullableFace(pdc, steamworkKey(KEY_B), faceB);
        SteamLogicSupport.saveNullableFace(pdc, steamworkKey(KEY_OUT), faceOut);
        pdc.set(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, signalThreshold);
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of();
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        double moved = 0;
        for (RebarFluid fluid : STEAMS) {
            boolean a = hasSignal(faceA, fluid);
            boolean b = hasSignal(faceB, fluid);
            boolean pass = switch (mode) {
                case AND -> a && b;
                case OR  -> a || b;
                case XOR -> a ^ b;
                case NOT -> a && !b;   // A 有汽 + B 无压 → 放行；B 加压则抑制
            };
            if (!pass) continue;

            if (mode == GateMode.NOT) {
                moved += transfer(faceA, faceOut, fluid);
            } else {
                if (a) moved += transfer(faceA, faceOut, fluid);
                if (b) moved += transfer(faceB, faceOut, fluid);
            }
        }
        setActive(moved > 0);
        notifyGuiItems();
    }

    /** 输入面相邻机器是否有该蒸汽（压力 ≥ 阈值）= HIGH 信号。 */
    private boolean hasSignal(@Nullable BlockFace face, @NotNull RebarFluid fluid) {
        if (face == null) return false;
        return BlockStorage.get(getBlock().getRelative(face)) instanceof FluidBufferRebarBlock fb
                && fb.hasFluid(fluid)
                && fb.fluidAmount(fluid) >= signalThreshold;
    }

    /** 从源面相邻机器抽取该蒸汽、注入输出面相邻机器，返回实际搬运量。 */
    private double transfer(@Nullable BlockFace srcFace, @Nullable BlockFace outFace, @NotNull RebarFluid fluid) {
        if (srcFace == null || outFace == null) return 0;
        if (!(BlockStorage.get(getBlock().getRelative(srcFace)) instanceof FluidBufferRebarBlock src)) return 0;
        if (!(BlockStorage.get(getBlock().getRelative(outFace)) instanceof FluidBufferRebarBlock dst)) return 0;
        if (!src.hasFluid(fluid) || !dst.hasFluid(fluid)) return 0;
        double amt = Math.min(transferPerTick, Math.min(src.fluidAmount(fluid), dst.fluidSpaceRemaining(fluid)));
        if (amt <= 0) return 0;
        src.removeFluid(fluid, amt);
        dst.addFluid(fluid, amt);
        return amt;
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void notifyGuiItems() {
        modeItem.notifyWindows();
        thresholdItem.notifyWindows();
        faceAItem.notifyWindows();
        faceBItem.notifyWindows();
        outItem.notifyWindows();
        statusItem.notifyWindows();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# M T A B # O S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('M', modeItem)
                .addIngredient('T', thresholdItem)
                .addIngredient('A', faceAItem)
                .addIngredient('B', faceBItem)
                .addIngredient('O', outItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("mode", mode.component().color(NamedTextColor.AQUA)),
                RebarArgument.of("state", Component.translatable(lastActive
                        ? "steamwork.gui.pneumatic_logic_gate.state.passing"
                        : "steamwork.gui.pneumatic_logic_gate.state.blocked"))
        ));
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    // ── 面工具 ───────────────────────────────────────────────────────────────

    private boolean anySignal(@Nullable BlockFace face) {
        for (RebarFluid f : STEAMS) if (hasSignal(face, f)) return true;
        return false;
    }

    private boolean isFluidNeighbor(@Nullable BlockFace face) {
        return face != null && BlockStorage.get(getBlock().getRelative(face)) instanceof FluidBufferRebarBlock;
    }

    // ── GUI 内部类 ─────────────────────────────────────────────────────────────

    private final class ModeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.COMPARATOR)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.mode_label")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", mode.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.mode_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            GateMode[] ms = GateMode.values();
            mode = ms[(mode.ordinal() + 1) % ms.length];
            notifyGuiItems();
        }
    }

    private final class ThresholdItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.REPEATER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_value",
                                    RebarArgument.of("value", String.valueOf((int) signalThreshold)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_desc")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            double step = (t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT) ? 10.0 : 1.0;
            if (t == ClickType.LEFT || t == ClickType.SHIFT_LEFT) {
                signalThreshold = Math.min(1000.0, signalThreshold + step);
            } else if (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) {
                signalThreshold = Math.max(1.0, signalThreshold - step);
            }
            notifyGuiItems();
        }
    }

    private abstract class FaceItem extends AbstractItem {
        abstract @Nullable BlockFace face();
        abstract void set(@Nullable BlockFace f);
        abstract @NotNull String labelKey();
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            boolean connected = isFluidNeighbor(face());
            boolean signal = anySignal(face());
            Material mat = !connected ? Material.COAL
                    : signal ? Material.GLOWSTONE_DUST : Material.LIGHT_GRAY_DYE;
            return ItemStackBuilder.of(mat)
                    .name(SteamLogicSupport.ni(Component.translatable(labelKey(),
                            RebarArgument.of("face", SteamLogicSupport.faceComponent(face())))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(connected
                                    ? "steamwork.gui.common.connected_fluid_buffer"
                                    : "steamwork.gui.common.no_fluid_buffer")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.signal",
                                    RebarArgument.of("value", Component.translatable(signal
                                            ? "steamwork.gui.pneumatic_logic_gate.signal.high"
                                            : "steamwork.gui.pneumatic_logic_gate.signal.low")))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle_right_clear"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            set((t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) ? null : SteamLogicSupport.nextFace(face()));
            notifyGuiItems();
        }
    }

    private final class FaceAItem extends FaceItem {
        @Override @Nullable BlockFace face() { return faceA; }
        @Override void set(@Nullable BlockFace f) { faceA = f; }
        @Override @NotNull String labelKey() { return mode == GateMode.NOT ? "steamwork.gui.pneumatic_logic_gate.input_a_not" : "steamwork.gui.pneumatic_logic_gate.input_a"; }
    }
    private final class FaceBItem extends FaceItem {
        @Override @Nullable BlockFace face() { return faceB; }
        @Override void set(@Nullable BlockFace f) { faceB = f; }
        @Override @NotNull String labelKey() { return mode == GateMode.NOT ? "steamwork.gui.pneumatic_logic_gate.control_b" : "steamwork.gui.pneumatic_logic_gate.input_b"; }
    }
    private final class OutItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            boolean connected = isFluidNeighbor(faceOut);
            return ItemStackBuilder.of(connected ? Material.PISTON : Material.BARRIER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.output_face",
                            RebarArgument.of("face", SteamLogicSupport.faceComponent(faceOut)))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(connected
                                    ? "steamwork.gui.common.connected_fluid_buffer"
                                    : "steamwork.gui.common.no_fluid_buffer")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.output_hint")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle_right_clear"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            faceOut = (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) ? null : SteamLogicSupport.nextFace(faceOut);
            notifyGuiItems();
        }
    }
    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(SteamLogicSupport.ni(Component.translatable(lastActive
                            ? "steamwork.gui.pneumatic_logic_gate.state.passing"
                            : "steamwork.gui.pneumatic_logic_gate.state.blocked")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", mode.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.all_steam_kinds"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }
}
