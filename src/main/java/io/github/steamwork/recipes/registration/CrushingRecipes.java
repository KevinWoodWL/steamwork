package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamCrushingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 重型冲击破碎机配方。
 * <p>定位：硬质方块 / 矿石的粗碎，与蒸汽研磨机的细粉路线互补。</p>
 */
public final class CrushingRecipes {

    private CrushingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // ===== 硬岩破碎（提供石材建材的粗加工） =====
        // 黑曜石 → 黑曜石碎片（Pylon 建材链需要）
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_obsidian"),
                RecipeInput.of(new ItemStack(Material.OBSIDIAN)),
                PylonItems.OBSIDIAN_CHIP.clone().asQuantity(4),
                80.0,
                300));

        // 岩浆块 → 岩浆膏×2（粉碎提取内部凝固的岩浆膏，比合成路线少量损耗）
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_magma_block"),
                RecipeInput.of(new ItemStack(Material.MAGMA_BLOCK)),
                new ItemStack(Material.MAGMA_CREAM, 2),
                35.0,
                180));

        // ===== 深板岩矿石 → 双倍粗矿（破碎机的招牌特性：高产但慢） =====
        // 深板岩煤矿 → 2 煤
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_deepslate_coal_ore"),
                RecipeInput.of(new ItemStack(Material.DEEPSLATE_COAL_ORE)),
                new ItemStack(Material.COAL, 2),
                50.0,
                240));

        // 深板岩铁矿 → 2 粗铁
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_deepslate_iron_ore"),
                RecipeInput.of(new ItemStack(Material.DEEPSLATE_IRON_ORE)),
                new ItemStack(Material.RAW_IRON, 2),
                60.0,
                280));

        // 深板岩铜矿 → 6 粗铜（铜矿基数高）
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_deepslate_copper_ore"),
                RecipeInput.of(new ItemStack(Material.DEEPSLATE_COPPER_ORE)),
                new ItemStack(Material.RAW_COPPER, 6),
                55.0,
                260));

        // 深板岩金矿 → 2 粗金
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_deepslate_gold_ore"),
                RecipeInput.of(new ItemStack(Material.DEEPSLATE_GOLD_ORE)),
                new ItemStack(Material.RAW_GOLD, 2),
                65.0,
                280));

        // 深板岩青金石矿 → 8 青金石
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_deepslate_lapis_ore"),
                RecipeInput.of(new ItemStack(Material.DEEPSLATE_LAPIS_ORE)),
                new ItemStack(Material.LAPIS_LAZULI, 8),
                55.0,
                260));

        // ===== 废料粗碎回收（与研磨机的精细回收互补） =====
        // 机器废屑粗碎成铁粒（中间产物，无需精炼即得有用产物）
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_scrap_to_nuggets"),
                RecipeInput.of(SteamworkItems.MACHINE_SCRAP, 2),
                new ItemStack(Material.IRON_NUGGET, 3),
                25.0,
                160));

        // ===== 建材粗化 =====
        // 圆石 → 砂砾（建筑物粗加工）
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_cobblestone_to_gravel"),
                RecipeInput.of(new ItemStack(Material.COBBLESTONE)),
                new ItemStack(Material.GRAVEL),
                15.0,
                100));

        // 砂砾 → 沙
        SteamCrushingRecipe.RECIPE_TYPE.addRecipe(new SteamCrushingRecipe(
                steamworkKey("crush_gravel_to_sand"),
                RecipeInput.of(new ItemStack(Material.GRAVEL)),
                new ItemStack(Material.SAND),
                15.0,
                100));
    }
}
