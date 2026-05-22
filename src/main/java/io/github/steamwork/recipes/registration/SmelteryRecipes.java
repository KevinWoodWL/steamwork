package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonFluids;
import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.pylon.recipes.CastingRecipe;
import io.github.pylonmc.pylon.recipes.KilnRecipe;
import io.github.pylonmc.pylon.recipes.MeltingRecipe;
import io.github.pylonmc.pylon.recipes.SmelteryRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SmelteryRecipes {

    private SmelteryRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // pylon 0.37+ 的 MeltingRecipe 签名删了 int timeTicks 参数（时间由 melting point + 熔炉本身决定）。
        MeltingRecipe.RECIPE_TYPE.addRecipe(new MeltingRecipe(
                steamworkKey("zinc_from_amethyst_shard"),
                RecipeInput.of(new ItemStack(Material.AMETHYST_SHARD)),
                SteamworkFluids.MOLTEN_ZINC, 16.0));
        MeltingRecipe.RECIPE_TYPE.addRecipe(new MeltingRecipe(
                steamworkKey("zinc_from_amethyst_block"),
                RecipeInput.of(new ItemStack(Material.AMETHYST_BLOCK)),
                SteamworkFluids.MOLTEN_ZINC, 144.0));
        MeltingRecipe.RECIPE_TYPE.addRecipe(new MeltingRecipe(
                SteamworkKeys.ZINC_INGOT, RecipeInput.of(SteamworkItems.ZINC_INGOT),
                SteamworkFluids.MOLTEN_ZINC, 144.0));
        MeltingRecipe.RECIPE_TYPE.addRecipe(new MeltingRecipe(
                SteamworkKeys.BRASS_INGOT, RecipeInput.of(SteamworkItems.BRASS_INGOT),
                SteamworkFluids.MOLTEN_BRASS, 144.0));

        // SmelteryRecipe 第 4 参数是 double temperature（°C），不再是 timeTicks。
        // 黄铜液相线约 920 °C，与 MOLTEN_BRASS 的 MeltingPoint 保持一致。
        // 煤炭系燃料最高加热至 1100 °C，高于 920 °C，因此炉温可达标。
        SmelteryRecipe.RECIPE_TYPE.addRecipe(new SmelteryRecipe(
                SteamworkKeys.BRASS_INGOT, Map.of(
                        PylonFluids.COPPER, 0.88, SteamworkFluids.MOLTEN_ZINC, 0.12),
                Map.of(SteamworkFluids.MOLTEN_BRASS, 1.0), 920.0));

        // pylon 0.37+ 的 CastingRecipe 加了 mold 参数（铸模）并删了 timeTicks。
        // 铸锭都用 INGOT_MOLD。锌的 CastingRecipe 移到 CookingRecipes 顶部以保证在配方书里排首位。
        CastingRecipe.RECIPE_TYPE.addRecipe(new CastingRecipe(
                SteamworkKeys.BRASS_INGOT, PylonItems.INGOT_MOLD,
                RecipeInput.of(SteamworkFluids.MOLTEN_BRASS, 144.0),
                SteamworkItems.BRASS_INGOT));

        // KilnRecipe(key, input1, input2, outputItem, outputFluid, outputFluidAmount, timeTicks, temperature)
        // input2/outputFluid 可为 null。窑炉给玩家在造熔炼塔之前一条入门合金路径，与上面熔炼塔路线并存。
        // 紫水晶碎片入窑出锌液 + 渣，温度对齐锌的熔点（约 420 °C）。
        KilnRecipe.RECIPE_TYPE.addRecipe(new KilnRecipe(
                steamworkKey("kiln_zinc_from_amethyst"),
                RecipeInput.of(new ItemStack(Material.AMETHYST_SHARD)),
                null,
                PylonItems.SLAG.clone(),
                SteamworkFluids.MOLTEN_ZINC, 16.0,
                180, 420.0));
        // 2 紫水晶块入窑出 144 mB 锌液 + 2 渣，给一个略高产的成块入炉路线。
        KilnRecipe.RECIPE_TYPE.addRecipe(new KilnRecipe(
                steamworkKey("kiln_zinc_from_amethyst_block"),
                RecipeInput.of(new ItemStack(Material.AMETHYST_BLOCK)),
                null,
                PylonItems.SLAG.clone().asQuantity(2),
                SteamworkFluids.MOLTEN_ZINC, 144.0,
                240, 420.0));
        // 2 紫铜锭 + 1 锌锭入窑直接出黄铜液 432 mB + 2 渣，黄铜液相线 920 °C。
        // 与 Pylon 自带的 bronze 配方写法对齐（2 copper + 1 tin → 432 mB bronze）。
        KilnRecipe.RECIPE_TYPE.addRecipe(new KilnRecipe(
                steamworkKey("kiln_brass"),
                RecipeInput.of(new ItemStack(Material.COPPER_INGOT), 2),
                RecipeInput.of(SteamworkItems.ZINC_INGOT, 1),
                PylonItems.SLAG.clone().asQuantity(2),
                SteamworkFluids.MOLTEN_BRASS, 432.0,
                240, 920.0));
    }
}
