package io.github.steamwork.recipes.pylon;

import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.pylonmc.rebar.recipe.RebarRecipe;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.steamwork.recipes.SteamProcessRecipe;
import xyz.xenondevs.invui.gui.Gui;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class PylonRecipeWrapper<R extends RebarRecipe> implements SteamProcessRecipe {

    protected final R wrapped;
    private final double steamCost;
    private final int timeTicks;

    protected PylonRecipeWrapper(@NotNull R wrapped, double steamCost, int timeTicks) {
        this.wrapped = wrapped;
        this.steamCost = steamCost;
        this.timeTicks = timeTicks;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return wrapped.getKey();
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
    public @NotNull List<RecipeInput> getInputs() {
        return wrapped.getInputs();
    }

    @Override
    public @NotNull List<FluidOrItem> getResults() {
        return wrapped.getResults();
    }

    /** Delegates to the wrapped Pylon recipe's display GUI (may be null). */
    @Override
    public @Nullable Gui display() {
        return wrapped.display();
    }

    public @NotNull R wrapped() {
        return wrapped;
    }
}
