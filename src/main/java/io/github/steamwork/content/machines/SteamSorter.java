package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
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

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 蒸汽压力分拣机 —— 消耗蒸汽将内部输入槽物品主动推送到上下左右前后六个相邻容器，
 * 根据过滤槽配置的物品类型决定推送目标；无匹配时轮询空位投送。
 *
 * GUI 布局（5行 × 9列）：
 *   行1：[bg][N标][N滤][S标][S滤][E标][E滤][W标][W滤]
 *   行2：[bg][bg][bg][U标][U滤][D标][D滤][bg][bg]
 *   行3：背景分隔
 *   行4：输入槽 × 9（满行）
 *   行5：蒸汽量表 | 背景 × 7 | 状态
 *
 * 过滤槽对应关系（filterInventory 索引与 FACES 一致）：
 *   0=北  1=南  2=东  3=西  4=上  5=下
 */
public class SteamSorter extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarInventoryBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final int defaultTickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
    private final int itemsPerTick = getSettings().getOrThrow("items-per-tick", ConfigAdapter.INTEGER);

    private boolean lastActive = false;
    /** 用于判断蒸汽量是否变化，避免 GUI 每 tick 无谓刷新 */
    private double lastSteamAmount = -1;
    /** 轮询游标：每次成功发送后递增，跨 tick 持续，保证多目标均衡分发。 */
    private int roundRobinCursor = 0;

    public static final int MIN_TICK_INTERVAL = 1;
    public static final int MAX_TICK_INTERVAL = 200;

    private static final NamespacedKey TICK_INTERVAL_KEY = steamworkKey("ss_tick_interval");
    /** PDC 键：每个面的幽灵过滤槽材质（材质名称字符串，缺失 = 无过滤）。 */
    private static final NamespacedKey[] FILTER_KEYS = {
            steamworkKey("sf_n"), steamworkKey("sf_s"), steamworkKey("sf_e"),
            steamworkKey("sf_w"), steamworkKey("sf_u"), steamworkKey("sf_d")
    };

    /**
     * 输入槽（7格）：待分拣的物品放在这里，机器主动推送出去。
     *
     * <p>过滤槽（幽灵槽，每面一格）：左键用手持/光标物品设置该面的过滤类型；
     * 空手左键清除；不消耗玩家手中的物品。过滤仅按 {@link Material} 类型匹配。</p>
     */
    private final VirtualInventory inputInventory = new VirtualInventory(7);
    /** 每个面的幽灵过滤材质，null = 无过滤（索引与 FACES 一一对应）。 */
    private final @Nullable Material[] filterMaterials = new @Nullable Material[6];
    /** 与 filterMaterials 对应的 GUI 交互按钮。 */
    private final FilterSlotItem[] filterSlotItems = new FilterSlotItem[6];
    /** 工作 tick 间隔，1–200，GUI 可调（默认取配置文件值）。 */
    private int tickIntervalOverride;

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final TickIntervalItem tickIntervalItem = new TickIntervalItem();

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
        private final int itemsPerTick = getSettings().getOrThrow("items-per-tick", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("steam-per-item", UnitFormat.MILLIBUCKETS.format(steamPerItem)),
                    RebarArgument.of("items-per-tick", String.valueOf(itemsPerTick))
            );
        }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public SteamSorter(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        tickIntervalOverride = defaultTickInterval;
        setTickInterval(tickIntervalOverride);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, steamBuffer, true, false);
        initFilterSlots();
    }

    @SuppressWarnings("unused")
    public SteamSorter(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        tickIntervalOverride = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL,
                pdc.getOrDefault(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, defaultTickInterval)));
        setTickInterval(tickIntervalOverride);
        for (int i = 0; i < 6; i++) {
            String matName = pdc.get(FILTER_KEYS[i], PersistentDataType.STRING);
            if (matName != null) {
                try { filterMaterials[i] = Material.valueOf(matName); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        initFilterSlots();
    }

    private void initFilterSlots() {
        for (int i = 0; i < 6; i++) filterSlotItems[i] = new FilterSlotItem(i);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, tickIntervalOverride);
        for (int i = 0; i < 6; i++) {
            if (filterMaterials[i] != null) {
                pdc.set(FILTER_KEYS[i], PersistentDataType.STRING, filterMaterials[i].name());
            } else {
                pdc.remove(FILTER_KEYS[i]);
            }
        }
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarFluidBufferBlock.super.onBreak(drops, context);
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        PneumaticUtils.pullFromAdjacentHoppers(getBlock(), inputInventory);
        int pushed = 0;

        for (int n = 0; n < itemsPerTick; n++) {
            if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < steamPerItem) break;

            ItemStack[] inputs = inputInventory.getItems();
            boolean moved = false;

            for (int i = 0; i < inputs.length; i++) {
                ItemStack stack = inputs[i];
                if (stack == null || stack.getType().isAir()) continue;

                // 找到接受该物品的目标（直连容器 或 导管网络端点）
                Block target = findTarget(stack);
                if (target == null) continue;

                // 推入 1 个物品（PneumaticUtils 同时支持原版容器和 Rebar 机器）
                if (!PneumaticUtils.tryPushItem(target, stack)) continue;

                // 从输入槽减少一个
                MachineUpdateReason reason = new MachineUpdateReason();
                if (stack.getAmount() <= 1) {
                    inputInventory.setItem(reason, i, null);
                } else {
                    ItemStack reduced = stack.clone();
                    reduced.setAmount(stack.getAmount() - 1);
                    inputInventory.setItem(reason, i, reduced);
                }

                removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerItem);
                pushed++;
                moved = true;
                break;
            }

            if (!moved) break;
        }

        if (pushed > 0) spawnSortFx();
        setActive(pushed > 0);
        statusItem.notifyWindows();

        // 只在蒸汽量实际变化时才刷新量表，避免每 tick 不必要地更新 GUI
        double currentSteam = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
        if (currentSteam != lastSteamAmount) {
            lastSteamAmount = currentSteam;
            steamGaugeItem.notifyWindows();
        }
    }

    /**
     * 查找接受指定物品的目标方块。
     *
     * <p>搜索顺序（每个面独立）：
     * <ol>
     *   <li>直接相邻的容器 / Rebar 机器 ── 过滤匹配者优先，否则加入备选池</li>
     *   <li>该面有导管时，BFS 遍历整个导管网络，对每个端点同样做过滤匹配</li>
     * </ol>
     * 过滤槽索引与 {@link #FACES} 一一对应：0=北 1=南 2=东 3=西 4=上 5=下。
     * 某个面的过滤槽留空，则该面（及其导管网络）接受任何物品，进入备选池。</p>
     *
     * @param item 待推送的物品
     * @return 找到的目标方块，找不到则 {@code null}
     */
    private @Nullable Block findTarget(@NotNull ItemStack item) {
        // 先按过滤规则收集所有候选目标（有过滤 = 精确匹配面，无过滤 = 任意有空间的面）
        List<Block> filtered  = new ArrayList<>();  // 过滤槽匹配
        List<Block> unfiltered = new ArrayList<>(); // 无过滤槽的面

        for (int fi = 0; fi < FACES.length; fi++) {
            Block neighbor = getBlock().getRelative(FACES[fi]);
            Material filter = filterMaterials[fi];
            boolean filterSet = filter != null;
            boolean typeMatch = filterSet && filter == item.getType();

            // 直连气动输出端
            if (BlockStorage.get(neighbor) instanceof PneumaticOutput) {
                if (typeMatch && PneumaticUtils.hasSpace(neighbor, item)) filtered.add(neighbor);
                else if (!filterSet && PneumaticUtils.hasSpace(neighbor, item)) unfiltered.add(neighbor);
                continue;
            }

            // 直连普通容器/机器
            if (!PneumaticDuct.isNetworkDuct(neighbor)) {
                if (PneumaticUtils.isItemTarget(neighbor)) {
                    if (typeMatch && PneumaticUtils.hasSpace(neighbor, item)) filtered.add(neighbor);
                    else if (!filterSet && PneumaticUtils.hasSpace(neighbor, item)) unfiltered.add(neighbor);
                }
                continue;
            }

            // 导管网络 → BFS 查找气动输入端
            for (Block endpoint : PneumaticDuct.findReachableEndpoints(neighbor)) {
                if (!(BlockStorage.get(endpoint) instanceof PneumaticInput)) continue;
                if (typeMatch && PneumaticUtils.hasSpace(endpoint, item)) filtered.add(endpoint);
                else if (!filterSet && PneumaticUtils.hasSpace(endpoint, item)) unfiltered.add(endpoint);
            }
        }

        // 有过滤匹配优先；否则用无过滤候选池；两者都用游标轮询
        List<Block> pool = !filtered.isEmpty() ? filtered : unfiltered;
        if (pool.isEmpty()) return null;

        int size = pool.size();
        int start = Math.floorMod(roundRobinCursor, size);
        for (int i = 0; i < size; i++) {
            int idx = (start + i) % size;
            Block candidate = pool.get(idx);
            if (PneumaticUtils.hasSpace(candidate, item)) {
                roundRobinCursor = Math.floorMod(idx + 1, size);
                return candidate;
            }
        }
        return null;
    }

    private void spawnSortFx() {
        var loc = getBlock().getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CRIT, loc, 4, 0.3, 0.1, 0.3, 0.04);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 2, 0.2, 0.1, 0.2, 0.01);
        if (Math.random() < 0.15) {
            getBlock().getWorld().playSound(getBlock().getLocation(),
                    Sound.BLOCK_DISPENSER_DISPENSE, 0.2f, 1.5f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    /**
     * 布局说明：
     * <pre>
     * # N # S # E # W #   ← N/S/E/W 方向标签（交错排列）
     * # f # f # f # f #   ← 对应过滤槽（列号与上方标签相同）
     * # # U # # # D # #   ← U/D 方向标签（居中）
     * # # f # # # f # #   ← U/D 过滤槽
     * # # # # # # # # #   ← 分隔行
     * s i i i i i i i a   ← 蒸汽量表 | 7格输入槽 | 状态
     * </pre>
     * filterInventory 顺序与 FACES 一致：0=北 1=南 2=东 3=西 4=上 5=下
     */
    /**
     * 布局说明：
     * <pre>
     * # N # S # E # W #   ← N/S/E/W 方向标签（交错排列）
     * # 0 # 1 # 2 # 3 #   ← 北/南/东/西 幽灵过滤槽
     * # # U # # # D # #   ← U/D 方向标签（居中）
     * # # 4 # # # 5 # #   ← 上/下 幽灵过滤槽
     * # # # # # # # # #   ← 分隔行
     * s i i i i i i i a   ← 蒸汽量表 | 7格输入槽 | 状态
     * </pre>
     * 过滤槽 0-5 与 FACES（北/南/东/西/上/下）一一对应。
     */
    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# N # S # E # W #",
                        "# 0 # 1 # 2 # 3 #",
                        "# # U # # # D # #",
                        "# # 4 # # # 5 # #",
                        "# # # # t # # # #",
                        "s i i i i i i i a"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', tickIntervalItem)
                // 幽灵过滤槽（0=北 1=南 2=东 3=西 4=上 5=下）
                .addIngredient('0', filterSlotItems[0])
                .addIngredient('1', filterSlotItems[1])
                .addIngredient('2', filterSlotItems[2])
                .addIngredient('3', filterSlotItems[3])
                .addIngredient('4', filterSlotItems[4])
                .addIngredient('5', filterSlotItems[5])
                // 方向标签行
                .addIngredient('N', dirLabel(Material.BLUE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.north"))
                .addIngredient('S', dirLabel(Material.RED_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.south"))
                .addIngredient('E', dirLabel(Material.GREEN_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.east"))
                .addIngredient('W', dirLabel(Material.WHITE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.west"))
                .addIngredient('U', dirLabel(Material.YELLOW_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.up"))
                .addIngredient('D', dirLabel(Material.ORANGE_STAINED_GLASS_PANE,
                        "steamwork.gui.steam_sorter.filter.down"))
                .build();
    }

    /**
     * 幽灵过滤槽 —— 左键用光标/手持物品设置该面的过滤类型；空手左键清除。
     * 不消耗玩家手中的任何物品。
     */
    private final class FilterSlotItem extends AbstractItem {
        private final int index;

        FilterSlotItem(int index) { this.index = index; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = filterMaterials[index];
            if (mat == null) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.empty")))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.ghost_hint"))));
            }
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.filter.set",
                            RebarArgument.of("item", Component.translatable(mat.translationKey())))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.filter.ghost_hint_clear"))))
                    .amount(1);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            // 优先读取光标（拖动中的物品），其次读取主手
            ItemStack cursor = player.getOpenInventory().getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                filterMaterials[index] = cursor.getType();
            } else {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!hand.getType().isAir()) {
                    filterMaterials[index] = hand.getType();
                } else {
                    filterMaterials[index] = null;
                }
            }
            notifyWindows();
        }
    }

    private final class TickIntervalItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_sorter.tick_interval",
                    RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                    RebarArgument.of("max",       String.valueOf(MAX_TICK_INTERVAL))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_decrease")));
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.tick_interval_title")))
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

    /**
     * 创建方向标签静态物品（不可点击，仅作视觉引导）。
     * 使用各方向对应的颜色玻璃板，lore 提示该槽位的过滤规则。
     */
    private static AbstractItem dirLabel(Material mat, String translationKey) {
        return new AbstractItem() {
            @Override
            public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
                return ItemStackBuilder.of(mat)
                        .name(noItalic(Component.translatable(translationKey)))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.steam_sorter.filter.hint"))));
            }

            @Override
            public void handleClick(@NotNull ClickType clickType,
                                    @NotNull Player player,
                                    @NotNull Click click) {}
        };
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_sorter.title"));
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
                RebarArgument.of("steam-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12, TextColor.fromHexString("#d8edf0")
                )),
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
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.throughput",
                            RebarArgument.of("per-item", UnitFormat.MILLIBUCKETS.format(steamPerItem).decimalPlaces(1)),
                            RebarArgument.of("max-items", String.valueOf(itemsPerTick))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double amount = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM);
            return ItemStackBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_sorter.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_sorter.fluid_amount",
                            RebarArgument.of("amount", UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
