package io.github.steamwork.guide;

import io.github.pylonmc.rebar.guide.pages.MachineRecipesPage;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamGrindingRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;

import java.util.List;

/**
 * 指南页面：显示某台蒸汽机器安装 Pylon 联动模组后可执行的配方。
 *
 * <p>遵循 {@link SequencedChainPage} 相同模式：先以占位 RecipeType 调用父类构造
 * 以满足类型参数约束，随即清空并手动填入各 Pylon 包装器委托的 display() GUI。</p>
 *
 * <p>{@link PylonRecipeWrapper#display()} 会委托给被包装的 Pylon 原生配方，
 * 因此在指南中显示的是 Pylon 自己的配方卡片样式。</p>
 */
@SuppressWarnings("unchecked")
public class PylonCompatRecipesPage extends MachineRecipesPage {

    /**
     * @param pylonRecipes Pylon 联动配方包装器列表（由机器的 {@code pylonRecipesForItem()} 提供）
     */
    public PylonCompatRecipesPage(@NotNull List<SteamProcessRecipe> pylonRecipes) {
        // 以蒸汽研磨机配方类型为占位参数满足父类构造约束；构造后立即清空不需要的初始页面。
        super((RecipeType<RebarRecipe>) (RecipeType<?>) SteamGrindingRecipe.RECIPE_TYPE);
        getPages().clear();

        for (SteamProcessRecipe recipe : pylonRecipes) {
            Gui display = recipe.display();
            if (display != null) getPages().add(display);
        }
    }
}
