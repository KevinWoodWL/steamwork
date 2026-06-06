package io.github.steamwork.content.line;

import io.github.pylonmc.pylon.content.machines.simple.Grindstone;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.RecipeProcessorRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleRebarMultiblock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.logistics.LogisticGroup;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import xyz.xenondevs.invui.inventory.VirtualInventory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 将 Pylon 机器（或任何含有输入 logistic 组 / input 虚拟库存的 Rebar 方块）包装为产线成员。
 *
 * <p>产线成员字段（line_id、line_position 等）存储在方块所在区块的 PDC 中，
 * 以方块坐标为区分键，格式为 {@code steamwork:plm_<x>_<y>_<z>}（TAG_CONTAINER）。</p>
 *
 * <p>支持的机器类型：</p>
 * <ul>
 *   <li>实现 {@link LogisticRebarBlock} 且有 INPUT / BOTH 类型逻辑组的 Pylon 机器（如磨石、粗合金熔炉）</li>
 *   <li>实现 {@link VirtualInventoryRebarBlock} 且有 "input" 前缀虚拟库存的 Pylon 机器（如成型台）</li>
 * </ul>
 *
 * <p>若机器同时实现 {@link RecipeProcessorRebarBlock} 且存在 {@code tryStartRecipe()} 或
 * {@code getNextRecipe()} + {@code tryStartRecipe(recipe)} 方法，则自动触发配方处理；
 * 否则物品推入后等待玩家手动触发。</p>
 */
class PylonMachineMember implements ProductionLineMember, ManualInteractMember {

    private final @NotNull Block block;
    private final @NotNull RebarBlock rebarBlock;

    PylonMachineMember(@NotNull Block block, @NotNull RebarBlock rebarBlock) {
        this.block = block;
        this.rebarBlock = rebarBlock;
    }

    // ===== 检测工具 =====

    /**
     * 判断某个 Rebar 方块是否可以被包装为产线成员。
     * 条件：pylon 命名空间 + 至少有一个 INPUT/BOTH 逻辑组，或含 "input" 前缀的虚拟库存。
     */
    static boolean isPylonMachine(@NotNull RebarBlock rb) {
        if (!"pylon".equals(rb.getKey().getNamespace())) return false;
        if (rb instanceof LogisticRebarBlock logistic) {
            for (LogisticGroup g : logistic.getLogisticGroups().values()) {
                LogisticGroupType t = g.getSlotType();
                if (t == LogisticGroupType.INPUT || t == LogisticGroupType.BOTH) return true;
            }
        }
        if (rb instanceof VirtualInventoryRebarBlock vib) {
            for (String name : vib.getVirtualInventories().keySet()) {
                if (name.startsWith("input")) return true;
            }
        }
        return false;
    }

    // ===== 区块 PDC 存储 =====

