package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.FormingRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Wraps {@link FormingRecipe} for the steam press. */
public class FormingRecipeWrapper extends PylonRecipeWrapper<FormingRecipe> {

    public FormingRecipeWrapper(@NotNull FormingRecipe wrapped, double steamCost, int timeTicks) {
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
