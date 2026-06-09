package io.github.steamwork.recipes;
import io.github.pylonmc.rebar.guide.button.FluidButton;

import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RecipeInput;
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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机产出的洗选槽配方。
 * 每次配方开始时（{@link #onRecipeStart()}）从 {@code possibleResults} 中等概率随机选取一个产物，
 * 并在整个配方周期内保持幂等（{@link #producedStack()} 始终返回同一个物品）。
 *
 * <p>指南 GUI 会循环展示所有可能产物，让玩家了解随机范围。</p>
 */
public class SteamWashingRandomRecipe extends SteamWashingRecipe {

    /** 所有可能的产出物品（等概率）。 */
    private final @NotNull List<ItemStack> possibleResults;

    /** 本次配方周期锁定的产出，由 {@link #onRecipeStart()} 设置。 */
    private ItemStack lockedResult = null;

    public SteamWashingRandomRecipe(
            @NotNull NamespacedKey key,
            @NotNull RecipeInput.Item ingredient,
            @NotNull List<ItemStack> possibleResults,
            double steamCost,
            int timeTicks
    ) {
        // 传给父类的 result/outputCount 仅用于父类内部兼容，实际产出由本类覆盖
        super(key, ingredient, possibleResults.getFirst(), 1, steamCost, timeTicks);
        if (possibleResults.isEmpty()) {
            throw new IllegalArgumentException("possibleResults must not be empty");
        }
        this.possibleResults = List.copyOf(possibleResults);
    }

    /** 配方开始时随机锁定本次产出。 */
    @Override
    public void onRecipeStart() {
        int idx = ThreadLocalRandom.current().nextInt(possibleResults.size());
        lockedResult = possibleResults.get(idx).clone();
    }

    /** 返回本次已锁定的产出；若尚未锁定（如指南预览时）则返回第一个候选。 */
    @Override
    public @NotNull ItemStack producedStack() {
        return lockedResult != null ? lockedResult.clone() : possibleResults.getFirst().clone();
    }

    /** 所有可能产出都作为 results 暴露，使指南的"点击产物跳转配方"对每种产物都生效。 */
    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return possibleResults.stream()
                .map(FluidOrItem::of)
                .toList();
    }

    /**
     * 指南 GUI：循环展示所有可能产物，让玩家清楚随机范围。
     * 布局与普通洗选配方一致，产出槽改为循环切换所有候选物品。
     */
    @Override
    public @NotNull Gui display() {
        ItemStackBuilder clock = ItemStackBuilder.of(Material.CLOCK)
                .name(Component.translatable(
                        "steamwork.guide.recipe.steam_washing",
                        RebarArgument.of("time", UnitFormat.SECONDS.format(timeTicks() / 20.0)),
                        RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamCost()))
                ));

        // 把所有候选产物做成循环切换按钮（每个物品展示 40 tick）
        List<ItemStackBuilder> cyclingStates = possibleResults.stream()
                .map(ItemStackBuilder::of)
                .toList();
        Item cyclingOutput = Item.builder()
                .setCyclingItemProvider(40, cyclingStates)
                .build();

        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # i # o # # #",
                        "# # # # m # # # #",
                        "# # # s c # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.backgroundBlack())
                .addIngredient('i', ItemButton.of(ingredient()))
                .addIngredient('o', cyclingOutput)
                .addIngredient('m', ItemButton.of(SteamworkItems.STEAM_WASHING_TROUGH))
                .addIngredient('s', FluidButton.of(steamCost(), SteamworkFluids.STEAM))
                .addIngredient('c', GuiItems.progressCyclingItem(timeTicks(), clock))
                .build();
    }
}
