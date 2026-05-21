package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonFluids;
import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.pylon.recipes.CastingRecipe;
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
        // 铸锭都用 INGOT_MOLD。
        CastingRecipe.RECIPE_TYPE.addRecipe(new CastingRecipe(
                SteamworkKeys.ZINC_INGOT, PylonItems.INGOT_MOLD,
                RecipeInput.of(SteamworkFluids.MOLTEN_ZINC, 144.0),
                SteamworkItems.ZINC_INGOT));
        CastingRecipe.RECIPE_TYPE.addRecipe(new CastingRecipe(
                SteamworkKeys.BRASS_INGOT, PylonItems.INGOT_MOLD,
                RecipeInput.of(SteamworkFluids.MOLTEN_BRASS, 144.0),
                SteamworkItems.BRASS_INGOT));
    }
}
