package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.SteamLogicSupport;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽差分机 —— 致敬查尔斯·巴贝奇的差分机（Difference Engine）。
 *
 * <p>维护一组整数差分寄存器 {@code [v, d1, d2, d3]}：v 是当前序列值，d1/d2/d3 为各阶差分。
 * 每个「工作冲程」消耗一份蒸汽，按前向差分递推吐出序列的下一项：</p>
 * <pre>
 *   输出 = v
 *   v  ← v  + d1
 *   d1 ← d1 + d2   (阶数 ≥ 2)
 *   d2 ← d2 + d3   (阶数 ≥ 3)
 * </pre>
 * <p>所有寄存器都在模 {@code ceiling} 算术下运算，因此整机是一个有限状态机，序列必然
 * 有界并周期回绕。一阶 = 等差/斜坡，二阶 = 抛物线，三阶 = 三次曲线。</p>
 *
 * <p>输出三选一（全部收敛为差分机自身可观测的输出，不依赖下游机器改造）：</p>
 * <ul>
 *   <li>{@link OutputMode#REDSTONE}：把序列值归一化映射成 0–15 模拟红石，写入底层桶供比较器读取。</li>
 *   <li>{@link OutputMode#STEAM_PULSE}：每冲程向输出面相邻机器推送 v mB 蒸汽，生成非线性供汽波形。</li>
 *   <li>{@link OutputMode#FREQUENCY}：每隔 v 个 tick 发一次红石脉冲，序列变化即让节拍渐快渐慢（分频器）。</li>
 * </ul>
 */
public class SteamDifferenceEngine extends RebarBlock implements
        BlockBreakRebarBlockHandler,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private enum OutputMode {
        REDSTONE("steamwork.gui.steam_difference_engine.mode.redstone", TextColor.color(0xff5555)),
        STEAM_PULSE("steamwork.gui.steam_difference_engine.mode.steam_pulse", TextColor.color(0x18c0d8)),
        FREQUENCY("steamwork.gui.steam_difference_engine.mode.frequency", TextColor.color(0xffd166));

        private final String translationKey;
        private final TextColor color;

        OutputMode(@NotNull String translationKey, @NotNull TextColor color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        @NotNull Component component() {
            return Component.translatable(translationKey).color(color);
        }

        @NotNull OutputMode next() {
            OutputMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static @NotNull OutputMode fromOrdinal(int ordinal) {
            OutputMode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : REDSTONE;
        }
    }

    // ===== PDC 键 =====
    private static final String KEY_KIND = "de_kind";
    private static final String KEY_MODE = "de_mode";
    private static final String KEY_FACE = "de_face";
    private static final String KEY_ORDER = "de_order";
    private static final String KEY_CEILING = "de_ceiling";
    private static final String KEY_INIT_V = "de_init_v";
    private static final String KEY_INIT_D1 = "de_init_d1";
    private static final String KEY_INIT_D2 = "de_init_d2";
    private static final String KEY_INIT_D3 = "de_init_d3";
    private static final String KEY_CUR_V = "de_cur_v";
    private static final String KEY_CUR_D1 = "de_cur_d1";
    private static final String KEY_CUR_D2 = "de_cur_d2";
    private static final String KEY_CUR_D3 = "de_cur_d3";
    private static final String KEY_COUNTDOWN = "de_countdown";
    private static final String KEY_PULSE_LEFT = "de_pulse_left";
    private static final String KEY_RUNNING = "de_running";

    // ===== 比较器占位物（沿用压力传感器方案：填充底层桶的格子数 = 红石等级）=====
    private static final int BARREL_SIZE = 27;
    private static final int MAX_STACK = 64;
    private static final int TOTAL_UNITS = BARREL_SIZE * MAX_STACK;
    private static final NamespacedKey SIGNAL_ITEM_KEY = steamworkKey("difference_engine_signal_item");

    private static final int CEILING_MIN = 2;
    private static final int CEILING_MAX = 4096;

    // ===== settings =====
    private final int tickInterval = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 1);
    private final double buffer = getSettings().getOrThrow("buffer", ConfigAdapter.DOUBLE);
    private final double steamPerStroke = getSettings().getOrThrow("steam-per-stroke", ConfigAdapter.DOUBLE);
    private final int strokeInterval = getSettings().getOrThrow("stroke-interval", ConfigAdapter.INTEGER);
    private final int defaultCeiling = getSettings().getOrThrow("default-ceiling", ConfigAdapter.INTEGER);
    private final int maxOrder = getSettings().getOrThrow("max-order", ConfigAdapter.INTEGER);
    private final double maxPulseAmount = getSettings().getOrThrow("max-pulse-amount", ConfigAdapter.DOUBLE);
    private final int minFreqInterval = getSettings().getOrThrow("min-frequency-interval", ConfigAdapter.INTEGER);
    private final int maxFreqInterval = getSettings().getOrThrow("max-frequency-interval", ConfigAdapter.INTEGER);
    private final int pulseLength = getSettings().getOrThrow("pulse-length", ConfigAdapter.INTEGER);

    // ===== 配置（玩家可编程）=====
    private SteamKind steamKind = SteamKind.STEAM;
    private OutputMode outputMode = OutputMode.REDSTONE;
    private BlockFace outputFace = BlockFace.SOUTH;
    private int order = 1;
    private int ceiling;
    private long initV = 0;
    private long initD1 = 1;
    private long initD2 = 0;
    private long initD3 = 0;

    // ===== 运行态 =====
    private long curV;
    private long curD1;
    private long curD2;
    private long curD3;
    private int strokeCountdown;
    private int pulseTicksLeft = 0;
    private boolean running = true;
    private boolean stalled = false;

    // ===== 比较器输出缓存 =====
    private int lastLevel = -1;
    private int lastWrittenUnits = -1;

    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final OutputModeItem outputModeItem = new OutputModeItem();
    private final OutputFaceItem outputFaceItem = new OutputFaceItem();
    private final OrderItem orderItem = new OrderItem();
    private final CeilingItem ceilingItem = new CeilingItem();
    private final ResetItem resetItem = new ResetItem();
    private final RunToggleItem runToggleItem = new RunToggleItem();
    private final StatusItem statusItem = new StatusItem();
    private final RegisterItem initVItem = new RegisterItem(Register.V);
    private final RegisterItem initD1Item = new RegisterItem(Register.D1);
    private final RegisterItem initD2Item = new RegisterItem(Register.D2);
    private final RegisterItem initD3Item = new RegisterItem(Register.D3);

    public static class Item extends RebarItem {
        private final int strokeInterval = getSettings().getOrThrow("stroke-interval", ConfigAdapter.INTEGER);
        private final double steamPerStroke = getSettings().getOrThrow("steam-per-stroke", ConfigAdapter.DOUBLE);
        private final int maxOrder = getSettings().getOrThrow("max-order", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("order", String.valueOf(maxOrder)),
                    RebarArgument.of("interval", String.valueOf(strokeInterval)),
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamPerStroke).decimalPlaces(0))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamDifferenceEngine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setTickInterval(tickInterval);
        ceiling = clampCeiling(defaultCeiling);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createBuffers();
        resetRegisters();
    }

    @SuppressWarnings("unused")
    public SteamDifferenceEngine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(tickInterval);
        steamKind = SteamKind.fromOrdinal(pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, 0));
        outputMode = OutputMode.fromOrdinal(pdc.getOrDefault(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, 0));
        outputFace = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_FACE), BlockFace.SOUTH);
        order = clampOrder(pdc.getOrDefault(steamworkKey(KEY_ORDER), PersistentDataType.INTEGER, 1));
        ceiling = clampCeiling(pdc.getOrDefault(steamworkKey(KEY_CEILING), PersistentDataType.INTEGER, defaultCeiling));
        initV = pdc.getOrDefault(steamworkKey(KEY_INIT_V), PersistentDataType.LONG, 0L);
        initD1 = pdc.getOrDefault(steamworkKey(KEY_INIT_D1), PersistentDataType.LONG, 1L);
        initD2 = pdc.getOrDefault(steamworkKey(KEY_INIT_D2), PersistentDataType.LONG, 0L);
        initD3 = pdc.getOrDefault(steamworkKey(KEY_INIT_D3), PersistentDataType.LONG, 0L);
        curV = pdc.getOrDefault(steamworkKey(KEY_CUR_V), PersistentDataType.LONG, initV);
        curD1 = pdc.getOrDefault(steamworkKey(KEY_CUR_D1), PersistentDataType.LONG, initD1);
        curD2 = pdc.getOrDefault(steamworkKey(KEY_CUR_D2), PersistentDataType.LONG, initD2);
        curD3 = pdc.getOrDefault(steamworkKey(KEY_CUR_D3), PersistentDataType.LONG, initD3);
        strokeCountdown = Math.max(0, pdc.getOrDefault(steamworkKey(KEY_COUNTDOWN), PersistentDataType.INTEGER, strokeInterval));
        pulseTicksLeft = Math.max(0, pdc.getOrDefault(steamworkKey(KEY_PULSE_LEFT), PersistentDataType.INTEGER, 0));
        running = pdc.getOrDefault(steamworkKey(KEY_RUNNING), PersistentDataType.BOOLEAN, true);
    }

    private void createBuffers() {
        createFluidBuffer(SteamworkFluids.STEAM, buffer, true, false);
        createFluidBuffer(SteamworkFluids.SUPERHEATED_STEAM, buffer, true, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, buffer, true, false);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, steamKind.ordinal());
        pdc.set(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, outputMode.ordinal());
        pdc.set(steamworkKey(KEY_FACE), PersistentDataType.STRING, outputFace.name());
        pdc.set(steamworkKey(KEY_ORDER), PersistentDataType.INTEGER, order);
        pdc.set(steamworkKey(KEY_CEILING), PersistentDataType.INTEGER, ceiling);
        pdc.set(steamworkKey(KEY_INIT_V), PersistentDataType.LONG, initV);
        pdc.set(steamworkKey(KEY_INIT_D1), PersistentDataType.LONG, initD1);
        pdc.set(steamworkKey(KEY_INIT_D2), PersistentDataType.LONG, initD2);
        pdc.set(steamworkKey(KEY_INIT_D3), PersistentDataType.LONG, initD3);
        pdc.set(steamworkKey(KEY_CUR_V), PersistentDataType.LONG, curV);
        pdc.set(steamworkKey(KEY_CUR_D1), PersistentDataType.LONG, curD1);
        pdc.set(steamworkKey(KEY_CUR_D2), PersistentDataType.LONG, curD2);
        pdc.set(steamworkKey(KEY_CUR_D3), PersistentDataType.LONG, curD3);
        pdc.set(steamworkKey(KEY_COUNTDOWN), PersistentDataType.INTEGER, strokeCountdown);
        pdc.set(steamworkKey(KEY_PULSE_LEFT), PersistentDataType.INTEGER, pulseTicksLeft);
        pdc.set(steamworkKey(KEY_RUNNING), PersistentDataType.BOOLEAN, running);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearBarrelInventory();
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== 差分引擎 =====

    @Override
    public void tick() {
        if (running) {
            // 分频模式：红石脉冲高电平倒计时，归零回落
            if (pulseTicksLeft > 0) {
                pulseTicksLeft--;
                if (pulseTicksLeft == 0 && outputMode == OutputMode.FREQUENCY) {
                    setLevel(0);
                }
            }
            if (strokeCountdown > 0) {
                strokeCountdown--;
            }
            if (strokeCountdown <= 0) {
                doStroke();
            }
        }
        // 比较器占位物自愈（玩家/漏斗可能动过桶内容）
        if (barrelInventoryDirty()) {
            writeComparatorInventory(Math.max(0, lastLevel));
        }
        notifyGuiItems();
    }

    private void doStroke() {
        var fluid = steamKind.fluid();
        if (fluidAmount(fluid) < steamPerStroke) {
            // 蒸汽不足，停摆，每 tick 重试，不递推不消耗
            stalled = true;
            strokeCountdown = 1;
            return;
        }
        stalled = false;
        removeFluid(fluid, steamPerStroke);

        long output = curV; // 本次序列值（递推前）

        switch (outputMode) {
            case REDSTONE -> setLevel(mapToLevel(output));
            case STEAM_PULSE -> pushSteam(output);
            case FREQUENCY -> {
                setLevel(15);
                pulseTicksLeft = Math.max(1, pulseLength);
            }
        }

        // 前向差分递推（模 ceiling，整机闭合 → 序列周期回绕）
        long nextV = wrap(curV + curD1);
        if (order >= 2) curD1 = wrap(curD1 + curD2);
        if (order >= 3) curD2 = wrap(curD2 + curD3);
        curV = nextV;

        strokeCountdown = (outputMode == OutputMode.FREQUENCY)
                ? (int) clampLong(curV, minFreqInterval, maxFreqInterval)
                : strokeInterval;

        spawnStrokeFx();
    }

    /** 把序列值（已在 [0,ceiling)）归一化映射为 0–15 红石等级。ceiling=16 时即恒等映射。 */
    private int mapToLevel(long v) {
        long w = wrap(v);
        if (ceiling <= 1) return (int) Math.max(0, Math.min(15, w));
        double norm = (double) w / (double) (ceiling - 1);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, norm)) * 15.0);
    }

    private double pushSteam(long amount) {
        if (amount <= 0) return 0.0;
        var dst = SteamLogicSupport.fluidNeighbor(getBlock(), outputFace);
        if (dst == null) return 0.0;
        double cap = Math.min((double) amount, maxPulseAmount);
        return SteamLogicSupport.transfer(this, dst, steamKind, cap);
    }

    private void resetRegisters() {
        curV = wrap(initV);
        curD1 = wrap(initD1);
        curD2 = wrap(initD2);
        curD3 = wrap(initD3);
        strokeCountdown = strokeInterval;
        pulseTicksLeft = 0;
        stalled = false;
        lastLevel = -1; // 强制下次冲程重写比较器
    }

    /** 任意配置变更后调用：重新夹取并把运行态对齐到新配置（所见即所得）。 */
    private void applyConfigChange() {
        ceiling = clampCeiling(ceiling);
        order = clampOrder(order);
        initV = wrap(initV);
        initD1 = wrap(initD1);
        initD2 = wrap(initD2);
        initD3 = wrap(initD3);
        resetRegisters();
    }

    private long wrap(long x) {
        long c = Math.max(1, ceiling);
        return ((x % c) + c) % c;
    }

    private static long clampLong(long x, long lo, long hi) {
        return Math.max(lo, Math.min(hi, x));
    }

    private int clampCeiling(int c) {
        return Math.max(CEILING_MIN, Math.min(CEILING_MAX, c));
    }

    private int clampOrder(int o) {
        return Math.max(1, Math.min(maxOrder, o));
    }

    private void spawnStrokeFx() {
        var loc = getBlock().getLocation().add(0.5, 0.85, 0.5);
        getBlock().getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.16, 0.06, 0.16, 0.0);
        if (Math.random() < 0.15) {
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.25f, 1.5f);
        }
    }

    // ===== 比较器占位物（与压力传感器同套方案）=====

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
                .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.signal_item")
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

    private void notifyGuiItems() {
        steamKindItem.notifyWindows();
        outputModeItem.notifyWindows();
        outputFaceItem.notifyWindows();
        orderItem.notifyWindows();
        ceilingItem.notifyWindows();
        resetItem.notifyWindows();
        runToggleItem.notifyWindows();
        statusItem.notifyWindows();
        initVItem.notifyWindows();
        initD1Item.notifyWindows();
        initD2Item.notifyWindows();
        initD3Item.notifyWindows();
    }

    // ===== WAILA =====

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("mode", outputMode.component()),
                RebarArgument.of("value", String.valueOf(curV)),
                RebarArgument.of("state", statusComponent())
        ));
    }

    private @NotNull Component statusComponent() {
        if (!running) {
            return Component.translatable("steamwork.gui.steam_difference_engine.state.stopped").color(NamedTextColor.GRAY);
        }
        if (stalled) {
            return Component.translatable("steamwork.gui.steam_difference_engine.state.starved").color(NamedTextColor.RED);
        }
        return Component.translatable("steamwork.gui.steam_difference_engine.state.running").color(NamedTextColor.GREEN);
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# K # M # F # T #",
                        "# O # C # R # S #",
                        "# V # A # B # D #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('K', steamKindItem)
                .addIngredient('M', outputModeItem)
                .addIngredient('F', outputFaceItem)
                .addIngredient('T', runToggleItem)
                .addIngredient('O', orderItem)
                .addIngredient('C', ceilingItem)
                .addIngredient('R', resetItem)
                .addIngredient('S', statusItem)
                .addIngredient('V', initVItem)
                .addIngredient('A', initD1Item)
                .addIngredient('B', initD2Item)
                .addIngredient('D', initD3Item)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.title"));
    }

    private final class SteamKindItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.COMPARATOR)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.steam_tier")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKind.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle_steam"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            steamKind = steamKind.next();
            notifyGuiItems();
        }
    }

    private final class OutputModeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material icon = switch (outputMode) {
                case REDSTONE -> Material.REDSTONE_TORCH;
                case STEAM_PULSE -> Material.LIGHT_BLUE_STAINED_GLASS;
                case FREQUENCY -> Material.CLOCK;
            };
            return ItemStackBuilder.of(icon)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.output_mode")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", outputMode.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.mode_desc." + outputMode.name().toLowerCase())),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            outputMode = outputMode.next();
            lastLevel = -1; // 模式切换强制重写比较器
            pulseTicksLeft = 0;
            notifyGuiItems();
        }
    }

    private final class OutputFaceItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean connected = SteamLogicSupport.fluidNeighbor(getBlock(), outputFace) != null;
            boolean relevant = outputMode == OutputMode.STEAM_PULSE;
            return ItemStackBuilder.of(relevant ? (connected ? Material.PISTON : Material.BARRIER) : Material.GRAY_STAINED_GLASS_PANE)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.output_face",
                            RebarArgument.of("face", SteamLogicSupport.faceComponent(outputFace)))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(relevant
                                    ? (connected ? "steamwork.gui.common.connected_fluid_buffer" : "steamwork.gui.common.no_fluid_buffer")
                                    : "steamwork.gui.steam_difference_engine.face_only_pulse")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            BlockFace next = SteamLogicSupport.nextFace(outputFace);
            outputFace = next == null ? BlockFace.NORTH : next;
            notifyGuiItems();
        }
    }

    private final class OrderItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.REPEATER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.order",
                            RebarArgument.of("order", String.valueOf(order)))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.order_desc",
                                    RebarArgument.of("max", String.valueOf(maxOrder)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            order = order >= maxOrder ? 1 : order + 1;
            applyConfigChange();
            notifyGuiItems();
        }
    }

    private final class CeilingItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BARRIER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.ceiling",
                            RebarArgument.of("ceiling", String.valueOf(ceiling)))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.ceiling_desc")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.adjust_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            int step = type.isShiftClick() ? 16 : 1;
            ceiling = clampCeiling(ceiling + (type.isRightClick() ? -step : step));
            applyConfigChange();
            notifyGuiItems();
        }
    }

    private final class ResetItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.LEVER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.reset")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.reset_desc"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            resetRegisters();
            notifyGuiItems();
        }
    }

    private final class RunToggleItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(running ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(SteamLogicSupport.ni(Component.translatable(running
                            ? "steamwork.gui.steam_difference_engine.running_on"
                            : "steamwork.gui.steam_difference_engine.running_off")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.running_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            running = !running;
            notifyGuiItems();
        }
    }

    private enum Register { V, D1, D2, D3 }

    private final class RegisterItem extends AbstractItem {
        private final Register register;

        RegisterItem(@NotNull Register register) {
            this.register = register;
        }

        private long value() {
            return switch (register) {
                case V -> initV;
                case D1 -> initD1;
                case D2 -> initD2;
                case D3 -> initD3;
            };
        }

        private void set(long v) {
            long w = wrap(v);
            switch (register) {
                case V -> initV = w;
                case D1 -> initD1 = w;
                case D2 -> initD2 = w;
                case D3 -> initD3 = w;
            }
        }

        /** 该寄存器在当前阶数下是否参与递推。 */
        private boolean active() {
            return switch (register) {
                case V -> true;
                case D1 -> order >= 1;
                case D2 -> order >= 2;
                case D3 -> order >= 3;
            };
        }

        private @NotNull String labelKey() {
            return switch (register) {
                case V -> "steamwork.gui.steam_difference_engine.reg_v";
                case D1 -> "steamwork.gui.steam_difference_engine.reg_d1";
                case D2 -> "steamwork.gui.steam_difference_engine.reg_d2";
                case D3 -> "steamwork.gui.steam_difference_engine.reg_d3";
            };
        }

        private @NotNull Material icon() {
            boolean on = active();
            return switch (register) {
                case V -> on ? Material.PAPER : Material.GRAY_DYE;
                default -> on ? Material.GLOWSTONE_DUST : Material.GRAY_DYE;
            };
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(icon())
                    .amount((int) Math.max(1, Math.min(64, value() + 1)))
                    .name(SteamLogicSupport.ni(Component.translatable(labelKey(),
                            RebarArgument.of("value", String.valueOf(value())))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(active()
                                    ? "steamwork.gui.steam_difference_engine.reg_active"
                                    : "steamwork.gui.steam_difference_engine.reg_inactive")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.adjust_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            int step = type.isShiftClick() ? 10 : 1;
            set(value() + (type.isRightClick() ? -step : step));
            applyConfigChange();
            notifyGuiItems();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material icon = !running ? Material.GRAY_STAINED_GLASS_PANE
                    : stalled ? Material.RED_STAINED_GLASS_PANE
                    : Material.GREEN_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(icon)
                    .name(SteamLogicSupport.ni(statusComponent()))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.cur_value",
                                    RebarArgument.of("value", String.valueOf(curV)))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_difference_engine.cur_level",
                                    RebarArgument.of("level", String.valueOf(mapToLevel(curV))))),
                            SteamLogicSupport.ni(SteamLogicSupport.pressureLine(
                                    fluidAmount(steamKind.fluid()), fluidCapacity(steamKind.fluid())))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }
}
