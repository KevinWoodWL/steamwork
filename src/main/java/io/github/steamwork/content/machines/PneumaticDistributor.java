package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.PneumaticUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 气动分发器 —— 消耗加压蒸汽，将输入槽物品按方向分发到相邻容器或气动导管网络的端点。
 *
 * <p>支持 1–6 个输出方向（北/南/东/西/上/下），每个方向可在 GUI 中独立启用或禁用。
 * 启用的方向若直连原版容器或 Rebar 机器的 input 槽，则直接推送；
 * 若方向连接气动导管，则 BFS 搜索整个导管网络，推送到第一个可接收的端点。
 * 多个启用方向之间采用轮询（round-robin）均衡分发。</p>
 *
 * <p>GUI 布局（5行）：
 * <pre>
 * # N # S # E # W #   ← N/S/E/W 方向标签（交错排列）
 * # 1 # 2 # 3 # 4 #   ← N/S/E/W 开关按钮（列号与上方标签相同）
 * # # U # # # D # #   ← U/D 方向标签（居中）
 * # # 5 # # # 6 # #   ← U/D 开关按钮
 * # # # # # # # # #   ← 分隔行
 * g i i i i i i i a   ← 蒸汽量表 | 7格输入槽 | 状态
 * </pre>
 * </p>
 */
