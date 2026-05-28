package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.steamwork.content.machines.SteamPress;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * 将蒸汽冲压机结构中的铁块包装为产线成员。
 *
 * <p>蒸汽冲压机多方块结构：
 * <pre>
 *  [冲压机]  ← Y+2（RebarBlock SteamPress 本体）
 *  [空气]    ← Y+1
 *  [铁块]    ← Y+0（本类所代表的方块，产线蓝图的操作对象）
 * </pre>
 *
 * <p>产线成员字段存储在铁块所在区块的 PDC 中，键格式：{@code steamwork:plm_x_y_z}。
 * 冲压机本体（Y+2）作为多方块附属方块，由 {@link #getOwnedMultiblockComponentBlocks()} 返回，
 * 产线扫描器会跳过它，不要求其实现 ProductionLineMember。</p>
 */
public class SteamPressMember implements ProductionLineMember {

    private final @NotNull Block ironBlock;

    SteamPressMember(@NotNull Block ironBlock) {
        this.ironBlock = ironBlock;
    }

    // ===== 检测 =====

    /**
     * 检测铁块是否是蒸汽冲压机多方块结构的底座：
     * 铁块正上方两格有 SteamPress 本体，且结构有效（中间为空气）。
     */
    static boolean isSteamPressBase(@NotNull Block block) {
        if (block.getType() != Material.IRON_BLOCK) return false;
        Block above2 = block.getRelative(BlockFace.UP).getRelative(BlockFace.UP);
        if (!(BlockStorage.get(above2) instanceof SteamPress press)) return false;
        return press.hasValidStructure();
    }

    /** 从铁块获取上方的 SteamPress 实例，若不存在返回 null。 */
    private @Nullable SteamPress getPress() {
        Block above2 = ironBlock.getRelative(BlockFace.UP).getRelative(BlockFace.UP);
        Object rb = BlockStorage.get(above2);
        return rb instanceof SteamPress press ? press : null;
    }

    // ===== 区块 PDC 存储 =====

    private @NotNull NamespacedKey posKey() {
        return new NamespacedKey("steamwork",
                "plm_" + ironBlock.getX() + "_" + ironBlock.getY() + "_" + ironBlock.getZ());
    }

    private @NotNull PersistentDataContainer readContainer() {
        PersistentDataContainer chunkPdc = ironBlock.getChunk().getPersistentDataContainer();
        PersistentDataContainer existing = chunkPdc.get(posKey(), PersistentDataType.TAG_CONTAINER);
        return existing != null ? existing : chunkPdc.getAdapterContext().newPersistentDataContainer();
    }

    private void writeContainer(@NotNull PersistentDataContainer container) {
        ironBlock.getChunk().getPersistentDataContainer()
                .set(posKey(), PersistentDataType.TAG_CONTAINER, container);
    }

    private void removeContainer() {
        ironBlock.getChunk().getPersistentDataContainer().remove(posKey());
    }

    // ===== ProductionLineMember 读取 =====

    @Override
    public @Nullable UUID getLineId() {
        String s = readContainer().get(LINE_ID_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public int getLinePosition() {
        return readContainer().getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public @NotNull BlockFace getLineDirection() {
        String s = readContainer().get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
        if (s == null) return BlockFace.SELF;
        try { return BlockFace.valueOf(s); } catch (IllegalArgumentException ignored) { return BlockFace.SELF; }
    }

    @Override
    public @Nullable String getLineCreator() {
        return readContainer().get(LINE_CREATOR_KEY, PersistentDataType.STRING);
    }

    @Override
    public int getLineNumber() {
        return readContainer().getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
    }

    // ===== ProductionLineMember 写入 =====

    @Override
    public void joinLine(@NotNull UUID lineId, int position, @NotNull BlockFace direction) {
        PersistentDataContainer c = readContainer();
        c.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
        c.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, position);
        c.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, direction.name());
        writeContainer(c);
    }

    @Override
    public void setLineCreator(@Nullable String creator) {
        PersistentDataContainer c = readContainer();
        if (creator != null) c.set(LINE_CREATOR_KEY, PersistentDataType.STRING, creator);
        else c.remove(LINE_CREATOR_KEY);
        writeContainer(c);
    }

    @Override
    public void setLineNumber(int number) {
        PersistentDataContainer c = readContainer();
        if (number > 0) c.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, number);
        else c.remove(LINE_NUMBER_KEY);
        writeContainer(c);
    }

    @Override
    public void leaveLine() {
        removeContainer();
    }

    // ===== 物品推送 =====

    /**
     * 接受产线上游推入的物品，委托给 SteamPress.insertInputItem。
     * 若冲压机结构不完整或正在处理，则拒绝。
     */
    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        SteamPress press = getPress();
        if (press == null) return false;
        return press.insertInputItem(item);
    }

    // ===== 多方块附属方块 =====

    /**
     * 返回冲压机本体方块（铁块上方两格），供产线扫描器跳过。
     * 中间的空气格不需要返回（ProductionLineMember.of 对空气不会返回成员，扫描器会自然跳过）。
     */
    @Override
    public @NotNull Set<Block> getOwnedMultiblockComponentBlocks() {
        SteamPress press = getPress();
        if (press == null) return Set.of();
        // 空气格（Y+1）和冲压机本体（Y+2）都视为附属方块
        Block air = ironBlock.getRelative(BlockFace.UP);
        Block machine = ironBlock.getRelative(BlockFace.UP).getRelative(BlockFace.UP);
        return Set.of(air, machine);
    }

    /**
     * 若 block 是蒸汽冲压机本体（SteamPress），返回其对应的铁块 SteamPressMember；否则返回 null。
     * 用于 onBlockBreak 中，当冲压机本体被破坏时找到产线成员并解散。
     */
    @Nullable
    static SteamPressMember fromPressBlock(@NotNull Block block) {
        if (!(BlockStorage.get(block) instanceof SteamPress press)) return null;
        Block ironBlock = press.getIronBlockPos();
        SteamPressMember member = new SteamPressMember(ironBlock);
        return member.isInLine() ? member : null;
    }

    // ===== 静态工具：产物推送 =====

    /**
     * 由 {@link SteamPress#completeRecipe} 调用：将产物尝试推送给产线下游。
     * 若铁块不在产线中，或推送后仍有剩余，返回剩余的 ItemStack（由调用方决定如何处理）。
     *
     * @param ironBlock 冲压机结构的铁块方块
     * @param produced  配方产物（amount >= 1）
     * @return 未能推送的剩余物品（可能 isEmpty()）
     */
    @NotNull
    public static ItemStack tryDeliverOutput(@NotNull Block ironBlock, @NotNull ItemStack produced) {
        SteamPressMember member = new SteamPressMember(ironBlock);
        UUID lineId = member.getLineId();
        BlockFace direction = member.getLineDirection();
        if (lineId == null || direction == BlockFace.SELF) return produced;

        ItemStack remaining = produced.clone();
        ProductionLineMember downstream = findNextMember(ironBlock, direction, lineId);
        if (downstream == null) return remaining;

        int delivered = 0;
        while (delivered < remaining.getAmount()) {
            if (!downstream.acceptFromLine(remaining.asQuantity(1))) break;
            delivered++;
        }
        remaining.setAmount(remaining.getAmount() - delivered);
        return remaining;
    }

    private static @Nullable ProductionLineMember findNextMember(
            @NotNull Block source,
            @NotNull BlockFace direction,
            @NotNull UUID lineId
    ) {
        for (int i = 1; i <= DISBAND_MAX_GAP + 1; i++) {
            ProductionLineMember member = ProductionLineMember.of(source.getRelative(direction, i));
            if (member == null) continue;
            return lineId.equals(member.getLineId()) ? member : null;
        }
        return null;
    }
}
