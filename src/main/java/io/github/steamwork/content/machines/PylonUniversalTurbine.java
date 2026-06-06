package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 通用 Pylon 涡轮 —— 加速 Pylon/Rebar 中不属于液压或柴油系列的其他处理机器。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>任何实现 {@link io.github.pylonmc.rebar.block.interfaces.ProcessorRebarBlock} 或
 *       {@link io.github.pylonmc.rebar.block.interfaces.RecipeProcessorRebarBlock} 的 Rebar/Pylon 机器</li>
 *   <li>排除液压系列（包路径 {@code hydraulics.*}）和柴油系列（包路径 {@code diesel.*}），
 *       它们分别由 {@link HydraulicTurbine} 和 {@link DieselTurbine} 负责</li>
 *   <li>排除 Steamwork 自家机器（{@link SteamBoostable}）</li>
 * </ul>
 * <p>注：Pylon 的 {@code HydraulicRefuelable} / {@code DieselRefuelable} 接口未被实际机器实现，
 * 改用包路径排除。</p>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 3）。
 */
public class PylonUniversalTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public PylonUniversalTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PylonUniversalTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.pylon_universal_turbine";
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

    /**
     * 识别非液压、非柴油的 Pylon/Rebar 处理机器。
     * 液压/柴油机器通过包路径排除（Pylon 的 HydraulicRefuelable/DieselRefuelable 未被实际机器实现）。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;
        if (rebarBlock instanceof SteamBoostable) return null;
        if (rebarBlock instanceof AbstractSteamBooster) return null;
        if (isPylonHydraulicMachine(block)) return null;
        if (isPylonDieselMachine(block)) return null;
        return processorTargetType(rebarBlock);
    }
}
