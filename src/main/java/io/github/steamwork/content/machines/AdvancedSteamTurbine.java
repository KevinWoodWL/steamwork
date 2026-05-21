package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarProcessor;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 高级蒸汽涡轮 —— 加速范围内一切"在工作的机器"。
 * <p>
 * 加速对象：
 * <ul>
 *   <li>原版熔炉 / 高炉 / 烟熏炉</li>
 *   <li>所有 Steamwork 自家 {@link SteamBoostable} 加工机器</li>
 *   <li>任何实现 {@link RebarProcessor} 的 Rebar/Pylon 机器
 *       （Pylon 的液压、柴油等系列）</li>
 * </ul>
 */
public class AdvancedSteamTurbine extends AbstractSteamBooster {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public AdvancedSteamTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public AdvancedSteamTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.advanced_steam_turbine";
    }

    @Override
    protected int particleCount() {
        return 4;
    }

    /**
     * 优先识别原版熔炉，再识别 Steamwork 自家加工机器，最后识别 Pylon/Rebar 处理类机器。
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
        if (rebarBlock instanceof RebarProcessor) {
            return TargetType.REBAR_PROCESSOR;
        }
        return null;
    }
}
