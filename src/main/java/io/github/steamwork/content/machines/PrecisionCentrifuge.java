package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.base.RebarGhostBlockHolder;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
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
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 精密离心机 —— 硬铝等级的多产物分离机器，多方块结构。
 * 高速旋转主轴需要硬铝的轻质高强度特性。专门处理混合物：脏液、矿浆、废料、
 * 复合粉末等"模糊输入"，按密度加权随机分离出多种产物。
 * <p>多方块结构（y↑，X=与朝向垂直的轴，B=机器，A=钢支撑梁）：
 * <pre>
 *  A . . . A    y+1  x=±2
 *  A A B A A    y=0  x=±2, ±1
 *  A . . . A    y-1  x=±2
 * </pre>
 * A = Pylon 钢支撑梁，共 8 个，沿与机器朝向垂直的水平轴分布。
 * y=0 侧方有梁时，产线扫描器通过 {@link #getOwnedMultiblockComponentBlocks()} 自动跳过。
 * </p>
 */
public class PrecisionCentrifuge extends AbstractSteamProcessor<SteamCentrifugationRecipe>
        implements RebarGhostBlockHolder {

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

    // ===== 多方块结构 =====

    /** 钢支撑梁组件（用于结构验证和 ghost 提示）。 */
    private static final MultiblockComponent STEEL_BEAM_COMPONENT =
            MultiblockComponent.of(PylonKeys.STEEL_SUPPORT_BEAM);

    /**
     * 结构模板：{sideMultiplier, dy}。
     * sideMultiplier 乘以与机器朝向垂直的水平单位向量得到实际偏移量。
     *
     * 视图（y↑，X=与朝向垂直的轴，B=机器）：
     * <pre>
     *  A . . . A    y+1  x=-2, x=+2
     *  A A B A A    y=0   x=-2,-1,+1,+2
     *  A . . . A    y-1  x=-2, x=+2
     * </pre>
     */
    private static final int[][] STRUCTURE_PATTERN = {
            {-2, +1}, {+2, +1},
            {-2,  0}, {-1,  0}, {+1,  0}, {+2,  0},
            {-2, -1}, {+2, -1}
    };

    @Nullable private List<Vector3i> cachedBeamOffsets = null;

    /**
     * 计算 8 个钢支撑梁相对于机器方块的偏移量列表（懒加载）。
     * 朝向 NORTH/SOUTH → 侧轴为 X；朝向 EAST/WEST → 侧轴为 Z。
     */
    private @NotNull List<Vector3i> getBeamOffsets() {
        if (cachedBeamOffsets != null) return cachedBeamOffsets;
        BlockFace facing;
        try { facing = getFacing(); } catch (IllegalStateException e) { facing = BlockFace.SOUTH; }
        int dx = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) ? 1 : 0;
        int dz = (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) ? 0 : 1;
        List<Vector3i> offsets = new ArrayList<>(STRUCTURE_PATTERN.length);
        for (int[] o : STRUCTURE_PATTERN) {
            offsets.add(new Vector3i(o[0] * dx, o[1], o[0] * dz));
        }
        cachedBeamOffsets = List.copyOf(offsets);
        return cachedBeamOffsets;
    }

    @Override
    public @NotNull Set<Block> getOwnedMultiblockComponentBlocks() {
        Set<Block> blocks = new HashSet<>(STRUCTURE_PATTERN.length);
        for (Vector3i o : getBeamOffsets())
            blocks.add(getBlock().getRelative(o.x, o.y, o.z));
        return blocks;
    }

    @Override
    protected boolean hasValidStructure() {
        syncGhostBlocks();
        for (Vector3i offset : getBeamOffsets()) {
            if (!STEEL_BEAM_COMPONENT.matches(getBlock().getRelative(offset.x, offset.y, offset.z))) {
                return false;
            }
        }
        return true;
    }

    /** 同步 ghost 提示：缺少钢支撑梁的位置显示 ghost，已就位的位置移除 ghost。 */
    private void syncGhostBlocks() {
        for (Vector3i offset : getBeamOffsets()) {
            boolean hasBlock = STEEL_BEAM_COMPONENT.matches(
                    getBlock().getRelative(offset.x, offset.y, offset.z));
            boolean hasGhost = hasGhostBlockAt(offset);
            if (!hasBlock && !hasGhost) {
                addGhostBlock(offset, List.of(), List.of(PylonKeys.STEEL_SUPPORT_BEAM));
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
        for (Vector3i offset : getBeamOffsets()) {
            if (hasGhostBlockAt(offset)) {
                removeGhostBlock(offset);
            }
        }
    }

    // ===== AbstractSteamProcessor 实现 =====

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
