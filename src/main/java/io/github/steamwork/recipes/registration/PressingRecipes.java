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
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_copper_ingot_to_sheet"),
                RecipeInput.of(new ItemStack(Material.COPPER_INGOT)),
                PylonItems.COPPER_SHEET,
                30.0,
                180));

        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_bronze_ingot_to_sheet"),
                RecipeInput.of(PylonItems.BRONZE_INGOT),
                PylonItems.BRONZE_SHEET,
                45.0,
                240));

        // ===== 废料回收（冲压路径） =====
        // 跟研磨机的"废屑 → 铁锭"互补：冲压把碎金属屑压回黄铜锭，
        // 因为 Steamwork 主线材料就是黄铜，给玩家一条直接产出主流货币的回收路径。
        // 比例 3:1 比研磨的 4:1 更划算，因为冲压机本身是中游机器（前置要求高一点）。
        SteamPressingRecipe.RECIPE_TYPE.addRecipe(new SteamPressingRecipe(
                steamworkKey("press_scrap_to_brass"),
                RecipeInput.of(SteamworkItems.MACHINE_SCRAP, 3),
                SteamworkItems.BRASS_INGOT.clone(),
                50.0,
                260));
    }
}
