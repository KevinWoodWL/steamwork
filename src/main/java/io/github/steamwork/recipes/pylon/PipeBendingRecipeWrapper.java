package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.PipeBendingRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Wraps {@link PipeBendingRecipe} for the steam press. */
public class PipeBendingRecipeWrapper extends PylonRecipeWrapper<PipeBendingRecipe> {

    public PipeBendingRecipeWrapper(@NotNull PipeBendingRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return wrapped.input();
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.result();
    }
}
