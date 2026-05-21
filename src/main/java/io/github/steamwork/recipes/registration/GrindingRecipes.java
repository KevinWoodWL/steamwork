package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamGrindingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class GrindingRecipes {

    private GrindingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 岩石粉碎类（填回阶段 1 删除的"闪长岩/花岗岩 → 石英"，原本就属于粉碎工艺） =====
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_diorite_to_quartz"),
                RecipeInput.of(new ItemStack(Material.DIORITE)),
                new ItemStack(Material.QUARTZ),
                30.0,
                180));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_granite_to_quartz"),
                RecipeInput.of(new ItemStack(Material.GRANITE)),
                new ItemStack(Material.QUARTZ),
                30.0,
                180));

        // ===== 金属锭粉碎（喂给加压熔炉的合金粉末配方，作为 Pylon dust 体系的早期来源） =====
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_iron_to_dust"),
                RecipeInput.of(new ItemStack(Material.IRON_INGOT)),
                PylonItems.IRON_DUST,
                35.0,
                200));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_copper_to_dust"),
                RecipeInput.of(new ItemStack(Material.COPPER_INGOT)),
                PylonItems.COPPER_DUST,
                30.0,
                180));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_gold_to_dust"),
                RecipeInput.of(new ItemStack(Material.GOLD_INGOT)),
                PylonItems.GOLD_DUST,
                35.0,
                200));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_steel_to_dust"),
                RecipeInput.of(PylonItems.STEEL_INGOT),
                PylonItems.STEEL_DUST,
                45.0,
                240));

        // ===== 燃料类 =====
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_coal_to_dust"),
                RecipeInput.of(new ItemStack(Material.COAL)),
                PylonItems.COAL_DUST,
                20.0,
                140));

        // ===== 废料回收 =====
        // 机器废屑碾碎重熔成铁锭。4 进 1 出，循环回收主路径。
        // 旧版是 8:1，太苛刻：以 10% 废屑产出率推算，玩家要跑 80 条配方才能换回 1 锭铁。
        // 改成 4:1 后约 40 条配方换 1 锭，更接近"工业回收"的合理预期。
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_scrap_to_iron"),
                RecipeInput.of(SteamworkItems.MACHINE_SCRAP, 4),
                new ItemStack(Material.IRON_INGOT),
                40.0,
                240));
    }
}
