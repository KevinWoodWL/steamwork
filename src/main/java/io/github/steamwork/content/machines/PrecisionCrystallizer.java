package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamCrystallizingRecipe;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 精密结晶炉 —— 因瓦合金等级的恒温提纯机器。
 * 利用因瓦的零热膨胀特性维持稳定温度，把低品矿物 / 粗矿 / 边角料慢速结晶成高纯产物。
 * 招牌特性：慢、稳、不复制。投入产出严格按"提纯比"，纯转化定位。
 */
public class PrecisionCrystallizer extends AbstractSteamProcessor<SteamCrystallizingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public PrecisionCrystallizer(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionCrystallizer(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamCrystallizingRecipe> recipeType() {
        return SteamCrystallizingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "crystallizing";
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
        return "steamwork.gui.precision_crystallizer";
    }

    /** 结晶特效：淡蓝色细粒子 + 缓慢的水晶生长粒子 + 轻柔的水声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.7, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.DUST, loc,
                count, 0.25, 0.2, 0.25, 0,
                new Particle.DustOptions(Color.fromRGB(150, 200, 255), 0.8f));
        getBlock().getWorld().spawnParticle(
                Particle.END_ROD, loc.clone().add(0, 0.2, 0),
                count / 2, 0.15, 0.1, 0.15, 0.005);
        playProcessingSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2f, 1.4f, 0.4);
    }

    public static void refreshRecipeCache() {}
}
