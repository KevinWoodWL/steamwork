package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.MoldingRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps {@link MoldingRecipe} for the Steam Precision Mill.
 *
 * <p>MoldingRecipe represents pressing/cycling material into a shaped mold.
 * The Precision Mill can replicate this via its steam-driven precision spindle,
 * machining the input into the molded shape over multiple cycles.</p>
 */
public class MoldingRecipeWrapper extends PylonRecipeWrapper<MoldingRecipe> {

    public MoldingRecipeWrapper(@NotNull MoldingRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        // MoldingRecipe stores input as ItemStack; wrap via RecipeInput factory
        ItemStack input = wrapped.input();
        RecipeInput ri = RecipeInput.of(input, input.getAmount());
        if (!(ri instanceof RecipeInput.Item item)) {
            throw new IllegalStateException(
                    "RecipeInput.of(ItemStack, int) 返回了非 RecipeInput.Item 类型：" + ri.getClass()
                    + "，请更新 MoldingRecipeWrapper.ingredient()");
        }
        return item;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.result().clone();
    }
}
