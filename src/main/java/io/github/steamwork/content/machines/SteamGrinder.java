package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamGrindingRecipe;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽研磨机 —— 干法粉碎，把岩石 / 原矿 / 合金锭磨成粉末。
 * 与洗选槽（湿法）互补；典型输入：闪长岩、花岗岩、铁锭、铜锭、煤。
 */
public class SteamGrinder extends AbstractSteamProcessor<SteamGrindingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamGrinder(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamGrinder(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamGrindingRecipe> recipeType() {
        return SteamGrindingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "grinding";
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_grinder";
    }

    /** 研磨时同时喷火花（CRIT）与少量烟雾 + 低频砂轮磨削声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.7, 0.5);
        getBlock().getWorld().spawnParticle(
                Particle.CRIT, loc,
                count, 0.2, 0.1, 0.2, 0.05);
        getBlock().getWorld().spawnParticle(
                Particle.SMOKE, loc,
                count / 2, 0.1, 0.05, 0.1, 0.01);
        playProcessingSound(Sound.BLOCK_GRINDSTONE_USE, 0.3f, 0.7f, 0.15);
    }

    /** 保留 API 兼容：实例级缓存随实例销毁释放，无需全局刷新。 */
    public static void refreshRecipeCache() {}
}
