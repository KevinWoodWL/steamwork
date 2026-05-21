package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.fluid.RebarFluid;
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
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;

import java.util.ArrayList;
import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public record SteamDistillationRecipe(
        @NotNull NamespacedKey key,
        @NotNull RecipeInput.Item ingredient,
        @Nullable RecipeInput.Fluid inputFluid,
        @NotNull List<ItemStack> itemResults,
        @NotNull List<FluidOutput> fluidResults,
        int requiredSections,
        double superheatedSteamCost,
        int timeTicks
) implements RebarRecipe {

    public static final RecipeType<SteamDistillationRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_distillation"));

    public record FluidOutput(@NotNull RebarFluid fluid, double amount) {
        public FluidOutput {
            if (amount <= 0) {
                throw new IllegalArgumentException("Fluid output amount must be positive");
            }
        }
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        List<RecipeInput> inputs = new ArrayList<>();
        inputs.add(ingredient);
        if (inputFluid != null) {
            inputs.add(inputFluid);
        }
        inputs.add(RecipeInput.of(SteamworkFluids.SUPERHEATED_STEAM, superheatedSteamCost));
        return inputs;
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        List<FluidOrItem> results = new ArrayList<>();
        for (ItemStack item : itemResults) {
            results.add(FluidOrItem.of(item));
        }
        for (FluidOutput fluid : fluidResults) {
            results.add(FluidOrItem.of(fluid.fluid(), fluid.amount()));
        }
        return results;
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_distillation",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(superheatedSteamCost)),
                        RebarArgument.of("sections", requiredSections)
                ));

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i f # m # 1 2 3",
                        "# # # # s # 4 5 6",
                        "# # # # c # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('i', ItemButton.of(ingredient))
                .addIngredient('f', inputFluid == null ? GuiItems.backgroundBlack() : new FluidButton(inputFluid))
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_DISTILLATION_TOWER))
                .addIngredient('s', new FluidButton(superheatedSteamCost, SteamworkFluids.SUPERHEATED_STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .addIngredient('1', resultButton(0))
                .addIngredient('2', resultButton(1))
                .addIngredient('3', resultButton(2))
                .addIngredient('4', resultButton(3))
                .addIngredient('5', resultButton(4))
                .addIngredient('6', resultButton(5))
                .build();
    }

    private Item resultButton(int index) {
        List<FluidOrItem> results = getResults();
        if (index >= results.size()) {
            return GuiItems.backgroundBlack();
        }
        FluidOrItem result = results.get(index);
        if (result instanceof FluidOrItem.Item item) {
            return ItemButton.of(item.item());
        }
        FluidOrItem.Fluid fluid = (FluidOrItem.Fluid) result;
        return new FluidButton(fluid.amountMillibuckets(), fluid.fluid());
    }
}
