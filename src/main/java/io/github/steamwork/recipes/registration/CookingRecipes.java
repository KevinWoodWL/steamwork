package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.recipe.CookingBookCategory;

import static io.github.steamwork.recipes.registration.RecipeHelpers.rebarChoice;
import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class CookingRecipes {

    private CookingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        addAlloyCookingRecipes(
                SteamworkKeys.NICHROME_INGOT,
                SteamworkItems.NICHROME_DUST,
                SteamworkItems.NICHROME_INGOT,
                0.8F,
                260);
        FurnaceRecipe zincConcentrateFurnace = new FurnaceRecipe(
                steamworkKey("zinc_ingot_from_concentrate_smelting"),
                SteamworkItems.ZINC_INGOT,
                rebarChoice(SteamworkItems.ZINC_CONCENTRATE),
                0.4F,
                180);
        zincConcentrateFurnace.setCategory(CookingBookCategory.MISC);
        RecipeType.VANILLA_FURNACE.addRecipe(zincConcentrateFurnace);

        BlastingRecipe zincConcentrateBlasting = new BlastingRecipe(
                steamworkKey("zinc_ingot_from_concentrate_blasting"),
                SteamworkItems.ZINC_INGOT,
                rebarChoice(SteamworkItems.ZINC_CONCENTRATE),
                0.4F,
                90);
        zincConcentrateBlasting.setCategory(CookingBookCategory.MISC);
        RecipeType.VANILLA_BLASTING.addRecipe(zincConcentrateBlasting);

        addAlloyCookingRecipes(
                SteamworkKeys.INVAR_INGOT,
                SteamworkItems.INVAR_DUST,
                SteamworkItems.INVAR_INGOT,
                0.7F,
                220);
        addAlloyCookingRecipes(
                SteamworkKeys.DURALUMIN_INGOT,
                SteamworkItems.DURALUMIN_DUST,
                SteamworkItems.DURALUMIN_INGOT,
                0.6F,
                200);
        addAlloyCookingRecipes(
                SteamworkKeys.TUNGSTEN_INGOT,
                SteamworkItems.TUNGSTEN_DUST,
                SteamworkItems.TUNGSTEN_INGOT,
                1.2F,
                360);
        addAlloyCookingRecipes(
                SteamworkKeys.MANGANESE_STEEL_INGOT,
                SteamworkItems.MANGANESE_STEEL_DUST,
                SteamworkItems.MANGANESE_STEEL_INGOT,
                0.9F,
                260);
        addAlloyCookingRecipes(
                SteamworkKeys.MANGANESE_BRONZE_INGOT,
                SteamworkItems.MANGANESE_BRONZE_DUST,
                SteamworkItems.MANGANESE_BRONZE_INGOT,
                0.7F,
                220);
    }

    private static void addAlloyCookingRecipes(
            NamespacedKey ingotKey,
            ItemStack dustItem,
            ItemStack ingotItem,
            float experience,
            int furnaceTicks
    ) {
        FurnaceRecipe furnace = new FurnaceRecipe(
                steamworkKey(ingotKey.getKey() + "_from_dust_smelting"),
                ingotItem,
                rebarChoice(dustItem),
                experience,
                furnaceTicks);
        furnace.setCategory(CookingBookCategory.MISC);
        RecipeType.VANILLA_FURNACE.addRecipe(furnace);

        BlastingRecipe blasting = new BlastingRecipe(
                steamworkKey(ingotKey.getKey() + "_from_dust_blasting"),
                ingotItem,
                rebarChoice(dustItem),
                experience,
                Math.max(1, furnaceTicks / 2));
        blasting.setCategory(CookingBookCategory.MISC);
        RecipeType.VANILLA_BLASTING.addRecipe(blasting);
    }
}
