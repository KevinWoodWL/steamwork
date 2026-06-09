package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.guide.button.FluidButton;
import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
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

public record SteamResearchRecipe(
        @NotNull NamespacedKey key,
        @NotNull RecipeInput.Item sample,
        int researchPoints,
        double steamCost,
        int timeTicks,
        @NotNull ItemStack residue,
        @NotNull String disciplineKey
) implements RebarRecipe {

    public static final RecipeType<SteamResearchRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_research"));

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        return List.of(sample, RecipeInput.of(SteamworkFluids.STEAM, steamCost));
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return List.of(FluidOrItem.of(residue), FluidOrItem.of(SteamworkItems.STEAM_SCIENCE_INTERFACE));
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_research",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamCost)),
                        RebarArgument.of("points", researchPoints),
                        RebarArgument.of("discipline", Component.translatable("steamwork.research_type." + disciplineKey))
                ));

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # i # r # # #",
                        "# # # # m # # # #",
                        "# # # s c # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('i', ItemButton.of(sample))
                .addIngredient('r', ItemButton.of(residue))
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_SCIENCE_INTERFACE))
                .addIngredient('s', FluidButton.of(steamCost, SteamworkFluids.STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .build();
    }
}
