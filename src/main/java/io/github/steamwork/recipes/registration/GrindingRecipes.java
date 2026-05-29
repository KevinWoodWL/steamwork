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
        // 下界岩研磨：16 下界岩 → 1 烈焰粉（提炼下界岩中的火焰物质，早期无需进入下界深处也能积累烈焰粉）。
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_netherrack_to_blaze_powder"),
                RecipeInput.of(new ItemStack(Material.NETHERRACK, 16)),
                new ItemStack(Material.BLAZE_POWDER, 1),
                35.0,
                200));


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

        // ===== 高合金锭研磨（研磨机专属定位：高合金锭 → 粉末，冲压机不覆盖此路径） =====
        // 用于将已铸造的高合金锭磨回粉末，支持配方调整和回收再利用。
        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_invar_to_dust"),
                RecipeInput.of(SteamworkItems.INVAR_INGOT),
                SteamworkItems.INVAR_DUST,
                55.0,
                280));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_duralumin_to_dust"),
                RecipeInput.of(SteamworkItems.DURALUMIN_INGOT),
                SteamworkItems.DURALUMIN_DUST,
                50.0,
                260));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_manganese_steel_to_dust"),
                RecipeInput.of(SteamworkItems.MANGANESE_STEEL_INGOT),
                SteamworkItems.MANGANESE_STEEL_DUST,
                60.0,
                300));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_manganese_bronze_to_dust"),
                RecipeInput.of(SteamworkItems.MANGANESE_BRONZE_INGOT),
                SteamworkItems.MANGANESE_BRONZE_DUST,
                55.0,
                280));

        SteamGrindingRecipe.RECIPE_TYPE.addRecipe(new SteamGrindingRecipe(
                steamworkKey("grind_tungsten_to_dust"),
                RecipeInput.of(SteamworkItems.TUNGSTEN_INGOT),
                SteamworkItems.TUNGSTEN_DUST,
                80.0,
                380));
    }
}
