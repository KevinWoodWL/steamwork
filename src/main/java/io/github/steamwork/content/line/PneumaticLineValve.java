package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkResearches;
import io.github.steamwork.util.SteamLogicSupport;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 气动产线阀 —— 汽动逻辑与产线系统的桥接方块（逻辑→物品）。
 *
 * <p>实现 {@link ProductionLineMember}，可作为产线的一环。
 * 信号面读取相邻蒸汽压力，≥阈值时允许内部缓存的物品向下游推送；
 * 低于阈值时阻断物品流动（相当于用蒸汽信号控制产线物品通断）。</p>
 *
 * <ul>
 *   <li>朝向 = 物品输出方向（产线方向）</li>
 *   <li>信号面：默认为方块背面（可在 GUI 切换为六向之一）</li>
 *   <li>1 格物品缓存：接受上游 acceptFromLine 推入</li>
 *   <li>tick 时检查信号面相邻的 FluidBufferRebarBlock 蒸汽量 ≥ 阈值即放行</li>
 * </ul>
 */
public class PneumaticLineValve extends RebarBlock implements
        DirectionalRebarBlock, FluidBufferRebarBlock,
        GuiRebarBlock, TickingRebarBlock, ProductionLineMember {

    // ===== 常量 =====

    private static final int MAX_GAP = 4;

    private static final RebarFluid[] STEAMS = {
        SteamworkFluids.STEAM, SteamworkFluids.SUPERHEATED_STEAM, SteamworkFluids.PRESSURIZED_STEAM
    };
    private static final String KEY_SIGNAL_FACE = "plv_signal_face";
    private static final String KEY_THRESHOLD   = "plv_threshold";
    private static final String KEY_STEAM_KIND  = "plv_kind";

    private final int tickInterval       = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 2);
    private final double defaultThreshold = getSettings().get("signal-threshold", ConfigAdapter.DOUBLE, 1.0);
    private final double bufferCapacity   = getSettings().get("buffer", ConfigAdapter.DOUBLE, 500.0);

    // ===== 状态 =====

    private @NotNull BlockFace signalFace = BlockFace.NORTH; // 标准面：放置后根据朝向旋转
    private double signalThreshold;
    private @Nullable SteamKind steamKind = null;
    private boolean open = false;
    private @Nullable ItemStack heldItem = null;

    // 产线成员字段
    @Nullable private UUID lineId = null;
    private int linePosition = 0;
    @NotNull private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String lineCreator = null;
    private int lineNumber = 0;
    private boolean lineJammed = false;

    // GUI 元素
    private final SignalFaceItem signalFaceItem = new SignalFaceItem();
    private final ThresholdItem thresholdItem = new ThresholdItem();
    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final StatusItem statusItem = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }

        @Override
        public boolean prePlace(@NotNull BlockCreateContext context) {
            // 货运 × 逻辑 桥：货运研究由框架按物品门控，这里额外要求已解锁汽动逻辑研究
            if (SteamworkResearches.denyLineBridgePlacement(context.getPlayer())) {
                return false;
            }
            return super.prePlace(context);
        }
    }

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public PneumaticLineValve(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing().getOppositeFace());
        signalThreshold = defaultThreshold;
        setTickInterval(tickInterval);
        // 流体端点：仅用于信号检测，让 FluidBufferRebarBlock 满足接口
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false, 0.5f);
        for (RebarFluid fluid : STEAMS) {
            createFluidBuffer(fluid, bufferCapacity, false, false);
        }
    }

    @SuppressWarnings("unused")
    public PneumaticLineValve(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(tickInterval);
        signalThreshold = pdc.getOrDefault(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, defaultThreshold);
        signalFace = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_SIGNAL_FACE), BlockFace.NORTH);
        int kindOrd = pdc.getOrDefault(steamworkKey(KEY_STEAM_KIND), PersistentDataType.INTEGER, -1);
        SteamKind[] kinds = SteamKind.values();
        steamKind = (kindOrd >= 0 && kindOrd < kinds.length) ? kinds[kindOrd] : null;
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.NORTH); }

        // 产线信息
        String id = pdc.get(LINE_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            try {
                lineId = UUID.fromString(id);
                linePosition = pdc.getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
                String dir = pdc.get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
                try { lineDirection = dir != null ? BlockFace.valueOf(dir) : BlockFace.SELF; }
                catch (IllegalArgumentException ignored) { lineDirection = BlockFace.SELF; }
                lineCreator = pdc.get(LINE_CREATOR_KEY, PersistentDataType.STRING);
                lineNumber = pdc.getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
            } catch (IllegalArgumentException ignored) { lineId = null; }
        }

        // 恢复缓存物品
        byte[] itemBytes = pdc.get(steamworkKey("plv_item"), PersistentDataType.BYTE_ARRAY);
        if (itemBytes != null) {
            try { heldItem = ItemStack.deserializeBytes(itemBytes); } catch (Exception ignored) {}
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, signalThreshold);
        pdc.set(steamworkKey(KEY_SIGNAL_FACE), PersistentDataType.STRING, signalFace.name());
        pdc.set(steamworkKey(KEY_STEAM_KIND), PersistentDataType.INTEGER, steamKind != null ? steamKind.ordinal() : -1);
        if (lineId != null) {
            pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
            pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, lineDirection.name());
            pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, lineCreator);
            else pdc.remove(LINE_CREATOR_KEY);
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
        if (heldItem != null && !heldItem.isEmpty()) {
            pdc.set(steamworkKey("plv_item"), PersistentDataType.BYTE_ARRAY, heldItem.serializeAsBytes());
        } else {
            pdc.remove(steamworkKey("plv_item"));
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        if (heldItem != null && !heldItem.isEmpty()) {
            drops.add(heldItem);
            heldItem = null;
        }
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== Tick =====

    @Override
    public void tick() {
        if (lineJammed) {
            open = false;
            notifyGuiItems();
            return;
        }
        // 检查信号面
        BlockFace worldSignalFace = SteamLogicSupport.rotate(getFacing(), signalFace);
        FluidBufferRebarBlock neighbor = SteamLogicSupport.fluidNeighbor(getBlock(), worldSignalFace);
        open = hasSignal(neighbor);

        if (open && heldItem != null && !heldItem.isEmpty() && isInLine() && lineDirection != BlockFace.SELF) {
            ItemStack remaining = ProductionLineMember.deliverToNextMember(getBlock(), this, heldItem);
            heldItem = remaining.isEmpty() ? null : remaining;
        }
        notifyGuiItems();
    }

    private boolean hasSignal(@Nullable FluidBufferRebarBlock neighbor) {
        if (neighbor == null) return false;
        RebarFluid[] toCheck = (steamKind != null) ? new RebarFluid[]{steamKind.fluid()} : STEAMS;
        for (RebarFluid fluid : toCheck) {
            if (neighbor.hasFluid(fluid) && neighbor.fluidAmount(fluid) >= signalThreshold) return true;
        }
        return false;
    }

    private void notifyGuiItems() {
        signalFaceItem.notifyWindows();
        thresholdItem.notifyWindows();
        steamKindItem.notifyWindows();
        statusItem.notifyWindows();
    }

    // ===== ProductionLineMember =====

    @Override public @Nullable UUID getLineId() { return lineId; }
    @Override public int getLinePosition() { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection() { return lineDirection; }
    @Override public @Nullable String getLineCreator() { return lineCreator; }
    @Override public int getLineNumber() { return lineNumber; }
    @Override public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }
    @Override public void setLineNumber(int number) { this.lineNumber = number; }

    @Override
    public void joinLine(@NotNull UUID id, int position, @NotNull BlockFace direction) {
        this.lineId = id;
        this.linePosition = position;
        this.lineDirection = direction;
    }

    @Override
    public void leaveLine() {
        this.lineId = null;
        this.linePosition = 0;
        this.lineDirection = BlockFace.SELF;
        this.lineCreator = null;
        this.lineNumber = 0;
        this.lineJammed = false;
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (heldItem != null && !heldItem.isEmpty()) return false;
        heldItem = item.clone();
        return true;
    }

    @Override public void onLineJammed() { this.lineJammed = true; }
    @Override public void onLineResumed() { this.lineJammed = false; }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# F # K # T # S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('F', signalFaceItem)
                .addIngredient('K', steamKindItem)
                .addIngredient('T', thresholdItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return ni(Component.translatable("steamwork.gui.pneumatic_line_valve.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(Component.translatable(open
                        ? "steamwork.gui.pneumatic_line_valve.state.open"
                        : "steamwork.gui.pneumatic_line_valve.state.closed")
                        .color(open ? NamedTextColor.GREEN : NamedTextColor.RED))
                .add(steamKindLabel());
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(open), 2));
        return props;
    }

    private @NotNull Component steamKindLabel() {
        return steamKind != null
                ? steamKind.component()
                : Component.translatable("steamwork.gui.common.steam_kind.all");
    }

    // ===== GUI 内部类 =====

    private final class SignalFaceItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.OBSERVER)
                    .name(ni(Component.translatable("steamwork.gui.pneumatic_line_valve.signal_face")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", SteamLogicSupport.faceComponent(signalFace)))),
                            ni(Component.translatable("steamwork.gui.pneumatic_line_valve.signal_face_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            signalFace = SteamLogicSupport.nextHorizontal(signalFace);
            notifyGuiItems();
        }
    }

    private final class ThresholdItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.REPEATER)
                    .name(ni(Component.translatable("steamwork.gui.pneumatic_line_valve.threshold")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_value",
                                    RebarArgument.of("value", String.valueOf((int) signalThreshold)))),
                            ni(Component.translatable("steamwork.gui.pneumatic_line_valve.threshold_desc")),
                            ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_hint"))
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

    private final class SteamKindItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.NETHER_STAR)
                    .name(ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel()))),
                            ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            if (steamKind == null) { steamKind = SteamKind.STEAM; }
            else {
                SteamKind[] kinds = SteamKind.values();
                int next = steamKind.ordinal() + 1;
                steamKind = (next >= kinds.length) ? null : kinds[next];
            }
            notifyGuiItems();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(open ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                    .name(ni(Component.translatable(open
                            ? "steamwork.gui.pneumatic_line_valve.state.open"
                            : "steamwork.gui.pneumatic_line_valve.state.closed")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.pneumatic_line_valve.status_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
