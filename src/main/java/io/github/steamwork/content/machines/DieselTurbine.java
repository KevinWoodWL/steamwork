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
 * 柴油涡轮 —— 专门加速 Pylon 柴油系列机器。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>包路径属于 {@code io.github.pylonmc.pylon.content.machines.diesel} 的所有柴油机器
 *       （柴油破碎机、柴油锤头、柴油混合附件、钯凝结器、生物精炼厂等）</li>
 * </ul>
 * <p>注：Pylon 的 {@code DieselRefuelable} 接口未被任何柴油机器实现，
 * 因此改用包路径识别。</p>
 * 同时驱动上限由配置文件 {@code max-targets} 控制（默认 4）。
 */
public class DieselTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public DieselTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public DieselTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.diesel_turbine";
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
     * 仅识别 Pylon 柴油包路径下的机器（按类名包路径判断，因为 DieselRefuelable 接口未被实际机器实现）。
     */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        RebarBlock rebarBlock = BlockStorage.get(block);
        if (rebarBlock == null || rebarBlock == this) return null;
        if (rebarBlock instanceof SteamBoostable) return null;
        if (rebarBlock instanceof AbstractSteamBooster) return null;
        if (!isPylonDieselMachine(block)) return null;
        return processorTargetType(rebarBlock);
    }
}
