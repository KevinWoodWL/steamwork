package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonFluids;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleRebarMultiblock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;

public abstract class AbstractSteamBoiler extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        SimpleRebarMultiblock,
        TickingRebarBlock {

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double waterBuffer = getSettings().getOrThrow("water-buffer", ConfigAdapter.DOUBLE);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double waterPerCycle = getSettings().getOrThrow("water-per-cycle", ConfigAdapter.DOUBLE);
    private final double steamPerCycle = getSettings().getOrThrow("steam-per-cycle", ConfigAdapter.DOUBLE);

    // ===== 分级惩罚 / 加成（yml 可选，旧 yml 自动用默认值）=====
    /** 启动期 tick 数：开始连续加热后这么多 tick 内，蒸汽产量按 startupMultiplier 折扣。0 = 无启动期。 */
    private final int startupTicks = getSettings().get("startup-ticks", ConfigAdapter.INTEGER, 0);
    /** 启动期产汽倍率（0~1），仅在启动期内生效。 */
    private final double startupMultiplier = getSettings().get("startup-multiplier", ConfigAdapter.DOUBLE, 0.5);
    /** 蒸汽泄漏间隔（tick）。0 = 不漏。 */
    private final int leakIntervalTicks = getSettings().get("leak-interval-ticks", ConfigAdapter.INTEGER, 0);
    /** 单次泄漏量占 buffer 的比例。 */
    private final double leakFraction = getSettings().get("leak-fraction", ConfigAdapter.DOUBLE, 0.01);
    /** 热源利用率倍率（< 1 是惩罚，> 1 是加成）。 */
    private final double heatEfficiency = getSettings().get("heat-efficiency", ConfigAdapter.DOUBLE, 1.0);

    // 加热状态缓存 - 避免每tick都检查
    private static final int HEAT_CACHE_TICKS = 10;  // 每10tick检查一次加热状态
    private int heatCacheTicks = 0;
    private Boolean cachedHeatedState = null;
    private int cachedBelowBlockType = 0;

    // 启动期 / 泄漏的运行时计数器（不持久化，chunk reload 等价于"重新冷启动"）。
    private int consecutiveHeatedTicks = 0;
    private int leakAccumulator = 0;

    public static class Item extends RebarItem {

        private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
        private final double waterBuffer = getSettings().getOrThrow("water-buffer", ConfigAdapter.DOUBLE);
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double waterPerCycle = getSettings().getOrThrow("water-per-cycle", ConfigAdapter.DOUBLE);
        private final double steamPerCycle = getSettings().getOrThrow("steam-per-cycle", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            double cyclesPerSecond = 20.0 / Math.max(1, tickInterval);
            return List.of(
                    RebarArgument.of("water-buffer", UnitFormat.MILLIBUCKETS.format(waterBuffer)),
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("water-per-cycle", UnitFormat.MILLIBUCKETS.format(waterPerCycle)),
                    RebarArgument.of("steam-per-cycle", UnitFormat.MILLIBUCKETS.format(steamPerCycle)),
                    RebarArgument.of("water-per-second", UnitFormat.MILLIBUCKETS_PER_SECOND.format(waterPerCycle * cyclesPerSecond)),
                    RebarArgument.of("steam-per-second", UnitFormat.MILLIBUCKETS_PER_SECOND.format(steamPerCycle * cyclesPerSecond))
            );
        }
    }

    protected AbstractSteamBoiler(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setMultiblockDirection(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false);
        createFluidBuffer(PylonFluids.WATER, waterBuffer, true, false);
        createFluidBuffer(producedSteam(), steamBuffer, false, true);
    }

    protected AbstractSteamBoiler(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        // Rebar 不会在 PDC 构造器里自动恢复朝向，但 getFacing() 会在 onMultiblockFormed -> refreshBlockTextureItem 里被调用。
        // 如果旧存档没有方向数据就会 IllegalStateException，这里兜底设一个默认方向。
        try {
            getFacing();
        } catch (IllegalStateException e) {
            setFacing(BlockFace.SOUTH);
        }
    }

    @Override
    public void tick() {
        if (heatCacheTicks > 0) {
            heatCacheTicks--;
        }

        boolean heated = isHeatedCached();

        // 维护"连续加热 tick 数"：用于启动期判定。失去热源立刻清零（重新进入冷启动）。
        if (heated) {
            consecutiveHeatedTicks += tickInterval;
        } else {
            consecutiveHeatedTicks = 0;
        }

        // 蒸汽泄漏：即使不在产汽也会漏（密封不严），但仅在 buffer 有蒸汽时触发。
        if (leakIntervalTicks > 0 && fluidAmount(producedSteam()) > 0) {
            leakAccumulator += tickInterval;
            if (leakAccumulator >= leakIntervalTicks) {
                leakAccumulator -= leakIntervalTicks;
                double leakAmt = Math.min(
                        fluidCapacity(producedSteam()) * leakFraction,
                        fluidAmount(producedSteam()));
                if (leakAmt > 0) {
                    removeFluid(producedSteam(), leakAmt);
                    spawnLeakFx();
                }
            }
        }

        // 产汽前置条件检查。
        if (!isFormedAndFullyLoaded()
                || !heated
                || fluidAmount(PylonFluids.WATER) < waterPerCycle) {
            return;
        }

        // 计算本次的有效产汽量（启动期 × 热源利用率 × 压力反作用）。
        double effectiveSteam = steamPerCycle * currentMultiplier() * pressureBackoff();
        if (effectiveSteam <= 0 || fluidSpaceRemaining(producedSteam()) < effectiveSteam) {
            return;
        }

        removeFluid(PylonFluids.WATER, waterPerCycle);
        addFluid(producedSteam(), effectiveSteam);
        refreshBlockTextureItem();
        spawnRunningFx();
    }

    /** 计算当前 cycle 的有效产汽倍率（启动期惩罚 × 热源利用率）。 */
    private double currentMultiplier() {
        double mult = heatEfficiency;
        if (startupTicks > 0 && consecutiveHeatedTicks < startupTicks) {
            mult *= startupMultiplier;
        }
        return mult;
    }

    /**
     * 压力反作用：蒸汽 buffer 越满，外向压差越小，产汽速率衰减（"高压反推"）。
     * 让 {@code pressureState()} 不再只是显示用的伪状态，而是真的影响产能。
     * <ul>
     *   <li>≤50% 填充："low/medium" 区，100% 产速</li>
     *   <li>50%-90% 填充："high" 渐进区，线性衰减到 30%</li>
     *   <li>≥90% 填充："venting" 区，30% 上限。剩下的 0% 由 buffer 溢出兜底</li>
     * </ul>
     */
    private double pressureBackoff() {
        double cap = fluidCapacity(producedSteam());
        if (cap <= 0) return 1.0;
        double fillRatio = fluidAmount(producedSteam()) / cap;
        if (fillRatio <= 0.5) return 1.0;
        if (fillRatio >= 0.9) return 0.3;
        // 0.5 → 1.0, 0.9 → 0.3 的线性衰减。
        return 1.0 - (fillRatio - 0.5) * (0.7 / 0.4);
    }

    /** 蒸汽泄漏的视觉 + 音效。 */
    private void spawnLeakFx() {
        Block b = getBlock();
        b.getWorld().spawnParticle(
                Particle.CLOUD,
                b.getLocation().add(0.5, 1.1, 0.5),
                12, 0.4, 0.1, 0.4, 0.05);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.5f);
    }

    /** 每次成功产汽时的视觉 + 音效，子类可覆盖以实现差异化效果。默认无效果。 */
    protected void spawnRunningFx() {}

    /** WAILA 蒸汽条颜色，子类可覆盖。默认普通蒸汽色 #d8edf0。 */
    protected @NotNull TextColor steamBarColor() {
        return TextColor.fromHexString("#d8edf0");
    }

    @Override
    public @NotNull Map<@NotNull Vector3i, @NotNull MultiblockComponent> getComponents() {
        return Map.of(
                new Vector3i(-1, 0, 0), casingComponent(),
                new Vector3i(1, 0, 0), casingComponent(),
                new Vector3i(0, 1, 0), capComponent()
        );
    }

    @Override
    public void onMultiblockFormed() {
        SimpleRebarMultiblock.super.onMultiblockFormed();
        refreshBlockTextureItem();
    }

    @Override
    public void onMultiblockUnformed(boolean partUnloaded) {
        SimpleRebarMultiblock.super.onMultiblockUnformed(partUnloaded);
        refreshBlockTextureItem();
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        Map<String, kotlin.Pair<String, Integer>> properties = super.getBlockTextureProperties();
        properties.put("active", new kotlin.Pair<>(Boolean.toString(isFormedAndFullyLoaded() && isHeated()), 2));
        properties.put("pressure", new kotlin.Pair<>(pressureState(), 4));
        return properties;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(ProgressBar.fluidContentsWithName(PylonFluids.WATER, fluidCapacity(PylonFluids.WATER), fluidAmount(PylonFluids.WATER)))
                .add(ProgressBar.fluidContentsWithName(producedSteam(), fluidCapacity(producedSteam()), fluidAmount(producedSteam())))
                .add(Component.translatable("steamwork.pressure." + pressureState()))
                .add(Component.translatable("steamwork.structure." + (isFormedAndFullyLoaded() ? "formed" : "missing")));
    }

    protected abstract @NotNull MultiblockComponent casingComponent();

    protected abstract @NotNull MultiblockComponent capComponent();

    /**
     * 决定本锅炉输出哪一种蒸汽。低阶锅炉返回 {@code SteamworkFluids.STEAM}，
     * 高阶锅炉（锰钢、钨）返回 {@code SteamworkFluids.SUPERHEATED_STEAM}，
     * 给高阶机器（卷管机、结晶器、蒸馏塔等）专用。
     */
    protected abstract @NotNull RebarFluid producedSteam();

    private boolean isHeated() {
        Block below = getBlock().getRelative(BlockFace.DOWN);
        Material heat = below.getType();

        if (heat == Material.CAMPFIRE
                || heat == Material.SOUL_CAMPFIRE
                || heat == Material.FIRE
                || heat == Material.SOUL_FIRE
                || heat == Material.LAVA
                || heat == Material.MAGMA_BLOCK
                || heat == Material.LAVA_CAULDRON) {
            return true;
        }

        return false;
    }

    /**
     * 使用缓存的加热状态检测
     */
    private boolean isHeatedCached() {
        Block below = getBlock().getRelative(BlockFace.DOWN);
        int currentBlockType = below.getType().ordinal();

        // 检查是否需要刷新缓存
        boolean needsRefresh = cachedHeatedState == null
                || heatCacheTicks <= 0
                || currentBlockType != cachedBelowBlockType;

        if (needsRefresh) {
            cachedHeatedState = isHeated();
            cachedBelowBlockType = currentBlockType;
            heatCacheTicks = HEAT_CACHE_TICKS;
        }

        return cachedHeatedState;
    }

    private String pressureState() {
        double fill = fluidAmount(producedSteam()) / Math.max(1.0, fluidCapacity(producedSteam()));
        if (fill >= 0.9) {
            return "venting";
        }
        if (fill >= 0.65) {
            return "high";
        }
        if (fill >= 0.25) {
            return "medium";
        }
        return "low";
    }
}
