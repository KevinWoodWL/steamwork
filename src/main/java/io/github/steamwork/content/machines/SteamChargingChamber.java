package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.SteamCharge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;
import java.util.Map;

/**
 * 蒸汽充汽舱（多方块）。
 *
 * <p>消耗<b>加压蒸汽</b>给站内玩家的全身装备与背包蒸汽罐充汽。</p>
 *
 * <p>结构（以充汽舱主方块 M 所在层为 Y=0；{@code B}=锰青铜块，{@code S}=倒置铜楼梯，
 * {@code K}=锰钢块）：</p>
 * <pre>
 *   Y=+1  B  S  B    顶层：四角锰青铜块 + 四侧各一倒置铜楼梯（↑流体入口 Y+1 正中为空）
 *         S  ↑  S
 *         B  S  B
 *
 *   Y=0   B  M  B    主方块层：四角锰青铜块，中央放充汽舱
 *
 *         B  M  B
 *
 *   Y=-1  B  K  B    底层：四角锰青铜块 + 正中锰钢块
 *
 *         B  K  B
 * </pre>
 * <p>加压蒸汽管道从主方块<b>顶部</b>（Y+1 正中，楼梯已让出该位置）接入。<br>
 * 玩家检测范围：以主方块为中心向四周延伸约 1 格的帧内空间。</p>
 *
 * <p><b>结构朝向固定为世界 NORTH 基准</b>，不随主方块放置朝向旋转：北侧楼梯朝南、
 * 南侧朝北、东侧朝西、西侧朝东（均朝结构内侧）。这样保证倒置楼梯朝向恒定正确，
 * 无论玩家放置主方块时面朝哪个方向。</p>
 */
