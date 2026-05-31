package io.github.steamwork.recipes;

import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.rebar.util.WeightedSet;
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
 * 精密离心机配方。一次输入按密度加权随机分离出多种产物。
 * 硬铝合金的轻质高强度特性使主轴能承受高速旋转，是离心分离的关键。
 * <p>与研磨机的差异：研磨机是单一确定输出，离心机是多产物加权随机输出，
 * 模拟"按密度分层 + 部分回收物质"的真实工业流程。</p>
 */
public record SteamCentrifugationRecipe(
        @NotNull NamespacedKey key,
        @NotNull RecipeInput.Item ingredient,
        @NotNull WeightedSet<ItemStack> results,
        double steamCost,
        int timeTicks
) implements SteamProcessRecipe {

    public static final RecipeType<SteamCentrifugationRecipe> RECIPE_TYPE =
            new RecipeType<>(steamworkKey("steam_centrifugation"));

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    /** 每次产出按权重随机抽取一种产物。 */
    @Override
    public @NotNull ItemStack producedStack() {
        return results.getRandom().clone();
    }

    @Override
    public @NotNull List<RecipeInput> getInputs() {
        return List.of(ingredient, RecipeInput.of(SteamworkFluids.SUPERHEATED_STEAM, steamCost));
    }

    /**
     * 只返回权重最高的那个产物作为"代表产物"，让 Rebar 只把这条配方挂到主产物的指南页上。
     * 若返回全部可能产物，每个可能输出物品的页面都会出现同一条配方（不符合预期）。
     */
    @Override
    public @NotNull List<FluidOrItem> getResults() {
        ItemStack primary = results.stream()
                .max(java.util.Comparator.comparingDouble(WeightedSet.Element::weight))
                .map(WeightedSet.Element::element)
                .orElseGet(() -> results.getElements().iterator().next());
        return List.of(FluidOrItem.of(primary));
    }

    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_centrifugation",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamCost))
                ));

        // 产物用集合中第一项代表，保证指南每次显示一致（避免 getRandom() 每次不同）
        java.util.Set<ItemStack> elements = results.getElements();
        ItemStack representativeOutput = elements.isEmpty()
                ? new ItemStack(Material.BARRIER)
                : elements.iterator().next();

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
                .addIngredient('o', ItemButton.of(representativeOutput))
                .addIngredient('m', ItemButton.of(SteamworkItems.PRECISION_CENTRIFUGE))
                .addIngredient('s', new io.github.pylonmc.rebar.guide.button.FluidButton(steamCost, SteamworkFluids.SUPERHEATED_STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks, clock))
                .build();
    }
}
