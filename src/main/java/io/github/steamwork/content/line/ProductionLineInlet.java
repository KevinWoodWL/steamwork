package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.content.machines.upgrade.UpgradeableMachine;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.steamwork.util.PneumaticUtils;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 产线入口方块。
 *
 * <p>物品槽分为原料槽（7 格）和燃料槽（7 格）两组，分别向产线机器推送原料和燃料。</p>
 * <p>两组均支持气动网络（ingredient/fuel INPUT 组）或玩家直接放入。</p>
 * <p>燃料分发支持轮询（默认）和单目标两种模式，通过 GUI 内的扫描按钮和模式切换按钮控制。</p>
 */
public class ProductionLineInlet extends RebarBlock implements
        DirectionalRebarBlock, TickingRebarBlock,
        VirtualInventoryRebarBlock, GuiRebarBlock,
        LogisticRebarBlock, ProductionLineMember, UpgradeableMachine {

    enum FuelMode { ROUND_ROBIN, SINGLE_TARGET }

    // ===== PDC 键 =====

    private static final NamespacedKey KEY_FUEL_MODE       = steamworkKey("inlet_fuel_mode");
    private static final NamespacedKey KEY_FUEL_TARGETS    = steamworkKey("inlet_fuel_targets");
    private static final NamespacedKey KEY_FUEL_BINDINGS   = steamworkKey("inlet_fuel_bindings");
    /** 扫描时跳过梁的最大查找距离，与 ProductionLineListener.MAX_COMPONENT_GAP 对应。 */
    private static final int MAX_GAP = 4;

    // ===== Item =====

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    // ===== 缓冲 =====

    private final VirtualInventory ingredientBuffer = new VirtualInventory(7);
    private final VirtualInventory fuelBuffer       = new VirtualInventory(7);

    // ===== 产线成员字段 =====

    @Nullable private UUID      lineId        = null;
    private int                 linePosition  = 0;
    @NotNull private BlockFace  lineDirection = BlockFace.SELF;
    @Nullable private String    lineCreator   = null;
    private int                 lineNumber    = 0;

    // ===== 燃料分发字段 =====

    @NotNull private FuelMode    fuelMode        = FuelMode.ROUND_ROBIN;
    private final List<Block>    fuelTargetBlocks = new ArrayList<>();
    private final int[]          fuelSlotBindings = new int[7]; // -1 = 未绑定
    private int                  fuelRrIndex      = 0;

    private boolean lastPushSucceeded = false;
    /** 出口满导致产线停摆时为 true。 */
    private boolean outletJammed = false;
    /** 停摆后每 100 tick（5 秒）通知一次产线主人，此为计数器。 */
    private int jamNotifyCounter = 0;
    private static final int JAM_NOTIFY_INTERVAL = 100; // 5 tick/次 × 20 次 = 100 tick = 5 秒

    /** 缓存的产线出口引用，joinLine/leaveLine 时更新，避免每 tick 遍历整条产线。 */
    @Nullable private ProductionLineOutlet cachedOutlet = null;
    /** 缓存的产线内所有 ManualInteractMember，joinLine/leaveLine 时更新。 */
    @Nullable private List<ManualInteractMember> cachedManualMembers = null;

    // ===== GUI 元素 =====

    private final LineInfoItem         lineInfoItem        = new LineInfoItem();
    private final ScanButton           scanButton          = new ScanButton();
    private final ModeToggleItem       modeToggleItem      = new ModeToggleItem();
    private final MachineDisplayItem[] machineDisplayItems = new MachineDisplayItem[7];
    private final VirtualInventory     upgradeInventory    = new VirtualInventory(1);

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public ProductionLineInlet(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(5);
        Arrays.fill(fuelSlotBindings, -1);
        for (int i = 0; i < 7; i++) machineDisplayItems[i] = new MachineDisplayItem(i);
    }

    @SuppressWarnings("unused")
    public ProductionLineInlet(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(5);
        Arrays.fill(fuelSlotBindings, -1);
        for (int i = 0; i < 7; i++) machineDisplayItems[i] = new MachineDisplayItem(i);

        // ─ 产线信息 ─
        String id = pdc.get(LINE_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            try {
                lineId       = UUID.fromString(id);
                linePosition = pdc.getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
                String dir   = pdc.get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
                if (dir != null) {
                    try { lineDirection = BlockFace.valueOf(dir); }
                    catch (IllegalArgumentException ignored) {}
                }
                lineCreator = pdc.get(LINE_CREATOR_KEY, PersistentDataType.STRING);
                lineNumber = pdc.getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
            } catch (IllegalArgumentException ignored) {
                lineId = null;
            }
        }

        // ─ 燃料模式 ─
        int modeOrd = pdc.getOrDefault(KEY_FUEL_MODE, PersistentDataType.INTEGER, 0);
        fuelMode = (modeOrd == 1) ? FuelMode.SINGLE_TARGET : FuelMode.ROUND_ROBIN;

        // ─ 燃料目标方块 ─
        String targetsStr = pdc.get(KEY_FUEL_TARGETS, PersistentDataType.STRING);
        if (targetsStr != null && !targetsStr.isEmpty()) {
            for (String part : targetsStr.split(";")) {
                String[] xyz = part.split(",");
                if (xyz.length == 3) {
                    try {
                        fuelTargetBlocks.add(block.getWorld().getBlockAt(
                                Integer.parseInt(xyz[0]),
                                Integer.parseInt(xyz[1]),
                                Integer.parseInt(xyz[2])));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // ─ 燃料槽绑定 ─
        String bindStr = pdc.get(KEY_FUEL_BINDINGS, PersistentDataType.STRING);
        if (bindStr != null && !bindStr.isEmpty()) {
            String[] parts = bindStr.split(",");
            for (int i = 0; i < Math.min(7, parts.length); i++) {
                try { fuelSlotBindings[i] = Integer.parseInt(parts[i]); }
                catch (NumberFormatException ignored) {}
            }
        }

    }

    // ===== 生命周期 =====

    @Override
    public void postInitialise() {
        createLogisticGroup("ingredient", LogisticGroupType.INPUT, ingredientBuffer);
        createLogisticGroup("fuel",       LogisticGroupType.INPUT, fuelBuffer);
        upgradeInventory.addPreUpdateHandler(event -> {
            ItemStack newItem = event.getNewItem();
            if (newItem == null || newItem.isEmpty()) return;
            if (!(RebarItem.fromStack(newItem) instanceof AutoProductionModule)) {
                event.setCancelled(true);
            }
        });
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (lineId != null) {
            pdc.set(LINE_ID_KEY,        PersistentDataType.STRING,  lineId.toString());
            pdc.set(LINE_POSITION_KEY,  PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING,  lineDirection.name());
            pdc.set(LINE_NUMBER_KEY,    PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, lineCreator);
            else                     pdc.remove(LINE_CREATOR_KEY);
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
        pdc.set(KEY_FUEL_MODE, PersistentDataType.INTEGER, fuelMode.ordinal());
        if (!fuelTargetBlocks.isEmpty()) {
            pdc.set(KEY_FUEL_TARGETS, PersistentDataType.STRING,
                    fuelTargetBlocks.stream()
                            .map(b -> b.getX() + "," + b.getY() + "," + b.getZ())
                            .collect(Collectors.joining(";")));
        } else {
            pdc.remove(KEY_FUEL_TARGETS);
        }
        String bindStr = Arrays.stream(fuelSlotBindings)
                .mapToObj(String::valueOf).collect(Collectors.joining(","));
        pdc.set(KEY_FUEL_BINDINGS, PersistentDataType.STRING, bindStr);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== tick =====

    @Override
    public void tick() {
        // 从相邻漏斗拉取物品到原料槽
        PneumaticUtils.pullFromAdjacentHoppers(getBlock(), ingredientBuffer);

        if (!isInLine() || lineDirection == BlockFace.SELF) {
            lineInfoItem.notifyWindows();
            return;
        }

        // 出口满停摆：停止推送，每 JAM_NOTIFY_INTERVAL tick 通知产线主人
        if (outletJammed) {
            // 检查出口是否已经有空间，有则自动解除停摆
            if (!isOutletFull()) {
                outletJammed = false;
                jamNotifyCounter = 0;
                notifyAllMembers(false);
                lineInfoItem.notifyWindows();
                // 解除后继续正常 tick，不 return
            } else {
                // 持续通知所有成员保持停摆
                notifyAllMembers(true);
                jamNotifyCounter += 5;
                if (jamNotifyCounter >= JAM_NOTIFY_INTERVAL) {
                    jamNotifyCounter = 0;
                    notifyJammedOwner();
                }
                lineInfoItem.notifyWindows();
                return;
            }
        }

        // 推送一个原料物品
        for (int i = 0; i < 7; i++) {
            ItemStack item = ingredientBuffer.getItem(i);
            if (item == null || item.isEmpty()) continue;
            ProductionLineMember next = findNextInLine();
            if (next == null) { lastPushSucceeded = false; break; }
            ItemStack single = item.clone();
            single.setAmount(1);
            if (next.acceptFromLine(single)) {
                decrementBuffer(ingredientBuffer, i, item);
                lastPushSucceeded = true;
            } else {
                lastPushSucceeded = false;
                // 检查是否是出口满导致的阻塞
                if (isOutletFull()) {
                    outletJammed = true;
                    jamNotifyCounter = JAM_NOTIFY_INTERVAL; // 立即触发第一次通知
                    notifyAllMembers(true);
                }
            }
            break;
        }

        // 推送一个燃料物品
        if (!outletJammed) {
            for (int i = 0; i < 7; i++) {
                ItemStack fuel = fuelBuffer.getItem(i);
                if (fuel == null || fuel.isEmpty()) continue;
                ProductionLineMember target = resolveFuelTarget(i);
                if (target == null) continue;
                ItemStack single = fuel.clone();
                single.setAmount(1);
                if (target.acceptFuelFromLine(single)) {
                    decrementBuffer(fuelBuffer, i, fuel);
                }
                break;
            }
        }

        // 自动生产模组：对产线内需要手动触发的机器执行自动交互
        if (hasAutoProductionModule()) tickAutoInteract();

        lineInfoItem.notifyWindows();
    }

    private boolean hasAutoProductionModule() {
        ItemStack m = upgradeInventory.getItem(0);
        return m != null && !m.isEmpty() && RebarItem.isRebarItem(m, AutoProductionModule.class);
    }

    /**
     * 遍历产线内所有成员，对实现了 {@link ManualInteractMember} 的机器执行一次自动交互。
     * 使用缓存列表，避免每 tick 重新扫描整条产线。
     */
    private void tickAutoInteract() {
        if (!isInLine() || lineDirection == BlockFace.SELF || lineId == null) return;
        for (ManualInteractMember mim : getOrBuildCachedManualMembers()) {
            mim.performAutoInteract();
        }
    }

    private void decrementBuffer(@NotNull VirtualInventory inv, int slot, @NotNull ItemStack current) {
        if (current.getAmount() <= 1) {
            inv.setItem(new MachineUpdateReason(), slot, null);
        } else {
            ItemStack updated = current.clone();
            updated.setAmount(current.getAmount() - 1);
            inv.setItem(new MachineUpdateReason(), slot, updated);
        }
    }

    // ===== 产线导航 =====

    @Nullable
    private ProductionLineMember findNextInLine() {
        Block b = findNextMemberBlockFrom(getBlock());
        return b != null ? ProductionLineMember.of(b) : null;
    }

    @Nullable
    private Block findNextMemberBlockFrom(@NotNull Block from) {
        for (int i = 1; i <= MAX_GAP; i++) {
            Block cursor = from.getRelative(lineDirection, i);
            ProductionLineMember m = ProductionLineMember.of(cursor);
            if (m != null && lineId != null && lineId.equals(m.getLineId())) return cursor;
        }
        return null;
    }

    // ===== 燃料分发 =====

    @Nullable
    private ProductionLineMember resolveFuelTarget(int slotIndex) {
        if (fuelTargetBlocks.isEmpty()) return null;
        if (fuelMode == FuelMode.ROUND_ROBIN) {
            for (int attempt = 0; attempt < fuelTargetBlocks.size(); attempt++) {
                fuelRrIndex = (fuelRrIndex + 1) % fuelTargetBlocks.size();
                ProductionLineMember m = ProductionLineMember.of(fuelTargetBlocks.get(fuelRrIndex));
                if (m != null && m.hasFuelSlot()) return m;
            }
            return null;
        } else {
            int idx = fuelSlotBindings[slotIndex];
            if (idx < 0 || idx >= fuelTargetBlocks.size()) return null;
            ProductionLineMember m = ProductionLineMember.of(fuelTargetBlocks.get(idx));
            return (m != null && m.hasFuelSlot()) ? m : null;
        }
    }

    private void scanForFuelTargets() {
        if (!isInLine() || lineDirection == BlockFace.SELF) return;
        fuelTargetBlocks.clear();
        Arrays.fill(fuelSlotBindings, -1);
        fuelRrIndex = 0;

        Block cursor = getBlock();
        for (int step = 0; step < 64; step++) {
            Block nextBlock = findNextMemberBlockFrom(cursor);
            if (nextBlock == null) break;
            cursor = nextBlock;
            ProductionLineMember m = ProductionLineMember.of(cursor);
            if (m == null || !lineId.equals(m.getLineId())) break;
            if (m instanceof ProductionLineOutlet) break;
            if (m.hasFuelSlot()) {
                fuelTargetBlocks.add(cursor);
                if (fuelTargetBlocks.size() >= 7) break;
            }
        }

        for (MachineDisplayItem mdi : machineDisplayItems) mdi.notifyWindows();
        modeToggleItem.notifyWindows();
        scanButton.notifyWindows();
    }

    // ===== ProductionLineMember =====

    public @NotNull VirtualInventory getIngredientBuffer() { return ingredientBuffer; }
    public @NotNull VirtualInventory getFuelBuffer()       { return fuelBuffer; }

    @Override public @Nullable UUID getLineId()            { return lineId; }
    @Override public int getLinePosition()                 { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection() { return lineDirection; }
    @Override public @Nullable String getLineCreator()     { return lineCreator; }
    @Override public int getLineNumber()                   { return lineNumber; }

    @Override
    public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }

    @Override
    public void setLineNumber(int number) { this.lineNumber = number; }

    @Override
    public void joinLine(@NotNull UUID id, int position, @NotNull BlockFace direction) {
        this.lineId        = id;
        this.linePosition  = position;
        this.lineDirection = direction;
        // 缓存将在 postInitialise 或首次 tick 时延迟构建（此时产线其他成员可能还未 joinLine）
        this.cachedOutlet = null;
        this.cachedManualMembers = null;
    }

    @Override
    public void leaveLine() {
        this.lineId        = null;
        this.linePosition  = 0;
        this.lineDirection = BlockFace.SELF;
        this.lineCreator   = null;
        this.lineNumber    = 0;
        this.lastPushSucceeded = false;
        this.outletJammed = false;
        this.jamNotifyCounter = 0;
        this.cachedOutlet = null;
        this.cachedManualMembers = null;
        this.fuelTargetBlocks.clear();
        Arrays.fill(fuelSlotBindings, -1);
        this.fuelRrIndex = 0;
    }

    /**
     * 检查产线出口是否处于堵塞状态，委托给出口自身的连续拒绝计数。
     */
    private boolean isOutletFull() {
        ProductionLineOutlet outlet = getOrBuildCachedOutlet();
        return outlet != null && outlet.isJammed();
    }

    /** 懒加载并缓存产线出口引用。 */
    @Nullable
    private ProductionLineOutlet getOrBuildCachedOutlet() {
        if (cachedOutlet != null) return cachedOutlet;
        if (lineId == null || lineDirection == BlockFace.SELF) return null;
        Block cursor = getBlock();
        for (int step = 0; step < 64; step++) {
            Block next = findNextMemberBlockFrom(cursor);
            if (next == null) break;
            ProductionLineMember m = ProductionLineMember.of(next);
            if (m == null || !lineId.equals(m.getLineId())) break;
            if (m instanceof ProductionLineOutlet outlet) {
                cachedOutlet = outlet;
                return outlet;
            }
            cursor = next;
        }
        return null;
    }

    /** 懒加载并缓存产线内所有 ManualInteractMember。 */
    @NotNull
    private List<ManualInteractMember> getOrBuildCachedManualMembers() {
        if (cachedManualMembers != null) return cachedManualMembers;
        List<ManualInteractMember> list = new ArrayList<>();
        if (lineId == null || lineDirection == BlockFace.SELF) {
            cachedManualMembers = list;
            return list;
        }
        Block cursor = getBlock();
        for (int step = 0; step < 64; step++) {
            Block next = findNextMemberBlockFrom(cursor);
            if (next == null) break;
            ProductionLineMember m = ProductionLineMember.of(next);
            if (m == null || !lineId.equals(m.getLineId())) break;
            if (m instanceof ProductionLineOutlet) break;
            if (m instanceof ManualInteractMember mim) list.add(mim);
            cursor = next;
        }
        cachedManualMembers = list;
        return list;
    }

    /**
     * 遍历产线所有成员（含缓存箱、熔炉等），通知它们进入或退出停摆状态。
     * 复用 cachedManualMembers 以外，还需要遍历所有成员（包括非 ManualInteract 的）。
     */
    private void notifyAllMembers(boolean jammed) {
        if (lineId == null || lineDirection == BlockFace.SELF) return;
        Block cursor = getBlock();
        for (int step = 0; step < 64; step++) {
            Block next = findNextMemberBlockFrom(cursor);
            if (next == null) break;
            ProductionLineMember m = ProductionLineMember.of(next);
            if (m == null || !lineId.equals(m.getLineId())) break;
            if (jammed) m.onLineJammed(); else m.onLineResumed();
            if (m instanceof ProductionLineOutlet) break;
            cursor = next;
        }
    }

    /** 向产线主人发送堵塞通知。 */
    private void notifyJammedOwner() {
        if (lineCreator == null) return;
        org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayerExact(lineCreator);
        if (owner == null) return;
        ProductionLineListener.msg(owner, net.kyori.adventure.text.format.NamedTextColor.RED,
                "steamwork.line.jammed",
                io.github.pylonmc.rebar.i18n.RebarArgument.of("number",
                        net.kyori.adventure.text.Component.text(lineNumber)));
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        return false; // 入口是产线起点，不接受来自产线的推送
    }

    // ===== WAILA =====

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        if (!isInLine()) {
            return new WailaDisplay(Component.translatable("steamwork.item.production_line_inlet.waila_idle"));
        }
        boolean hasItems = false;
        for (int i = 0; i < 7; i++) {
            ItemStack s = ingredientBuffer.getItem(i);
            if (s != null && !s.isEmpty()) { hasItems = true; break; }
        }
        String stateKey = outletJammed ? "jammed"
                        : (hasItems && !lastPushSucceeded) ? "blocked"
                        : hasItems ? "pushing" : "waiting";
        Component creatorComp = lineCreator != null
                ? Component.text(lineCreator)
                : Component.translatable("steamwork.line.unknown_creator");
        return new WailaDisplay(Component.translatable(
                "steamwork.item.production_line_inlet.waila",
                RebarArgument.of("number", Component.text(lineNumber)),
                RebarArgument.of("state", Component.translatable("steamwork.line.state." + stateKey)),
                RebarArgument.of("creator", creatorComp)
        ));
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        var builder = Gui.builder()
                .setStructure(
                        "# # # # T # # # #",
                        "# i i i i i i i #",
                        "# f f f f f f f #",
                        "# 0 1 2 3 4 5 6 #",
                        "# # # L S M # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('T', new SlotHintItem())
                .addIngredient('i', ingredientBuffer)
                .addIngredient('f', fuelBuffer)
                .addIngredient('L', lineInfoItem)
                .addIngredient('S', scanButton)
                .addIngredient('M', modeToggleItem);
        for (int i = 0; i < 7; i++) {
            builder.addIngredient((char) ('0' + i), machineDisplayItems[i]);
        }
        return builder.build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.translatable("steamwork.gui.production_line_inlet.title");
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("ingredient", ingredientBuffer, "fuel", fuelBuffer, "upgrades", upgradeInventory);
    }

    // ===== 内部 GUI 元素 =====

    private final class LineInfoItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (!isInLine()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.production_line_inlet.line_status.title")))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.line_status.not_configured"))));
            }
            boolean hasItems = false;
            for (int i = 0; i < 7; i++) {
                ItemStack s = ingredientBuffer.getItem(i);
                if (s != null && !s.isEmpty()) { hasItems = true; break; }
            }
            Material mat;
            String stateKey;
            if (outletJammed) {
                mat = Material.RED_STAINED_GLASS_PANE; stateKey = "jammed";
            } else if (hasItems && !lastPushSucceeded) {
                mat = Material.ORANGE_STAINED_GLASS_PANE; stateKey = "blocked";
            } else if (hasItems) {
                mat = Material.GREEN_STAINED_GLASS_PANE; stateKey = "pushing";
            } else {
                mat = Material.YELLOW_STAINED_GLASS_PANE; stateKey = "waiting";
            }
            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_inlet.line_status.title")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.line_status." + stateKey))));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }

    private final class ScanButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (!isInLine()) {
                return ItemStackBuilder.of(Material.COMPASS)
                        .name(ni(Component.translatable("steamwork.gui.production_line_inlet.scan_button.title")))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.scan_button.not_in_line"))));
            }
            return ItemStackBuilder.of(Material.COMPASS)
                    .name(ni(Component.translatable("steamwork.gui.production_line_inlet.scan_button.title")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.scan_button.hint",
                            RebarArgument.of("count", Component.text(fuelTargetBlocks.size()))))));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (!isInLine()) return;
            scanForFuelTargets();
        }
    }

    private final class ModeToggleItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean isRR = (fuelMode == FuelMode.ROUND_ROBIN);
            return ItemStackBuilder.of(isRR ? Material.CLOCK : Material.CROSSBOW)
                    .name(ni(Component.translatable("steamwork.gui.production_line_inlet.mode_toggle.title")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.production_line_inlet.mode_toggle."
                                    + (isRR ? "round_robin" : "single_target"))),
                            ni(Component.translatable("steamwork.gui.production_line_inlet.mode_toggle.hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            fuelMode = (fuelMode == FuelMode.ROUND_ROBIN) ? FuelMode.SINGLE_TARGET : FuelMode.ROUND_ROBIN;
            Arrays.fill(fuelSlotBindings, -1);
            fuelRrIndex = 0;
            notifyWindows();
            for (MachineDisplayItem mdi : machineDisplayItems) mdi.notifyWindows();
        }
    }

    // ===== UpgradeableMachine =====

    @Override
    public int upgradeSlotCount() { return 1; }

    @Override
    public void openUpgradeGui(@NotNull Player player) {
        Window.builder()
                .setUpperGui(buildUpgradeGui())
                .setTitle(ni(Component.translatable("steamwork.gui.upgrade.title")))
                .setViewer(player)
                .build()
                .open();
    }

    private @NotNull Gui buildUpgradeGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # # u # # # #",
                        "# # # # c # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('u', upgradeInventory)
                .addIngredient('c', new CloseItem())
                .build();
    }

    private final class CloseItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BARRIER)
                    .name(ni(Component.translatable("steamwork.gui.upgrade.close")));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            player.closeInventory();
        }
    }

    private final class MachineDisplayItem extends AbstractItem {
        private final int index;

        MachineDisplayItem(int index) { this.index = index; }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (fuelTargetBlocks.isEmpty()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.no_scan")));
            }
            if (fuelMode == FuelMode.ROUND_ROBIN) {
                if (index >= fuelTargetBlocks.size()) {
                    return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                            .name(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.empty")));
                }
                Block b = fuelTargetBlocks.get(index);
                return ItemStackBuilder.of(b.getType())
                        .name(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.machine",
                                RebarArgument.of("n", Component.text(index + 1)),
                                RebarArgument.of("pos", Component.text("(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")")))));
            } else {
                int bound = fuelSlotBindings[index];
                if (bound < 0 || bound >= fuelTargetBlocks.size()) {
                    return ItemStackBuilder.of(Material.ORANGE_STAINED_GLASS_PANE)
                            .name(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.unbound")))
                            .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.click_cycle"))));
                }
                Block b = fuelTargetBlocks.get(bound);
                return ItemStackBuilder.of(b.getType())
                        .name(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.bound",
                                RebarArgument.of("n", Component.text(bound + 1)),
                                RebarArgument.of("pos", Component.text("(" + b.getX() + "," + b.getY() + "," + b.getZ() + ")")))))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_inlet.machine_slot.click_cycle"))));
            }
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (fuelMode != FuelMode.SINGLE_TARGET || fuelTargetBlocks.isEmpty()) return;
            int current = fuelSlotBindings[index];
            int next = current + 1;
            if (next >= fuelTargetBlocks.size()) next = -1; // 回绕到"未绑定"
            fuelSlotBindings[index] = next;
            notifyWindows();
        }
    }

    private final class SlotHintItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.PAPER)
                    .name(ni(Component.translatable("steamwork.gui.production_line_inlet.slot_hint.title")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.production_line_inlet.slot_hint.ingredient")),
                            ni(Component.translatable("steamwork.gui.production_line_inlet.slot_hint.fuel"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