public class SteamChargingChamber extends RebarBlock implements
        RebarSimpleMultiblock,
        RebarFluidBufferBlock,
        RebarTickingBlock {

    private final int tickInterval   = getSettings().getOrThrow("tick-interval",         ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer",           ConfigAdapter.DOUBLE);
    /** 每 tick 每件物品转入的加压蒸汽量（mB）。 */
    private final double chargeRate  = getSettings().getOrThrow("charge-rate-per-tick",   ConfigAdapter.DOUBLE);

    // ===== 物品（指南占位）=====

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer",         ConfigAdapter.DOUBLE);
        private final double chargeRate  = getSettings().getOrThrow("charge-rate-per-tick", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer",         UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("charge-rate-per-tick", UnitFormat.MILLIBUCKETS.format(chargeRate))
            );
        }
    }

    // ===== 定向倒置楼梯组件（静态延迟初始化，各自只接受一种朝向）=====
    //
    // 四侧楼梯均倒置（Half.TOP），各自朝向结构内侧：
    //   北侧楼梯 (0,+1,-1) → facing=SOUTH（步阶朝南，即朝内）
    //   南侧楼梯 (0,+1,+1) → facing=NORTH
    //   东侧楼梯 (+1,+1,0) → facing=WEST
    //   西侧楼梯 (-1,+1,0) → facing=EAST

    private static MultiblockComponent STAIR_NORTH = null; // 北侧位置，朝南（内侧）
    private static MultiblockComponent STAIR_SOUTH = null; // 南侧位置，朝北
    private static MultiblockComponent STAIR_EAST  = null; // 东侧位置，朝西
    private static MultiblockComponent STAIR_WEST  = null; // 西侧位置，朝东

    private static final Material[] CUT_COPPER_STAIR_VARIANTS = {
            Material.CUT_COPPER_STAIRS,
            Material.EXPOSED_CUT_COPPER_STAIRS,
            Material.WEATHERED_CUT_COPPER_STAIRS,
            Material.OXIDIZED_CUT_COPPER_STAIRS,
            Material.WAXED_CUT_COPPER_STAIRS,
            Material.WAXED_EXPOSED_CUT_COPPER_STAIRS,
            Material.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS
    };

    /** 创建仅接受指定朝向的倒置切制铜楼梯组件，允许氧化与涂蜡变体。 */
    private static @NotNull MultiblockComponent makeStair(@NotNull BlockFace inwardFacing) {
        List<BlockData> variants = new java.util.ArrayList<>();
        for (Material material : CUT_COPPER_STAIR_VARIANTS) {
            Stairs s = (Stairs) Bukkit.createBlockData(material);
            s.setHalf(Bisected.Half.TOP);        // 倒置
            s.setFacing(inwardFacing);           // 朝向内侧
            s.setShape(Stairs.Shape.STRAIGHT);
            s.setWaterlogged(false);
            variants.add(s);
        }
        return MultiblockComponent.of(variants.toArray(new BlockData[0]));
    }

    private static void ensureStairsInitialized() {
        if (STAIR_NORTH == null) {
            STAIR_NORTH = makeStair(BlockFace.SOUTH);
            STAIR_SOUTH = makeStair(BlockFace.NORTH);
            STAIR_EAST  = makeStair(BlockFace.WEST);
            STAIR_WEST  = makeStair(BlockFace.EAST);
        }
    }

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public SteamChargingChamber(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        // 固定结构朝向为 NORTH，不随主方块放置朝向旋转。
        // 原因：Rebar 的 rotateComponentsToFace 只旋转组件坐标（Vector3i），不会旋转楼梯
        // BlockData 的 facing。若随朝向旋转，四侧倒置楼梯的位置会转、朝向却不转，导致结构错乱。
        // 因结构本身四重对称（四角 + 四侧朝内楼梯 + 中心），固定朝向不影响摆放体验。
        setMultiblockDirection(BlockFace.NORTH);
        setTickInterval(tickInterval);
        // 加压蒸汽从主方块顶部（Y+1 正中，楼梯已让出该位置）接入
        createFluidPoint(FluidPointType.INPUT, BlockFace.UP, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, steamBuffer, true, false);
    }

    @SuppressWarnings("unused")
    public SteamChargingChamber(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        // 强制固定为 NORTH，覆盖任何历史持久化的朝向值，保证楼梯朝向恒定正确。
        setMultiblockDirection(BlockFace.NORTH);
        setTickInterval(tickInterval);
    }

    // ===== 多方块结构 =====
    //
    // 三层：底层 (Y=-1)、主方块层 (Y=0)、顶层 (Y=+1)。
    //
    // 角柱：锰青铜块 → MultiblockComponent.of(SteamworkKeys.MANGANESE_BRONZE_BLOCK)
    // 底座：锰钢块   → MultiblockComponent.of(SteamworkKeys.MANGANESE_STEEL_BLOCK)
    // 顶侧：倒置铜楼梯，四侧各一（位于 ±1 轴 Y+1 的中点）；顶层中央 (0,+1,0) 留空供流体接入。

    @Override
    public @NotNull Map<@NotNull Vector3i, @NotNull MultiblockComponent> getComponents() {
        ensureStairsInitialized();
        MultiblockComponent bronze = MultiblockComponent.of(SteamworkKeys.MANGANESE_BRONZE_BLOCK);
        MultiblockComponent steel  = MultiblockComponent.of(SteamworkKeys.MANGANESE_STEEL_BLOCK);

        return Map.ofEntries(
                // ── Y=-1 底层 ──────────────────────────────────────────────
                Map.entry(new Vector3i(-1, -1, -1), bronze),       // 左后角 锰青铜块
                Map.entry(new Vector3i(-1, -1,  1), bronze),       // 左前角 锰青铜块
                Map.entry(new Vector3i( 1, -1, -1), bronze),       // 右后角 锰青铜块
                Map.entry(new Vector3i( 1, -1,  1), bronze),       // 右前角 锰青铜块
                Map.entry(new Vector3i( 0, -1,  0), steel),        // 中央   锰钢块
                // ── Y=0 主方块层 ────────────────────────────────────────────
                Map.entry(new Vector3i(-1,  0, -1), bronze),       // 左后角 锰青铜块
                Map.entry(new Vector3i(-1,  0,  1), bronze),       // 左前角 锰青铜块
                Map.entry(new Vector3i( 1,  0, -1), bronze),       // 右后角 锰青铜块
                Map.entry(new Vector3i( 1,  0,  1), bronze),       // 右前角 锰青铜块
                // ── Y=+1 顶层 ──────────────────────────────────────────────
                Map.entry(new Vector3i(-1,  1, -1), bronze),       // 左后角 锰青铜块
                Map.entry(new Vector3i(-1,  1,  1), bronze),       // 左前角 锰青铜块
                Map.entry(new Vector3i( 1,  1, -1), bronze),       // 右后角 锰青铜块
                Map.entry(new Vector3i( 1,  1,  1), bronze),       // 右前角 锰青铜块
                // 四侧楼梯：各自固定朝向（倒置 Half.TOP，步阶朝内侧）
                Map.entry(new Vector3i( 0,  1, -1), STAIR_NORTH),  // 北侧 facing=SOUTH
                Map.entry(new Vector3i( 0,  1,  1), STAIR_SOUTH),  // 南侧 facing=NORTH
                Map.entry(new Vector3i( 1,  1,  0), STAIR_EAST),   // 东侧 facing=WEST
                Map.entry(new Vector3i(-1,  1,  0), STAIR_WEST)    // 西侧 facing=EAST
                // (0,+1,0) 正中留空 → 流体管道从此处接入（BlockFace.UP）
        );
    }

    // ===== Tick：给站内玩家充汽 =====

    @Override
    public void tick() {
        if (!isFormedAndFullyLoaded()) return;
        if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < chargeRate) return;

        // 扫描玩家：以主方块为中心向四周延伸约 1 格的帧内空间
        Block core = getBlock();
        BoundingBox box = BoundingBox.of(core).expand(1.0);

        for (var entity : core.getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof Player player)) continue;
            chargePlayer(player);
        }
    }

    /**
     * 给玩家全身槽位（含护甲+副手）和背包里所有有蒸汽储能的物品充汽，
     * 同时收集统计数据用于 subtitle 显示。
     *
     * <p>用索引遍历 + setItem 写回：{@code getItem(i)} 返回的可能是镜像副本，
     * {@link SteamCharge#addAmount} 改的是其 meta，必须 setItem 写回才会同步到客户端与存档。</p>
     */
    private void chargePlayer(@NotNull Player player) {
        var inv = player.getInventory();

        double totalCapacity     = 0;
        double totalCurrentAfter = 0;
        double totalAdded        = 0;
        int    chargeableCount   = 0;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!SteamCharge.hasSteamStorage(stack)) continue;

            double cap = SteamCharge.getCapacity(stack);
            double cur = SteamCharge.getAmount(stack);
            totalCapacity += cap;
            chargeableCount++;

            double steam = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            if (steam > 0 && cur < cap) {
                double toGive = Math.min(Math.min(chargeRate, cap - cur), steam);
                double added  = SteamCharge.addAmount(stack, toGive);
                if (added > 0) {
                    inv.setItem(i, stack);
                    removeFluid(SteamworkFluids.PRESSURIZED_STEAM, added);
                    totalAdded += added;
                    cur        += added;
                }
            }
            totalCurrentAfter += cur;
        }

        if (totalAdded > 0) {
            spawnChargeFx(player, totalCurrentAfter, totalCapacity, totalAdded, chargeableCount);
        }
    }

    // 进度条宽度（字符数）
    private static final int BAR_WIDTH = 14;

    /**
     * 充汽特效：舱室内大量白色蒸汽粒子 + 加压蒸汽音效 + subtitle 充汽状态。
     *
     * @param totalCurrent  充汽后全部可充汽物品的当前蒸汽总量（mB）
     * @param totalCapacity 全部可充汽物品的最大容量总和（mB）
     * @param added         本次 tick 实际充入量（mB）
     * @param itemCount     背包中可充汽物品总数
     */
    private void spawnChargeFx(@NotNull Player player,
                                double totalCurrent, double totalCapacity,
                                double added, int itemCount) {
        Block core = getBlock();

        // ── 粒子：结构中央漫射云 + 玩家周围上升蒸汽 ──
        core.getWorld().spawnParticle(
                Particle.CLOUD,
                core.getLocation().add(0.5, 0.5, 0.5),
                30, 1.1, 0.7, 1.1, 0.012);
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                player.getLocation().add(0, 1.0, 0),
                18, 0.5, 0.6, 0.5, 0.025);

        // ── 音效 ──
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS,
                0.35f, 1.8f);

        // ── Subtitle：进度条 + 当前/容量 + 本次充汽量 + 件数 ──
        double pct    = totalCapacity > 0 ? totalCurrent / totalCapacity : 0;
        int    filled = (int) Math.round(pct * BAR_WIDTH);

        // 进度条颜色：已充 = 青色，未充 = 深灰
        Component bar = Component.empty();
        for (int i = 0; i < BAR_WIDTH; i++) {
            bar = bar.append(Component.text(
                    i < filled ? "█" : "░",
                    i < filled ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        }

        Component subtitle = Component.empty()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(bar)
                .append(Component.text("]  ", NamedTextColor.GRAY))
                .append(Component.text((int) totalCurrent, NamedTextColor.AQUA))
                .append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                .append(Component.text((int) totalCapacity + " mB", NamedTextColor.AQUA))
                .append(Component.text("  +", NamedTextColor.GRAY))
                .append(Component.text((int) added + " mB", NamedTextColor.GREEN))
                .append(Component.text("  [" + itemCount + " 件]", NamedTextColor.GRAY));

        // ActionBar 显示充汽状态；原生自带约 3s 淡出，每 tick（500ms）刷新一次不会闪烁
        player.sendActionBar(subtitle);
    }

    // ===== WAILA =====

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("structure", Component.translatable(
                        "steamwork.structure." + (isFormedAndFullyLoaded() ? "formed" : "missing"))),
                RebarArgument.of("steam-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        16, TextColor.fromHexString("#18c0d8")))
        ));
    }
}
