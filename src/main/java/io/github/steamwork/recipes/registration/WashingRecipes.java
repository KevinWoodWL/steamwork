package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamWashingRandomRecipe;
import io.github.steamwork.recipes.SteamWashingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class WashingRecipes {

    private WashingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 锌矿浮选（基础路径） =====
        // 花岗岩/闪长岩含锌的痕量元素，通过湿法浮选提取得到锌精矿。
        // 玩家走廉价但慢的"湿法选矿"路径；Pylon Smeltery 的 紫水晶 → molten_zinc 路径作为高级直接路径并存。
        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_diorite_to_zinc_concentrate"),
                RecipeInput.of(ItemStack.of(Material.DIORITE, 4)),
                SteamworkItems.ZINC_CONCENTRATE,
                1,
                50.0,
                280));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_granite_to_zinc_concentrate"),
                RecipeInput.of(ItemStack.of(Material.GRANITE, 4)),
                SteamworkItems.ZINC_CONCENTRATE,
                1,
                50.0,
                280));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_gravel_to_flint"),
                RecipeInput.of(ItemStack.of(Material.GRAVEL)),
                ItemStack.of(Material.FLINT),
                1,
                25.0,
                140));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_gravel_to_iron_nugget"),
                RecipeInput.of(ItemStack.of(Material.GRAVEL, 8)),
                ItemStack.of(Material.IRON_NUGGET),
                3,
                55.0,
                260));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_suspicious_gravel_to_raw_iron"),
                RecipeInput.of(ItemStack.of(Material.SUSPICIOUS_GRAVEL, 4)),
                ItemStack.of(Material.RAW_IRON),
                1,
                60.0,
                300));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_sand_to_clay"),
                RecipeInput.of(ItemStack.of(Material.SAND, 4)),
                ItemStack.of(Material.CLAY),
                1,
                40.0,
                220));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_sand_to_silica_grit"),
                RecipeInput.of(ItemStack.of(Material.SAND, 2)),
                SteamworkItems.SILICA_GRIT,
                1,
                30.0,
                160));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_red_sand_to_silica_grit"),
                RecipeInput.of(ItemStack.of(Material.RED_SAND, 2)),
                SteamworkItems.SILICA_GRIT,
                1,
                32.0,
                170));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_clay_to_mineral_flux"),
                RecipeInput.of(ItemStack.of(Material.CLAY)),
                SteamworkItems.MINERAL_FLUX,
                2,
                45.0,
                220));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_tuff_to_mineral_flux"),
                RecipeInput.of(ItemStack.of(Material.TUFF, 2)),
                SteamworkItems.MINERAL_FLUX,
                1,
                45.0,
                220));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_calcite_to_silica_grit"),
                RecipeInput.of(ItemStack.of(Material.CALCITE, 2)),
                SteamworkItems.SILICA_GRIT,
                2,
                40.0,
                200));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_soul_sand_to_soul_soil"),
                RecipeInput.of(ItemStack.of(Material.SOUL_SAND)),
                ItemStack.of(Material.SOUL_SOIL),
                1,
                25.0,
                160));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_raw_copper_to_crushed_raw_copper"),
                RecipeInput.of(ItemStack.of(Material.RAW_COPPER)),
                PylonItems.CRUSHED_RAW_COPPER,
                2,
                45.0,
                220));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_raw_iron_to_crushed_raw_iron"),
                RecipeInput.of(ItemStack.of(Material.RAW_IRON)),
                PylonItems.CRUSHED_RAW_IRON,
                2,
                55.0,
                260));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_raw_gold_to_crushed_raw_gold"),
                RecipeInput.of(ItemStack.of(Material.RAW_GOLD)),
                PylonItems.CRUSHED_RAW_GOLD,
                2,
                55.0,
                260));

        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRecipe(
                steamworkKey("wash_raw_tin_to_crushed_raw_tin"),
                RecipeInput.of(PylonItems.RAW_TIN),
                PylonItems.CRUSHED_RAW_TIN,
                2,
                50.0,
                240));

        // ===== 沙砾岩石分选（随机产出） =====
        // 沙砾经高压蒸汽水流冲洗，分离出其中夹带的岩石矿物碎屑，
        // 随机产出花岗岩粉、闪长岩粉或安山岩粉之一（等概率 1/3）。
        SteamWashingRecipe.RECIPE_TYPE.addRecipe(new SteamWashingRandomRecipe(
                steamworkKey("wash_gravel_to_stone_dust"),
                RecipeInput.of(ItemStack.of(Material.GRAVEL, 3)),
                List.of(
                        SteamworkItems.GRANITE_DUST,
                        SteamworkItems.DIORITE_DUST,
                        SteamworkItems.ANDESITE_DUST
                ),
                40.0,
                220));
    }
}
