package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.pylon.recipes.FormingRecipe;
import io.github.pylonmc.pylon.recipes.PipeBendingRecipe;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamFoundryRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.FormingRecipeWrapper;
import io.github.steamwork.recipes.pylon.PipeBendingRecipeWrapper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PrecisionFoundry extends AbstractSteamProcessor<SteamFoundryRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    /** 静态版联动配方列表，供 {@link Item} 构造器和 {@link #buildPylonRecipes()} 共用。 */
    public static @NotNull List<SteamProcessRecipe> pylonRecipesForItem() {
        List<SteamProcessRecipe> list = new ArrayList<>();
        FormingRecipe.RECIPE_TYPE.getRecipes().stream()
                .map(r -> (SteamProcessRecipe) new FormingRecipeWrapper(r, 50.0, 240))
                .forEach(list::add);
        PipeBendingRecipe.RECIPE_TYPE.getRecipes().stream()
                .map(r -> (SteamProcessRecipe) new PipeBendingRecipeWrapper(r, 50.0, 240))
                .forEach(list::add);
        return list;
    }

    @SuppressWarnings("unused")
    public PrecisionFoundry(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionFoundry(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamFoundryRecipe> recipeType() {
        return SteamFoundryRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "precision_foundry";
    }

    @Override
    public int upgradeSlotCount() { return 4; }

    @Override
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return pylonRecipesForItem();
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.precision_foundry";
    }

    @Override
    protected @NotNull RebarFluid steamFluid() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    @Override
    protected @NotNull Material[] steamGaugeMaterials() {
        return new Material[]{
                Material.ORANGE_STAINED_GLASS,
                Material.YELLOW_STAINED_GLASS,
                Material.RED_STAINED_GLASS,
                Material.GRAY_STAINED_GLASS
        };
    }

    @Override
    protected @NotNull String steamBarColor() {
        return "#ff8c00";
    }

    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.75, 0.5);
        getBlock().getWorld().spawnParticle(Particle.FLAME, loc, count / 2, 0.18, 0.12, 0.18, 0.02);
        getBlock().getWorld().spawnParticle(Particle.LAVA, loc, Math.max(1, count / 5), 0.12, 0.08, 0.12, 0.0);
        playProcessingSound(Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.35f, 1.1f, 0.16);
    }

    public static void refreshRecipeCache() {}
}
