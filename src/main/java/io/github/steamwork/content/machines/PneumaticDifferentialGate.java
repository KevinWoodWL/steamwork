package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.block.interfaces.EntityCulledRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
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
 * 汽动差压门——比较两路蒸汽的量，差值 ≥ 阈值时才放行。
 *
 * <ul>
 *   <li><b>正前</b>（朝向）= 输出</li>
 *   <li><b>正后</b> = 主供汽源（A），差压成立时从此抽汽搬入自身缓存</li>
 *   <li><b>右侧</b> = 参考压力（B），仅读取蒸汽量，不消耗</li>
 * </ul>
 * 条件：A 的蒸汽量 − B 的蒸汽量 ≥ 阈值 → 放行。三种蒸汽各自独立计算。
 * 接口面随放置朝向旋转，也可在 GUI 中手动旋转（北 → 东 → 南 → 西）；
 * 顶部 Observer BlockDisplay 朝向同步。
 * 典型用途：仅当锅炉（A）远比下游机器（B）蓄汽充足时才补汽，防止倒灌。
 */
public class PneumaticDifferentialGate extends RebarBlock implements
        EntityCulledRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private static final String KEY_THRESHOLD      = "pdg_threshold";
    private static final String KEY_KIND           = "pdg_kind"; // -1=全部, 0/1/2=SteamKind ordinal
    private static final String KEY_FACING         = "pdg_facing"; // 基准朝向（正前 = NORTH 经此旋转）

    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_diff_gate_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_diff_gate_display_owner");

    private final int    tickInterval      = getSettings().get("tick-interval",    ConfigAdapter.INTEGER, 2);
    private final double transferPerTick   = getSettings().get("transfer-per-tick",ConfigAdapter.DOUBLE,  50.0);
    private final double defaultThreshold  = getSettings().get("signal-threshold", ConfigAdapter.DOUBLE,  100.0);
    private final double bufferCapacity    = getSettings().get("buffer",           ConfigAdapter.DOUBLE,  1000.0);

    private double    threshold;
    private @Nullable SteamKind steamKind = null;
    /** 基准朝向：正前（输出）方向。流体点、逻辑面、顶部 display 均由此推导。 */
    private @NotNull  BlockFace facing = BlockFace.NORTH;
    private boolean   lastActive = false;
    private volatile List<UUID> displayUuids = List.of();

    private final ThresholdItem thresholdItem = new ThresholdItem();
    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final FacingItem    facingItem    = new FacingItem();
    private final StatusItem    statusItem    = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticDifferentialGate(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        facing = context.getFacing();
        threshold = defaultThreshold;
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false, 0.5f);
        createFluidPoint(FluidPointType.INPUT,  BlockFace.NORTH, context, false, 0.5f);
        createFluidPoint(FluidPointType.INPUT,  BlockFace.EAST,  context, false, 0.5f);
        for (SteamKind kind : SteamKind.values()) {
            createFluidBuffer(kind.fluid(), bufferCapacity, false, true);
        }
    }

    @SuppressWarnings("unused")
    public PneumaticDifferentialGate(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        threshold = pdc.getOrDefault(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, defaultThreshold);
        int kindOrd = pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, -1);
        SteamKind[] kinds = SteamKind.values();
        steamKind = (kindOrd >= 0 && kindOrd < kinds.length) ? kinds[kindOrd] : null;
        facing = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_FACING), BlockFace.NORTH);
        setTickInterval(tickInterval);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_THRESHOLD), PersistentDataType.DOUBLE, threshold);
        pdc.set(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, steamKind != null ? steamKind.ordinal() : -1);
        pdc.set(steamworkKey(KEY_FACING), PersistentDataType.STRING, facing.name());
    }

    @Override public void postInitialise() { refreshDisplay(); }

    @Override public @NotNull Iterable<UUID> getCulledEntityIds() { return displayUuids; }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearDisplays();
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ── 面布局（放置时朝向玩家=后端=输入）：SOUTH=输出(正前/背离玩家)，NORTH=源A(后端)，EAST=参考B ──

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        boolean moved = false;
        FluidBufferRebarBlock srcA = SteamLogicSupport.fluidNeighbor(
                getBlock(), SteamLogicSupport.rotate(facing, BlockFace.NORTH));
        FluidBufferRebarBlock refB = SteamLogicSupport.fluidNeighbor(
                getBlock(), SteamLogicSupport.rotate(facing, BlockFace.EAST));
        if (srcA != null) {
            SteamKind[] toProcess = steamKind != null ? new SteamKind[]{steamKind} : SteamKind.values();
            for (SteamKind kind : toProcess) {
                double amtA = SteamLogicSupport.amount(srcA, kind);
                double amtB = refB != null ? SteamLogicSupport.amount(refB, kind) : 0.0;
                if (amtA - amtB >= threshold) {
                    moved |= pull(srcA, kind) > 0;
                }
            }
        }
        setActive(moved);
        notifyGuiItems();
    }

    private double pull(@NotNull FluidBufferRebarBlock src, @NotNull SteamKind kind) {
        if (!src.hasFluid(kind.fluid()) || !hasFluid(kind.fluid())) return 0;
        double amt = Math.min(transferPerTick,
                Math.min(src.fluidAmount(kind.fluid()), fluidSpaceRemaining(kind.fluid())));
        if (amt <= 0) return 0;
        src.removeFluid(kind.fluid(), amt);
        addFluid(kind.fluid(), amt);
        return amt;
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

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void notifyGuiItems() {
        thresholdItem.notifyWindows();
        steamKindItem.notifyWindows();
        facingItem.notifyWindows();
        statusItem.notifyWindows();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# T # K # O # S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('T', thresholdItem)
                .addIngredient('K', steamKindItem)
                .addIngredient('O', facingItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_differential_gate.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("threshold", String.valueOf((int) threshold)),
                RebarArgument.of("kind", steamKindLabel()),
                RebarArgument.of("state", Component.translatable(lastActive
                        ? "steamwork.gui.pneumatic_logic_gate.state.passing"
                        : "steamwork.gui.pneumatic_logic_gate.state.blocked")
                        .color(lastActive ? NamedTextColor.GREEN : NamedTextColor.GRAY))
        ));
    }

    // ── Display：顶部 Observer（半大小）表示面朝向 ────────────────────────────

    private void refreshDisplay() {
        clearDisplays();
        BlockData data = Material.OBSERVER.createBlockData();
        // 观察者朝向 = 输出（正前）方向 = facing 的反面（facing 指向后端输入）
        BlockFace out = facing.getOppositeFace();
        if (data instanceof Directional dir && dir.getFaces().contains(out)) {
            dir.setFacing(out);
        }
        BlockDisplay display = new BlockDisplayBuilder()
                .blockData(data)
                .transformation(new TransformBuilder().scale(0.5).translate(0, 1.5, 0))
                .persistent(true)
                .build(center());
        markDisplay(display);
        displayUuids = List.of(display.getUniqueId());
    }

    private @NotNull Location center() { return getBlock().getLocation().toCenterLocation(); }

    private void clearDisplays() {
        for (Display d : findManagedDisplays()) if (d.isValid()) d.remove();
        displayUuids = List.of();
    }

    private void markDisplay(@NotNull Entity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN, true);
        pdc.set(DISPLAY_OWNER_KEY,  RebarSerializers.INTEGER_ARRAY,
                new int[]{getBlock().getX(), getBlock().getY(), getBlock().getZ()});
    }

    private @NotNull List<Display> findManagedDisplays() {
        BoundingBox box = BoundingBox.of(getBlock()).expand(DISPLAY_SCAN_RADIUS);
        List<Display> result = new ArrayList<>();
        for (Entity e : getBlock().getWorld().getNearbyEntities(box)) {
            if (!(e instanceof Display d)) continue;
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            if (!Boolean.TRUE.equals(pdc.get(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN))) continue;
            int[] owner = pdc.get(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY);
            if (owner != null && owner.length == 3
                    && owner[0] == getBlock().getX()
                    && owner[1] == getBlock().getY()
                    && owner[2] == getBlock().getZ()) {
                result.add(d);
            }
        }
        return result;
    }

    // ── GUI 内部类 ─────────────────────────────────────────────────────────────

    private @NotNull Component steamKindLabel() {
        return steamKind != null
                ? steamKind.component()
                : Component.translatable("steamwork.gui.common.steam_kind.all");
    }

    /** GUI 手动旋转朝向：删除旧端点 → 更新基准 → 按新朝向重建端点 → 刷新顶部 display。 */
    private void rotateFacing() {
        BlockFace newFacing = SteamLogicSupport.nextHorizontal(facing);
        removePoint(FluidPointType.OUTPUT, SteamLogicSupport.rotate(facing, BlockFace.SOUTH));
        removePoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.NORTH));
        removePoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.EAST));
        facing = newFacing;
        createFluidPoint(FluidPointType.OUTPUT, SteamLogicSupport.rotate(facing, BlockFace.SOUTH), 0.5f);
        createFluidPoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.NORTH), 0.5f);
        createFluidPoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.EAST), 0.5f);
        refreshDisplay();
    }

    private void removePoint(@NotNull FluidPointType type, @NotNull BlockFace face) {
        String name = SteamLogicSupport.fluidPointName(type, face);
        Entity e = getHeldEntity(name);
        if (e != null) e.remove();          // 触发 onDeath → FluidManager.remove，清理网络
        getHeldEntities().remove(name);     // 立即清空持有表，避免重建时名称冲突
    }

    private final class FacingItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.COMPASS)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.orientation")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", SteamLogicSupport.faceComponent(facing.getOppositeFace())))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.orientation_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            rotateFacing();
            notifyGuiItems();
        }
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

    private final class ThresholdItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.OBSERVER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_differential_gate.threshold")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.threshold_value",
                                    RebarArgument.of("value", String.valueOf((int) threshold)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_differential_gate.threshold_desc")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_differential_gate.threshold_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            double step = (t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT) ? 100.0 : 10.0;
            if (t == ClickType.LEFT || t == ClickType.SHIFT_LEFT) {
                threshold = Math.min(10000.0, threshold + step);
            } else if (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) {
                threshold = Math.max(1.0, threshold - step);
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
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_differential_gate.layout_hint")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel())))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }
}
