package io.github.steamwork.guide;

import io.github.pylonmc.rebar.guide.pages.MachineRecipesPage;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeType;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.gui.Gui;

import java.util.List;

/**
 * 按指定顺序展示来自不同机器类型的特定配方，用于在指南中显示多步工序链。
 *
 * <p>原理：先以第一步的配方类型调用父类构造（填充 pages），
 * 再清空并按给定 key 列表重新填入目标配方，避免显示无关配方。</p>
 */
@SuppressWarnings("unchecked")
public class SequencedChainPage extends MachineRecipesPage {

    /**
     * 一个工序步骤：来自哪种机器的哪条配方。
     *
     * @param recipeType  配方类型（对应某台机器）
     * @param recipeKey   配方的 NamespacedKey
     */
    public record Step(RecipeType<? extends RebarRecipe> recipeType, NamespacedKey recipeKey) {}

    /**
     * 构造一个只展示给定步骤配方的页面。
     *
     * @param steps 按顺序排列的工序步骤列表，不能为空
     */
    public SequencedChainPage(@NotNull List<Step> steps) {
        // 先以第一步的配方类型初始化父类（会填充 pages，但我们马上清掉）
        super((RecipeType<RebarRecipe>) (RecipeType<?>) steps.get(0).recipeType());
        getPages().clear();

        for (Step step : steps) {
            RebarRecipe recipe = step.recipeType().getRecipe(step.recipeKey());
            if (recipe == null || recipe.isHidden()) continue;
            Gui display = recipe.display();
            if (display != null) getPages().add(display);
        }
    }
}
