package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamCatalyticReactionRecipe;
import io.github.steamwork.util.SequencedWorkpiece;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class CatalyticReactionRecipes {

    private CatalyticReactionRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        SteamCatalyticReactionRecipe reactPalladium = new SteamCatalyticReactionRecipe(
                steamworkKey("react_palladium_alloy"),
                List.of(
                        RecipeInput.of(PylonItems.PALLADIUM_INGOT),
                        RecipeInput.of(SteamworkItems.INVAR_INGOT),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SequencedWorkpiece.palladiumAlloy(1),
                260.0,
                360);
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(reactPalladium);
        // 钯合金工序链第 1 步：用更高 priority 让指南把工序按 1 → 2 → 3 顺序展示
        RebarRecipe.setPriority(reactPalladium, 30.0);

        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(new SteamCatalyticReactionRecipe(
                steamworkKey("react_high_polymer"),
                List.of(
                        RecipeInput.of(PylonItems.FIBER, 2),
                        RecipeInput.of(SteamworkItems.VULCANIZED_RUBBER),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SteamworkItems.HIGH_POLYMER.clone().asQuantity(2),
                220.0,
                320));
    }
}
