package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamPressurizingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class PressurizingRecipes {

    private PressurizingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_nichrome"),
                List.of(
                        RecipeInput.of(PylonItems.STEEL_DUST),
                        RecipeInput.of(ItemStack.of(Material.BLAZE_POWDER), 2),
                        RecipeInput.of(SteamworkItems.ZINC_INGOT),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.NICHROME_DUST.clone().asQuantity(2),
                180.0,
                300));

        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_invar"),
                List.of(
                        RecipeInput.of(PylonItems.STEEL_INGOT),
                        RecipeInput.of(ItemStack.of(Material.IRON_INGOT), 2),
                        RecipeInput.of(SteamworkItems.NICHROME_INGOT),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.INVAR_DUST.clone().asQuantity(2),
                220.0,
                320));

        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_duralumin"),
                List.of(
                        RecipeInput.of(ItemStack.of(Material.COPPER_INGOT), 2),
                        RecipeInput.of(SteamworkItems.ZINC_INGOT),
                        RecipeInput.of(ItemStack.of(Material.AMETHYST_SHARD), 2),
                        RecipeInput.of(SteamworkItems.SILICA_GRIT)
                ),
                SteamworkItems.DURALUMIN_DUST.clone().asQuantity(2),
                150.0,
                260));

        // 硬铝粉精细路径：用无菌培养基替代硅砂作为细颗粒载体，产量 +1，耗汽 / 时间略增。
        // 给孤儿材料 STERILE_CULTURE 找用途。
        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_duralumin_premium"),
                List.of(
                        RecipeInput.of(ItemStack.of(Material.COPPER_INGOT), 2),
                        RecipeInput.of(SteamworkItems.ZINC_INGOT),
                        RecipeInput.of(ItemStack.of(Material.AMETHYST_SHARD), 2),
                        RecipeInput.of(SteamworkItems.STERILE_CULTURE)
                ),
                SteamworkItems.DURALUMIN_DUST.clone().asQuantity(3),
                160.0,
                280));

        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_tungsten"),
                List.of(
                        RecipeInput.of(ItemStack.of(Material.NETHERITE_SCRAP)),
                        RecipeInput.of(PylonItems.CARBON),
                        RecipeInput.of(ItemStack.of(Material.BLAZE_ROD)),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX, 2)
                ),
                SteamworkItems.TUNGSTEN_DUST.clone(),
                450.0,
                520));

        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_manganese_steel"),
                List.of(
                        RecipeInput.of(PylonItems.STEEL_INGOT, 2),
                        RecipeInput.of(PylonItems.CARBON),
                        RecipeInput.of(ItemStack.of(Material.LAPIS_LAZULI), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.MANGANESE_STEEL_DUST.clone().asQuantity(2),
                260.0,
                360));

        SteamPressurizingRecipe.RECIPE_TYPE.addRecipe(new SteamPressurizingRecipe(
                steamworkKey("pressurizing_manganese_bronze"),
                List.of(
                        RecipeInput.of(PylonItems.BRONZE_INGOT, 2),
                        RecipeInput.of(SteamworkItems.BRASS_INGOT),
                        RecipeInput.of(ItemStack.of(Material.LAPIS_LAZULI), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.MANGANESE_BRONZE_DUST.clone().asQuantity(3),
                160.0,
                260));
    }
}
