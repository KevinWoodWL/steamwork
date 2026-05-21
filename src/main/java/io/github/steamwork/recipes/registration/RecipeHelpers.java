package io.github.steamwork.recipes.registration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class RecipeHelpers {

    private RecipeHelpers() {
        throw new AssertionError("Utility class");
    }

    public static RecipeChoice.ExactChoice rebarChoice(ItemStack rebarItem) {
        return new RecipeChoice.ExactChoice(rebarItem);
    }
}
