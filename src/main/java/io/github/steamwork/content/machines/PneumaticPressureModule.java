package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.EntityCulledRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.Steamwork;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.SteamLogicSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 气动管道模块 —— 嵌在气动导管链中段的在线节点（借鉴 PneumaticCraft 管道模块）。
 *
 * <p>像 {@link PneumaticGateValve} 一样占据一个方块位、有朝向、参与气动网络遍历；底材为
 * {@link Material#BARREL}，因此能复用「桶内填占位物 → 比较器读取」机制输出 0–15 模拟红石。</p>
 *
 * <p>三种模式（GUI 切换），全部作用于<b>经过本模块的物品流</b>：</p>
 * <ul>
 *   <li>{@link Mode#FLOW_METER} 流量计：滑窗统计途经物品速率 → 0–15 红石。</li>
 *   <li>{@link Mode#THROTTLE} 限流阀：限制每个窗口通过的物品数，配额耗尽则本窗口断开网络。</li>
 *   <li>{@link Mode#OVERFLOW_ALARM} 溢流报警：途经传输因下游满载被拒时输出红石报警。</li>
 * </ul>
 *
 * <p>物品流由 {@link PneumaticOutput}/{@link SteamSorter}/{@link PneumaticDistributor} 驱动，
 * 经 {@link PneumaticDuct#notifyPassage} 在传输成功/被拒时回调本模块。</p>
 */
public class PneumaticPressureModule extends RebarBlock implements
        BlockBreakRebarBlockHandler,
        DirectionalRebarBlock,
        EntityCulledRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    public enum Mode {
        FLOW_METER("steamwork.gui.pneumatic_pressure_module.mode.flow_meter", TextColor.color(0x4caf50)),
        THROTTLE("steamwork.gui.pneumatic_pressure_module.mode.throttle", TextColor.color(0xffd166)),
        OVERFLOW_ALARM("steamwork.gui.pneumatic_pressure_module.mode.overflow_alarm", TextColor.color(0xff5555));

        private final String translationKey;
        private final TextColor color;

        Mode(@NotNull String translationKey, @NotNull TextColor color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        @NotNull Component component() {
            return Component.translatable(translationKey).color(color);
        }

        @NotNull Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static @NotNull Mode fromOrdinal(int ordinal) {
            Mode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FLOW_METER;
        }
    }

    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final double THICKNESS = 0.35D;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_pressure_module_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY = steamworkKey("pneumatic_pressure_module_display_owner");
    private static final NamespacedKey SIGNAL_ITEM_KEY = steamworkKey("pneumatic_pressure_module_signal_item");

    private static final String KEY_MODE = "ppm_mode";
    private static final String KEY_THROTTLE_LIMIT = "ppm_throttle_limit";

    private static final int BARREL_SIZE = 27;
    private static final int MAX_STACK = 64;
    private static final int TOTAL_UNITS = BARREL_SIZE * MAX_STACK;

    // ===== settings =====
    private final int tickInterval = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 1);
    private final int flowWindowTicks = getSettings().getOrThrow("flow-window-ticks", ConfigAdapter.INTEGER);
    private final double flowRateFull = getSettings().getOrThrow("flow-rate-full", ConfigAdapter.DOUBLE);
    private final int throttleWindowTicks = getSettings().getOrThrow("throttle-window-ticks", ConfigAdapter.INTEGER);
    private final int defaultThrottleLimit = getSettings().getOrThrow("default-throttle-limit", ConfigAdapter.INTEGER);
    private final int minThrottleLimit = getSettings().getOrThrow("min-throttle-limit", ConfigAdapter.INTEGER);
    private final int maxThrottleLimit = getSettings().getOrThrow("max-throttle-limit", ConfigAdapter.INTEGER);
    private final int alarmDecayTicks = getSettings().getOrThrow("alarm-decay-ticks", ConfigAdapter.INTEGER);

    // ===== 配置 =====
    private Mode mode = Mode.FLOW_METER;
    private int throttleLimit;

    // ===== 运行态（不持久化，重载即重置）=====
    private int flowCounter = 0;
    private int flowWindowTick = 0;
    private double currentRate = 0.0;
    private int throttleUsed = 0;
    private int throttleWindowTick = 0;
    private boolean alarmActive = false;
    private int alarmTicksLeft = 0;
    private long totalPassed = 0;

    // ===== 比较器输出缓存 =====
    private int lastLevel = -1;
    private int lastWrittenUnits = -1;

    private volatile List<UUID> displayUuids = List.of();

    private final ModeItem modeItem = new ModeItem();
    private final ParamItem paramItem = new ParamItem();
    private final StatusItem statusItem = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    public PneumaticPressureModule(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        throttleLimit = clampThrottle(defaultThrottleLimit);
    }

    @SuppressWarnings("unused")
    public PneumaticPressureModule(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try {
            getFacing();
        } catch (IllegalStateException e) {
            setFacing(BlockFace.SOUTH);
        }
        setTickInterval(tickInterval);
        mode = Mode.fromOrdinal(pdc.getOrDefault(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, 0));
        throttleLimit = clampThrottle(pdc.getOrDefault(steamworkKey(KEY_THROTTLE_LIMIT), PersistentDataType.INTEGER, defaultThrottleLimit));
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, mode.ordinal());
        pdc.set(steamworkKey(KEY_THROTTLE_LIMIT), PersistentDataType.INTEGER, throttleLimit);
    }

    @Override
    public void postInitialise() {
        refreshDisplays();
        PneumaticDuct.notifyNeighboringDucts(getBlock());
        // 重载时邻居可能尚未注册，延迟一拍重算导管连接与显示
        Bukkit.getScheduler().runTaskLater(
                Steamwork.getInstance(),
                () -> {
                    if (!PneumaticEndpointSupport.isChunkLoaded(getBlock())) return;
                    if (PneumaticEndpointSupport.loadedRebarBlock(getBlock()) != this) return;
                    refreshDisplays();
                    PneumaticDuct.notifyNeighboringDucts(getBlock());
                },
                4L);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearBarrelInventory();
        clearDisplays();
    }

    @Override
    public void onPostBlockBreak(@NotNull BlockBreakContext context) {
        PneumaticDuct.notifyNeighboringDucts(getBlock());
    }

    @Override
    public @NotNull Iterable<UUID> getCulledEntityIds() {
        return displayUuids;
    }

    // ===== 气动网络接口 =====

    /** 通行轴：朝向与其反面。限流阀本窗口配额耗尽时整体断开。 */
    public boolean acceptsPneumaticConnection(@NotNull BlockFace face) {
        if (isThrottleBlocked()) return false;
        return face == getFacing() || face == getFacing().getOppositeFace();
    }

    public @NotNull List<BlockFace> getTraversalFaces() {
        if (isThrottleBlocked()) return List.of();
        return List.of(getFacing(), getFacing().getOppositeFace());
    }

    private boolean isThrottleBlocked() {
        return mode == Mode.THROTTLE && throttleUsed >= throttleLimit;
    }

    // ===== 传输事件回调（由 PneumaticDuct.notifyPassage 调用）=====

    /** 物品成功途经本模块。 */
    public void onItemsPassed(int count) {
        if (count <= 0) return;
        totalPassed += count;
        if (mode == Mode.FLOW_METER) {
            flowCounter += count;
        } else if (mode == Mode.THROTTLE) {
            throttleUsed += count;
        }
    }

    /** 途经传输因下游满载被拒。 */
    public void onTransferBlocked(int count) {
        if (count <= 0) return;
        if (mode == Mode.OVERFLOW_ALARM) {
            alarmActive = true;
            alarmTicksLeft = Math.max(1, alarmDecayTicks);
        }
    }

    // ===== tick =====

    @Override
    public void tick() {
        switch (mode) {
            case FLOW_METER -> {
                flowWindowTick++;
                if (flowWindowTick >= flowWindowTicks) {
                    // 速率 = 窗口内通过数 / 窗口秒数
                    double seconds = Math.max(1, flowWindowTicks) / 20.0;
                    currentRate = flowCounter / seconds;
                    flowCounter = 0;
                    flowWindowTick = 0;
                }
                setLevel(rateToLevel(currentRate));
            }
            case THROTTLE -> {
                throttleWindowTick++;
                if (throttleWindowTick >= throttleWindowTicks) {
                    throttleUsed = 0;
                    throttleWindowTick = 0;
                }
                // 红石指示：正在限流（配额耗尽）输出 15，否则 0
                setLevel(isThrottleBlocked() ? 15 : 0);
            }
            case OVERFLOW_ALARM -> {
                if (alarmTicksLeft > 0) {
                    alarmTicksLeft--;
                    if (alarmTicksLeft == 0) alarmActive = false;
                }
                setLevel(alarmActive ? 15 : 0);
            }
        }
        if (barrelInventoryDirty()) {
            writeComparatorInventory(Math.max(0, lastLevel));
        }
        modeItem.notifyWindows();
        paramItem.notifyWindows();
        statusItem.notifyWindows();
    }

    private int rateToLevel(double rate) {
        if (flowRateFull <= 0.0) return 0;
        double norm = Math.max(0.0, Math.min(1.0, rate / flowRateFull));
        return (int) Math.round(norm * 15.0);
    }

    private int clampThrottle(int v) {
        return Math.max(minThrottleLimit, Math.min(maxThrottleLimit, v));
    }

    // ===== 比较器占位物（复用差分机/压力传感器方案）=====

    private void setLevel(int level) {
        int clamped = Math.max(0, Math.min(15, level));
        if (clamped != lastLevel) {
            lastLevel = clamped;
            writeComparatorInventory(clamped);
        }
    }

    private boolean barrelInventoryDirty() {
        if (!(getBlock().getState() instanceof Container container)) return false;
        Inventory inventory = container.getInventory();
        int actualUnits = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (!isSignalItem(stack)) return true;
            actualUnits += stack.getAmount();
        }
        return actualUnits != lastWrittenUnits;
    }

    private void writeComparatorInventory(int level) {
        if (!(getBlock().getState() instanceof Container container)) return;
        Inventory inventory = container.getInventory();
        inventory.clear();
        int remaining = unitsForLevel(level);
        lastWrittenUnits = remaining;
        for (int slot = 0; slot < BARREL_SIZE && remaining > 0; slot++) {
            int amount = Math.min(MAX_STACK, remaining);
            inventory.setItem(slot, signalItem(amount));
            remaining -= amount;
        }
    }

    private int unitsForLevel(int level) {
        if (level <= 0) return 0;
        if (level == 1) return 1;
        if (level >= 15) return TOTAL_UNITS;
        return (int) Math.ceil(((double) (level - 1) / 14.0) * TOTAL_UNITS);
    }

    private @NotNull ItemStack signalItem(int amount) {
        ItemStack stack = ItemStackBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .amount(amount)
                .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.signal_item")
                        .color(NamedTextColor.AQUA)))
                .build();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(SIGNAL_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isSignalItem(@NotNull ItemStack stack) {
        if (stack.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(SIGNAL_ITEM_KEY, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void clearBarrelInventory() {
        if (getBlock().getState() instanceof Container container) {
            container.getInventory().clear();
        }
    }

    // ===== 显示实体（仿 GateValve）=====

    void refreshDisplays() {
        clearDisplays();
        BlockFace facing = getFacing();
        // 连接端点那一端伸到 ±0.825：刚好接到端点自身的连接桩，
        // 不再伸到端点中心（±1.0）穿进连接桩造成材质重叠/穿模。
        // 未连接（或接导管）时伸到 ±0.65：略微探出木桶面（±0.5），避免导管被木桶材质盖住/打架。
        double negEnd = isEndpointNeighbor(facing.getOppositeFace()) ? -0.825D : -0.65D;
        double posEnd = isEndpointNeighbor(facing) ? 0.825D : 0.65D;

        List<UUID> ids = new ArrayList<>();
        ids.add(createLineDisplay(negEnd, -0.22D).getUniqueId());
        ids.add(createLineDisplay(0.22D, posEnd).getUniqueId());
        ids.add(createCoreDisplay().getUniqueId());
        displayUuids = List.copyOf(ids);
    }

    private boolean isEndpointNeighbor(@NotNull BlockFace face) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(face));
        return rb instanceof PneumaticInput || rb instanceof PneumaticOutput;
    }

    private @NotNull ItemDisplay createLineDisplay(double fromScale, double toScale) {
        BlockFace facing = getFacing();
        Vector3d from = new Vector3d(facing.getModX() * fromScale, facing.getModY() * fromScale, facing.getModZ() * fromScale);
        Vector3d to = new Vector3d(facing.getModX() * toScale, facing.getModY() * toScale, facing.getModZ() * toScale);
        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(Material.GRAY_CONCRETE)
                        .addCustomModelDataString(SteamworkKeys.PNEUMATIC_DUCT + ":line")
                        .build())
                .transformation(new LineBuilder()
                        .from(from)
                        .to(to)
                        .thickness(THICKNESS)
                        .extraLength(0.0)
                        .build())
                .brightness(ambientBrightness())
                .persistent(true)
                .build(center());
        markDisplay(display);
        return display;
    }

    /**
     * 显示实体落在不透明的桶方块中心，环境光为 0 → 渲染发黑。
     * 这里取周围 6 个邻居的最大光照作为亮度，使管道显示与相邻导管/端点的环境亮度一致、不再发黑。
     */
    private @NotNull Display.Brightness ambientBrightness() {
        int blockLight = 0;
        int skyLight = 0;
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST,  BlockFace.UP,    BlockFace.DOWN}) {
            Block neighbor = getBlock().getRelative(face);
            blockLight = Math.max(blockLight, neighbor.getLightFromBlocks());
            skyLight   = Math.max(skyLight,   neighbor.getLightFromSky());
        }
        return new Display.Brightness(blockLight, skyLight);
    }

    private @NotNull ItemDisplay createCoreDisplay() {
        Material coreMaterial = switch (mode) {
            case FLOW_METER -> Material.LIME_TERRACOTTA;
            case THROTTLE -> Material.YELLOW_TERRACOTTA;
            case OVERFLOW_ALARM -> Material.RED_TERRACOTTA;
        };
        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ItemStack.of(coreMaterial))
                .transformation(new TransformBuilder().scale(0.45))
                .brightness(ambientBrightness())
                .persistent(true)
                .build(center());
        markDisplay(display);
        return display;
    }

    private @NotNull Location center() {
        return getBlock().getLocation().toCenterLocation();
    }

    private void clearDisplays() {
        for (ItemDisplay display : findManagedDisplays()) {
            if (display.isValid()) display.remove();
        }
        displayUuids = List.of();
    }

    private void markDisplay(@NotNull ItemDisplay display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN, true);
        pdc.set(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY, new int[] {
                getBlock().getX(), getBlock().getY(), getBlock().getZ()
        });
    }

    private @NotNull List<ItemDisplay> findManagedDisplays() {
        BoundingBox box = BoundingBox.of(getBlock()).expand(DISPLAY_SCAN_RADIUS);
        List<ItemDisplay> displays = new ArrayList<>();
        for (Entity entity : getBlock().getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof ItemDisplay display)) continue;
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

    // ===== WAILA =====

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(mode.component())
                .add(detailComponent());
    }

    private @NotNull Component detailComponent() {
        return switch (mode) {
            case FLOW_METER -> Component.translatable("steamwork.gui.pneumatic_pressure_module.detail.flow",
                    RebarArgument.of("rate", String.format("%.1f", currentRate)),
                    RebarArgument.of("level", String.valueOf(Math.max(0, lastLevel))));
            case THROTTLE -> Component.translatable("steamwork.gui.pneumatic_pressure_module.detail.throttle",
                    RebarArgument.of("used", String.valueOf(throttleUsed)),
                    RebarArgument.of("limit", String.valueOf(throttleLimit)));
            case OVERFLOW_ALARM -> Component.translatable(alarmActive
                    ? "steamwork.gui.pneumatic_pressure_module.detail.alarm_on"
                    : "steamwork.gui.pneumatic_pressure_module.detail.alarm_off");
        };
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # M # P # S # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('M', modeItem)
                .addIngredient('P', paramItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.title"));
    }

    private final class ModeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material icon = switch (mode) {
                case FLOW_METER -> Material.COMPARATOR;
                case THROTTLE -> Material.PISTON;
                case OVERFLOW_ALARM -> Material.REDSTONE_TORCH;
            };
            return ItemStackBuilder.of(icon)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.mode_title")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", mode.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.mode_desc." + mode.name().toLowerCase())),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            mode = mode.next();
            // 模式切换重置运行态与比较器
            flowCounter = 0;
            flowWindowTick = 0;
            currentRate = 0.0;
            throttleUsed = 0;
            throttleWindowTick = 0;
            alarmActive = false;
            alarmTicksLeft = 0;
            lastLevel = -1;
            refreshDisplays();
            PneumaticDuct.notifyNeighboringDucts(getBlock());
            modeItem.notifyWindows();
            paramItem.notifyWindows();
            statusItem.notifyWindows();
        }
    }

    private final class ParamItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (mode == Mode.THROTTLE) {
                return ItemStackBuilder.of(Material.HOPPER)
                        .amount(Math.max(1, Math.min(64, throttleLimit)))
                        .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.throttle_limit",
                                RebarArgument.of("limit", String.valueOf(throttleLimit)),
                                RebarArgument.of("window", String.valueOf(throttleWindowTicks)))))
                        .lore(List.of(
                                SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.throttle_desc")),
                                SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.adjust_hint"))
                        ));
            }
            Material icon = mode == Mode.FLOW_METER ? Material.CLOCK : Material.BELL;
            return ItemStackBuilder.of(icon)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.param_readonly")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.mode_desc." + mode.name().toLowerCase()))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (mode != Mode.THROTTLE) return;
            int step = type.isShiftClick() ? 10 : 1;
            throttleLimit = clampThrottle(throttleLimit + (type.isRightClick() ? -step : step));
            paramItem.notifyWindows();
            statusItem.notifyWindows();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material icon = (lastLevel > 0)
                    ? Material.LIME_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(icon)
                    .name(SteamLogicSupport.ni(detailComponent()))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.redstone_level",
                                    RebarArgument.of("level", String.valueOf(Math.max(0, lastLevel))))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.pneumatic_pressure_module.total_passed",
                                    RebarArgument.of("total", String.valueOf(totalPassed))))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }
}
