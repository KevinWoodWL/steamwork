package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 产线系统的全局监听器，负责：
 * <ol>
 *   <li>处理蓝图道具右键交互，完成产线配置与激活</li>
 *   <li>监听产线成员方块被破坏，解散整条产线</li>
 *   <li>玩家下线 / 切换手持物品时清除未完成的蓝图配置</li>
 * </ol>
 */
public class ProductionLineListener implements Listener {

    /** 产线扫描时允许跳过的最大连续附属方块数量（覆盖多方块机器的侧边梁等）。 */
    private static final int MAX_COMPONENT_GAP = 3;

    /** 当前正在配置中的蓝图状态，key = 玩家 UUID。 */
    private final Map<UUID, BlueprintSession> sessions = new HashMap<>();

    /**
     * 蓝图配置会话，记录已选中的方块列表（第一个为入口）。
     */
    private record BlueprintSession(@NotNull List<Block> selected) {
        BlueprintSession() { this(new ArrayList<>()); }
    }

    // ==================== 蓝图交互 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlueprintInteract(@NotNull PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!(RebarItem.fromStack(hand) instanceof ProductionLineBlueprint)) return;

        // 取消原版交互
        event.setCancelled(true);

        // 潜行 + 右键 → 取消配置
        if (player.isSneaking() && action == Action.RIGHT_CLICK_BLOCK) {
            cancelSession(player);
            return;
        }

        RebarBlock rebar = RebarBlock.getRebarBlock(block);

        BlueprintSession session = sessions.get(player.getUniqueId());

        // ── 情形 1：尚未开始 → 必须先右键入口 ──
        if (session == null) {
            if (action != Action.RIGHT_CLICK_BLOCK) return;
            if (!(rebar instanceof ProductionLineInlet inlet)) {
                msg(player, NamedTextColor.RED, "steamwork.line.blueprint.start_with_inlet");
                return;
            }
            if (inlet.isInLine()) {
                msg(player, NamedTextColor.RED, "steamwork.line.blueprint.already_in_line");
                return;
            }
            BlueprintSession newSession = new BlueprintSession();
            newSession.selected().add(block);
            sessions.put(player.getUniqueId(), newSession);
            msg(player, NamedTextColor.GREEN, "steamwork.line.blueprint.started");
            return;
        }

        // ── 情形 2：配置进行中 ──
        if (action != Action.LEFT_CLICK_BLOCK) return;

        List<Block> selected = session.selected();

        // 防止重复添加
        if (selected.contains(block)) {
            msg(player, NamedTextColor.YELLOW, "steamwork.line.blueprint.already_selected");
            return;
        }

        // 验证与上一个方块的关系（相邻或允许间隔多方块附属块 & 共线）
        Block prev = selected.get(selected.size() - 1);
        ProductionLineMember prevMember  = ProductionLineMember.of(prev);
        ProductionLineMember blockMember = ProductionLineMember.of(block);
        @Nullable BlockFace dir = getLineAxisWithGap(prev, prevMember, block, blockMember);
        if (dir == null) {
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.not_adjacent");
            return;
        }
        // 方向必须与已有产线方向一致（selected.size() >= 2 时才有固定方向）
        if (selected.size() >= 2) {
            Block s0 = selected.get(0), s1 = selected.get(1);
            ProductionLineMember m0 = ProductionLineMember.of(s0);
            ProductionLineMember m1 = ProductionLineMember.of(s1);
            BlockFace existingDir = getLineAxisWithGap(s0, m0, s1, m1);
            if (existingDir != null && existingDir != dir) {
                msg(player, NamedTextColor.RED, "steamwork.line.blueprint.not_collinear");
                return;
            }
        }

        // 必须是合法的产线成员
        ProductionLineMember member = ProductionLineMember.of(block);
        if (member == null) {
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.invalid_member");
            return;
        }
        if (member.isInLine()) {
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.already_in_line");
            return;
        }

