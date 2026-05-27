package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.HammerRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps {@link HammerRecipe} for the Hydraulic Forge.
 *
 * <p>HammerRecipe requires a specific hammer tool and mining level; the Hydraulic Forge
 * replaces the manual hammer with its steam-driven hydraulic ram, so tool requirements
 * are irrelevant and only the input/output items matter.</p>
 */
public class HammerRecipeWrapper extends PylonRecipeWrapper<HammerRecipe> {

    public HammerRecipeWrapper(@NotNull HammerRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return wrapped.input();
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.result().clone();
    }
}
