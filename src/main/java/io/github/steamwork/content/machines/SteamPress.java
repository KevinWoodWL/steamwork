package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamPressingRecipe;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽冲压机 —— 通过高压蒸汽推动活塞，把柔软/散粒材料压制成板、片、密封件。
 * 典型输入：处理木材、蒸汽纸浆、金属锭。
 */
public class SteamPress extends AbstractSteamProcessor<SteamPressingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamPress(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamPress(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamPressingRecipe> recipeType() {
        return SteamPressingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "pressing";
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_press";
    }

    /** 冲压时喷烟雾粒子代表蒸汽排放与冲击 + 低频活塞推送声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.SMOKE,
                getBlock().getLocation().add(0.5, 0.9, 0.5),
                count, 0.15, 0.05, 0.15, 0.01);
        playProcessingSound(Sound.BLOCK_PISTON_EXTEND, 0.4f, 1.4f, 0.15);
    }

    /** 保留 API 兼容：实例级缓存随实例销毁释放，无需全局刷新。 */
    public static void refreshRecipeCache() {}
}
