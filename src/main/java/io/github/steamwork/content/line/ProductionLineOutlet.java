package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
import io.github.pylonmc.rebar.block.base.RebarLogisticBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.steamwork.util.PneumaticUtils;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 产线出口方块。
 *
 * <p>作用：接收产线末台机器推来的产物（通过 {@link #acceptFromLine}），
 * 将产物缓存在内部 buffer，外部气动网络（逻辑组 OUTPUT）可从中拉取物品。</p>
 *
 * <p>玩家也可直接从 GUI 取走物品。</p>
 */
public class ProductionLineOutlet extends RebarBlock implements
        RebarDirectionalBlock, RebarTickingBlock,
        RebarVirtualInventoryBlock, RebarInventoryBlock,
        RebarLogisticBlock, ProductionLineMember {

    // ===== Item =====

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    // ===== 缓冲 =====

    private final VirtualInventory buffer = new VirtualInventory(7);

    // ===== 产线成员字段 =====

    @Nullable private UUID lineId = null;
    private int linePosition = 0;
    @NotNull private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String lineCreator = null;
    private int lineNumber = 0;

    private final LineInfoItem lineInfoItem = new LineInfoItem();

    /**
     * 连续拒绝来自上游推送的次数。
     * acceptFromLine 返回 false 时递增，返回 true 或 tick 中成功排出物品时归零。
     */
    private int consecutiveRejections = 0;
    private static final int JAM_THRESHOLD = 3;

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public ProductionLineOutlet(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(5);
    }

    @SuppressWarnings("unused")
    public ProductionLineOutlet(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(5);
        String id = pdc.get(LINE_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            try {
                lineId = UUID.fromString(id);
                linePosition = pdc.getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
                String dir = pdc.get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
                try {
                    lineDirection = dir != null ? BlockFace.valueOf(dir) : BlockFace.SELF;
                } catch (IllegalArgumentException ignored) {
                    lineDirection = BlockFace.SELF;
                }
                lineCreator = pdc.get(LINE_CREATOR_KEY, PersistentDataType.STRING);
                lineNumber = pdc.getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
            } catch (IllegalArgumentException ignored) {
                lineId = null;
            }
        }
    }

    // ===== 生命周期 =====

    @Override
    public void postInitialise() {
        createLogisticGroup("buffer", LogisticGroupType.OUTPUT, buffer);
        // 任何方式取走物品（玩家、漏斗、气动）都重置堵塞计数
        buffer.addPostUpdateHandler(event -> {
            ItemStack prev = event.getPreviousItem();
            ItemStack next = event.getNewItem();
            int prevAmt = (prev != null && !prev.isEmpty()) ? prev.getAmount() : 0;
            int nextAmt = (next != null && !next.isEmpty()) ? next.getAmount() : 0;
            if (nextAmt < prevAmt) {
                consecutiveRejections = 0;
            }
        });
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (lineId != null) {
            pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
            pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, lineDirection.name());
            pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) {
                pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, lineCreator);
            } else {
                pdc.remove(LINE_CREATOR_KEY);
            }
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    // ===== tick（出口无需主动推送，由逻辑组 OUTPUT 供外部拉取）=====

    @Override
    public void tick() {
        PneumaticUtils.pushToAdjacentHoppers(getBlock(), buffer);
        lineInfoItem.notifyWindows();
    }

    // ===== ProductionLineMember =====

    public @NotNull VirtualInventory getBuffer() { return buffer; }

    @Override public @Nullable UUID getLineId() { return lineId; }
    @Override public int getLinePosition() { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection() { return lineDirection; }
    @Override public @Nullable String getLineCreator() { return lineCreator; }
    @Override public int getLineNumber() { return lineNumber; }

    @Override
    public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }

    @Override
    public void setLineNumber(int number) { this.lineNumber = number; }

    @Override
    public void joinLine(@NotNull UUID id, int position, @NotNull BlockFace direction) {
        this.lineId = id;
        this.linePosition = position;
        this.lineDirection = direction;
    }

    @Override
    public void leaveLine() {
        this.lineId = null;
        this.linePosition = 0;
        this.lineDirection = BlockFace.SELF;
        this.lineCreator = null;
        this.lineNumber = 0;
        this.consecutiveRejections = 0;
    }

    /**
     * 接收来自产线上游的物品，放入出口缓冲。
     * 成功时重置连续拒绝计数，失败时递增。
     */
    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (!buffer.canHold(item)) {
            consecutiveRejections++;
            return false;
        }
        buffer.addItem(new MachineUpdateReason(), item);
        consecutiveRejections = 0;
        lineInfoItem.notifyWindows();
        return true;
    }

    /** 出口是否处于堵塞状态：连续拒绝次数达到阈值。 */
    public boolean isJammed() {
        return consecutiveRejections >= JAM_THRESHOLD;
    }

    /** 外部排出物品后（漏斗/气动取走）重置堵塞计数。 */
    public void resetJam() {
        consecutiveRejections = 0;
    }

    // ===== WAILA =====

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        if (!isInLine()) {
            return new WailaDisplay(Component.translatable("steamwork.item.production_line_outlet.waila_idle"));
        }
        boolean bufferHasItems = false;
        for (int i = 0; i < buffer.getSize(); i++) {
            ItemStack s = buffer.getItem(i);
            if (s != null && !s.isEmpty()) { bufferHasItems = true; break; }
        }
        String stateKey = bufferHasItems ? "receiving" : "waiting";
        Component creatorComp = lineCreator != null
                ? Component.text(lineCreator)
                : Component.translatable("steamwork.line.unknown_creator");
        return new WailaDisplay(Component.translatable(
                "steamwork.item.production_line_outlet.waila",
                RebarArgument.of("number", Component.text(lineNumber)),
                RebarArgument.of("state", Component.translatable("steamwork.line.state." + stateKey)),
                RebarArgument.of("creator", creatorComp)
        ));
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# b b b b b b b #",
                        "# # # # L # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('b', buffer)
                .addIngredient('L', lineInfoItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.translatable("steamwork.gui.production_line_outlet.title");
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("buffer", buffer);
    }

    // ===== LineInfoItem =====

    private final class LineInfoItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (!isInLine()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.production_line_outlet.line_status.title")))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_outlet.line_status.not_configured"))));
            }

            boolean bufferHasItems = false;
            for (int i = 0; i < buffer.getSize(); i++) {
                ItemStack s = buffer.getItem(i);
                if (s != null && !s.isEmpty()) { bufferHasItems = true; break; }
            }

            Material mat;
            String stateKey;
            if (bufferHasItems) {
                mat = Material.GREEN_STAINED_GLASS_PANE;
                stateKey = "receiving";
            } else {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                stateKey = "waiting";
            }

            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_outlet.line_status.title")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_outlet.line_status." + stateKey))));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