        // ── 情形 2a：右键出口 → 完成配置 ──
        if (rebar instanceof ProductionLineOutlet) {
            selected.add(block);
            if (activateLine(player, selected)) {
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        // ── 情形 2b：右键中间成员（非入口）→ 继续添加 ──
        if (rebar instanceof ProductionLineInlet) {
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.invalid_member");
            return;
        }
        selected.add(block);
        msg(player, NamedTextColor.AQUA, "steamwork.line.blueprint.added",
                RebarArgument.of("position", Component.text(selected.size() - 1)));
    }

    /**
     * 激活一条已验证的产线（selected[0]=入口，selected[n]=出口）。
     *
     * @return true 表示激活成功
     */
    private boolean activateLine(@NotNull Player player, @NotNull List<Block> selected) {
        if (selected.size() < 3) {
            // 至少：入口 + 1 台机器 + 出口
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.too_short");
            return false;
        }

        Block s0 = selected.get(0), s1 = selected.get(1);
        ProductionLineMember lm0 = ProductionLineMember.of(s0);
        ProductionLineMember lm1 = ProductionLineMember.of(s1);
        BlockFace direction = getLineAxisWithGap(s0, lm0, s1, lm1);
        if (direction == null) {
            msg(player, NamedTextColor.RED, "steamwork.line.blueprint.activation_error");
            return false;
        }
        UUID lineId = UUID.randomUUID();
        ProductionLineRegistry registry = ProductionLineRegistry.get();
        int lineNumber = registry != null
                ? registry.register(lineId, player.getUniqueId(), player.getName(), direction, selected)
                : 0;

        for (int i = 0; i < selected.size(); i++) {
            Block b = selected.get(i);
            ProductionLineMember member = ProductionLineMember.of(b);
            if (member == null) {
                msg(player, NamedTextColor.RED, "steamwork.line.blueprint.activation_error");
                return false;
            }
            // 出口存相同方向（用于解散扫描），不用于推送
            member.joinLine(lineId, i, direction);
            member.setLineCreator(player.getName());
            member.setLineNumber(lineNumber);
        }

        msg(player, NamedTextColor.GREEN, "steamwork.line.blueprint.activated",
                RebarArgument.of("count", Component.text(selected.size() - 2)),
                RebarArgument.of("number", Component.text(lineNumber))); // 机器数量（不含入口出口）
        return true;
    }

    // ==================== 方块破坏 → 解散产线 ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        ProductionLineMember member = ProductionLineMember.of(block);
        if (member == null) return;
        if (!member.isInLine()) return;

        UUID lineId = member.getLineId();
        BlockFace dir = member.getLineDirection();
        int lineNumber = member.getLineNumber();
        String lineCreator = member.getLineCreator();
        ProductionLineRegistry.LineRecord record = null;

        // 从 YAML 注册表中删除该产线
        if (lineId != null) {
            ProductionLineRegistry registry = ProductionLineRegistry.get();
            if (registry != null) record = registry.unregister(lineId);
        }
        notifyLineInvalidated(record, lineCreator, lineNumber);

        // 解散被破坏方块自身
        member.leaveLine();

        if (dir == BlockFace.SELF || lineId == null) return;

        // 向下游扫描并清除（跳过多方块机器的附属方块）
        disbandScan(block.getRelative(dir), dir, lineId, member);

        // 向上游扫描并清除
        BlockFace reverse = dir.getOppositeFace();
        disbandScan(block.getRelative(reverse), reverse, lineId, member);
    }

    // ==================== 熔炉烧炼完成 → 推送产物至下游 ====================

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(@NotNull FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        if (!VanillaFurnaceMember.isVanillaFurnace(block)) return;
        VanillaFurnaceMember member = new VanillaFurnaceMember(block);
        if (!member.isInLine()) return;
        // 延迟 1 tick，等 Bukkit 把产物写入结果槽后再推送
        Bukkit.getScheduler().runTask(io.github.steamwork.Steamwork.getInstance(),
                () -> new VanillaFurnaceMember(block).tryPushResultDownstream());
    }

    // ==================== 玩家离线 / 切换手持 ====================

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onSlotChange(@NotNull PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack currentItem = player.getInventory().getItem(event.getPreviousSlot());
        if (!(RebarItem.fromStack(currentItem) instanceof ProductionLineBlueprint)) return;
        cancelSession(player);
    }

    // ==================== 工具方法 ====================

