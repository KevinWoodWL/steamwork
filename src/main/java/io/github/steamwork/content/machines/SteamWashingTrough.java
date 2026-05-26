package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.SteamWashingRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 蒸汽洗选槽 —— 湿法选矿与岩石分选。
 * 典型输入：砂砾、砂、原矿、灵魂沙、凝灰岩。
 */
public class SteamWashingTrough extends AbstractSteamProcessor<SteamWashingRecipe> {

    public static class Item extends BaseItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    @SuppressWarnings("unused")
    public SteamWashingTrough(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public SteamWashingTrough(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull RecipeType<SteamWashingRecipe> recipeType() {
        return SteamWashingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "washing";
    }

    @Override
    public int upgradeSlotCount() { return 2; }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_washing_trough";
    }

    /** 洗选时同时喷水泡粒子与蒸汽云 + 低频气泡爆破声。 */
    @Override
    protected void spawnProcessingParticles(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.BUBBLE_COLUMN_UP,
                getBlock().getLocation().add(0.5, 0.5, 0.5),
                count, 0.2, 0.2, 0.2, 0.02);
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD,
                getBlock().getLocation().add(0.5, 0.8, 0.5),
                count / 2, 0.2, 0.1, 0.2, 0.01);
        playProcessingSound(Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.35f, 1.8f, 0.15);
    }

    /** 在进度条上追加"产出: N×物品"信息，提示玩家本次配方的实际产量。 */
    @Override
    protected @NotNull List<Component> additionalProgressLore(@NotNull SteamProcessRecipe recipe) {
        ItemStack produced = recipe.producedStack();
        return List.of(Component.translatable(
                "steamwork.gui.steam_processor.output_summary",
                RebarArgument.of("amount", Component.text(produced.getAmount())),
                RebarArgument.of("item", produced.effectiveName())
        ).decoration(TextDecoration.ITALIC, false));
    }

    /** 保留 API 兼容：实例级缓存随实例销毁释放，无需全局刷新。 */
    public static void refreshRecipeCache() {}
}
