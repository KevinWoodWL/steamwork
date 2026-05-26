package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkFluids;
import io.github.pylonmc.pylon.recipes.GrindstoneRecipe;
import io.github.steamwork.recipes.SteamCrushingRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.GrindstoneRecipeWrapper;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 重型冲击破碎机 —— 锰钢级粗碎机器。
 * 与蒸汽研磨机互补：研磨机做细粉（锭/软矿→粉），破碎机做粗碎（硬矿/方块→碎块或粉混合料）。
 * 定位是"承接 Pylon 锤击 / 手动钻取的自动化替代"。
 */
public class HeavyImpactCrusher extends AbstractSteamProcessor<SteamCrushingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    /** 静态版联动配方列表（破碎机也吃 Pylon Grindstone 配方，但能耗 / 时长更高，体现"粗暴大力"定位）。 */
    public static @NotNull List<SteamProcessRecipe> pylonRecipesForItem() {
        return GrindstoneRecipe.RECIPE_TYPE.getRecipes().stream()
                .map(r -> (SteamProcessRecipe) new GrindstoneRecipeWrapper(r, 50.0, 200))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unused")
    public HeavyImpactCrusher(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public HeavyImpactCrusher(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamCrushingRecipe> recipeType() {
        return SteamCrushingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "crushing";
    }

    @Override
    public int upgradeSlotCount() { return 2; }

    @Override
    protected @NotNull RebarFluid steamFluid() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    @Override
    protected @NotNull org.bukkit.Material[] steamGaugeMaterials() {
        return new org.bukkit.Material[]{
                org.bukkit.Material.ORANGE_STAINED_GLASS,
                org.bukkit.Material.YELLOW_STAINED_GLASS,
                org.bukkit.Material.RED_STAINED_GLASS,
                org.bukkit.Material.GRAY_STAINED_GLASS
        };
    }

    @Override
    protected @NotNull String steamBarColor() { return "#ff8c00"; }

    @Override
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return pylonRecipesForItem();
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.heavy_impact_crusher";
    }

    /** 重型冲击：大量灰尘 + 块状碎裂粒子 + 沉重的锚击声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.7, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.BLOCK, loc,
                count * 2, 0.3, 0.15, 0.3, 0.05,
                org.bukkit.Material.STONE.createBlockData());
        getBlock().getWorld().spawnParticle(
                Particle.LARGE_SMOKE, loc,
                count, 0.2, 0.1, 0.2, 0.02);
        playProcessingSound(Sound.BLOCK_ANVIL_LAND, 0.25f, 0.6f, 0.2);
    }

    public static void refreshRecipeCache() {}
}
