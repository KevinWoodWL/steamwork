package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.pylon.recipes.KilnRecipe;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamCatalyticReactionRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.KilnRecipeWrapper;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class PrecisionCatalyticReactor extends AbstractSteamProcessor<SteamCatalyticReactionRecipe>
        implements PrecisionSteamBoostable {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    /** 静态版联动配方列表，供 {@link Item} 构造器和 {@link #buildPylonRecipes()} 共用。 */
    public static @NotNull List<SteamProcessRecipe> pylonRecipesForItem() {
        return KilnRecipe.RECIPE_TYPE.getRecipes().stream()
                .filter(r -> r.outputItem() != null)
                .map(r -> (SteamProcessRecipe) new KilnRecipeWrapper(r, 50.0, 240))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public PrecisionCatalyticReactor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionCatalyticReactor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamCatalyticReactionRecipe> recipeType() {
        return SteamCatalyticReactionRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "precision_catalytic_reactor";
    }

    @Override
    public int upgradeSlotCount() { return 4; }

    @Override
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return pylonRecipesForItem();
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.precision_catalytic_reactor";
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
        Location loc = getBlock().getLocation().add(0.5, 0.85, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.DUST,
                loc,
                count,
                0.2,
                0.12,
                0.2,
                0.01,
                new Particle.DustOptions(Color.fromRGB(0x54E0B5), 0.8f));
        getBlock().getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, Math.max(1, count / 3), 0.15, 0.1, 0.15, 0.02);
        playProcessingSound(Sound.BLOCK_BEACON_AMBIENT, 0.18f, 1.7f, 0.10);
    }

    public static void refreshRecipeCache() {}
}
