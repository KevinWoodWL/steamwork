package io.github.steamwork.content.line;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 将原版 FURNACE / BLAST_FURNACE / SMOKER 包装为产线成员。
 *
 * <p>无状态：所有产线成员字段通过方块 TileEntity 的 PDC 读写，实例不持有可变字段。</p>
 *
 * <p>由 {@link ProductionLineMember#of(Block)} 在 Rebar 方块检测失败后自动创建。</p>
 */
class VanillaFurnaceMember implements ProductionLineMember {

    private final @NotNull Block block;

    VanillaFurnaceMember(@NotNull Block block) {
        this.block = block;
    }

    // ===== 类型检测 =====

    /** 判断方块是否是原版熔炉 / 高炉 / 烟熏炉。 */
    public static boolean isVanillaFurnace(@NotNull Block block) {
        Material type = block.getType();
        return type == Material.FURNACE
                || type == Material.BLAST_FURNACE
                || type == Material.SMOKER;
    }

    // ===== PDC 辅助（仅读取使用，写入方法内联 state.update()）=====

    private @NotNull PersistentDataContainer readPdc() {
        return ((Container) block.getState()).getPersistentDataContainer();
    }

    // ===== ProductionLineMember 读取 =====

    @Override
    public @Nullable UUID getLineId() {
        String s = readPdc().get(LINE_ID_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public int getLinePosition() {
        return readPdc().getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public @NotNull BlockFace getLineDirection() {
        String s = readPdc().get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
        if (s == null) return BlockFace.SELF;
        try { return BlockFace.valueOf(s); } catch (IllegalArgumentException ignored) { return BlockFace.SELF; }
    }

    @Override
    public @Nullable String getLineCreator() {
        return readPdc().get(LINE_CREATOR_KEY, PersistentDataType.STRING);
    }

    @Override
    public int getLineNumber() {
        return readPdc().getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
    }

    // ===== ProductionLineMember 写入 =====

    @Override
    public void joinLine(@NotNull UUID lineId, int position, @NotNull BlockFace direction) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
        pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, position);
        pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, direction.name());
        state.update(true, false);
    }

    @Override
    public void setLineCreator(@Nullable String creator) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        if (creator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, creator);
        else pdc.remove(LINE_CREATOR_KEY);
        state.update(true, false);
    }

    @Override
    public void setLineNumber(int number) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        if (number > 0) pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, number);
        else pdc.remove(LINE_NUMBER_KEY);
        state.update(true, false);
    }

    @Override
    public void leaveLine() {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        pdc.remove(LINE_ID_KEY);
        pdc.remove(LINE_POSITION_KEY);
        pdc.remove(LINE_DIRECTION_KEY);
        pdc.remove(LINE_CREATOR_KEY);
        pdc.remove(LINE_NUMBER_KEY);
        state.update(true, false);
    }

    // ===== 燃料槽支持 =====

    @Override
    public boolean hasFuelSlot() { return true; }

    private static final org.bukkit.NamespacedKey LINE_JAMMED_KEY =
            new org.bukkit.NamespacedKey("steamwork", "line_jammed");

    /** 堵塞时在 PDC 写入标志，供 FurnaceSmeltEvent/FurnaceBurnEvent 拦截使用。 */
    @Override
    public void onLineJammed() {
        var state = block.getState();
        ((Container) state).getPersistentDataContainer()
                .set(LINE_JAMMED_KEY, PersistentDataType.BYTE, (byte) 1);
        // 立即冻结 cookTime，阻止当前进度继续推进
        if (state instanceof Furnace furnace) {
            furnace.setCookTime((short) 0);
        }
        state.update(true, false);
    }

    @Override
    public void onLineResumed() {
        var state = block.getState();
        ((Container) state).getPersistentDataContainer().remove(LINE_JAMMED_KEY);
        state.update(true, false);
        // 解除停摆后循环推送产物槽，直到清空或下游满
        org.bukkit.Bukkit.getScheduler().runTask(
                io.github.steamwork.Steamwork.getInstance(),
                this::drainResultSlot);
    }

    /** 循环推送产物槽中所有物品给下游，直到清空或下游拒绝。 */
    private void drainResultSlot() {
        if (!isInLine()) return;
        BlockFace dir = getLineDirection();
        if (dir == BlockFace.SELF) return;
        UUID myLineId = getLineId();
        if (myLineId == null) return;
        if (!(block.getState() instanceof Furnace furnace)) return;
        FurnaceInventory inv = furnace.getInventory();

        // 找下游成员
        ProductionLineMember downstream = null;
        for (int i = 1; i <= DISBAND_MAX_GAP + 1; i++) {
            ProductionLineMember candidate = ProductionLineMember.of(block.getRelative(dir, i));
            if (candidate == null) continue;
            if (!myLineId.equals(candidate.getLineId())) return;
            downstream = candidate;
            break;
        }
        if (downstream == null) return;

        // 循环推送直到产物槽清空或下游拒绝
        while (true) {
            ItemStack result = inv.getResult();
            if (result == null || result.getType() == Material.AIR) break;
            ItemStack single = result.clone();
            single.setAmount(1);
            if (!ProductionLineMember.acceptIntoLine(downstream, single)) break;
            if (result.getAmount() <= 1) {
                inv.setResult(null);
            } else {
                result.setAmount(result.getAmount() - 1);
                inv.setResult(result);
            }
        }
    }

    /** 检查熔炉是否处于产线停摆状态。 */
    static boolean isLineJammed(@NotNull Block block) {
        if (!(block.getState() instanceof Container container)) return false;
        return container.getPersistentDataContainer().has(LINE_JAMMED_KEY, PersistentDataType.BYTE);
    }

    // ===== 物品推送 =====

    /** 将 1 个物品放入熔炉的原料槽（slot 0）。 */
    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        return pushToFurnaceSlot(item, false);
    }

    /** 将 1 个物品放入熔炉的燃料槽（slot 1）。 */
    @Override
    public boolean acceptFuelFromLine(@NotNull ItemStack item) {
        return pushToFurnaceSlot(item, true);
    }

    /** 将产物槽（slot 2）中的一个物品推送给产线下游成员。由 FurnaceSmeltEvent 延迟调用。 */
    void tryPushResultDownstream() {
        if (!isInLine()) return;
        BlockFace dir = getLineDirection();
        if (dir == BlockFace.SELF) return;
        UUID myLineId = getLineId();
        if (myLineId == null) return;
        if (!(block.getState() instanceof Furnace furnace)) return;
        FurnaceInventory inv = furnace.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType() == Material.AIR) return;
        for (int i = 1; i <= DISBAND_MAX_GAP + 1; i++) {
            Block cursor = block.getRelative(dir, i);
            ProductionLineMember downstream = ProductionLineMember.of(cursor);
            if (downstream == null) continue;
            if (!myLineId.equals(downstream.getLineId())) return;
            ItemStack single = result.clone();
            single.setAmount(1);
            if (ProductionLineMember.acceptIntoLine(downstream, single)) {
                if (result.getAmount() <= 1) inv.setResult(null);
                else { result.setAmount(result.getAmount() - 1); inv.setResult(result); }
            }
            return;
        }
    }

    private boolean pushToFurnaceSlot(@NotNull ItemStack item, boolean fuelSlot) {
        if (!(block.getState() instanceof Furnace furnace)) return false;
        FurnaceInventory inv = furnace.getInventory();
        ItemStack current = fuelSlot ? inv.getFuel() : inv.getSmelting();
        if (current == null || current.getType() == Material.AIR) {
            ItemStack toSet = item.clone();
            toSet.setAmount(1);
            if (fuelSlot) inv.setFuel(toSet); else inv.setSmelting(toSet);
            return true;
        }
        if (current.isSimilar(item) && current.getAmount() < current.getMaxStackSize()) {
            current.setAmount(current.getAmount() + 1);
            if (fuelSlot) inv.setFuel(current); else inv.setSmelting(current);
            return true;
        }
        return false;
    }
}