    /** 生成当前方块在区块 PDC 中使用的键，格式：steamwork:plm_x_y_z */
    private @NotNull NamespacedKey posKey() {
        // NamespacedKey 的 key 部分允许 [a-z0-9/._-]，负号和数字均合法
        return new NamespacedKey("steamwork",
                "plm_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    /** 读取（或创建空的）产线数据子容器。 */
    private @NotNull PersistentDataContainer readContainer() {
        PersistentDataContainer chunkPdc = block.getChunk().getPersistentDataContainer();
        PersistentDataContainer existing = chunkPdc.get(posKey(), PersistentDataType.TAG_CONTAINER);
        return existing != null ? existing : chunkPdc.getAdapterContext().newPersistentDataContainer();
    }

    /** 将修改后的子容器写回区块 PDC。 */
    private void writeContainer(@NotNull PersistentDataContainer container) {
        block.getChunk().getPersistentDataContainer().set(posKey(), PersistentDataType.TAG_CONTAINER, container);
    }

    /** 从区块 PDC 删除子容器（leaveLine 时调用）。 */
    private void removeContainer() {
        block.getChunk().getPersistentDataContainer().remove(posKey());
    }

    // ===== ProductionLineMember 读取 =====

    @Override
    public @Nullable UUID getLineId() {
        String s = readContainer().get(LINE_ID_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    @Override
    public int getLinePosition() {
        return readContainer().getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public @NotNull BlockFace getLineDirection() {
        String s = readContainer().get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
        if (s == null) return BlockFace.SELF;
        try { return BlockFace.valueOf(s); } catch (IllegalArgumentException e) { return BlockFace.SELF; }
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

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        // 优先：LogisticRebarBlock 的 INPUT / BOTH 逻辑组
        if (rebarBlock instanceof LogisticRebarBlock logistic) {
            for (LogisticGroup group : logistic.getLogisticGroups().values()) {
                LogisticGroupType type = group.getSlotType();
                if (type != LogisticGroupType.INPUT && type != LogisticGroupType.BOTH) continue;
                if (!checkGroupFilter(group, item)) continue;
                if (pushToLogisticGroup(group, item)) return true;
            }
        }
        // 回退：VirtualInventoryRebarBlock 中名称以 "input" 开头的虚拟库存
        if (rebarBlock instanceof VirtualInventoryRebarBlock vib) {
            for (Map.Entry<String, VirtualInventory> e : vib.getVirtualInventories().entrySet()) {
                if (!e.getKey().startsWith("input")) continue;
                if (pushToVirtualInventory(e.getValue(), item)) return true;
            }
        }
        return false;
    }

    /**
     * 通过反射调用 Kotlin 闭包过滤器（{@code LogisticGroup.filter}）。
     * 若调用失败或无 filter，则默认放行。
     */
    private static boolean checkGroupFilter(@NotNull LogisticGroup group, @NotNull ItemStack item) {
        Object filter = group.getFilter();
        if (filter == null) return true;
        try {
            for (Method m : filter.getClass().getMethods()) {
                if ("invoke".equals(m.getName()) && m.getParameterCount() == 1) {
                    Object result = m.invoke(filter, item);
                    return Boolean.TRUE.equals(result);
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static boolean pushToLogisticGroup(@NotNull LogisticGroup group, @NotNull ItemStack item) {
        for (LogisticSlot slot : group.getSlots()) {
            ItemStack current = slot.getItemStack();
            if (current == null || current.isEmpty()) {
                slot.set(item, 1);
                return true;
            }
            long maxAmount = slot.getMaxAmount(item);
            if (current.isSimilar(item) && slot.getAmount() < maxAmount) {
                slot.set(current, slot.getAmount() + 1);
                return true;
            }
        }
        return false;
    }

    private static boolean pushToVirtualInventory(@NotNull VirtualInventory inv, @NotNull ItemStack item) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.isEmpty()) {
                inv.setItem(new MachineUpdateReason(), i, item.clone());
                return true;
            }
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                slot.setAmount(slot.getAmount() + 1);
                inv.setItem(new MachineUpdateReason(), i, slot);
                return true;
            }
        }
        return false;
    }

    // ===== 自动触发配方（ManualInteractMember）=====

    /**
     * 通过反射触发 Pylon 机器开始处理配方。
     *
     * <p>优先尝试无参 {@code tryStartRecipe()}（Kiln、CrudeAlloyFurnace、Press 等），
     * 若无则尝试 {@code getNextRecipe()} + {@code tryStartRecipe(recipe)}（Grindstone）。</p>
     *
     * <p>仅对实现了 {@link RecipeProcessorRebarBlock} 的机器生效；
     * 若机器不实现该接口（如成型台），物品推入后等待玩家手动操作。</p>
     */
    @Override
    public void performAutoInteract() {
        if (!(rebarBlock instanceof RecipeProcessorRebarBlock<?>)) return;
        try {
            // 1. 无参 tryStartRecipe()（Kiln / CrudeAlloyFurnace / Press 等模式）
            Method noArg = findMethodByName(rebarBlock.getClass(), "tryStartRecipe", 0);
            if (noArg != null) {
                noArg.invoke(rebarBlock);
                return;
            }
            // 2. getNextRecipe() + tryStartRecipe(recipe)（Grindstone 模式）
            Method getNext = findMethodByName(rebarBlock.getClass(), "getNextRecipe", 0);
            if (getNext == null) return;
            Object recipe = getNext.invoke(rebarBlock);
            if (recipe == null) return;
            Method tryStart = findMethodByName(rebarBlock.getClass(), "tryStartRecipe", 1);
            if (tryStart != null) {
                Object started = tryStart.invoke(rebarBlock, recipe);
                if (Boolean.TRUE.equals(started) && rebarBlock instanceof Grindstone) {
                    PylonLineOutputBridge.expectOutput(block, recipe);
                }
            }
        } catch (Exception ignored) {
            // 配方触发失败；在下一 tick 重试
        }
    }

    @Nullable
    private static Method findMethodByName(@NotNull Class<?> clazz, @NotNull String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        return null;
    }

    // ===== 多方块附属方块（供产线扫描器跳过） =====

    /**
     * 返回本 Pylon 多方块机器所拥有的附属方块集合（如磨石手柄、窑的外壁等）。
     * 产线扫描器遇到这些方块时会跳过，不要求它们是 ProductionLineMember。
     */
    @Override
    public @NotNull Set<Block> getOwnedMultiblockComponentBlocks() {
        if (!(rebarBlock instanceof SimpleRebarMultiblock multiblock)) return Set.of();
        if (!multiblock.isFormedAndFullyLoaded()) return Set.of();
        Set<Block> result = new HashSet<>();
        for (Vector3i pos : multiblock.getComponents().keySet()) {
            result.add(multiblock.getMultiblockBlock(pos));
        }
        return result;
    }
}
