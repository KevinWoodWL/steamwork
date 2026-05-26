package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.TableSawRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Wraps {@link TableSawRecipe} for the steam press. */
public class TableSawRecipeWrapper extends PylonRecipeWrapper<TableSawRecipe> {

    public TableSawRecipeWrapper(@NotNull TableSawRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        ItemStack input = wrapped.input();
        // TableSawRecipe.input() 返回 ItemStack 而非 RecipeInput.Item，需要通过工厂方法包装。
        // RecipeInput.of(ItemStack, int) 对 ItemStack 参数返回的实现类即 RecipeInput.Item，
        // 若 Rebar 升级后此假设不成立，运行期会在此处抛出 ClassCastException，届时改为对应工厂方法。
        RecipeInput ri = RecipeInput.of(input, input.getAmount());
        if (!(ri instanceof RecipeInput.Item item)) {
            throw new IllegalStateException(
                    "RecipeInput.of(ItemStack, int) 返回了非 RecipeInput.Item 类型：" + ri.getClass()
                    + "，请更新 TableSawRecipeWrapper.ingredient()");
        }
        return item;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.result();
    }
}
