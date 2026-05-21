package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.steamwork.SteamworkItems;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;

import java.util.ArrayList;
import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽装配台配方：把若干部件（底座 + 合金件 + 黄铜组件）组装成一件成品装备。
 * 不耗蒸汽，不耗时间（玩家按按钮触发，瞬时完成）。
 * 输入顺序不计较，由装配台按"逐项匹配 + 占位"算法解析。
 */
public record SteamAssemblyRecipe(
        @NotNull NamespacedKey key,
        @NotNull List<RecipeInput.Item> ingredients,
        @NotNull ItemStack result
) implements RebarRecipe {

    public static final RecipeType<SteamAssemblyRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_assembly"));

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    public @NotNull ItemStack producedStack() {
        return result.clone();
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        return new ArrayList<>(ingredients);
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return List.of(FluidOrItem.of(result));
    }

    @Override
    public @NotNull Gui display() {
        // 横排 5 个输入 + 中间装配台 + 右侧输出。
        Gui.Builder<?, ?> builder = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# 1 2 3 4 5 # m o",
                        "# # # # # # # # #",
                        "# # # # # # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_ASSEMBLY_BENCH))
                .addIngredient('o', ItemButton.of(result));

        for (int i = 0; i < 5; i++) {
            char slot = (char) ('1' + i);
            if (i < ingredients.size()) {
                builder.addIngredient(slot, ItemButton.of(ingredients.get(i)));
            } else {
                builder.addIngredient(slot, GuiItems.backgroundBlack());
            }
        }

        return builder.build();
    }
}
