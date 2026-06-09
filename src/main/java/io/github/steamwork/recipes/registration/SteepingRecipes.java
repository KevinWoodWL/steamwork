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
        // 圆石浸煮：16 圆石 → 1 下界岩（高温蒸汽软化并渗入硫化物，模拟下界岩的形成环境）。
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_cobblestone_to_netherrack"),
                RecipeInput.of(ItemStack.of(Material.COBBLESTONE, 16)),
                ItemStack.of(Material.NETHERRACK, 1),
                30.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_bamboo_to_fiber"),
                RecipeInput.of(ItemStack.of(Material.BAMBOO, 4)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(3),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_sugar_cane_to_pulp"),
                RecipeInput.of(ItemStack.of(Material.SUGAR_CANE, 3)),
                SteamworkItems.STEAM_PULP.clone().asQuantity(2),
                30.0,
                160));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_wheat_to_fiber"),
                RecipeInput.of(ItemStack.of(Material.WHEAT, 3)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_kelp_to_fiber"),
                RecipeInput.of(ItemStack.of(Material.KELP, 3)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                25.0,
                140));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_vines_to_fiber"),
                RecipeInput.of(ItemStack.of(Material.VINE, 2)),
                SteamworkItems.PLANT_FIBER.clone().asQuantity(2),
                20.0,
                120));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_mangrove_roots_to_resin"),
                RecipeInput.of(ItemStack.of(Material.MANGROVE_ROOTS)),
                SteamworkItems.RAW_RESIN.clone().asQuantity(2),
                45.0,
                220));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_honeycomb_to_resin"),
                RecipeInput.of(ItemStack.of(Material.HONEYCOMB, 2)),
                SteamworkItems.RAW_RESIN.clone().asQuantity(3),
                40.0,
                200));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_slime_to_resin"),
                RecipeInput.of(ItemStack.of(Material.SLIME_BALL)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_oak_log_to_charcoal"),
                RecipeInput.of(ItemStack.of(Material.OAK_LOG)),
                ItemStack.of(Material.CHARCOAL, 2),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_oak_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_OAK_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_spruce_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_SPRUCE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_birch_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_BIRCH_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_jungle_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_JUNGLE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_acacia_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_ACACIA_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_dark_oak_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_DARK_OAK_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_mangrove_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_MANGROVE_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_cherry_log_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_CHERRY_LOG)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                45.0,
                240));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_crimson_stem_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_CRIMSON_STEM)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                50.0,
                260));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_warped_stem_to_treated_wood"),
                RecipeInput.of(ItemStack.of(Material.STRIPPED_WARPED_STEM)),
                SteamworkItems.TREATED_WOOD.clone().asQuantity(4),
                50.0,
                260));

        // 原木浸煮提取粗树脂（按含脂量分级）
        // 高含脂（云杉、丛林木）：1 原木 → 1 粗树脂
        for (Material log : new Material[]{Material.SPRUCE_LOG, Material.JUNGLE_LOG}) {
            SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                    steamworkKey("steep_" + log.name().toLowerCase() + "_to_resin"),
                    RecipeInput.of(ItemStack.of(log)),
                    SteamworkItems.RAW_RESIN.clone(),
                    45.0, 240));
        }
        // 中等含脂（橡树、暗橡树）：2 原木 → 1 粗树脂
        for (Material log : new Material[]{Material.OAK_LOG, Material.DARK_OAK_LOG}) {
            SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                    steamworkKey("steep_" + log.name().toLowerCase() + "_to_resin"),
                    RecipeInput.of(ItemStack.of(log, 2)),
                    SteamworkItems.RAW_RESIN.clone(),
                    45.0, 240));
        }
        // 低含脂（白桦、金合欢、樱花、红树）：3 原木 → 1 粗树脂
        for (Material log : new Material[]{
                Material.BIRCH_LOG, Material.ACACIA_LOG,
                Material.CHERRY_LOG, Material.MANGROVE_LOG}) {
            SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                    steamworkKey("steep_" + log.name().toLowerCase() + "_to_resin"),
                    RecipeInput.of(ItemStack.of(log, 3)),
                    SteamworkItems.RAW_RESIN.clone(),
                    45.0, 240));
        }
        // 极低含脂（苍白橡树、竹块）：4 原木 → 1 粗树脂
        for (Material log : new Material[]{Material.PALE_OAK_LOG, Material.BAMBOO_BLOCK}) {
            SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                    steamworkKey("steep_" + log.name().toLowerCase() + "_to_resin"),
                    RecipeInput.of(ItemStack.of(log, 4)),
                    SteamworkItems.RAW_RESIN.clone(),
                    45.0, 240));
        }

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_crimson_roots_to_resin"),
                RecipeInput.of(ItemStack.of(Material.CRIMSON_ROOTS, 2)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_warped_roots_to_resin"),
                RecipeInput.of(ItemStack.of(Material.WARPED_ROOTS, 2)),
                SteamworkItems.RAW_RESIN,
                35.0,
                180));

        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_clay_to_terracotta"),
                RecipeInput.of(ItemStack.of(Material.CLAY)),
                ItemStack.of(Material.TERRACOTTA),
                35.0,
                200));

        // 树叶浸煮提取粗树脂（所有树种，8叶 → 1粗树脂）
        for (Material leaves : new Material[]{
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
                Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
                Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
                Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
                Material.PALE_OAK_LEAVES
        }) {
            String key = "steep_" + leaves.name().toLowerCase() + "_to_resin";
            SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                    steamworkKey(key),
                    RecipeInput.of(ItemStack.of(leaves, 8)),
                    SteamworkItems.RAW_RESIN.clone(),
                    30.0,
                    160));
        }

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

        // ===== 化工浸出路径 =====
        // 矿物助熔剂 + 蒸馏水 → 矿物浸出液（水浸法提取矿物离子，给矿物浸出液加一条浸煮路径）
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_mineral_flux_to_leachate"),
                RecipeInput.of(SteamworkItems.MINERAL_FLUX, 3),
                SteamworkItems.MINERAL_LEACHATE_VIAL.clone().asQuantity(2),
                55.0,
                260));

        // 植物精华 + 无菌生质 → 无菌培养基（生物活性浸培，与催化反应堆路径形成竞争）
        SteamSteepingRecipe.RECIPE_TYPE.addRecipe(new SteamSteepingRecipe(
                steamworkKey("steep_plant_essence_to_culture"),
                RecipeInput.of(SteamworkItems.PLANT_ESSENCE, 2),
                SteamworkItems.STERILE_CULTURE.clone().asQuantity(3),
                60.0,
                280));
    }
}