public class PneumaticDistributor extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    /** 6 个输出方向，索引与 GUI 切换按钮对应：0=北 1=南 2=东 3=西 4=上 5=下 */
    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST,  BlockFace.UP,    BlockFace.DOWN
    };

    /** enabledFacesMask 的默认值：6 位全 1，即所有方向初始均启用 */
    private static final int DEFAULT_MASK = 0b111111;

    public static final int MIN_TICK_INTERVAL = 1;
    public static final int MAX_TICK_INTERVAL = 200;

    /** PDC 键：6 位 int 掩码，bit i 为 1 表示 FACES[i] 方向已启用 */
    private static final NamespacedKey ENABLED_MASK_KEY  = steamworkKey("pdist_enabled_faces");
    private static final NamespacedKey TICK_INTERVAL_KEY = steamworkKey("pdist_tick_interval");

    private final int defaultTickInterval = getSettings().getOrThrow("tick-interval",      ConfigAdapter.INTEGER);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem   = getSettings().getOrThrow("steam-per-item",    ConfigAdapter.DOUBLE);
    private final int itemsPerTick      = getSettings().getOrThrow("items-per-tick",    ConfigAdapter.INTEGER);

    /** 启用方向掩码，存入 PDC 以持久化 */
    private int enabledFacesMask = DEFAULT_MASK;
    /** 工作 tick 间隔，1–200，GUI 可调（默认取配置文件值）。 */
    private int tickIntervalOverride;

    /** 上次分发到的目标列表索引（round-robin） */
    private int roundRobinIndex = 0;
    /** 每个启用面内部的端点轮询游标，key = face ordinal。 */
    private final int[] endpointCursor = new int[6];

    private boolean lastActive = false;
    /** 用于避免每 tick 无谓刷新蒸汽量表 */
    private double lastSteamAmount = -1;

    private final VirtualInventory inputInventory = new VirtualInventory(7);
    private final StatusItem statusItem  = new StatusItem();
    private final GaugeItem  gaugeItem   = new GaugeItem();
    private final TickIntervalItem tickIntervalItem = new TickIntervalItem();
    /** 与 FACES 一一对应的 6 个方向开关按钮 */
    private final ToggleItem[] toggleItems = new ToggleItem[FACES.length];

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem      = getSettings().getOrThrow("steam-per-item",     ConfigAdapter.DOUBLE);
        private final int itemsPerTick         = getSettings().getOrThrow("items-per-tick",     ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("pressurized-buffer", UnitFormat.MILLIBUCKETS.format(pressurizedBuffer)),
                    RebarArgument.of("steam-per-item",     UnitFormat.MILLIBUCKETS.format(steamPerItem)),
                    RebarArgument.of("items-per-tick",     String.valueOf(itemsPerTick))
            );
        }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticDistributor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        tickIntervalOverride = defaultTickInterval;
        setTickInterval(tickIntervalOverride);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, true, false);
        initToggleItems();
    }

    @SuppressWarnings("unused")
    public PneumaticDistributor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        Integer mask = pdc.get(ENABLED_MASK_KEY, PersistentDataType.INTEGER);
        enabledFacesMask = (mask != null) ? mask : DEFAULT_MASK;
        tickIntervalOverride = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL,
                pdc.getOrDefault(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, defaultTickInterval)));
        setTickInterval(tickIntervalOverride);
        initToggleItems();
    }

    private void initToggleItems() {
        for (int i = 0; i < FACES.length; i++) {
            toggleItems[i] = new ToggleItem(i);
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(ENABLED_MASK_KEY,  PersistentDataType.INTEGER, enabledFacesMask);
        pdc.set(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, tickIntervalOverride);
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarFluidBufferBlock.super.onBreak(drops, context);
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    // ── 方向掩码工具 ──────────────────────────────────────────────────────────

    private boolean isEnabled(int faceIndex) {
        return (enabledFacesMask & (1 << faceIndex)) != 0;
    }

    private void toggleDirection(int faceIndex) {
        enabledFacesMask ^= (1 << faceIndex);
    }

    private int enabledCount() {
        return Integer.bitCount(enabledFacesMask & 0x3F);
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        PneumaticUtils.pullFromAdjacentHoppers(getBlock(), inputInventory);
        ItemStack probe = pickFirstItem();
        if (probe == null) {
            setActive(false);
            updateGauge();
            return;
        }

        // 启用方向的稳定列表（按 FACES 顺序，不随当前空间状态变化）。
        // 游标在此列表上推进，保证多 tick 均衡。
        List<Integer> enabledFaceIndices = new ArrayList<>();
        for (int fi = 0; fi < FACES.length; fi++) {
            if (isEnabled(fi)) enabledFaceIndices.add(fi);
        }
        if (enabledFaceIndices.isEmpty()) {
            setActive(false);
            updateGauge();
            return;
        }

        int pushed = 0;
        for (int n = 0; n < itemsPerTick; n++) {
            if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < steamPerItem) break;

            ItemStack current = pickFirstItem();
            if (current == null) break;

            if (roundRobinIndex >= enabledFaceIndices.size()) roundRobinIndex = 0;

            boolean sent = false;
            // 捕获本轮起始游标，内层循环不修改 roundRobinIndex，只在成功时推进
            int startCursor = roundRobinIndex;
            for (int attempt = 0; attempt < enabledFaceIndices.size(); attempt++) {
                int slot = (startCursor + attempt) % enabledFaceIndices.size();
                int faceIndex = enabledFaceIndices.get(slot);

                Block target = findTargetInDirection(FACES[faceIndex], current);
                if (target == null) continue;
                if (!PneumaticUtils.tryPushItem(target, current)) continue;

                consumeItem(current);
                removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerItem);
                roundRobinIndex = (slot + 1) % enabledFaceIndices.size();
                pushed++;
                sent = true;
                break;
            }

            if (!sent) break;
        }

        if (pushed > 0) spawnFx();
        setActive(pushed > 0);
        statusItem.notifyWindows();
        updateGauge();
    }

    /**
     * 在指定方向寻找首个对 {@code item} 有空间的目标方块。
     * <ul>
     *   <li>直连容器/Rebar 机器 → 直接返回</li>
     *   <li>连接气动导管 → BFS 搜索端点，返回第一个有空间的</li>
     * </ul>
     */
    private @Nullable Block findTargetInDirection(@NotNull BlockFace face, @NotNull ItemStack item) {
        Block neighbor = getBlock().getRelative(face);
        int faceOrdinal = face.ordinal();

        // 直连气动输出端
        if (BlockStorage.get(neighbor) instanceof PneumaticOutput
                && PneumaticUtils.hasSpace(neighbor, item)) {
            return neighbor;
        }

        // 直连普通容器/机器（非导管、非输出端）
        if (!PneumaticDuct.isNetworkDuct(neighbor)
                && !(BlockStorage.get(neighbor) instanceof PneumaticOutput)
                && PneumaticUtils.isItemTarget(neighbor)
                && PneumaticUtils.hasSpace(neighbor, item)) {
            return neighbor;
        }

        // 导管网络 → BFS 收集所有可达输入端，游标轮询
        if (PneumaticDuct.isNetworkDuct(neighbor)) {
            List<Block> endpoints = new ArrayList<>();
            for (Block ep : PneumaticDuct.findReachableEndpoints(neighbor)) {
                if (BlockStorage.get(ep) instanceof PneumaticInput
                        && PneumaticUtils.hasSpace(ep, item)) {
                    endpoints.add(ep);
                }
            }
            if (endpoints.isEmpty()) return null;
            int size = endpoints.size();
            int start = Math.floorMod(endpointCursor[faceOrdinal], size);
            for (int i = 0; i < size; i++) {
                int idx = (start + i) % size;
                Block candidate = endpoints.get(idx);
                if (PneumaticUtils.hasSpace(candidate, item)) {
                    endpointCursor[faceOrdinal] = Math.floorMod(idx + 1, size);
                    return candidate;
                }
            }
        }

        return null;
    }

    private @Nullable ItemStack pickFirstItem() {
        for (ItemStack s : inputInventory.getItems()) {
            if (s != null && !s.getType().isAir()) return s.clone().asQuantity(1);
        }
        return null;
    }

    private void consumeItem(@NotNull ItemStack one) {
        MachineUpdateReason reason = new MachineUpdateReason();
        ItemStack[] items = inputInventory.getItems();
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            if (s == null || s.getType().isAir() || !s.isSimilar(one)) continue;
            if (s.getAmount() <= 1) {
                inputInventory.setItem(reason, i, null);
            } else {
                ItemStack reduced = s.clone();
                reduced.setAmount(s.getAmount() - 1);
                inputInventory.setItem(reason, i, reduced);
            }
            return;
        }
    }

    private void spawnFx() {
        var loc = getBlock().getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.25, 0.1, 0.25, 0.04);
        if (Math.random() < 0.2) {
            getBlock().getWorld().playSound(getBlock().getLocation(),
                    Sound.BLOCK_PISTON_EXTEND, 0.15f, 0.8f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    private void updateGauge() {
        double current = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
        if (current != lastSteamAmount) {
            lastSteamAmount = current;
            gaugeItem.notifyWindows();
        }
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# N # S # E # W #",
                        "# 1 # 2 # 3 # 4 #",
                        "# # U # # # D # #",
                        "# # 5 # # # 6 # #",
                        "# # # # t # # # #",
                        "g i i i i i i i a"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('g', gaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', tickIntervalItem)
                // 方向标签（静态，仅作视觉引导）
                .addIngredient('N', dirLabel(Material.BLUE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.north"))
                .addIngredient('S', dirLabel(Material.RED_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.south"))
                .addIngredient('E', dirLabel(Material.GREEN_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.east"))
                .addIngredient('W', dirLabel(Material.WHITE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.west"))
                .addIngredient('U', dirLabel(Material.YELLOW_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.up"))
                .addIngredient('D', dirLabel(Material.ORANGE_STAINED_GLASS_PANE,
                        "steamwork.gui.pneumatic_distributor.direction.down"))
                // 方向开关按钮（'1'-'6' 对应 FACES[0]-FACES[5]）
                .addIngredient('1', toggleItems[0])
                .addIngredient('2', toggleItems[1])
                .addIngredient('3', toggleItems[2])
                .addIngredient('4', toggleItems[3])
                .addIngredient('5', toggleItems[4])
                .addIngredient('6', toggleItems[5])
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.title"));
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("pressurized-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12, TextColor.fromHexString("#00cfff")
                )),
                RebarArgument.of("enabled-directions", String.valueOf(enabledCount())),
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("input", inputInventory);
    }

    // ── GUI 物品 ──────────────────────────────────────────────────────────────

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(
                            lastActive ? Material.GREEN_STAINED_GLASS_PANE
                                       : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.status."
                                    + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.throughput",
                            RebarArgument.of("per-item",   UnitFormat.MILLIBUCKETS.format(steamPerItem).decimalPlaces(1)),
                            RebarArgument.of("max-items",  String.valueOf(itemsPerTick))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
    }

    private final class GaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double amount   = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM);
            return ItemStackBuilder.of(Material.CYAN_STAINED_GLASS)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.pressurized_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.fluid_amount",
                            RebarArgument.of("amount",   UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
    }

    /**
     * 方向启用/禁用切换按钮。
     * 绿色玻璃板 = 该方向已启用（会向此面分发）；
     * 红色玻璃板 = 该方向已禁用（跳过此面）。
     * 左键点击切换状态。
     */
    private final class ToggleItem extends AbstractItem {
        private final int faceIndex;

        ToggleItem(int faceIndex) { this.faceIndex = faceIndex; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean enabled = isEnabled(faceIndex);
            return ItemStackBuilder.of(enabled
                            ? Material.LIME_STAINED_GLASS_PANE
                            : Material.RED_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.toggle."
                                    + (enabled ? "enabled" : "disabled"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_distributor.toggle.hint"))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            toggleDirection(faceIndex);
            // 刷新所有方向开关 + 状态条，保证 UI 即时反映新状态
            for (ToggleItem t : toggleItems) {
                if (t != null) t.notifyWindows();
            }
            statusItem.notifyWindows();
        }
    }

    private final class TickIntervalItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_distributor.tick_interval",
                    RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                    RebarArgument.of("max",       String.valueOf(MAX_TICK_INTERVAL))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_decrease")));
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.pneumatic_distributor.tick_interval_title")))
                    .amount(Math.min(tickIntervalOverride, 64))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            int delta = 0;
            if (clickType == ClickType.LEFT)             delta =   1;
            else if (clickType == ClickType.SHIFT_LEFT)  delta =  10;
            else if (clickType == ClickType.RIGHT)       delta =  -1;
            else if (clickType == ClickType.SHIFT_RIGHT) delta = -10;
            if (delta == 0) return;
            int next = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL, tickIntervalOverride + delta));
            if (next == tickIntervalOverride) return;
            tickIntervalOverride = next;
            setTickInterval(tickIntervalOverride);
            notifyWindows();
        }
    }

    /** 创建方向标签静态物品（不可交互，仅提供视觉引导）。 */
    private static AbstractItem dirLabel(@NotNull Material mat, @NotNull String translationKey) {
        return new AbstractItem() {
            @Override
            public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
                return ItemStackBuilder.of(mat)
                        .name(noItalic(Component.translatable(translationKey)));
            }

            @Override
            public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                    @NotNull Click click) {}
        };
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
