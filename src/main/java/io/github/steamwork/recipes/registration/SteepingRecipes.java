package io.github.steamwork.recipes.registration;

import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamSteepingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteepingRecipes {

    private SteepingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_bamboo_to_fiber"),
                RecipeInput.of(new ItemStack(Material.BAMBOO, 4)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(3),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_sugar_cane_to_pulp"),
                RecipeInput.of(new ItemStack(Material.SUGAR_CANE, 3)),
                SteamworkItems.STEAM_PULP.clone().asQuantity(2),
                30.0,
                160));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_wheat_to_fiber"),
                RecipeInput.of(new ItemStack(Material.WHEAT, 3)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_kelp_to_fiber"),
                RecipeInput.of(new ItemStack(Material.KELP, 3)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_vines_to_fiber"),
                RecipeInput.of(new ItemStack(Material.VINE, 2)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                20.0,
                120));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_mangrove_roots_to_resin"),
                RecipeInput.of(new ItemStack(Material.MANGROVE_ROOTS)),
                SteamworkItems.RAW_RESIN.clone().asQuantity(2),
                45.0,
                220));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_honeycomb_to_resin"),
                RecipeInput.of(new ItemStack(Material.HONEYCOMB, 2)),
                SteamworkItems.RAW_RESIN.clone().asQuantity(3),
                40.0,
                200));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_slime_to_resin"),
                RecipeInput.of(new ItemStack(Material.SLIME_BALL)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_oak_log_to_charcoal"),
                RecipeInput.of(new ItemStack(Material.OAK_LOG)),
                new ItemStack(Material.CHARCOAL, 2),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_oak_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_OAK_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_spruce_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_SPRUCE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_birch_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_BIRCH_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_jungle_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_JUNGLE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_acacia_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_ACACIA_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_dark_oak_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_DARK_OAK_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_mangrove_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_MANGROVE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_cherry_log_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_CHERRY_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_crimson_stem_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_CRIMSON_STEM)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                50.0,
                260));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_warped_stem_to_treated_wood"),
                RecipeInput.of(new ItemStack(Material.STRIPPED_WARPED_STEM)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                50.0,
                260));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_spruce_log_to_resin"),
                RecipeInput.of(new ItemStack(Material.SPRUCE_LOG)),
                SteamworkItems.RAW_RESIN,
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_crimson_roots_to_resin"),
                RecipeInput.of(new ItemStack(Material.CRIMSON_ROOTS, 2)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_warped_roots_to_resin"),
                RecipeInput.of(new ItemStack(Material.WARPED_ROOTS, 2)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_clay_to_terracotta"),
                RecipeInput.of(new ItemStack(Material.CLAY)),
                new ItemStack(Material.TERRACOTTA),
                35.0,
                200));

        // 处理木材 -> 纤维板 已搬到蒸汽冲压机（PressingRecipes），那才是真正的"压制"工艺。

        // 树脂浸煮硫化 -> 硫化橡胶（原灭菌箱配方，搬到浸煮桶更符合"浸煮硫化"工艺）
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_resin_to_vulcanized_rubber"),
                RecipeInput.of(SteamworkItems.RAW_RESIN),
                SteamworkItems.VULCANIZED_RUBBER,
                60.0,
                260));

        // 纤维浸渍胶合 -> 覆胶织物（原灭菌箱配方，搬到浸煮桶更符合"浸渍"工艺）
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_fiber_to_rubberized_fabric"),
                RecipeInput.of(SteamworkItems.PLANT_FIBER, 2),
                SteamworkItems.RUBBERIZED_FABRIC,
                45.0,
                220));
    }
}
