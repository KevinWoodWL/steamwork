package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonFluids;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamDistillationRecipe;
import io.github.steamwork.recipes.SteamDistillationRecipe.FluidOutput;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class DistillationRecipes {

    private DistillationRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // 1 section: 原生树脂 + 蒸汽 → 精炼树脂 + 植物精华
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_raw_resin"),
                RecipeInput.of(SteamworkItems.RAW_RESIN, 2),
                null,
                List.of(
                        SteamworkItems.REFINED_RESIN.clone().asQuantity(1),
                        SteamworkItems.PLANT_ESSENCE.clone().asQuantity(1)
                ),
                List.<FluidOutput>of(),
                1,
                160.0,
                240
        ));

        // 1 section: 矿物助剂 + 蒸馏水 + 蒸汽 → 矿物浓缩液 + 废酸瓶
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_mineral_flux"),
                RecipeInput.of(SteamworkItems.MINERAL_FLUX, 2),
                RecipeInput.of(SteamworkFluids.DISTILLED_WATER, 200.0),
                List.of(
                        SteamworkItems.MINERAL_CONCENTRATE.clone().asQuantity(1),
                        SteamworkItems.WASTE_ACID_VIAL.clone().asQuantity(1)
                ),
                List.of(
                        new FluidOutput(SteamworkFluids.MINERAL_LEACHATE, 150.0)
                ),
                1,
                200.0,
                280
        ));

        // 1 section: 纤维残渣 → 骨粉 x3 + 矿物助熔剂（消化死路副产物）
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_fiber_residue"),
                RecipeInput.of(SteamworkItems.FIBER_RESIDUE, 2),
                null,
                List.of(
                        ItemStack.of(Material.BONE_MEAL, 3),
                        SteamworkItems.MINERAL_FLUX.clone().asQuantity(1)
                ),
                List.<FluidOutput>of(),
                1,
                120.0,
                200
        ));

        // 1 section: 废酸瓶 + 矿物助熔剂 → 矿物浓缩液瓶 x2（废酸中和再利用）
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_waste_acid_neutralize"),
                RecipeInput.of(SteamworkItems.WASTE_ACID_VIAL, 2),
                null,
                List.of(
                        SteamworkItems.MINERAL_LEACHATE_VIAL.clone().asQuantity(2),
                        SteamworkItems.MINERAL_FLUX.clone().asQuantity(1)
                ),
                List.<FluidOutput>of(),
                1,
                140.0,
                220
        ));

        // 2 sections: 精炼树脂 → 硫化橡胶 x2 + 植物精华（高效橡胶路线，比浸煮桶出率更高）
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_refined_resin_to_rubber"),
                RecipeInput.of(SteamworkItems.REFINED_RESIN, 2),
                null,
                List.of(
                        SteamworkItems.VULCANIZED_RUBBER.clone().asQuantity(3),
                        SteamworkItems.PLANT_ESSENCE.clone().asQuantity(1)
                ),
                List.<FluidOutput>of(),
                2,
                240.0,
                340
        ));

        // 2 sections: 矿物浓缩物 + 蒸馏水 → 锌精矿 x3 + 矿物助熔剂（矿物深加工，锌精矿高产路线）
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_mineral_concentrate"),
                RecipeInput.of(SteamworkItems.MINERAL_CONCENTRATE, 2),
                RecipeInput.of(SteamworkFluids.DISTILLED_WATER, 150.0),
                List.of(
                        SteamworkItems.ZINC_CONCENTRATE.clone().asQuantity(3),
                        SteamworkItems.MINERAL_FLUX.clone().asQuantity(1)
                ),
                List.of(
                        new FluidOutput(SteamworkFluids.WASTE_ACID, 100.0)
                ),
                2,
                280.0,
                360
        ));

        // 3 sections: 植物纤维 + 蒸汽 → 植物精华 + 纤维残渣 + 蒸馏水
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_plant_fiber"),
                RecipeInput.of(SteamworkItems.PLANT_FIBER, 4),
                null,
                List.of(
                        SteamworkItems.PLANT_ESSENCE.clone().asQuantity(2),
                        SteamworkItems.FIBER_RESIDUE.clone().asQuantity(1),
                        SteamworkItems.DISTILLED_WATER_VIAL.clone().asQuantity(1)
                ),
                List.of(
                        new FluidOutput(SteamworkFluids.DISTILLED_WATER, 100.0)
                ),
                3,
                320.0,
                420
        ));

        // 3 sections: 煤炭干馏 → 焦炭 + 煤焦油（轻/重馏分）
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_coal_carbonization"),
                RecipeInput.of(ItemStack.of(Material.COAL, 4)),
                null,
                List.of(
                        ItemStack.of(Material.COAL, 3)
                ),
                List.of(
                        new FluidOutput(SteamworkFluids.LIGHT_FRACTION, 150.0),
                        new FluidOutput(SteamworkFluids.HEAVY_FRACTION, 100.0)
                ),
                3,
                380.0,
                480
        ));

        // 4 sections: 植物油 + 蒸汽 → 轻/中/重馏分
        SteamDistillationRecipe.RECIPE_TYPE.addRecipe(new SteamDistillationRecipe(
                steamworkKey("distillation_plant_oil_cracking"),
                RecipeInput.of(SteamworkItems.RAW_RESIN, 1),
                RecipeInput.of(PylonFluids.PLANT_OIL, 500.0),
                List.<org.bukkit.inventory.ItemStack>of(),
                List.of(
                        new FluidOutput(SteamworkFluids.LIGHT_FRACTION, 200.0),
                        new FluidOutput(SteamworkFluids.MEDIUM_FRACTION, 200.0),
                        new FluidOutput(SteamworkFluids.HEAVY_FRACTION, 100.0)
                ),
                4,
                500.0,
                600
        ));
    }
}
