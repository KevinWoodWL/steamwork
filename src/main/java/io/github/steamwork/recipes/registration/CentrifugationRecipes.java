package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.WeightedSet;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamCentrifugationRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 精密离心机配方。
 * <p>定位：模糊混合物 → 加权随机多产物。</p>
 */
public final class CentrifugationRecipes {

    private CentrifugationRecipes() {
        throw new AssertionError("Utility class");
    }

    private static WeightedSet<ItemStack> weighted(Object... pairs) {
        WeightedSet<ItemStack> set = new WeightedSet<>();
        for (int i = 0; i < pairs.length; i += 2) {
            ItemStack stack = (ItemStack) pairs[i];
            float weight = ((Number) pairs[i + 1]).floatValue();
            set.add(new WeightedSet.Element<>(stack, weight));
        }
        return set;
    }

    public static void register() {
        // ===== 矿物分析样品离心：科研路线高级回收 =====
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_mineral_sample"),
                RecipeInput.of(SteamworkItems.MINERAL_ANALYSIS_SAMPLE, 1),
                weighted(
                        PylonItems.IRON_DUST.clone(), 35f,
                        PylonItems.COPPER_DUST.clone(), 25f,
                        PylonItems.GOLD_DUST.clone(), 10f,
                        PylonItems.TIN_DUST.clone(), 20f,
                        SteamworkItems.SILICA_GRIT.clone(), 10f),
                40.0,
                160));

        // 冶金样品 → 高级合金粉
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_metallurgical_sample"),
                RecipeInput.of(SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE, 1),
                weighted(
                        SteamworkItems.INVAR_DUST.clone(), 30f,
                        SteamworkItems.NICHROME_DUST.clone(), 25f,
                        SteamworkItems.MANGANESE_BRONZE_DUST.clone(), 20f,
                        SteamworkItems.MANGANESE_STEEL_DUST.clone(), 15f,
                        SteamworkItems.TUNGSTEN_DUST.clone(), 10f),
                70.0,
                280));

        // 流体样品 → 流体凝缩物
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_fluid_sample"),
                RecipeInput.of(SteamworkItems.FLUID_ANALYSIS_SAMPLE, 1),
                weighted(
                        SteamworkItems.MINERAL_FLUX.clone(), 30f,
                        SteamworkItems.SILICA_GRIT.clone(), 25f,
                        SteamworkItems.PLANT_ESSENCE.clone(), 20f,
                        SteamworkItems.REFINED_RESIN.clone(), 15f,
                        SteamworkItems.RAW_RESIN.clone(), 10f),
                50.0,
                200));

        // 有机样品 → 植物 / 生质产物
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_organic_sample"),
                RecipeInput.of(SteamworkItems.ORGANIC_ANALYSIS_SAMPLE, 1),
                weighted(
                        SteamworkItems.PLANT_ESSENCE.clone(), 30f,
                        SteamworkItems.STERILE_BIOMASS.clone(), 25f,
                        SteamworkItems.STERILE_CULTURE.clone(), 15f,
                        SteamworkItems.PLANT_FIBER.clone().asQuantity(2), 20f,
                        SteamworkItems.RAW_RESIN.clone(), 10f),
                40.0,
                180));

        // ===== 废料离心 =====
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_machine_scrap"),
                RecipeInput.of(SteamworkItems.MACHINE_SCRAP, 4),
                weighted(
                        new ItemStack(Material.IRON_NUGGET, 4), 50f,
                        PylonItems.IRON_DUST.clone(), 25f,
                        PylonItems.COPPER_DUST.clone(), 15f,
                        PylonItems.GOLD_DUST.clone(), 5f,
                        SteamworkItems.SILICA_GRIT.clone(), 5f),
                50.0,
                220));

        // 分析残渣离心
        SteamCentrifugationRecipe.RECIPE_TYPE.addRecipe(new SteamCentrifugationRecipe(
                steamworkKey("centrifuge_analysis_residue"),
                RecipeInput.of(SteamworkItems.ANALYSIS_RESIDUE, 2),
                weighted(
                        SteamworkItems.SILICA_GRIT.clone(), 40f,
                        SteamworkItems.MINERAL_FLUX.clone(), 30f,
                        PylonItems.GOLD_DUST.clone(), 10f,
                        SteamworkItems.ZINC_CONCENTRATE.clone(), 20f),
                35.0,
                160));
    }
}
