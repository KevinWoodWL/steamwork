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
 * 基础加工涡轮 —— 专门加速 Steamwork 基础加工机器。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>所有实现 {@link SteamBoostable} 但不实现 {@link PrecisionSteamBoostable} 的机器
 *       （蒸汽灭菌箱、浸煮桶、洗选槽、冲压机、研磨机、压力炉、蒸馏塔、加热室等）</li>
 * </ul>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 5）。
 */
public class BasicProcessingTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public BasicProcessingTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public BasicProcessingTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.basic_processing_turbine";
    }

    @Override
    protected int particleCount() {
        return 3;
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
     * 仅识别基础 {@link SteamBoostable} 机器，排除精密系列（{@link PrecisionSteamBoostable}）。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;
        if (rebarBlock instanceof PrecisionSteamBoostable) return null;
        if (rebarBlock instanceof SteamBoostable) {
            return TargetType.STEAMWORK_BOOSTABLE;
        }
        return null;
    }
}
