package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamCrystallizingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 精密结晶炉配方。
 * <p>定位：低品矿物 → 高品锭 / 高纯产物。慢、稳、不复制。</p>
 */
public final class CrystallizingRecipes {

    private CrystallizingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 矿物精炼链：粗矿 → 直接锭（跳过冶炼，提供"高品质"路线） =====
        // 6 粗铁 → 1 钢锭（绕过 Pylon 高炉路线，代价是更多原矿 + 更长结晶时间）
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_steel_from_raw_iron"),
                RecipeInput.of(ItemStack.of(Material.RAW_IRON, 6)),
                PylonItems.STEEL_INGOT.clone(),
                120.0,
                480));

        // 6 粗铜 → 1 青铜锭（同理：耗材换便利）
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_bronze_from_raw_copper"),
                RecipeInput.of(ItemStack.of(Material.RAW_COPPER, 6)),
                PylonItems.BRONZE_INGOT.clone(),
                80.0,
                360));

        // ===== 粉末提纯链 =====
        // 4 锌精矿 → 2 锌锭（提纯比 2:1，快速精炼）
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_zinc_ingot"),
                RecipeInput.of(SteamworkItems.ZINC_CONCENTRATE, 4),
                SteamworkItems.ZINC_INGOT.clone().asQuantity(2),
                60.0,
                280));

        // 矿物分析样品 → 2 因瓦合金粉末（科研路线高纯输出）
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_invar_dust_from_sample"),
                RecipeInput.of(SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE, 1),
                SteamworkItems.INVAR_DUST.clone().asQuantity(2),
                70.0,
                320));

        // ===== 宝石提纯（Pylon 宝石粉 → 原版宝石） =====
        // 4 钻石粉 → 1 钻石（Pylon 钻石粉本身没有出口）
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_diamond_from_dust"),
                RecipeInput.of(PylonItems.DIAMOND_DUST, 4),
                ItemStack.of(Material.DIAMOND),
                90.0,
                360));

        // 4 绿宝石粉 → 1 绿宝石
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_emerald_from_dust"),
                RecipeInput.of(PylonItems.EMERALD_DUST, 4),
                ItemStack.of(Material.EMERALD),
                85.0,
                340));

        // 4 石英粉 → 1 下界石英
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_quartz_from_dust"),
                RecipeInput.of(PylonItems.QUARTZ_DUST, 4),
                ItemStack.of(Material.QUARTZ),
                70.0,
                300));

        // ===== 粉末再结晶为锭（Pylon 单粉末通常需要外部冶炼） =====
        // 2 铜粉 → 1 铜锭
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_copper_from_dust"),
                RecipeInput.of(PylonItems.COPPER_DUST, 2),
                ItemStack.of(Material.COPPER_INGOT),
                40.0,
                200));

        // 2 铁粉 → 1 铁锭
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_iron_from_dust"),
                RecipeInput.of(PylonItems.IRON_DUST, 2),
                ItemStack.of(Material.IRON_INGOT),
                40.0,
                200));

        // 2 金粉 → 1 金锭
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_gold_from_dust"),
                RecipeInput.of(PylonItems.GOLD_DUST, 2),
                ItemStack.of(Material.GOLD_INGOT),
                40.0,
                200));

        // 4 锡粉 → 2 锡锭
        SteamCrystallizingRecipe.RECIPE_TYPE.addRecipe(new SteamCrystallizingRecipe(
                steamworkKey("crystallize_tin_from_dust"),
                RecipeInput.of(PylonItems.TIN_DUST, 4),
                PylonItems.TIN_INGOT.clone().asQuantity(2),
                50.0,
                240));
    }
}
