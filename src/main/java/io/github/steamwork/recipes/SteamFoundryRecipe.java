package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.guide.button.FluidButton;
import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;

import java.util.ArrayList;
import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public record SteamFoundryRecipe(
        @NotNull NamespacedKey key,
        @NotNull List<RecipeInput.Item> ingredients,
        @NotNull ItemStack result,
        double steamCost,
        int timeTicks
) implements SteamProcessRecipe {

    public static final RecipeType<SteamFoundryRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_foundry"));

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return ingredients.getFirst();
    }

    @Override
    public @NotNull List<RecipeInput.Item> ingredients() {
        return ingredients;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return result.clone();
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        List<RecipeInput> inputs = new ArrayList<>(ingredients);
        inputs.add(RecipeInput.of(SteamworkFluids.SUPERHEATED_STEAM, steamCost));
        return inputs;
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return List.of(FluidOrItem.of(result));
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_foundry",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamCost))
                ));

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# 1 2 3 # m # o #",
                        "# # f # # s # # #",
                        "# # # # # c # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('1', ingredientButton(0))
                .addIngredient('2', ingredientButton(1))
                .addIngredient('3', ingredientButton(2))
                .addIngredient('f', ingredientButton(3))
                .addIngredient('m', ItemButton.of(SteamworkItems.PRECISION_FOUNDRY))
                .addIngredient('s', FluidButton.of(steamCost, SteamworkFluids.SUPERHEATED_STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .addIngredient('o', ItemButton.of(result))
                .build();
    }

    private Item ingredientButton(int index) {
        if (index >= ingredients.size()) {
            return GuiItems.backgroundBlack();
        }
        return ItemButton.of(ingredients.get(index));
    }
}
