package io.github.steamwork.util;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.steamwork.content.machines.PneumaticCargoHub;
import io.github.steamwork.content.machines.PneumaticInput;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.inventory.VirtualInventory;

/**
 * 汽动物流系统共用工具 —— 统一对原版容器和 Rebar 虚拟背包机器的物品推入逻辑。
 *
 * <p>设计上汽动网络只「推送」，不再做主动「抽取」（抽取语义已由分发器/分拣器自身承担）。
 * 本工具只暴露推入相关的 API + 漏斗反向拉取辅助。</p>
 */
public final class PneumaticUtils {

    private PneumaticUtils() {}

    // ── 推入（输入端操作）────────────────────────────────────────────────────────

    /**
     * 向目标方块的输入槽推入 1 个物品（克隆后推送，不修改传入的 item）。
     *
     * <p>支持：
     * <ul>
     *   <li>实现 {@link RebarVirtualInventoryBlock} 并暴露 {@code "input"} 槽的 Rebar 机器
     *       （汽动货运站走特殊路径，对应其 {@code "send"} 槽）</li>
     *   <li>原版容器（箱子、漏斗、投掷器等）</li>
     * </ul>
     * </p>
     *
     * @return 成功推入返回 {@code true}；目标不存在或空间不足返回 {@code false}
     */
    public static boolean tryPushItem(@NotNull Block block, @NotNull ItemStack item) {
        // 过滤检查：如果目标是汽动输入端，先校验黑/白名单
        if (BlockStorage.get(block) instanceof PneumaticInput input && !input.isAllowed(item)) return false;

        ItemStack single = item.clone().asQuantity(1);

        // 1. Rebar 虚拟背包机器（优先 —— 某些机器底块本身是 Dispenser/Dropper 等 Container）
        VirtualInventory vi = resolveInputInventory(block);
        if (vi != null) {
            // InvUI canHold 只检查类型约束，不检查容量，需手动判断
            if (!hasViSpace(vi, single)) return false;
            vi.addItem(new MachineUpdateReason(), single);
            return true;
        }

        // 2. 原版容器
        if (block.getState() instanceof org.bukkit.block.Container c) {
            return c.getInventory().addItem(single).isEmpty();
        }
        return false;
    }

