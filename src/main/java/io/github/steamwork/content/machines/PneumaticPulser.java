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
import org.bukkit.block.data.type.Repeater;
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
 * 汽动脉冲器——积攒蒸汽到阈值后一次性释放，周期性产生蒸汽脉冲。
 *
 * <ul>
 *   <li><b>正后</b> = 充汽输入（缓慢积攒到蓄能阈值）</li>
 *   <li><b>正前</b>（朝向）= 脉冲输出（达到阈值时释放）</li>
 * </ul>
 * 三种蒸汽各自独立积攒、独立释放。接口面随放置朝向旋转，也可在 GUI 中手动旋转
 * （北 → 东 → 南 → 西）；顶部重复器 BlockDisplay 的延迟刻度显示蓄能进度，朝向同步。
 * 典型用途：定时触发锁存器的 SET 信号、驱动周期性的生产节拍。
 */
public class PneumaticPulser extends RebarBlock implements
        EntityCulledRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private static final SteamKind[] KINDS = SteamKind.values();

    private static final String KEY_CHARGE_THRESHOLD  = "pls_charge";
    private static final String KEY_DISCHARGE_AMOUNT  = "pls_discharge";
    private static final String KEY_KIND              = "pls_kind"; // -1=全部, 0/1/2=SteamKind ordinal
    private static final String KEY_FACING            = "pls_facing"; // 基准朝向（正前 = NORTH 经此旋转）
    // 每种蒸汽的积攒量：pls_acc0, pls_acc1, pls_acc2
    private static final String KEY_ACC_PREFIX        = "pls_acc";

    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_pulser_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_pulser_display_owner");

    private final int    tickInterval          = getSettings().get("tick-interval",       ConfigAdapter.INTEGER, 2);
    private final double chargeRate            = getSettings().get("charge-rate",         ConfigAdapter.DOUBLE,  50.0);
    private final double defaultChargeThreshold= getSettings().get("charge-threshold",    ConfigAdapter.DOUBLE, 500.0);
    private final double defaultDischargeAmount= getSettings().get("discharge-amount",    ConfigAdapter.DOUBLE, 200.0);
    private final double outputBufferCapacity  = getSettings().get("output-buffer",       ConfigAdapter.DOUBLE, 500.0);

    /** 每种蒸汽的私有积攒量（不在流体网络中，手动管理）。 */
    private final double[] accumulators = new double[KINDS.length];
    private double    chargeThreshold;
    private double    dischargeAmount;
    private @Nullable SteamKind steamKind = null;
    /** 基准朝向：正前（输出）方向。流体点、逻辑面、顶部 display 均由此推导。 */
    private @NotNull  BlockFace facing = BlockFace.NORTH;

    /** 脉冲刚触发后保持几 tick 让重复器显示"放行"状态。 */
    private int    pulseCooldown   = 0;
    /** 上次刷新 display 时的状态，避免每 tick 更新 entity。 */
    private int    lastDisplayDelay   = 1;
    private boolean lastDisplayPowered = false;

    private volatile List<UUID> displayUuids = List.of();

    private final ChargeItem    chargeItem    = new ChargeItem();
    private final DischargeItem dischargeItem = new DischargeItem();
    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final FacingItem    facingItem    = new FacingItem();
    private final StatusItem    statusItem    = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticPulser(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        facing = context.getFacing();
        chargeThreshold = defaultChargeThreshold;
        dischargeAmount = defaultDischargeAmount;
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT,  BlockFace.NORTH, context, false, 0.5f);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false, 0.5f);
        for (SteamKind kind : KINDS) {
            createFluidBuffer(kind.fluid(), outputBufferCapacity, false, true);
        }
    }

    @SuppressWarnings("unused")
    public PneumaticPulser(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        chargeThreshold = pdc.getOrDefault(steamworkKey(KEY_CHARGE_THRESHOLD),
                PersistentDataType.DOUBLE, defaultChargeThreshold);
        dischargeAmount = pdc.getOrDefault(steamworkKey(KEY_DISCHARGE_AMOUNT),
                PersistentDataType.DOUBLE, defaultDischargeAmount);
        int kindOrd = pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, -1);
        SteamKind[] kinds = SteamKind.values();
        steamKind = (kindOrd >= 0 && kindOrd < kinds.length) ? kinds[kindOrd] : null;
        facing = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_FACING), BlockFace.NORTH);
        for (int i = 0; i < KINDS.length; i++) {
            accumulators[i] = pdc.getOrDefault(
                    steamworkKey(KEY_ACC_PREFIX + i), PersistentDataType.DOUBLE, 0.0);
        }
        setTickInterval(tickInterval);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_CHARGE_THRESHOLD), PersistentDataType.DOUBLE, chargeThreshold);
        pdc.set(steamworkKey(KEY_DISCHARGE_AMOUNT), PersistentDataType.DOUBLE, dischargeAmount);
        pdc.set(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, steamKind != null ? steamKind.ordinal() : -1);
        pdc.set(steamworkKey(KEY_FACING), PersistentDataType.STRING, facing.name());
        for (int i = 0; i < KINDS.length; i++) {
            pdc.set(steamworkKey(KEY_ACC_PREFIX + i), PersistentDataType.DOUBLE, accumulators[i]);
        }
    }

    @Override public void postInitialise() { refreshDisplay(true); }

    @Override public @NotNull Iterable<UUID> getCulledEntityIds() { return displayUuids; }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearDisplays();
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (pulseCooldown > 0) pulseCooldown--;

        FluidBufferRebarBlock input = SteamLogicSupport.fluidNeighbor(
                getBlock(), SteamLogicSupport.rotate(facing, BlockFace.NORTH));

        boolean pulsed = false;
        for (int i = 0; i < KINDS.length; i++) {
            if (steamKind != null && KINDS[i] != steamKind) continue;
            SteamKind kind = KINDS[i];

            // 从输入端抽汽进私有积攒桶
            if (input != null) {
                double avail  = SteamLogicSupport.amount(input, kind);
                double space  = chargeThreshold - accumulators[i];
                double pullAmt = Math.min(chargeRate, Math.min(avail, space));
                if (pullAmt > 0) {
                    input.removeFluid(kind.fluid(), pullAmt);
                    accumulators[i] += pullAmt;
                }
            }

            // 积满 → 脉冲释放到输出缓存
            if (accumulators[i] >= chargeThreshold) {
                double toRelease = Math.min(dischargeAmount, fluidSpaceRemaining(kind.fluid()));
                if (toRelease > 0) addFluid(kind.fluid(), toRelease);
                accumulators[i] = 0;
                pulsed = true;
            }
        }

        if (pulsed) pulseCooldown = 3;
        refreshDisplay(false);
        notifyGuiItems();
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        return super.getBlockTextureProperties();
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void notifyGuiItems() {
        chargeItem.notifyWindows();
        dischargeItem.notifyWindows();
        steamKindItem.notifyWindows();
        facingItem.notifyWindows();
        statusItem.notifyWindows();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# C D # O # K S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('C', chargeItem)
                .addIngredient('D', dischargeItem)
                .addIngredient('O', facingItem)
                .addIngredient('K', steamKindItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        double maxRatio;
        if (steamKind != null) {
            maxRatio = accumulators[steamKind.ordinal()] / chargeThreshold;
        } else {
            maxRatio = 0;
            for (int i = 0; i < KINDS.length; i++) {
                maxRatio = Math.max(maxRatio, accumulators[i] / chargeThreshold);
            }
        }
        int pct = (int) (maxRatio * 100);
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("charge", pct),
                RebarArgument.of("kind", steamKindLabel()),
                RebarArgument.of("state", Component.translatable(pulseCooldown > 0
                        ? "steamwork.gui.pneumatic_pulser.state.pulsing"
                        : "steamwork.gui.pneumatic_pulser.state.charging")
                        .color(pulseCooldown > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
        ));
    }

    // ── Display：顶部重复器，延迟刻度 = 蓄能进度（1-4），放行瞬间 powered=true ──

    private void refreshDisplay(boolean force) {
        double maxRatio = 0;
        for (int i = 0; i < KINDS.length; i++) {
            maxRatio = Math.max(maxRatio, accumulators[i] / chargeThreshold);
        }
        int     newDelay   = Math.max(1, Math.min(4, 1 + (int) (maxRatio * 3.999)));
        boolean newPowered = pulseCooldown > 0;

        if (!force && newDelay == lastDisplayDelay && newPowered == lastDisplayPowered) return;
        lastDisplayDelay   = newDelay;
        lastDisplayPowered = newPowered;

        if (!displayUuids.isEmpty()) {
            Entity e = getBlock().getWorld().getEntity(displayUuids.get(0));
            if (e instanceof BlockDisplay bd) {
                bd.setBlock(buildRepeaterData(newDelay, newPowered));
                return;
            }
        }
        // 实体丢失则重建
        clearDisplays();
        BlockDisplay bd = new BlockDisplayBuilder()
                .blockData(buildRepeaterData(newDelay, newPowered))
                .transformation(new TransformBuilder().scale(0.5).translate(0, 1.5, 0))
                .persistent(true)
                .build(center());
        markDisplay(bd);
        displayUuids = List.of(bd.getUniqueId());
    }

    private @NotNull BlockData buildRepeaterData(int delay, boolean powered) {
        BlockData data = Material.REPEATER.createBlockData();
        if (data instanceof Repeater rep) {
            // 重复器朝向 = 脉冲输出（正前）方向 = facing 的反面（facing 指向后端输入）
            BlockFace out = facing.getOppositeFace();
            if (rep.getFaces().contains(out)) rep.setFacing(out);
            rep.setDelay(delay);
            rep.setLocked(false);
            rep.setPowered(powered);
        }
        return data;
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
        removePoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.NORTH));
        removePoint(FluidPointType.OUTPUT, SteamLogicSupport.rotate(facing, BlockFace.SOUTH));
        facing = newFacing;
        createFluidPoint(FluidPointType.INPUT,  SteamLogicSupport.rotate(facing, BlockFace.NORTH), 0.5f);
        createFluidPoint(FluidPointType.OUTPUT, SteamLogicSupport.rotate(facing, BlockFace.SOUTH), 0.5f);
        refreshDisplay(true);
    }

    private void removePoint(@NotNull FluidPointType type, @NotNull BlockFace face) {
        String name = SteamLogicSupport.fluidPointName(type, face);
        Entity e = getHeldEntity(name);
        if (e != null) e.remove();          // 触发 onDeath → FluidManager.remove，清理网络
        getHeldEntities().remove(name);     // 立即清空持有表，避免重建时名称冲突
    }

    /** 当前蓄能进度（受 steamKind 过滤影响）。 */
    private double chargeRatio() {
        if (steamKind != null) return accumulators[steamKind.ordinal()] / chargeThreshold;
        double max = 0;
        for (int i = 0; i < KINDS.length; i++) max = Math.max(max, accumulators[i] / chargeThreshold);
        return max;
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

    private final class ChargeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            double maxRatio = chargeRatio();
            return ItemStackBuilder.of(Material.REPEATER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.charge_threshold")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.charge_current",
                                    RebarArgument.of("pct", String.valueOf((int)(maxRatio * 100))))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.charge_value",
                                    RebarArgument.of("value", String.valueOf((int) chargeThreshold)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.charge_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            double step = (t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT) ? 100.0 : 10.0;
            if (t == ClickType.LEFT || t == ClickType.SHIFT_LEFT) {
                chargeThreshold = Math.min(10000.0, chargeThreshold + step);
            } else if (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) {
                chargeThreshold = Math.max(10.0, chargeThreshold - step);
            }
            notifyGuiItems();
        }
    }

    private final class DischargeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.LIGHTNING_ROD)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.discharge_amount")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.discharge_value",
                                    RebarArgument.of("value", String.valueOf((int) dischargeAmount)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.discharge_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            double step = (t == ClickType.SHIFT_LEFT || t == ClickType.SHIFT_RIGHT) ? 100.0 : 10.0;
            if (t == ClickType.LEFT || t == ClickType.SHIFT_LEFT) {
                dischargeAmount = Math.min(outputBufferCapacity, dischargeAmount + step);
            } else if (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) {
                dischargeAmount = Math.max(10.0, dischargeAmount - step);
            }
            notifyGuiItems();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            boolean isPulsing = pulseCooldown > 0;
            return ItemStackBuilder.of(isPulsing ? Material.GREEN_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE)
                    .name(SteamLogicSupport.ni(Component.translatable(isPulsing
                            ? "steamwork.gui.pneumatic_pulser.state.pulsing"
                            : "steamwork.gui.pneumatic_pulser.state.charging")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pulser.layout_hint")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel())))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }
}
