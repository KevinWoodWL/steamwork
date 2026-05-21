package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamSteepingRecipe;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽浸煮桶 —— 用于蒸煮、萃取、软化植物质与树脂。
 * 典型输入：竹子、原木、根须、蜂巢、粘土、植物纤维。
 */
public class SteamSteepingVat extends AbstractSteamProcessor<SteamSteepingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamSteepingVat(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamSteepingVat(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamSteepingRecipe> recipeType() {
        return SteamSteepingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "steeping";
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_steeping_vat";
    }

    /** 浸煮效果：水花 + 蒸汽云 + 低频气泡爆破（湿煮感）。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        World world = getBlock().getWorld();
        Location base = getBlock().getLocation().add(0.5, 0.7, 0.5);
        world.spawnParticle(Particle.SPLASH, base, count, 0.2, 0.1, 0.2, 0.0);
        world.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.2, 0),
                Math.max(1, count / 2), 0.2, 0.05, 0.2, 0.01);
        playProcessingSound(Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.3f, 1.2f, 0.15);
    }

    /** 保留 API 兼容：实例级缓存随实例销毁释放，无需全局刷新。 */
    public static void refreshRecipeCache() {}
}
