package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.item.ItemTypeWrapper;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 蒸汽加工类机器（灭菌箱 / 浸煮桶 / 洗选槽 等）的通用配方接口。
 * 抽出 {@link io.github.steamwork.content.machines.AbstractSteamProcessor} 共享的字段访问。
 */
public interface SteamProcessRecipe extends RebarRecipe {

    /** 单一物品输入（蒸汽作为流体输入由机器单独处理）。 */
    @NotNull RecipeInput.Item ingredient();

    default @NotNull List<RecipeInput.Item> ingredients() {
        return List.of(ingredient());
    }

    /** 配方需要消耗的蒸汽量。 */
    double steamCost();

    /** 配方需要的总 tick 数。 */
    int timeTicks();

    /**
     * 实际产出 {@link ItemStack}（已包含数量倍数）。
     * 大多数配方只用 {@code result} 字段，洗选配方有单独的 {@code outputCount}，
     * 各 record 类型按需重写。
     * <p>
     * <b>注意</b>：每次配方开始时，机器会先调用 {@link #onRecipeStart()} 通知配方，
     * 随机产出类配方（如 Pylon 砂轮配方）应在 onRecipeStart 里固定本次产出，
     * 确保同一次配方周期内所有对 producedStack() 的调用返回同一个物品。
     */
    @NotNull ItemStack producedStack();

    /**
     * 机器启动本配方时的回调。用于非确定性产出的包装器（如砂轮随机结果表）
     * 提前固定本次产出，使 {@link #producedStack()} 在整个配方周期内幂等。
     * 默认空实现（确定性产出的配方不需要处理）。
     */
    default void onRecipeStart() {}

    /**
     * 按 Rebar 物品类型（schema / Material）匹配输出，使指南里点击同 schema 的物品
     * （例如未带 PDC 的基础“序列工件”）也能跳转到本配方页面。
     *
     * <p>{@link RebarRecipe#isOutput} 默认使用 {@code isSimilar()}（严格比对 NBT / PDC），
     * 会让带 PDC 的工序中间品无法从基础物品被发现。这里改为与 {@link RebarRecipe#isInput}
     * 一致的 {@link ItemTypeWrapper} 比较，对称且更直观。</p>
     */
    @Override
    default boolean isOutput(@NotNull ItemStack stack) {
        ItemTypeWrapper target = ItemTypeWrapper.of(stack);
        for (FluidOrItem r : getResults()) {
            if (r instanceof FluidOrItem.Item item
                    && ItemTypeWrapper.of(item.item()).equals(target)) {
                return true;
            }
        }
        return false;
    }
}
