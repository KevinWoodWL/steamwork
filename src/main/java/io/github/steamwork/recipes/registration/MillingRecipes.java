package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamMillingRecipe;
import io.github.steamwork.util.SequencedWorkpiece;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class MillingRecipes {

    private MillingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 因瓦合金系列 =====
        // 低热膨胀性 → 精密传动零件，精密齿轮是中期机器的核心零件。
        // 钯合金工序链第 3 步
        SteamMillingRecipe millBlank = new SteamMillingRecipe(
                steamworkKey("mill_palladium_alloy_blank"),
                RecipeInput.of(SequencedWorkpiece.palladiumAlloy(2)),
                SequencedWorkpiece.palladiumAlloy(3),
                160.0,
                300);
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(millBlank);
        RebarRecipe.setPriority(millBlank, 10.0);

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_invar_to_precision_gear"),
                RecipeInput.of(SteamworkItems.INVAR_INGOT, 2),
                SteamworkItems.PRECISION_GEAR.clone().asQuantity(3),
                80.0,
                320));

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_duralumin_to_precision_gear"),
                RecipeInput.of(SteamworkItems.DURALUMIN_INGOT, 1),
                SteamworkItems.PRECISION_GEAR.clone().asQuantity(2),
                70.0,
                280));

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_invar_to_precision_screw"),
                RecipeInput.of(SteamworkItems.INVAR_INGOT, 1),
                SteamworkItems.PRECISION_SCREW.clone().asQuantity(4),
                60.0,
                240));

        // ===== 钨系列 =====
        // 高硬度高熔点 → 耐磨密封件和精密阀门。
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_tungsten_to_precision_valve"),
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 1),
                SteamworkItems.PRECISION_VALVE.clone().asQuantity(2),
                100.0,
                400));

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_tungsten_to_wear_plate"),
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 2),
                SteamworkItems.WEAR_PLATE.clone().asQuantity(3),
                90.0,
                360));

        // ===== 硬铝系列 =====
        // 轻质高强 → 散热结构件，轻量化机器框架用。
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_duralumin_to_heat_sink"),
                RecipeInput.of(SteamworkItems.DURALUMIN_INGOT, 2),
                SteamworkItems.HEAT_SINK.clone().asQuantity(3),
                70.0,
                280));

        // ===== 锰钢系列 =====
        // 高耐磨 → 锯齿刀片（铣床自身的核心耗材，也是后续机器配方的材料）。
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_manganese_steel_to_milling_blade"),
                RecipeInput.of(SteamworkItems.MANGANESE_STEEL_INGOT, 2),
                SteamworkItems.MILLING_BLADE.clone().asQuantity(2),
                85.0,
                340));

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_manganese_bronze_to_precision_bearing"),
                RecipeInput.of(SteamworkItems.MANGANESE_BRONZE_INGOT, 2),
                SteamworkItems.PRECISION_BEARING.clone().asQuantity(3),
                85.0,
                340));

        // ===== Pylon 协同 =====
        // 钯金 → 精密催化芯（稀有材料，产出高价值零件）。
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_palladium_to_catalyst_core"),
                RecipeInput.of(PylonItems.PALLADIUM_INGOT, 1),
                SteamworkItems.CATALYST_CORE.clone().asQuantity(1),
                120.0,
                480));

        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_palladium_alloy_to_catalyst_core"),
                RecipeInput.of(SteamworkItems.PALLADIUM_ALLOY_INGOT, 1),
                SteamworkItems.CATALYST_CORE.clone().asQuantity(3),
                140.0,
                560));

        // 钢 → 精密轴承（Pylon 钢的铣床出路）。
        SteamMillingRecipe.RECIPE_TYPE.addRecipe(new SteamMillingRecipe(
                steamworkKey("mill_steel_to_precision_bearing"),
                RecipeInput.of(PylonItems.STEEL_INGOT, 2),
                SteamworkItems.PRECISION_BEARING.clone().asQuantity(3),
                75.0,
                300));
    }
}
