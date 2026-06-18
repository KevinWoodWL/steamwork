package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.steamwork.util.PneumaticEndpointSupport;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 产线成员接口：由产线入口（ProductionLineInlet）、产线出口（ProductionLineOutlet）
 * 以及参与产线的蒸汽加工机（AbstractSteamProcessor 子类）共同实现。
 *
 * <p>线信息通过蓝图道具写入各成员方块的 PDC，服务器重载后可恢复。</p>
 */
public interface ProductionLineMember {

    /** 产线 UUID，格式化为 String 存入 PDC。 */
    NamespacedKey LINE_ID_KEY = steamworkKey("line_id");
    /** 在产线中的序号（0 = 入口，1..n = 机器，n+1 = 出口）。 */
    NamespacedKey LINE_POSITION_KEY = steamworkKey("line_position");
    /**
     * 朝向产线下一个成员的方向（NORTH/SOUTH/EAST/WEST）。
     * 入口、中间机器均存真实方向；出口也存该方向（=整条产线的走向），
     * 仅用于解散扫描，不用于 push。
     */
    NamespacedKey LINE_DIRECTION_KEY = steamworkKey("line_direction");
    /** 激活该产线的玩家游戏名。 */
    NamespacedKey LINE_CREATOR_KEY = steamworkKey("line_creator");
    /** 创建者个人视角下的产线编号，例如 #1、#2。 */
    NamespacedKey LINE_NUMBER_KEY = steamworkKey("line_number");

    // ===== 读取 =====

    @Nullable UUID getLineId();

    int getLinePosition();

    @NotNull BlockFace getLineDirection();

    /** 激活该产线的玩家游戏名；若尚未激活则为 null。 */
    @Nullable String getLineCreator();

    /** 创建者个人视角下的产线编号；未加入产线时为 0。 */
    int getLineNumber();

    default boolean isInLine() { return getLineId() != null; }

    // ===== 写入（蓝图调用）=====

    void joinLine(@NotNull UUID lineId, int position, @NotNull BlockFace direction);

    /** 设置产线制作人，在 joinLine 后由 activateLine 统一调用。 */
    void setLineCreator(@Nullable String creator);

    /** 设置创建者个人视角下的产线编号，在 joinLine 后由 activateLine 统一调用。 */
    void setLineNumber(int number);

    void leaveLine();

    // ===== 多方块附属方块 =====

    /**
     * 返回本成员"拥有"的多方块附属方块集合（默认空集）。
     * 产线扫描器遇到这些方块时会跳过，不要求它们是 {@link ProductionLineMember}，
     * 也不会对它们调用 {@link #joinLine}。
     * <p>多方块机器（如精密离心机）需覆盖此方法，返回所有结构辅助方块的位置。</p>
     */
    default @NotNull Set<Block> getOwnedMultiblockComponentBlocks() {
        return Set.of();
    }

    // ===== 物品推送协议 =====

    /**
     * 被上游成员调用：尝试将 {@code item}（数量=1）放入本成员的输入缓冲。
     *
     * @return {@code true} 表示接受成功；{@code false} 表示缓冲满，拒绝接收。
     */
    boolean acceptFromLine(@NotNull ItemStack item);

    /** 是否有燃料槽（如原版熔炉/高炉/烟熏炉）。默认 false。 */
    default boolean hasFuelSlot() { return false; }

    /** 被入口调用：将 {@code item}（数量=1）推入燃料槽。默认拒绝。 */
    default boolean acceptFuelFromLine(@NotNull ItemStack item) { return false; }

    /**
     * 产线进入堵塞停摆时由入口调用，通知成员暂停工作。
     * 默认无操作；需要主动停摆的成员（如原版熔炉）可覆盖此方法。
     */
    default void onLineJammed() {}

    /**
     * 产线从堵塞状态恢复时由入口调用，通知成员恢复工作。
     * 默认无操作。
     */
    default void onLineResumed() {}

