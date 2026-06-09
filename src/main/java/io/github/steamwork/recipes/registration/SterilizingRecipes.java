package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamSterilizingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SterilizingRecipes {

    private SterilizingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_rotten_flesh_to_leather"),
                RecipeInput.of(ItemStack.of(Material.ROTTEN_FLESH, 4)),
                ItemStack.of(Material.LEATHER),
                55.0,
                240));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_rotten_flesh_to_biomass"),
                RecipeInput.of(ItemStack.of(Material.ROTTEN_FLESH, 2)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                35.0,
                180));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_bone_meal_to_biomass"),
                RecipeInput.of(ItemStack.of(Material.BONE_MEAL, 4)),
                SteamworkItems.STERILE_BIOMASS,
                30.0,
                160));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_bones_to_biomass"),
                RecipeInput.of(ItemStack.of(Material.BONE, 2)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                40.0,
                200));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_spider_eye"),
                RecipeInput.of(ItemStack.of(Material.SPIDER_EYE, 2)),
                ItemStack.of(Material.FERMENTED_SPIDER_EYE),
                40.0,
                200));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_poisonous_potato"),
                RecipeInput.of(ItemStack.of(Material.POISONOUS_POTATO)),
                ItemStack.of(Material.POTATO, 2),
                20.0,
                120));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_biomass_to_culture"),
                RecipeInput.of(SteamworkItems.STERILE_BIOMASS, 3),
                SteamworkItems.STERILE_CULTURE,
                55.0,
                240));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_crimson_fungus"),
                RecipeInput.of(ItemStack.of(Material.CRIMSON_FUNGUS)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                35.0,
                180));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_warped_fungus"),
                RecipeInput.of(ItemStack.of(Material.WARPED_FUNGUS)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                35.0,
                180));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_red_mushroom"),
                RecipeInput.of(ItemStack.of(Material.RED_MUSHROOM, 2)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                30.0,
                160));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_brown_mushroom"),
                RecipeInput.of(ItemStack.of(Material.BROWN_MUSHROOM, 2)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(2),
                30.0,
                160));

        SteamSterilizingRecipe.RECIPE_TYPE.addRecipe(new SteamSterilizingRecipe(
                steamworkKey("sterilize_moss_to_biomass"),
                RecipeInput.of(ItemStack.of(Material.MOSS_BLOCK)),
                SteamworkItems.STERILE_BIOMASS.clone().asQuantity(3),
                45.0,
                220));
    }
}
