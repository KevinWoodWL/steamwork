package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.base.RebarGhostBlockHolder;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.pylon.recipes.MoldingRecipe;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamMillingRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.MoldingRecipeWrapper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 蒸汽精密铣床 —— 多方块结构：
 * <pre>
 *    [铣床]           ← 机器方块
 * [钢][钢][钢]         ← 机器正下方，沿与朝向垂直的轴横向排列的三个铁块
 * </pre>
 * 使用过热蒸汽，把合金锭铣削成精密零部件。
 * 三个铁块未全部就位时机器停摆并以 ghost 块提示玩家。
 */
public class SteamPrecisionMill extends AbstractSteamProcessor<SteamMillingRecipe>
        implements RebarGhostBlockHolder {

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

    // ===== 多方块结构 =====

    /**
     * 缓存的三个铁块偏移量（懒加载，依赖机器朝向，方向固定后不会改变）。
     * 沿垂直于机器朝向的水平轴排列，在视觉上形成一条铣床底座轨道。
     */
    @Nullable
    private List<Vector3i> cachedSteelOffsets = null;

    /**
     * 返回机器正下方三个铁块相对于机器方块的偏移量列表。
     * 朝向 NORTH/SOUTH → 沿 X 轴（东西）延伸；
     * 朝向 EAST/WEST  → 沿 Z 轴（南北）延伸。
     */
    private @NotNull List<Vector3i> getSteelOffsets() {
        if (cachedSteelOffsets != null) return cachedSteelOffsets;
        BlockFace facing;
        try { facing = getFacing(); } catch (IllegalStateException e) { facing = BlockFace.SOUTH; }
        int dx = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) ? 1 : 0;
        int dz = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) ? 0 : 1;
        cachedSteelOffsets = List.of(
                new Vector3i(-dx, -1, -dz),
                new Vector3i(0,   -1,  0),
                new Vector3i( dx, -1,  dz)
        );
        return cachedSteelOffsets;
    }

    /**
     * 校验三个铁块是否就位，同时更新 ghost 提示块。
     * 每 tick 由 AbstractSteamProcessor.tick() 首先调用。
     */
    /** 用于复用的 Pylon 钢块组件（含 matches + ghost 信息）。 */
    private static final MultiblockComponent STEEL_BLOCK_COMPONENT =
            MultiblockComponent.of(PylonKeys.STEEL_BLOCK);

    @Override
    protected boolean hasValidStructure() {
        syncGhostBlocks();
        for (Vector3i offset : getSteelOffsets()) {
            if (!STEEL_BLOCK_COMPONENT.matches(getBlock().getRelative(offset.x, offset.y, offset.z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 同步 ghost 块：缺少 Pylon 钢块的位置显示 ghost 提示，已就位的位置移除 ghost。
     */
    private void syncGhostBlocks() {
        for (Vector3i offset : getSteelOffsets()) {
            boolean hasBlock = STEEL_BLOCK_COMPONENT.matches(
                    getBlock().getRelative(offset.x, offset.y, offset.z));
            boolean hasGhost = hasGhostBlockAt(offset);
            if (!hasBlock && !hasGhost) {
                addGhostBlock(offset, List.of(), List.of(PylonKeys.STEEL_BLOCK));
            } else if (hasBlock && hasGhost) {
                removeGhostBlock(offset);
            }
        }
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        syncGhostBlocks();
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        super.onBreak(drops, context);
        for (Vector3i offset : getSteelOffsets()) {
            if (hasGhostBlockAt(offset)) {
                removeGhostBlock(offset);
            }
        }
    }

    // ===== AbstractSteamProcessor 实现 =====

    @Override
    protected @NotNull RecipeType<SteamMillingRecipe> recipeType() {
        return SteamMillingRecipe.RECIPE_TYPE;
    }

    @Override
    protected @NotNull String pdcKeyPrefix() {
        return "milling";
    }

    @Override
    public int upgradeSlotCount() { return 3; }

    @Override
    protected @NotNull String translationPrefix() {
        return "steamwork.gui.steam_precision_mill";
    }

    @Override
    protected @NotNull RebarFluid steamFluid() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    /** 过热蒸汽量仪：橙→红色系，直观反映高温状态。 */
    @Override
    protected @NotNull Material[] steamGaugeMaterials() {
        return new Material[]{
            Material.ORANGE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.RED_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS
        };
    }

    /** WAILA 蒸汽条颜色：橙色，对应过热蒸汽。 */
    @Override
    protected @NotNull String steamBarColor() {
        return "#ff8c00";
    }

    /** 铣削时喷细小火花（CRIT）+ 金属碎屑（DUST）+ 低频金属切削声。 */
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

    // ===== Pylon 联动 =====

    /**
     * 静态版联动配方列表，供 {@link Item} 构造器和 {@link #buildPylonRecipes()} 共用。
     *
     * <p>精密铣床以蒸汽精密主轴替代 Pylon 手持模具，可执行所有 {@link MoldingRecipe}；
     * 压制周期由过热蒸汽提供恒压，工具需求由蒸汽系统保证。</p>
     */
    public static @NotNull List<SteamProcessRecipe> pylonRecipesForItem() {
        return MoldingRecipe.RECIPE_TYPE.getRecipes().stream()
                .map(r -> (SteamProcessRecipe) new MoldingRecipeWrapper(r, 40.0, 180))
                .collect(Collectors.toList());
    }

    @Override
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return pylonRecipesForItem();
    }

    public static void refreshRecipeCache() {}
}