    /**
     * 工厂方法：将方块包装为 {@link ProductionLineMember}。
     * 优先检测 Rebar 方块，失败后尝试原版熔炉包装。
     * 若是其它 Rebar 方块（非成员）则返回 null，不会误包装。
     */
    @Nullable
    static ProductionLineMember of(@NotNull Block block) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(block);
        if (rb instanceof ProductionLineMember m) return m;
        if (rb != null) {
            if (PylonMixingPotMember.isPylonMixingPot(rb)
                    && rb instanceof io.github.pylonmc.pylon.content.machines.simple.MixingPot mixingPot) {
                return new PylonMixingPotMember(block, mixingPot);
            }
            // 尝试将 Pylon 机器包装为产线成员（pylon 命名空间 + 有输入逻辑组或虚拟库存）
            if (PylonMachineMember.isPylonMachine(rb)) return new PylonMachineMember(block, rb);
            return null;
        }
        if (SteamPressMember.isSteamPressBase(block)) return new SteamPressMember(block);
        if (VanillaFurnaceMember.isVanillaFurnace(block)) return new VanillaFurnaceMember(block);
        if (VanillaCrafterMember.isVanillaCrafter(block)) return new VanillaCrafterMember(block);
        return null;
    }

    // ===== 解散扫描工具 =====

    /**
     * 将物品推送给产线下游的下一个成员。
     * 若 {@code source} 不在产线中、方向未知或下游无成员，则原样返回。
     *
     * @param source 当前产线成员方块（调用方的 {@code getBlock()}）
     * @param member 调用方自身（即已知的产线成员）
     * @param item   待推送的物品（amount >= 1）
     * @return 未能推送的剩余物品（可能 isEmpty()）
     */
    @NotNull
    static ItemStack deliverToNextMember(
            @NotNull Block source,
            @NotNull ProductionLineMember member,
            @NotNull ItemStack item
    ) {
        UUID lineId = member.getLineId();
        BlockFace direction = member.getLineDirection();
        if (lineId == null || direction == BlockFace.SELF) return item;

        ProductionLineMember next = null;
        for (int i = 1; i <= DISBAND_MAX_GAP + 1; i++) {
            ProductionLineMember candidate = of(source.getRelative(direction, i));
            if (candidate != null && lineId.equals(candidate.getLineId())) { next = candidate; break; }
        }
        if (next == null) return item;

        ItemStack remaining = item.clone();
        int delivered = 0;
        while (delivered < remaining.getAmount()) {
            if (!acceptIntoLine(next, remaining.asQuantity(1))) break;
            delivered++;
        }
        remaining.setAmount(remaining.getAmount() - delivered);
        return remaining;
    }

    /**
     * 向下游成员推送 1 个物品，并按「耗汽驱动」扣除产线入口的加压蒸汽。
     *
     * <p>这是产线所有「物品向下游推进」的统一计费入口：每成功推进 1 个物品，
     * 就从该产线的入口扣 {@code steam-per-item} 点加压蒸汽。入口蒸汽不足时<b>拒绝推进</b>
     * （产线停摆），物品停在原处直到补汽。若该产线没有可用入口（未加载 / 旧线无缓存等），
     * 则不计费、原样推进，保证兼容。</p>
     *
     * @param next    下游成员（其 {@link #getLineId()} 即所在产线）
     * @param oneItem 待推送物品（amount = 1）
     * @return 是否成功推进（true=已接收且已扣汽；false=下游已满或入口缺汽）
     */
    static boolean acceptIntoLine(@NotNull ProductionLineMember next, @NotNull ItemStack oneItem) {
        UUID lineId = next.getLineId();
        ProductionLineInlet inlet = (lineId != null) ? ProductionLineInlet.forLine(lineId) : null;
        if (inlet == null) {
            return next.acceptFromLine(oneItem); // 无耗汽入口 → 原样推进
        }
        double cost = inlet.getSteamPerItem();
        if (cost > 0.0 && !inlet.hasDriveSteam(cost)) {
            return false; // 缺汽 → 停摆
        }
        if (!next.acceptFromLine(oneItem)) {
            return false; // 下游已满
        }
        if (cost > 0.0) inlet.consumeDriveSteam(cost);
        return true;
    }

    /**
     * 相邻产线成员之间允许的最大间隔格数（对应 {@code ProductionLineListener.MAX_COMPONENT_GAP}）。
     * 多方块机器的附属方块（如梁）占用的空隙不超过此值。
     */
    int DISBAND_MAX_GAP = 3;

    /**
     * 从 {@code start} 沿 {@code dir} 方向扫描，对所有属于 {@code lineId} 的
     * 产线成员依次调用 {@link #leaveLine()}。
     *
     * <p>能够跳过多方块机器的附属方块（最多连续 {@value #DISBAND_MAX_GAP} 格），
     * 与 {@code ProductionLineListener.disbandScan} 逻辑完全一致。
     * 两处均应调用本方法，而非各自维护独立实现。</p>
     *
     * @param start          扫描起点（触发解散的机器的相邻格）
     * @param dir            扫描方向
     * @param lineId         目标产线 UUID
     * @param firstDissolved 调用方自身（已知属于该产线，用于附属方块的跳过判定）
     */
    static void disbandScan(@NotNull Block start, @NotNull BlockFace dir,
                            @NotNull UUID lineId, @NotNull ProductionLineMember firstDissolved) {
        Block cursor = start;
        ProductionLineMember lastDissolved = firstDissolved;

        while (true) {
            ProductionLineMember m = of(cursor);
            if (m != null) {
                if (!lineId.equals(m.getLineId())) break;
                lastDissolved = m;
                m.leaveLine();
                cursor = cursor.getRelative(dir);
                continue;
            }

            // 当前格是上一个已解散成员的附属方块（如多方块机器的钢梁）→ 跳过
            if (lastDissolved.getOwnedMultiblockComponentBlocks().contains(cursor)) {
                cursor = cursor.getRelative(dir);
                continue;
            }

            // 前瞻：当前格是否是前方成员的前置附属方块
            boolean skipped = false;
            Block peek = cursor.getRelative(dir);
            for (int i = 0; i < DISBAND_MAX_GAP; i++) {
                ProductionLineMember mPeek = of(peek);
                if (mPeek != null && lineId.equals(mPeek.getLineId())) {
                    if (mPeek.getOwnedMultiblockComponentBlocks().contains(cursor)) {
                        cursor = cursor.getRelative(dir);
                        skipped = true;
                    }
                    break;
                }
                peek = peek.getRelative(dir);
            }
            if (!skipped) break;
        }
    }
}
