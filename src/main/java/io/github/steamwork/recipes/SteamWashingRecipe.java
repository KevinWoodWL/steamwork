package io.github.steamwork.recipes;

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

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * Recipe for the Steam Washing Trough.
 * Washes ores and sediments for ore multiplication and processing.
 */
public class SteamWashingRecipe implements SteamProcessRecipe {

    public static final RecipeType<SteamWashingRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_washing"));

    private final @NotNull NamespacedKey key;
    private final @NotNull RecipeInput.Item ingredient;
    private final @NotNull ItemStack result;
    private final int outputCount;
    private final double steamCost;
    private final int timeTicks;

    public SteamWashingRecipe(
            @NotNull NamespacedKey key,
            @NotNull RecipeInput.Item ingredient,
            @NotNull ItemStack result,
            int outputCount,
            double steamCost,
            int timeTicks
    ) {
        this.key = key;
        this.ingredient = ingredient;
        this.result = result;
        this.outputCount = outputCount;
        this.steamCost = steamCost;
        this.timeTicks = timeTicks;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public @NotNull RecipeInput.Item ingredient() {
        return ingredient;
    }

    public @NotNull ItemStack result() {
        return result;
    }

    public int outputCount() {
        return outputCount;
    }

    @Override
    public double steamCost() {
        return steamCost;
    }

    @Override
    public int timeTicks() {
        return timeTicks;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return result.clone().asQuantity(outputCount);
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        return List.of(ingredient, RecipeInput.of(SteamworkFluids.STEAM, steamCost));
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return List.of(FluidOrItem.of(result.clone().asQuantity(outputCount)));
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_washing",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamCost))
                ));

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # i # o # # #",
                        "# # # # m # # # #",
                        "# # # s c # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('i', ItemButton.of(ingredient))
                .addIngredient('o', ItemButton.of(result.clone().asQuantity(outputCount)))
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_WASHING_TROUGH))
                .addIngredient('s', new io.github.pylonmc.rebar.guide.button.FluidButton(steamCost, SteamworkFluids.STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .build();
    }
}
