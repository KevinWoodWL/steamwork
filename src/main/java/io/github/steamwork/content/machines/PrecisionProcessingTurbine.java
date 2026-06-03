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
 * 精密加工涡轮 —— 专门加速 Steamwork 精密加工机器。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>所有实现 {@link PrecisionSteamBoostable} 的机器
 *       （精密磨坊、精密铸造炉、精密催化反应釜、精密结晶塔、精密离心机、
 *        重型冲击破碎机、液压锻造机）</li>
 * </ul>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 3）。
 */
public class PrecisionProcessingTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public PrecisionProcessingTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionProcessingTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.precision_processing_turbine";
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

    /** 精密加工涡轮消耗过热蒸汽，与精密加工机器本身的能量体系对齐。 */
    @Override
    protected @NotNull RebarFluid boosterFluid() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    /**
     * 仅识别实现 {@link PrecisionSteamBoostable} 的精密加工机器。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;
        if (rebarBlock instanceof PrecisionSteamBoostable) {
            return TargetType.STEAMWORK_BOOSTABLE;
        }
        return null;
    }
}
