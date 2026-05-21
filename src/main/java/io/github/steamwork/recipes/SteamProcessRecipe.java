package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽加工类机器（灭菌箱 / 浸煮桶 / 洗选槽 等）的通用配方接口。
 * 抽出 {@link io.github.steamwork.content.machines.AbstractSteamProcessor} 共享的字段访问。
 */
public interface SteamProcessRecipe extends RebarRecipe {

    /** 单一物品输入（蒸汽作为流体输入由机器单独处理）。 */
    @NotNull RecipeInput.Item ingredient();

    /** 配方需要消耗的蒸汽量。 */
    double steamCost();

    /** 配方需要的总 tick 数。 */
    int timeTicks();

    /**
     * 实际产出 {@link ItemStack}（已包含数量倍数）。
     * 大多数配方只用 {@code result} 字段，洗选配方有单独的 {@code outputCount}，
     * 各 record 类型按需重写。
     */
    @NotNull ItemStack producedStack();
}
