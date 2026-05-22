package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamMillingRecipe;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽精密铣床 —— 把合金锭铣削成精密零部件。
 * 典型输入：因瓦锭 → 精密齿轮、钨锭 → 精密阀门、硬铝锭 → 散热片。
 */
public class SteamPrecisionMill extends AbstractSteamProcessor<SteamMillingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamPrecisionMill(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamPrecisionMill(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamMillingRecipe> recipeType() {
        return SteamMillingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "milling";
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_precision_mill";
    }

    /** 铣削时喷细小火花（CRIT）+ 金属碎屑（ITEM_SLIME）+ 低频金属切削声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.8, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.CRIT, loc,
                count, 0.15, 0.1, 0.15, 0.08);
        getBlock().getWorld().spawnParticle(
                Particle.DUST,
                loc, count / 2,
                0.1, 0.05, 0.1, 0.01,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0xC0C0C0), 0.6f));
        playProcessingSound(Sound.BLOCK_ANVIL_USE, 0.25f, 1.4f, 0.12);
    }

    public static void refreshRecipeCache() {}
}
