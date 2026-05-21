package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamSterilizingRecipe;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽灭菌箱 —— 用于无菌处理动植物有机物。
 * 典型输入：腐肉、骨粉、菌类、苔藓、有毒食物。
 */
public class SteamSterilizer extends AbstractSteamProcessor<SteamSterilizingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamSterilizer(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamSterilizer(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamSterilizingRecipe> recipeType() {
        return SteamSterilizingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "sterilizer";
    }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_sterilizer";
    }

    /** 灭菌效果：绿色"净化"粒子 + 白色蒸汽云 + 低频信标嗡鸣（蒸汽消毒感）。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        World world = getBlock().getWorld();
        Location top = getBlock().getLocation().add(0.5, 0.9, 0.5);
        world.spawnParticle(Particle.HAPPY_VILLAGER, top, count, 0.25, 0.1, 0.25, 0.0);
        world.spawnParticle(Particle.CLOUD, top, Math.max(1, count / 2), 0.2, 0.05, 0.2, 0.01);
        playProcessingSound(Sound.BLOCK_BEACON_AMBIENT, 0.25f, 1.6f, 0.15);
    }

    /** 保留 API 兼容：实例级缓存随实例销毁释放，无需全局刷新。 */
    public static void refreshRecipeCache() {}
}
