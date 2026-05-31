package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamPressingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class PressingRecipes {

    private PressingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 板材压制 =====
        // 处理木材 → 纤维板（从浸煮桶搬过来，"压制"才是真正的工艺）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_treated_wood_to_fiberboard"),
                RecipeInput.of(SteamworkItems.TREATED_WOOD, 2),
                SteamworkItems.FIBERBOARD.clone().asQuantity(3),
                35.0,
                200));

        // 蒸汽纸浆 → 纤维板（直接路径，比处理木材路径贵但更直接）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_steam_pulp_to_fiberboard"),
                RecipeInput.of(SteamworkItems.STEAM_PULP, 3),
                SteamworkItems.FIBERBOARD.clone(),
                40.0,
                220));

        // ===== 金属板材压制（Pylon 协同，蒸汽路径作为液压机的前置低速版） =====
        // 锡：早期材料，与铜同档。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_tin_ingot_to_sheet"),
                RecipeInput.of(PylonItems.TIN_INGOT),
                PylonItems.TIN_SHEET,
                28.0,
                175));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_copper_ingot_to_sheet"),
                RecipeInput.of(new ItemStack(Material.COPPER_INGOT)),
                PylonItems.COPPER_SHEET,
                30.0,
                180));

        // 金：延展性好，比铁软，压制略省力。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_gold_ingot_to_sheet"),
                RecipeInput.of(new ItemStack(Material.GOLD_INGOT)),
                PylonItems.GOLD_SHEET,
                32.0,
                185));

        // 铁：中游主力材料。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_iron_ingot_to_sheet"),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT)),
                PylonItems.IRON_SHEET,
                35.0,
                195));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_bronze_ingot_to_sheet"),
                RecipeInput.of(PylonItems.BRONZE_INGOT),
                PylonItems.BRONZE_SHEET,
                45.0,
                240));

        // 钢：高强度合金，需要更大压力。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_steel_ingot_to_sheet"),
                RecipeInput.of(PylonItems.STEEL_INGOT),
                PylonItems.STEEL_SHEET,
                55.0,
                280));

        // 钯：顶级材料，蒸汽压制效率较低但可行。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_palladium_ingot_to_sheet"),
                RecipeInput.of(PylonItems.PALLADIUM_INGOT),
                PylonItems.PALLADIUM_SHEET,
                65.0,
                320));

        // ===== 废料回收（冲压路径） =====
        // 跟研磨机的"废屑 → 铁锭"互补：冲压把碎金属屑压回黄铜锭，
        // 因为 Steamwork 主线材料就是黄铜，给玩家一条直接产出主流货币的回收路径。
        // 比例 3:1 比研磨的 4:1 更划算，因为冲压机本身是中游机器（前置要求高一点）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_scrap_to_brass"),
                RecipeInput.of(SteamworkItems.MACHINE_SCRAP, 6),
                SteamworkItems.BRASS_INGOT.clone(),
                50.0,
                260));

        // ===== 热处理金属 =====
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_nichrome_ingot_to_heat_treated_metal"),
                RecipeInput.of(SteamworkItems.NICHROME_INGOT, 1),
                SteamworkItems.HEAT_TREATED_METAL.clone(),
                50.0,
                260));

        // ===== 原矿压制（矿石产出 ×1.2） =====
        // 5 原矿 → 6 金属粉末，再熔炼得 6 锭，对比直接熔炼 5 原矿得 5 锭（+20%）。
        // 输出对接 Pylon 粉末体系，适合作为研磨机的平行早期路线。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_raw_iron_to_dust"),
                RecipeInput.of(new ItemStack(Material.RAW_IRON, 5)),
                PylonItems.IRON_DUST.clone().asQuantity(6),
                35.0,
                200));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_raw_copper_to_dust"),
                RecipeInput.of(new ItemStack(Material.RAW_COPPER, 5)),
                PylonItems.COPPER_DUST.clone().asQuantity(6),
                30.0,
                180));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_raw_gold_to_dust"),
                RecipeInput.of(new ItemStack(Material.RAW_GOLD, 5)),
                PylonItems.GOLD_DUST.clone().asQuantity(6),
                32.0,
                185));

        // ===== 天然材料压制 =====
        // 骨头：3 骨头 → 11 骨粉（原版 3 骨头 = 9 骨粉，+22%）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_bone_to_bonemeal"),
                RecipeInput.of(new ItemStack(Material.BONE, 3)),
                new ItemStack(Material.BONE_MEAL, 11),
                25.0,
                160));

        // 烈焰棒：4 烈焰棒 → 10 烈焰粉（原版 4 烈焰棒 = 8 烈焰粉，+25%）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_blaze_rod_to_powder"),
                RecipeInput.of(new ItemStack(Material.BLAZE_ROD, 4)),
                new ItemStack(Material.BLAZE_POWDER, 10),
                40.0,
                200));

        // ===== 确定性转化 =====
        // 砾石 → 燧石：2 砾石稳定产出 1 燧石，避免靠锹概率开采。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_gravel_to_flint"),
                RecipeInput.of(new ItemStack(Material.GRAVEL, 2)),
                new ItemStack(Material.FLINT),
                20.0,
                140));

        // 黏土块 → 砖：4 黏土球 → 5 砖（原版熔炉 4 黏土球 = 4 砖，+25%）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_clay_to_brick"),
                RecipeInput.of(new ItemStack(Material.CLAY_BALL, 4)),
                new ItemStack(Material.BRICK, 5),
                25.0,
                160));

        // ===== 石粉压制 =====
        // 花岗岩粉、闪长岩粉、安山岩粉各 4 个压制成对应原版石块。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_granite_dust_to_granite"),
                RecipeInput.of(SteamworkItems.GRANITE_DUST, 4),
                new ItemStack(Material.GRANITE),
                30.0,
                180));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_diorite_dust_to_diorite"),
                RecipeInput.of(SteamworkItems.DIORITE_DUST, 4),
                new ItemStack(Material.DIORITE),
                30.0,
                180));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_andesite_dust_to_andesite"),
                RecipeInput.of(SteamworkItems.ANDESITE_DUST, 4),
                new ItemStack(Material.ANDESITE),
                30.0,
                180));

        // ===== 高级分析样本：4 个基础样本压制为 1 个高级样本 =====
        // 蒸汽压机可在 T1 获取；压制本身消耗少量蒸汽，分析时再消耗大头。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_refined_mineral_sample"),
                RecipeInput.of(SteamworkItems.MINERAL_ANALYSIS_SAMPLE, 4),
                SteamworkItems.REFINED_MINERAL_SAMPLE.clone(),
                60.0,
                240));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_concentrated_organic_sample"),
                RecipeInput.of(SteamworkItems.ORGANIC_ANALYSIS_SAMPLE, 4),
                SteamworkItems.CONCENTRATED_ORGANIC_SAMPLE.clone(),
                60.0,
                240));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_refined_metallurgical_sample"),
                RecipeInput.of(SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE, 4),
                SteamworkItems.REFINED_METALLURGICAL_SAMPLE.clone(),
                80.0,
                280));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_purified_fluid_sample"),
                RecipeInput.of(SteamworkItems.FLUID_ANALYSIS_SAMPLE, 4),
                SteamworkItems.PURIFIED_FLUID_SAMPLE.clone(),
                70.0,
                260));
    }
}