    /**
     * 向目标方块的输入槽推入 {@code count} 个物品（实际推入数量可能更少）。
     *
     * @return 实际成功推入的数量
     */
    public static int tryPushItems(@NotNull Block block, @NotNull ItemStack item, int count) {
        // 过滤检查：如果目标是汽动输入端，先校验黑/白名单
        if (BlockStorage.get(block) instanceof PneumaticInput input && !input.isAllowed(item)) return 0;

        VirtualInventory vi = resolveInputInventory(block);
        if (vi != null) {
            int space = 0;
            for (ItemStack s : vi.getItems()) {
                if (s == null || s.getType().isAir()) space += item.getMaxStackSize();
                else if (s.isSimilar(item)) space += Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
            int toAdd = Math.min(count, space);
            if (toAdd <= 0) return 0;
            vi.addItem(new MachineUpdateReason(), item.clone().asQuantity(toAdd));
            return toAdd;
        }

        if (block.getState() instanceof org.bukkit.block.Container c) {
            ItemStack batch = item.clone();
            batch.setAmount(count);
            var leftovers = c.getInventory().addItem(batch);
            int leftoverCount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            return count - leftoverCount;
        }
        return 0;
    }

    /** 判断目标方块的输入槽是否有空间放入指定物品（至少 1 个）。 */
    public static boolean hasSpace(@NotNull Block block, @NotNull ItemStack item) {
        // 过滤检查：如果目标是汽动输入端，先校验黑/白名单
        if (BlockStorage.get(block) instanceof PneumaticInput input && !input.isAllowed(item)) return false;

        VirtualInventory vi = resolveInputInventory(block);
        if (vi != null) return hasViSpace(vi, item.clone().asQuantity(1));

        if (block.getState() instanceof org.bukkit.block.Container c) {
            for (ItemStack s : c.getInventory().getContents()) {
                if (s == null || s.getType().isAir()) return true;
                if (s.isSimilar(item) && s.getAmount() < s.getMaxStackSize()) return true;
            }
            return false;
        }
        return false;
    }

    /** 手动检查 VI 是否有空间放入 {@code item}（不依赖 InvUI canHold 的类型约束检查）。 */
    private static boolean hasViSpace(@NotNull VirtualInventory vi, @NotNull ItemStack item) {
        for (ItemStack s : vi.getItems()) {
            if (s == null || s.getType().isAir()) return true;
            if (s.isSimilar(item) && s.getAmount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /**
     * 判断方块是否是汽动系统可推入的有效目标。
     * <p>有效目标 = 原版容器 + 有 {@code "input"} VI 的 Rebar 机器（含 CargoHub 的 {@code "send"} 别名）。</p>
     */
    public static boolean isItemTarget(@NotNull Block block) {
        if (resolveInputInventory(block) != null) return true;
        return block.getState() instanceof org.bukkit.block.Container;
    }

    // ── 从原版容器抽取 ────────────────────────────────────────────────────────

    /**
     * 从原版容器或 Rebar 虚拟背包机器中抽取最多 {@code maxCount} 个物品到目标 VI。
     *
     * <p>支持：
     * <ul>
     *   <li>{@link PneumaticCargoHub} → 走其 {@code sendInventory}</li>
     *   <li>其他 {@link RebarVirtualInventoryBlock} → 走 {@code "output"} VI（若存在）</li>
     *   <li>原版容器（箱子、漏斗等）</li>
     * </ul>
     * </p>
     *
     * @return 实际抽取数量
     */
    public static int pullFromContainer(@NotNull Block source, @NotNull VirtualInventory target, int maxCount) {
        MachineUpdateReason reason = new MachineUpdateReason();

        // 1. Rebar 虚拟背包机器
        VirtualInventory sourceVi = resolveExtractInventory(source);
        if (sourceVi != null) {
            return pullFromVI(sourceVi, target, maxCount, reason);
        }

        // 2. 原版容器
        if (!(source.getState() instanceof org.bukkit.block.Container c)) return 0;
        org.bukkit.inventory.Inventory inv = c.getInventory();
        int pulled = 0;
        for (int i = 0; i < inv.getSize() && pulled < maxCount; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            int want = Math.min(s.getAmount(), maxCount - pulled);
            int space = 0;
            for (ItemStack vs : target.getItems()) {
                if (vs == null || vs.getType().isAir()) space += s.getMaxStackSize();
                else if (vs.isSimilar(s)) space += Math.max(0, s.getMaxStackSize() - vs.getAmount());
            }
            int toTake = Math.min(want, space);
            if (toTake <= 0) continue;
            target.addItem(reason, s.clone().asQuantity(toTake));
            if (toTake >= s.getAmount()) {
                inv.setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - toTake);
                inv.setItem(i, s);
            }
            pulled += toTake;
        }
        return pulled;
    }

    /**
     * 从虚拟背包中抽取物品到目标 VI（内部复用逻辑）。
     */
    private static int pullFromVI(@NotNull VirtualInventory source, @NotNull VirtualInventory target,
                                  int maxCount, @NotNull MachineUpdateReason reason) {
        ItemStack[] items = source.getItems();
        int pulled = 0;
        for (int i = 0; i < items.length && pulled < maxCount; i++) {
            ItemStack s = items[i];
            if (s == null || s.getType().isAir()) continue;
            int want = Math.min(s.getAmount(), maxCount - pulled);
            int space = 0;
            for (ItemStack ts : target.getItems()) {
                if (ts == null || ts.getType().isAir()) space += s.getMaxStackSize();
                else if (ts.isSimilar(s)) space += Math.max(0, s.getMaxStackSize() - ts.getAmount());
            }
            int toTake = Math.min(want, space);
            if (toTake <= 0) continue;
            target.addItem(reason, s.clone().asQuantity(toTake));
            if (toTake >= s.getAmount()) {
                source.setItem(reason, i, null);
            } else {
                ItemStack reduced = s.clone();
                reduced.setAmount(s.getAmount() - toTake);
                source.setItem(reason, i, reduced);
            }
            pulled += toTake;
        }
        return pulled;
    }

    // ── 漏斗交互 ─────────────────────────────────────────────────────────────

    /**
     * 让 Rebar 机器主动从相邻原版漏斗里拉取物品到自己的虚拟背包。
     *
     * <p>原版漏斗只能向 Bukkit {@link org.bukkit.block.Container} 推送，无法识别 Rebar 的
     * VirtualInventory；汽动货运站、汽动分发器、蒸汽分拣机的工作槽都是 VirtualInventory，
     * 因此需要由机器自己每 tick 反向拉取，模拟漏斗注入行为。</p>
     *
     * <p>规则：正上方漏斗始终拉取；其他面的漏斗只在朝向本方块时拉取。每次最多拉 1 个物品。</p>
     *
     * @return 实际拉取数量（0 或 1）
     */
    public static int pullFromAdjacentHoppers(@NotNull Block self, @NotNull VirtualInventory target) {
        for (BlockFace face : new BlockFace[] {
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN
        }) {
            Block neighbor = self.getRelative(face);
            if (!(neighbor.getState() instanceof Hopper hopperState)) continue;
            if (!(neighbor.getBlockData() instanceof org.bukkit.block.data.type.Hopper hopperData)) continue;
            // 漏斗朝向必须指向 self（上方漏斗 face == UP，opposite == DOWN，与 hopperData.getFacing() 匹配）
            if (hopperData.getFacing() != face.getOppositeFace()) continue;

            Inventory hopperInv = hopperState.getInventory();
            for (int i = 0; i < hopperInv.getSize(); i++) {
                ItemStack stack = hopperInv.getItem(i);
                if (stack == null || stack.getType().isAir()) continue;
                ItemStack single = stack.clone().asQuantity(1);
                if (!target.canHold(single)) continue;
                target.addItem(new MachineUpdateReason(), single);
                if (stack.getAmount() <= 1) {
                    hopperInv.setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    hopperInv.setItem(i, stack);
                }
                return 1;
            }
        }
        return 0;
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    /**
     * 从方块解析出用于接收物品的 {@link VirtualInventory}。
     *
     * <p>对汽动货运站走特例（{@code "send"} 槽），其他机器一律按 {@code "input"} 槽查找。</p>
     */
    private static @Nullable VirtualInventory resolveInputInventory(@NotNull Block block) {
        RebarBlock rb = BlockStorage.get(block);
        if (rb instanceof PneumaticCargoHub hub) return hub.getSendInventory();
        if (!(rb instanceof RebarVirtualInventoryBlock vib)) return null;
        return vib.getVirtualInventories().get("input");
    }

    /**
     * 从方块解析出用于抽取物品的 {@link VirtualInventory}（与 resolveInputInventory 对称）。
     *
     * <p>对汽动货运站走特例（{@code "send"} 槽），其他 Rebar 机器按 {@code "output"} 槽查找。</p>
     */
    private static @Nullable VirtualInventory resolveExtractInventory(@NotNull Block block) {
        RebarBlock rb = BlockStorage.get(block);
        if (rb instanceof PneumaticCargoHub hub) return hub.getSendInventory();
        if (!(rb instanceof RebarVirtualInventoryBlock vib)) return null;
        return vib.getVirtualInventories().get("output");
    }
}
