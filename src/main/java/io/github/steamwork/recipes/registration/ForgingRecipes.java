package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamForgingRecipe;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 液压锻造机配方。
 * <p>定位：金属锭 → 液压系统中间件（高压管件、法兰、活塞、密封件、锻造钢板）。</p>
 */
public final class ForgingRecipes {

    private ForgingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 高压管件链 =====
        // 锰青铜锭 → 高压管件（耐磨耐压，适合高压气路与液压管路）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_high_pressure_pipe"),
                RecipeInput.of(SteamworkItems.MANGANESE_BRONZE_INGOT, 2),
                SteamworkItems.HIGH_PRESSURE_PIPE.clone().asQuantity(4),
                50.0,
                240));

        // 因瓦合金 → 高压法兰（连接件，需要低热膨胀以保证密封）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_high_pressure_flange"),
                RecipeInput.of(SteamworkItems.INVAR_INGOT, 1),
                SteamworkItems.HIGH_PRESSURE_FLANGE.clone().asQuantity(2),
                50.0,
                240));

        // ===== 液压执行件 =====
        // 锰青铜 → 液压活塞（耐磨核心运动件）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_hydraulic_piston"),
                RecipeInput.of(SteamworkItems.MANGANESE_BRONZE_INGOT, 1),
                SteamworkItems.HYDRAULIC_PISTON.clone(),
                60.0,
                280));

        // 硫化橡胶 → 液压密封件（锻造机高压压制成型）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_hydraulic_seal"),
                RecipeInput.of(SteamworkItems.VULCANIZED_RUBBER, 2),
                SteamworkItems.HYDRAULIC_SEAL.clone().asQuantity(2),
                40.0,
                200));

        // ===== 锻造钢板（结构强化件） =====
        // 热处理金属 → 锻造钢板（高强度结构件，比直接用锭密度高一倍）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_plate_from_heat_treated"),
                RecipeInput.of(SteamworkItems.HEAT_TREATED_METAL, 1),
                SteamworkItems.FORGED_PLATE.clone().asQuantity(2),
                45.0,
                220));

        // 锰钢锭 → 锻造钢板（高产路径，给后期玩家用）
        SteamForgingRecipe.RECIPE_TYPE.addRecipe(new SteamForgingRecipe(
                steamworkKey("forge_plate_from_manganese_steel"),
                RecipeInput.of(SteamworkItems.MANGANESE_STEEL_INGOT, 1),
                SteamworkItems.FORGED_PLATE.clone().asQuantity(4),
                70.0,
                300));
    }
}
