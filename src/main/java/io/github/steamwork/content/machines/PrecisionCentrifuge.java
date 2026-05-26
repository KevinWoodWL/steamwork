package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamCentrifugationRecipe;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 精密离心机 —— 硬铝等级的多产物分离机器。
 * 高速旋转主轴需要硬铝的轻质高强度特性。专门处理混合物：脏液、矿浆、废料、
 * 复合粉末等"模糊输入"，按密度加权随机分离出多种产物。
 * <p>与研磨机的差异：研磨机出确定单一产物，离心机出加权随机的多种产物，
 * 模拟工业上"分层 + 部分回收"的真实流程。</p>
 */
public class PrecisionCentrifuge extends AbstractSteamProcessor<SteamCentrifugationRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public PrecisionCentrifuge(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PrecisionCentrifuge(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamCentrifugationRecipe> recipeType() {
        return SteamCentrifugationRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "centrifugation";
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
        return "steamwork.gui.precision_centrifuge";
    }

    /** 离心特效：旋转的浅蓝色粒子环 + 排气蒸汽。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        Location loc = getBlock().getLocation().add(0.5, 0.7, 0.5);
        // 环形旋转粒子
        for (int i = 0; i < count; i++) {
            double angle = (System.currentTimeMillis() / 50.0 + i * (360.0 / Math.max(count, 1))) * Math.PI / 180.0;
            double r = 0.35;
            Location offset = loc.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            getBlock().getWorld().spawnParticle(
                    Particle.DUST, offset,
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(170, 220, 255), 0.9f));
        }
        // 顶部排气
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD, loc.clone().add(0, 0.5, 0),
                count / 2, 0.15, 0.05, 0.15, 0.03);
        playProcessingSound(Sound.BLOCK_BEACON_AMBIENT, 0.18f, 1.9f, 0.25);
    }

    public static void refreshRecipeCache() {}
}
