package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.EntityCulledRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.SteamLogicSupport;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 汽动逻辑门 —— 借鉴 PneumaticCraft 的压力逻辑，但「信号即蒸汽」。
 *
 * <p>方块自带朝向（放置即定向），布局仿红石比较器：
 * <ul>
 *   <li><b>正前面</b>（朝向）= 输出</li>
 *   <li><b>正后 + 左 + 右</b>三个面 = 输入；某个输入面没贴蒸汽机器时<b>自动忽略</b></li>
 * </ul>
 * 逻辑判定读各输入面相邻机器的蒸汽压力（≥阈值 = HIGH），成立时把真实蒸汽搬进
 * 自身缓存，再由输出面供给下游——逻辑输出的就是能直接做功、可继续传导的蒸汽。
 * 因为门本身是 {@link FluidBufferRebarBlock} 节点，门→门可直接串联。</p>
 *
 * <ul>
 *   <li>AND：所有已连接输入都有汽 → 合流搬往输出</li>
 *   <li>OR：任一已连接输入有汽 → 把有汽的输入搬往输出</li>
 *   <li>XOR：恰好一个已连接输入有汽 → 搬那一路（多路有汽则截断）</li>
 *   <li>NOT：正后面为进汽源、左右为控制端；控制端无汽时把后面的汽搬往输出，
 *       任一控制端加压立即截断</li>
 * </ul>
 */
