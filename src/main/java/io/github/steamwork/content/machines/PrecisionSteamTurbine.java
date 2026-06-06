package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.steamwork.SteamworkFluids;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 精密全能涡轮 —— 加速范围内所有类型的机器，但同时驱动数量严格受限。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>原版熔炉 / 高炉 / 烟熏炉</li>
 *   <li>所有 Steamwork 自家 {@link SteamBoostable} 加工机器</li>
 *   <li>任何实现 {@link ProcessorRebarBlock} 的 Rebar/Pylon 机器</li>
 * </ul>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 2）。
 */
public class PrecisionSteamTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public PrecisionSteamTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionSteamTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.precision_steam_turbine";
    }

    @Override
    protected int particleCount() {
        return 6;
    }

    @Override
    public int upgradeSlotCount() {
        return 4;
    }

    @Override
    protected int maxTargets() {
        return maxTargetsConfig;
    }

    /** 精密全能涡轮消耗过热蒸汽，是全套精密机器体系的顶端驱动力。 */
    @Override
    protected @NotNull RebarFluid boosterFluid() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    /**
     * B — 破顶叠加：无视全局 MAX_BOOST_STACKS 限制。
     * 即使其他涡轮已将同一台机器的叠加层数填满，本涡轮的加速仍然生效。
     */
    @Override
    protected boolean canBypassBoostCap() { return true; }

    /**
     * C — 协同效率：每额外加速一台机器，单次耗汽降低 5%，最低保留 65%（8 台满载时）。
     * 使用上一 tick 的 {@link #lastTargetsBoosted} 作为基准，避免循环依赖。
     * 折扣曲线：1台=100%, 4台=85%, 8台=65%。
     */
    @Override
    protected double synergyMultiplier() {
        return Math.max(0.65, 1.0 - 0.05 * Math.max(0, lastTargetsBoosted - 1));
    }

    /**
     * 识别所有可加速目标：原版熔炉、Steamwork 加工机器、Pylon/Rebar 处理机器。
     * 排除自身避免自加速死循环。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        if (isVanillaFurnace(block)) {
            return TargetType.VANILLA_FURNACE;
        }

        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;

        if (rebarBlock instanceof SteamBoostable) {
            return TargetType.STEAMWORK_BOOSTABLE;
        }
        // 包含液压、柴油及所有其他 Pylon/Rebar 处理机器（全能）
        return processorTargetType(rebarBlock);
    }
}
