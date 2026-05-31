package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamResearchRecipe;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class ResearchRecipes {

    private ResearchRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_mineral_analysis"),
                RecipeInput.of(SteamworkItems.MINERAL_ANALYSIS_SAMPLE),
                5,
                240.0,
                400,
                SteamworkItems.ANALYSIS_RESIDUE.clone(),
                "material"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_organic_analysis"),
                RecipeInput.of(SteamworkItems.ORGANIC_ANALYSIS_SAMPLE),
                5,
                240.0,
                400,
                SteamworkItems.ANALYSIS_RESIDUE.clone(),
                "biology"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_metallurgical_analysis"),
                RecipeInput.of(SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE),
                7,
                320.0,
                500,
                SteamworkItems.ANALYSIS_RESIDUE.clone(),
                "precision"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_fluid_analysis"),
                RecipeInput.of(SteamworkItems.FLUID_ANALYSIS_SAMPLE),
                6,
                280.0,
                440,
                SteamworkItems.ANALYSIS_RESIDUE.clone(),
                "chemistry"
        ));

        // ===== 高级分析样本（约 5× 基础点数，蒸汽效率翻倍）=====
        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_refined_mineral"),
                RecipeInput.of(SteamworkItems.REFINED_MINERAL_SAMPLE),
                25,
                500.0,
                600,
                SteamworkItems.ANALYSIS_RESIDUE.clone().asQuantity(2),
                "material"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_concentrated_organic"),
                RecipeInput.of(SteamworkItems.CONCENTRATED_ORGANIC_SAMPLE),
                25,
                500.0,
                600,
                SteamworkItems.ANALYSIS_RESIDUE.clone().asQuantity(2),
                "biology"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_refined_metallurgical"),
                RecipeInput.of(SteamworkItems.REFINED_METALLURGICAL_SAMPLE),
                35,
                650.0,
                740,
                SteamworkItems.ANALYSIS_RESIDUE.clone().asQuantity(2),
                "precision"
        ));

        SteamResearchRecipe.RECIPE_TYPE.addRecipe(new SteamResearchRecipe(
                steamworkKey("research_purified_fluid"),
                RecipeInput.of(SteamworkItems.PURIFIED_FLUID_SAMPLE),
                30,
                560.0,
                660,
                SteamworkItems.ANALYSIS_RESIDUE.clone().asQuantity(2),
                "chemistry"
        ));
    }
}
