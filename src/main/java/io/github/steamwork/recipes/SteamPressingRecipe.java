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
 * 蒸汽冲压机配方。把柔软或散粒材料压制成刚性板/件。
 * 例：处理木材 → 纤维板、铜锭 → 铜片（Pylon 协同）。
 */
public record SteamPressingRecipe(
        @NotNull NamespacedKey key,
        @NotNull RecipeInput.Item ingredient,
        @NotNull ItemStack result,
        double steamCost,
        int timeTicks
) implements SteamProcessRecipe {

    public static final RecipeType<SteamPressingRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_pressing"));

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public @NotNull ItemStack producedStack() {
        return result.clone();
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        return List.of(ingredient, RecipeInput.of(SteamworkFluids.STEAM, steamCost));
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return List.of(FluidOrItem.of(result));
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_pressing",
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
                .addIngredient('o', ItemButton.of(result))
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_PRESS))
                .addIngredient('s', new io.github.pylonmc.rebar.guide.button.FluidButton(steamCost, SteamworkFluids.STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .build();
    }
}
