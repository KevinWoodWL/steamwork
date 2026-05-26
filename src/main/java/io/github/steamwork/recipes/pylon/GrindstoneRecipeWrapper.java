package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.GrindstoneRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps {@link GrindstoneRecipe} for the steam grinder.
 * <p>
 * The Pylon grindstone produces output from a weighted random table.
 * To keep {@link #producedStack()} idempotent within a single recipe cycle,
 * the random draw is performed once in {@link #onRecipeStart()} and cached
 * until the next call to {@link #onRecipeStart()}.
 */
public class GrindstoneRecipeWrapper extends PylonRecipeWrapper<GrindstoneRecipe> {

    /** Sampled at recipe-start; null means the next call to producedStack() must re-sample. */
    @Nullable private ItemStack sampledOutput = null;

    public GrindstoneRecipeWrapper(@NotNull GrindstoneRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    /** Called by the machine when it begins this recipe. Fixes the random result for this cycle. */
    @Override
    public void onRecipeStart() {
        sampledOutput = wrapped.results().getRandom();
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return wrapped.input();
    }

    /**
     * Returns the output sampled at recipe-start. Falls back to a fresh draw only
     * if called before {@link #onRecipeStart()} (e.g., during the pre-start
     * {@code canStoreOutput} check in {@code tryStartRecipe}).
     */
    @Override
    public @NotNull ItemStack producedStack() {
        if (sampledOutput == null) {
            sampledOutput = wrapped.results().getRandom();
        }
        return sampledOutput.clone();
    }
}
