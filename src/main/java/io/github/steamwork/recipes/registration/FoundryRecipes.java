package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamFoundryRecipe;
import io.github.steamwork.util.SequencedWorkpiece;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class FoundryRecipes {

    private FoundryRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // 钯合金工序链第 2 步
        SteamFoundryRecipe matrix = new SteamFoundryRecipe(
                steamworkKey("foundry_palladium_alloy_matrix"),
                List.of(
                        RecipeInput.of(SequencedWorkpiece.palladiumAlloy(1)),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SequencedWorkpiece.palladiumAlloy(2),
                220.0,
                220);
        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(matrix);
        RebarRecipe.setPriority(matrix, 20.0);

        // 钯合金工序链第 4 步（产出钯合金锭）
        SteamFoundryRecipe finalIngot = new SteamFoundryRecipe(
                steamworkKey("foundry_palladium_alloy_final"),
                List.of(
                        RecipeInput.of(SequencedWorkpiece.palladiumAlloy(3)),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.PALLADIUM_ALLOY_INGOT.clone(),
                260.0,
                240);
        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(finalIngot);
        // 不影响序列工件的展示（输出是钯合金锭而非工件），但让钯合金锭页签优先
        RebarRecipe.setPriority(finalIngot, 30.0);

        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(new SteamFoundryRecipe(
                steamworkKey("foundry_invar"),
                List.of(
                        RecipeInput.of(new ItemStack(Material.IRON_INGOT), 2),
                        RecipeInput.of(SteamworkItems.NICHROME_DUST),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.INVAR_INGOT.clone().asQuantity(2),
                180.0,
                160));

        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(new SteamFoundryRecipe(
                steamworkKey("foundry_duralumin"),
                List.of(
                        RecipeInput.of(new ItemStack(Material.COPPER_INGOT), 2),
                        RecipeInput.of(SteamworkItems.ZINC_INGOT),
                        RecipeInput.of(new ItemStack(Material.AMETHYST_SHARD), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.DURALUMIN_INGOT.clone().asQuantity(2),
                140.0,
                140));

        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(new SteamFoundryRecipe(
                steamworkKey("foundry_manganese_steel"),
                List.of(
                        RecipeInput.of(PylonItems.STEEL_INGOT, 2),
                        RecipeInput.of(PylonItems.CARBON),
                        RecipeInput.of(new ItemStack(Material.LAPIS_LAZULI), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.MANGANESE_STEEL_INGOT.clone().asQuantity(2),
                220.0,
                180));

        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(new SteamFoundryRecipe(
                steamworkKey("foundry_manganese_bronze"),
                List.of(
                        RecipeInput.of(PylonItems.BRONZE_INGOT, 2),
                        RecipeInput.of(SteamworkItems.BRASS_INGOT),
                        RecipeInput.of(new ItemStack(Material.LAPIS_LAZULI), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX)
                ),
                SteamworkItems.MANGANESE_BRONZE_INGOT.clone().asQuantity(3),
                150.0,
                130));

        SteamFoundryRecipe.RECIPE_TYPE.addRecipe(new SteamFoundryRecipe(
                steamworkKey("foundry_tungsten"),
                List.of(
                        RecipeInput.of(new ItemStack(Material.NETHERITE_SCRAP)),
                        RecipeInput.of(PylonItems.CARBON),
                        RecipeInput.of(new ItemStack(Material.BLAZE_POWDER), 2),
                        RecipeInput.of(SteamworkItems.MINERAL_FLUX, 2)
                ),
                SteamworkItems.TUNGSTEN_INGOT.clone(),
                380.0,
                260));
    }
}
