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
 * 液压涡轮 —— 专门加速 Pylon 液压系列机器。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>包路径属于 {@code io.github.pylonmc.pylon.content.machines.hydraulics} 的所有液压机器
 *       （液压破碎机、液压锤头、液压混合附件、液压表锯、液压管道弯折机等）</li>
 * </ul>
 * <p>注：Pylon 的 {@code HydraulicRefuelable} 接口未被任何液压机器实现，
 * 因此改用包路径识别。</p>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 4）。
 */
public class HydraulicTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public HydraulicTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public HydraulicTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.hydraulic_turbine";
    }

    @Override
    protected int particleCount() {
        return 4;
    }

    @Override
    public int upgradeSlotCount() {
        return 3;
    }

    @Override
    protected int maxTargets() {
        return maxTargetsConfig;
    }

    /** 液压系统天然需要加压蒸汽来维持液压压力。 */
    @Override
    protected @NotNull RebarFluid boosterFluid() {
        return SteamworkFluids.PRESSURIZED_STEAM;
    }

    /**
     * 仅识别 Pylon 液压包路径下的机器（按类名包路径判断，因为 HydraulicRefuelable 接口未被实际机器实现）。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;
        if (rebarBlock instanceof SteamBoostable) return null;
        if (rebarBlock instanceof AbstractSteamBooster) return null;
        if (!isPylonHydraulicMachine(block)) return null;
        return processorTargetType(rebarBlock);
    }
}