    /** 取消蓝图配置会话并通知玩家。 */
    private void cancelSession(@NotNull Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            msg(player, NamedTextColor.YELLOW, "steamwork.line.blueprint.cancelled");
        }
    }

    private static void notifyLineInvalidated(@Nullable ProductionLineRegistry.LineRecord record,
                                              @Nullable String fallbackCreator,
                                              int fallbackNumber) {
        int number = record != null ? record.number() : fallbackNumber;
        Player target = record != null
                ? Bukkit.getPlayer(record.creatorUuid())
                : fallbackCreator != null ? Bukkit.getPlayerExact(fallbackCreator) : null;
        if (target == null || number <= 0) return;
        msg(target, NamedTextColor.RED, "steamwork.line.blueprint.invalidated",
                RebarArgument.of("number", Component.text(number)));
    }

    /**
     * 沿 {@code dir} 方向扫描并解散产线成员，自动跳过多方块机器的附属方块。
     *
     * <p>跳过条件：当前方块不是成员，但
     * <ul>
     *   <li>属于上一个被解散成员的 {@link ProductionLineMember#getOwnedMultiblockComponentBlocks()}，或</li>
     *   <li>属于前方最近成员（最多 {@value MAX_COMPONENT_GAP} 格内）的附属集合。</li>
     * </ul>
     * </p>
     */
    private static void disbandScan(@NotNull Block start, @NotNull BlockFace dir,
                                    @NotNull UUID lineId, @NotNull ProductionLineMember firstDissolved) {
        // 委托给共享的 gap-aware 扫描工具，与 AbstractSteamProcessor.disbandLine() 保持一致
        ProductionLineMember.disbandScan(start, dir, lineId, firstDissolved);
    }

    /**
     * 返回从 {@code a} 到 {@code b} 的方向（仅允许 NORTH/SOUTH/EAST/WEST，且紧密相邻）。
     * 不满足条件返回 null。
     */
    @Nullable
    private static BlockFace getLineAxis(@NotNull Block a, @NotNull Block b) {
        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        if (dy != 0) return null; // 不允许上下
        if (Math.abs(dx) + Math.abs(dz) != 1) return null; // 不紧密相邻
        if (dx == 1) return BlockFace.EAST;
        if (dx == -1) return BlockFace.WEST;
        if (dz == 1) return BlockFace.SOUTH;
        if (dz == -1) return BlockFace.NORTH;
        return null;
    }

    /**
     * 与 {@link #getLineAxis} 相同，但允许两端之间存在最多 {@value MAX_COMPONENT_GAP} 个
     * 属于 {@code aMember} 或 {@code bMember} 的多方块附属方块。
     * 所有中间方块都必须被某一端的成员认领，否则返回 null。
     */
    @Nullable
    private static BlockFace getLineAxisWithGap(@NotNull Block a, @Nullable ProductionLineMember aMember,
                                                @NotNull Block b, @Nullable ProductionLineMember bMember) {
        BlockFace direct = getLineAxis(a, b);
        if (direct != null) return direct;

        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        if (dy != 0) return null;

        BlockFace face;
        int dist;
        if (dz == 0 && dx > 0)       { face = BlockFace.EAST;  dist = dx; }
        else if (dz == 0 && dx < 0)  { face = BlockFace.WEST;  dist = -dx; }
        else if (dx == 0 && dz > 0)  { face = BlockFace.SOUTH; dist = dz; }
        else if (dx == 0 && dz < 0)  { face = BlockFace.NORTH; dist = -dz; }
        else return null; // 斜向不支持

        if (dist < 2 || dist > MAX_COMPONENT_GAP + 1) return null;

        Set<Block> aOwned = aMember != null ? aMember.getOwnedMultiblockComponentBlocks() : Set.of();
        Set<Block> bOwned = bMember != null ? bMember.getOwnedMultiblockComponentBlocks() : Set.of();

        for (int i = 1; i < dist; i++) {
            Block mid = a.getRelative(face, i);
            if (!aOwned.contains(mid) && !bOwned.contains(mid)) return null;
        }
        return face;
    }

    /** 向玩家发送带颜色的产线系统提示消息（无占位符版）。 */
    private static void msg(@NotNull Player player, @NotNull NamedTextColor color,
                            @NotNull String key) {
        player.sendMessage(linePrefix().append(Component.translatable(key).color(color)));
    }

    /** 向玩家发送带颜色的产线系统提示消息（含命名占位符版）。 */
    private static void msg(@NotNull Player player, @NotNull NamedTextColor color,
                            @NotNull String key, @NotNull RebarArgument... args) {
        player.sendMessage(linePrefix().append(Component.translatable(key, args).color(color)));
    }

    /** 构造产线消息前缀：&8[&6蒸汽工坊&8] 。 */
    private static @NotNull Component linePrefix() {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("蒸汽工坊", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }
}