public class PneumaticLogicGate extends RebarBlock implements
        DirectionalRebarBlock,
        EntityCulledRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

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

    private static final String KEY_MODE      = "plg_mode";
    private static final String KEY_THRESHOLD = "plg_threshold";
    private static final String KEY_KIND      = "plg_kind"; // -1=全部, 0/1/2=SteamKind ordinal

    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_logic_gate_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_logic_gate_display_owner");

    private final int tickInterval        = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 2);
    private final double transferPerTick  = getSettings().get("transfer-per-tick", ConfigAdapter.DOUBLE, 50.0);
    private final double defaultThreshold = getSettings().get("signal-threshold", ConfigAdapter.DOUBLE, 1.0);
    private final double bufferCapacity   = getSettings().get("buffer", ConfigAdapter.DOUBLE, 1000.0);

    private @NotNull GateMode mode = GateMode.AND;
    private double signalThreshold;
    /** null = 全部三种蒸汽；非 null = 仅处理指定种类。 */
    private @Nullable SteamKind steamKind = null;
    private boolean lastActive = false;
    private volatile List<UUID> displayUuids = List.of();

    private final ModeItem      modeItem      = new ModeItem();
    private final ThresholdItem thresholdItem = new ThresholdItem();
    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final StatusItem    statusItem    = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }
    }

    @SuppressWarnings("unused")
    public PneumaticLogicGate(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        // getFacing() = 输出（正前）方向 = 背离玩家。放置时玩家面对的是后端（输入）。
        // 与 createFluidPoint(context) 的端点布局保持一致：OUTPUT 在 getFacing()，输入在背面 + 左右。
        setFacing(context.getFacing().getOppositeFace());
        signalThreshold = defaultThreshold;
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false, 0.5f);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false, 0.5f);
        createFluidPoint(FluidPointType.INPUT, BlockFace.EAST, context, false, 0.5f);
        createFluidPoint(FluidPointType.INPUT, BlockFace.WEST, context, false, 0.5f);
        // 自身缓存：不被流体网络自动灌入（input=false，由逻辑判定后主动抽取），可供下游抽取（output=true）
        for (RebarFluid fluid : STEAMS) {
            createFluidBuffer(fluid, bufferCapacity, false, true);
        }
    }

    @SuppressWarnings("unused")
    public PneumaticLogicGate(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        int ord = pdc.getOrDefault(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, 0);
        GateMode[] ms = GateMode.values();
        mode = (ord >= 0 && ord < ms.length) ? ms[ord] : GateMode.AND;
        signalThreshold = pdc.getOrDefault(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, defaultThreshold);
        int kindOrd = pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, -1);
        SteamKind[] kinds = SteamKind.values();
        steamKind = (kindOrd >= 0 && kindOrd < kinds.length) ? kinds[kindOrd] : null;
        setTickInterval(tickInterval);
        try {
            getFacing();
        } catch (IllegalStateException e) {
            setFacing(BlockFace.NORTH);
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_MODE),      PersistentDataType.INTEGER, mode.ordinal());
        pdc.set(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE,  signalThreshold);
        pdc.set(steamworkKey(KEY_KIND),      PersistentDataType.INTEGER, steamKind != null ? steamKind.ordinal() : -1);
    }

    @Override
    public void postInitialise() {
        refreshDisplay();
    }

    @Override
    public @NotNull Iterable<UUID> getCulledEntityIds() {
        return displayUuids;
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearDisplays();
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ── 面几何（以朝向推导）─────────────────────────────────────────────────────

    private @NotNull BlockFace backFace() {
        return getFacing().getOppositeFace();
    }

    /** 与朝向垂直的两个水平侧面（左、右）。 */
    private @NotNull BlockFace[] sideFaces() {
        return switch (getFacing()) {
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default         -> new BlockFace[]{BlockFace.WEST, BlockFace.EAST};
        };
    }

    /** 全部三个输入面：正后 + 左 + 右。 */
    private @NotNull List<BlockFace> inputFaces() {
        List<BlockFace> faces = new ArrayList<>(3);
        faces.add(backFace());
        for (BlockFace s : sideFaces()) faces.add(s);
        return faces;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        boolean moved = false;
        RebarFluid[] toProcess = (steamKind != null) ? new RebarFluid[]{steamKind.fluid()} : STEAMS;
        for (RebarFluid fluid : toProcess) {
            moved |= (mode == GateMode.NOT) ? tickNot(fluid) : tickCombinational(fluid);
        }
        setActive(moved);
        notifyGuiItems();
    }

    /** AND/OR/XOR：对所有已连接输入按门类型判定，成立则把有汽输入搬进自身缓存。 */
    private boolean tickCombinational(@NotNull RebarFluid fluid) {
        List<FluidBufferRebarBlock> connected = new ArrayList<>(3);
        List<Boolean> highs = new ArrayList<>(3);
        for (BlockFace face : inputFaces()) {
            FluidBufferRebarBlock neighbor = SteamLogicSupport.fluidNeighbor(getBlock(), face);
            if (neighbor == null) continue;            // 未连接 → 忽略
            connected.add(neighbor);
            highs.add(hasSignal(neighbor, fluid));
        }
        if (connected.isEmpty()) return false;

        int highCount = 0;
        for (boolean h : highs) if (h) highCount++;

        boolean pass = switch (mode) {
            case AND -> highCount == connected.size();   // 所有已连接输入都有汽
            case OR  -> highCount >= 1;                   // 任一有汽
            case XOR -> highCount == 1;                   // 恰好一路有汽
            default  -> false;
        };
        if (!pass) return false;

        boolean moved = false;
        for (int i = 0; i < connected.size(); i++) {
            if (highs.get(i)) moved |= pull(connected.get(i), fluid) > 0;
        }
        return moved;
    }

    /** NOT：正后进汽源，左右控制端；源有汽且全部控制端无汽时放行。 */
    private boolean tickNot(@NotNull RebarFluid fluid) {
        FluidBufferRebarBlock source = SteamLogicSupport.fluidNeighbor(getBlock(), backFace());
        if (source == null || !hasSignal(source, fluid)) return false;
        for (BlockFace side : sideFaces()) {
            FluidBufferRebarBlock control = SteamLogicSupport.fluidNeighbor(getBlock(), side);
            if (control != null && hasSignal(control, fluid)) return false;  // 控制端加压 → 抑制
        }
        return pull(source, fluid) > 0;
    }

    /** 相邻机器是否有该蒸汽（压力 ≥ 阈值）= HIGH 信号。 */
    private boolean hasSignal(@NotNull FluidBufferRebarBlock neighbor, @NotNull RebarFluid fluid) {
        return neighbor.hasFluid(fluid) && neighbor.fluidAmount(fluid) >= signalThreshold;
    }

    /** 从源机器把该蒸汽抽进自身缓存，返回实际搬运量。 */
    private double pull(@NotNull FluidBufferRebarBlock src, @NotNull RebarFluid fluid) {
        if (!src.hasFluid(fluid) || !hasFluid(fluid)) return 0;
        double amt = Math.min(transferPerTick, Math.min(src.fluidAmount(fluid), fluidSpaceRemaining(fluid)));
        if (amt <= 0) return 0;
        src.removeFluid(fluid, amt);
        addFluid(fluid, amt);
        return amt;
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void notifyGuiItems() {
        modeItem.notifyWindows();
        thresholdItem.notifyWindows();
        steamKindItem.notifyWindows();
        statusItem.notifyWindows();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# M # K # T # S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('M', modeItem)
                .addIngredient('K', steamKindItem)
                .addIngredient('T', thresholdItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("mode", mode.component().color(NamedTextColor.AQUA)),
                RebarArgument.of("kind", steamKindLabel()),
                RebarArgument.of("state", Component.translatable(lastActive
                        ? "steamwork.gui.pneumatic_logic_gate.state.passing"
                        : "steamwork.gui.pneumatic_logic_gate.state.blocked")
                        .color(lastActive ? NamedTextColor.GREEN : NamedTextColor.GRAY))
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

    // ── 朝向指示：居中的半大小比较器 BlockDisplay ───────────────────────────────

    private void refreshDisplay() {
        clearDisplays();
        BlockDisplay display = createComparatorDisplay();
        displayUuids = List.of(display.getUniqueId());
    }

    private @NotNull BlockDisplay createComparatorDisplay() {
        BlockData data = Material.COMPARATOR.createBlockData();
        BlockFace facing = getFacing();
        // 比较器朝向 = 门的输出方向（正前），仅水平面有效
        if (data instanceof Directional dir
                && facing != BlockFace.UP && facing != BlockFace.DOWN
                && dir.getFaces().contains(facing)) {
            dir.setFacing(facing);
        }
        BlockDisplay display = new BlockDisplayBuilder()
                .blockData(data)
                .transformation(new TransformBuilder().scale(0.5).translate(0, 0.5, 0))
                .persistent(true)
                .build(center());
        markDisplay(display);
        return display;
    }

    private @NotNull Location center() {
        return getBlock().getLocation().toCenterLocation();
    }

    private void clearDisplays() {
        for (Display display : findManagedDisplays()) {
            if (display.isValid()) display.remove();
        }
        displayUuids = List.of();
    }

    private void markDisplay(@NotNull Entity display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN, true);
        pdc.set(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY, new int[] {
                getBlock().getX(), getBlock().getY(), getBlock().getZ()
        });
    }

    private @NotNull List<Display> findManagedDisplays() {
        BoundingBox box = BoundingBox.of(getBlock()).expand(DISPLAY_SCAN_RADIUS);
        List<Display> displays = new ArrayList<>();
        for (Entity entity : getBlock().getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof Display display)) continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!Boolean.TRUE.equals(pdc.get(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN))) continue;
            int[] owner = pdc.get(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY);
            if (owner == null || owner.length != 3) continue;
            if (owner[0] == getBlock().getX() && owner[1] == getBlock().getY() && owner[2] == getBlock().getZ()) {
                displays.add(display);
            }
        }
        return displays;
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

    /** 当前 steamKind 过滤的显示文本（null → 全部）。 */
    private @NotNull Component steamKindLabel() {
        return steamKind != null
                ? steamKind.component()
                : Component.translatable("steamwork.gui.common.steam_kind.all");
    }

    private final class SteamKindItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.NETHER_STAR)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            if (steamKind == null) {
                steamKind = SteamKind.STEAM;
            } else {
                SteamKind[] kinds = SteamKind.values();
                int next = steamKind.ordinal() + 1;
                steamKind = (next >= kinds.length) ? null : kinds[next];
            }
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
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.layout_hint")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel())))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }
}
