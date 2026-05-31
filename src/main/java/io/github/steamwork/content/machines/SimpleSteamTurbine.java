package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 简易蒸汽涡轮 —— 只能加速原版熔炉 / 高炉 / 烟熏炉。
 */
public class SimpleSteamTurbine extends AbstractSteamBooster {

    private final int maxTargetsConfig = getSettings().getOrThrow("max-targets", ConfigAdapter.INTEGER);

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SimpleSteamTurbine(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SimpleSteamTurbine(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.simple_steam_turbine";
    }

    @Override
    protected int particleCount() {
        return 2;
    }

    @Override
    public int upgradeSlotCount() {
        return 2;
    }

    @Override
    protected int maxTargets() {
        return maxTargetsConfig;
    }

    /** 简易涡轮只识别原版熔炉系列。 */
    @Override
    protected @Nullable TargetType identifyTarget(@NotNull Block block) {
        return isVanillaFurnace(block) ? TargetType.VANILLA_FURNACE : null;
    }
}
