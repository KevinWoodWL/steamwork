package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.recipes.HammerRecipe;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamForgingRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.HammerRecipeWrapper;
import org.bukkit.Color;
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
 * 液压锻造机 —— 锰青铜级高压锻造机器。
 * 利用锰青铜的耐磨与减震特性长时间承受高压循环，专门产出液压系统中间件：
 * 高压管件、法兰、活塞、密封件、锻造钢板等。是 Pylon 液压链接入 Steamwork 的主要桥梁。
 */
public class HydraulicForge extends AbstractSteamProcessor<SteamForgingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public HydraulicForge(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public HydraulicForge(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamForgingRecipe> recipeType() {
        return SteamForgingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "forging";
    }

    @Override
    public int upgradeSlotCount() { return 3; }

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
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.hydraulic_forge";
    }

    /** 锻造特效：橙色火星 + 蒸汽喷射 + 锻锤声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.7, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.LAVA, loc,
                count, 0.25, 0.1, 0.25, 0);
        getBlock().getWorld().spawnParticle(
                Particle.DUST, loc,
                count * 2, 0.3, 0.15, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(255, 140, 40), 1.4f));
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD, loc.clone().add(0, 0.4, 0),
                count, 0.2, 0.05, 0.2, 0.02);
        playProcessingSound(Sound.BLOCK_ANVIL_USE, 0.3f, 1.2f, 0.15);
    }

    // ===== Pylon 联动 =====

    /**
     * 静态版联动配方列表，供 {@link Item} 构造器和 {@link #buildPylonRecipes()} 共用。
     *
     * <p>液压锻造机以蒸汽液压替代 Pylon 手持锤，可执行所有 {@link HammerRecipe}；
     * 工具等级要求由液压系统保证，不再限制。</p>
     */
    public static @NotNull List<SteamProcessRecipe> pylonRecipesForItem() {
        return HammerRecipe.RECIPE_TYPE.getRecipes().stream()
                .map(r -> (SteamProcessRecipe) new HammerRecipeWrapper(r, 60.0, 160))
                .collect(Collectors.toList());
    }

    @Override
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return pylonRecipesForItem();
    }

    public static void refreshRecipeCache() {}
}
