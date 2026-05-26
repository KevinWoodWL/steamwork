package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.CrudeAlloyFurnaceRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Wraps {@link CrudeAlloyFurnaceRecipe} for the steam steeping vat. */
public class CrudeAlloyFurnaceRecipeWrapper extends PylonRecipeWrapper<CrudeAlloyFurnaceRecipe> {

    public CrudeAlloyFurnaceRecipeWrapper(@NotNull CrudeAlloyFurnaceRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return wrapped.input1();
    }

    @Override
    public @NotNull List<RecipeInput.Item> ingredients() {
        return List.of(wrapped.input1(), wrapped.input2());
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.result();
    }
}
