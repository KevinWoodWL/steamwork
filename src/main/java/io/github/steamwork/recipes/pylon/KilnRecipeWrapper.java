package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.pylon.recipes.KilnRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps {@link KilnRecipe} for the steam heating chamber.
 * Only recipes with an item output are included (fluid-only kiln recipes are skipped at load time).
 * If the recipe has a second input, both are required.
 */
public class KilnRecipeWrapper extends PylonRecipeWrapper<KilnRecipe> {

    public KilnRecipeWrapper(@NotNull KilnRecipe wrapped, double steamCost, int timeTicks) {
        super(wrapped, steamCost, timeTicks);
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return wrapped.input1();
    }

    @Override
    public @NotNull List<RecipeInput.Item> ingredients() {
        List<RecipeInput.Item> list = new ArrayList<>();
        list.add(wrapped.input1());
        if (wrapped.input2() != null) {
            list.add(wrapped.input2());
        }
        return list;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return wrapped.outputItem();
    }
}
