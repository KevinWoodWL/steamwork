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

        // 蒸汽飞行核心工序链第 1 步：钯合金锭 + 钨锭 + 催化芯 → 活化飞行合金基体
        SteamCatalyticReactionRecipe reactFlightCore = new SteamCatalyticReactionRecipe(
                steamworkKey("react_flight_core"),
                List.of(
                        RecipeInput.of(SteamworkItems.PALLADIUM_ALLOY_INGOT),
                        RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT, 2),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SequencedWorkpiece.flightCore(1),
                300.0,
                420);
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(reactFlightCore);
        RebarRecipe.setPriority(reactFlightCore, 30.0);

        // 机器人核心工序链第 1 步：钯合金锭 + 精密齿轮×2 + 催化核心 → 活化机器人核心基体
        SteamCatalyticReactionRecipe reactRobotCore = new SteamCatalyticReactionRecipe(
                steamworkKey("react_robot_core"),
                List.of(
                        RecipeInput.of(SteamworkItems.PALLADIUM_ALLOY_INGOT),
                        RecipeInput.of(SteamworkItems.PRECISION_GEAR, 2),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SequencedWorkpiece.robotCore(1),
                320.0,
                480);
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(reactRobotCore);
        RebarRecipe.setPriority(reactRobotCore, 30.0);

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

        // ===== 有机催化合成 =====
        // 植物精华 + 矿物浸出液 → 无菌培养基（有机-矿物协同催化，给无菌培养基加一条高效来源）
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(new SteamCatalyticReactionRecipe(
                steamworkKey("react_organic_catalyst"),
                List.of(
                        RecipeInput.of(SteamworkItems.PLANT_ESSENCE, 2),
                        RecipeInput.of(SteamworkItems.MINERAL_LEACHATE_VIAL),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SteamworkItems.STERILE_CULTURE.clone().asQuantity(3),
                180.0,
                280));

        // ===== 矿物精炼 =====
        // 矿物浓缩液 + 废酸 → 锌精矿（酸浸提锌，给锌精矿加一条化工路径）
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(new SteamCatalyticReactionRecipe(
                steamworkKey("react_refined_mineral"),
                List.of(
                        RecipeInput.of(SteamworkItems.MINERAL_CONCENTRATE, 2),
                        RecipeInput.of(SteamworkItems.WASTE_ACID_VIAL),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SteamworkItems.ZINC_CONCENTRATE.clone().asQuantity(4),
                200.0,
                300));

        // ===== 高聚物硫化 =====
        // 高聚物 + 硫化橡胶 → 液压密封件（解决高聚物无下游，同时给液压密封加一条不依赖锻造机的来源）
        SteamCatalyticReactionRecipe.RECIPE_TYPE.addRecipe(new SteamCatalyticReactionRecipe(
                steamworkKey("react_vulcanized_compound"),
                List.of(
                        RecipeInput.of(SteamworkItems.HIGH_POLYMER, 2),
                        RecipeInput.of(SteamworkItems.VULCANIZED_RUBBER, 2),
                        RecipeInput.of(SteamworkItems.CATALYST_CORE)
                ),
                SteamworkItems.HYDRAULIC_SEAL.clone().asQuantity(4),
                240.0,
                340));
    }
}
