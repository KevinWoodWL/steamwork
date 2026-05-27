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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * A production-line member that decouples mixed outputs from the next machine.
 */
public class ProductionLineBufferChest extends RebarBlock implements
        RebarDirectionalBlock, RebarTickingBlock,
        RebarVirtualInventoryBlock, RebarInventoryBlock,
        RebarLogisticBlock, ProductionLineMember {

    private static final int MAX_GAP = 4;

    private static final NamespacedKey KEY_PUSH_MODE     = steamworkKey("buffer_push_mode");
    private static final NamespacedKey KEY_FUEL_TEMPLATE  = steamworkKey("buffer_fuel_template");

    /**
     * 推送模式。
     * <ul>
     *   <li>INGREDIENT — 全部送原料槽（默认）</li>
     *   <li>AUTO — 匹配燃料模板的物品送燃料槽，其余送原料槽</li>
     *   <li>FUEL — 全部送燃料槽</li>
     *   <li>OFF — 不推送（仅缓存）</li>
     * </ul>
     */
    enum PushMode { INGREDIENT, AUTO, FUEL, OFF }

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    private final VirtualInventory buffer = new VirtualInventory(27);

    @Nullable private UUID lineId = null;
    private int linePosition = 0;
    @NotNull private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String lineCreator = null;
    private int lineNumber = 0;
    private boolean lastPushSucceeded = false;
    @NotNull  private PushMode   pushMode     = PushMode.INGREDIENT;
    @Nullable private ItemStack  fuelTemplate = null;

    private final LineInfoItem    lineInfoItem    = new LineInfoItem();
    private final ModeToggleItem  modeToggleItem  = new ModeToggleItem();
    private final FuelFilterItem  fuelFilterItem  = new FuelFilterItem();

    @SuppressWarnings("unused")
    public ProductionLineBufferChest(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(5);
    }

    @SuppressWarnings("unused")
    public ProductionLineBufferChest(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
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
        String pm = pdc.get(KEY_PUSH_MODE, PersistentDataType.STRING);
        if (pm != null) {
            try { pushMode = PushMode.valueOf(pm); } catch (IllegalArgumentException ignored) {}
        }
        byte[] templateBytes = pdc.get(KEY_FUEL_TEMPLATE, PersistentDataType.BYTE_ARRAY);
        if (templateBytes != null) {
            try { fuelTemplate = ItemStack.deserializeBytes(templateBytes); } catch (Exception ignored) {}
        }
    }

    @Override
    public void postInitialise() {
        createLogisticGroup("buffer", LogisticGroupType.BOTH, buffer);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (lineId != null) {
            pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
            pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, lineDirection.name());
            pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, lineCreator);
            else pdc.remove(LINE_CREATOR_KEY);
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
        pdc.set(KEY_PUSH_MODE, PersistentDataType.STRING, pushMode.name());
        if (fuelTemplate != null && !fuelTemplate.isEmpty()) {
            pdc.set(KEY_FUEL_TEMPLATE, PersistentDataType.BYTE_ARRAY, fuelTemplate.serializeAsBytes());
        } else {
            pdc.remove(KEY_FUEL_TEMPLATE);
        }
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    @Override
    public void tick() {
        if (isInLine() && lineDirection != BlockFace.SELF) {
            pushAnyAcceptedItemToNext();
        }
        lineInfoItem.notifyWindows();
    }

    private void pushAnyAcceptedItemToNext() {
        if (pushMode == PushMode.OFF) {
            lastPushSucceeded = false;
            return;
        }
        ProductionLineMember next = findNextInLine();
        if (next == null) {
            lastPushSucceeded = false;
            return;
        }
        if (pushMode == PushMode.FUEL && !next.hasFuelSlot()) {
            lastPushSucceeded = false;
            return;
        }

        boolean hasTemplate = pushMode == PushMode.AUTO && fuelTemplate != null && !fuelTemplate.isEmpty();

        boolean hasItems = false;
        for (int i = 0; i < buffer.getSize(); i++) {
            ItemStack stack = buffer.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            hasItems = true;

            ItemStack single = stack.clone().asQuantity(1);
            boolean accepted;
            if (pushMode == PushMode.FUEL) {
                accepted = next.acceptFuelFromLine(single);
            } else if (pushMode == PushMode.AUTO && hasTemplate && single.isSimilar(fuelTemplate) && next.hasFuelSlot()) {
                // AUTO 模式：匹配模板 → 燃料槽；匹配失败则本格跳过（不送原料槽）
                accepted = next.acceptFuelFromLine(single);
            } else {
                accepted = next.acceptFromLine(single);
            }
            if (!accepted) continue;

            decrementBuffer(i, stack);
            lastPushSucceeded = true;
            return;
        }
        lastPushSucceeded = !hasItems;
    }

    private void decrementBuffer(int slot, @NotNull ItemStack current) {
        if (current.getAmount() <= 1) {
            buffer.setItem(new MachineUpdateReason(), slot, null);
        } else {
            ItemStack updated = current.clone();
            updated.setAmount(current.getAmount() - 1);
            buffer.setItem(new MachineUpdateReason(), slot, updated);
        }
    }

    @Nullable
    private ProductionLineMember findNextInLine() {
        for (int i = 1; i <= MAX_GAP; i++) {
            Block cursor = getBlock().getRelative(lineDirection, i);
            ProductionLineMember member = ProductionLineMember.of(cursor);
            if (member != null && lineId != null && lineId.equals(member.getLineId())) return member;
        }
        return null;
    }

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
        this.lastPushSucceeded = false;
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (!buffer.canHold(item)) return false;
        buffer.addItem(new MachineUpdateReason(), item);
        lineInfoItem.notifyWindows();
        return true;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        if (!isInLine()) {
            return new WailaDisplay(Component.translatable("steamwork.item.production_line_buffer_chest.waila_idle"));
        }
        String stateKey = hasBufferedItems()
                ? lastPushSucceeded ? "pushing" : "buffering"
                : "waiting";
        Component creatorComp = lineCreator != null
                ? Component.text(lineCreator)
                : Component.translatable("steamwork.line.unknown_creator");
        return new WailaDisplay(Component.translatable(
                "steamwork.item.production_line_buffer_chest.waila",
                RebarArgument.of("number", Component.text(lineNumber)),
                RebarArgument.of("state", Component.translatable("steamwork.line.state." + stateKey)),
                RebarArgument.of("creator", creatorComp)
        ));
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "b b b b b b b b b",
                        "b b b b b b b b b",
                        "b b b b b b b b b",
                        "# # # L M F # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('b', buffer)
                .addIngredient('L', lineInfoItem)
                .addIngredient('M', modeToggleItem)
                .addIngredient('F', fuelFilterItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.translatable("steamwork.gui.production_line_buffer_chest.title");
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("buffer", buffer);
    }

    private boolean hasBufferedItems() {
        for (int i = 0; i < buffer.getSize(); i++) {
            ItemStack stack = buffer.getItem(i);
            if (stack != null && !stack.isEmpty()) return true;
        }
        return false;
    }

    private final class LineInfoItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (!isInLine()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status.title")))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status.not_configured"))));
            }

            boolean hasItems = hasBufferedItems();
            Material mat;
            String stateKey;
            if (!hasItems) {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                stateKey = "waiting";
            } else if (lastPushSucceeded) {
                mat = Material.GREEN_STAINED_GLASS_PANE;
                stateKey = "pushing";
            } else {
                mat = Material.ORANGE_STAINED_GLASS_PANE;
                stateKey = "buffering";
            }

            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status.title")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status." + stateKey))));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }

    private final class FuelFilterItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (fuelTemplate != null && !fuelTemplate.isEmpty()) {
                return ItemStackBuilder.of(fuelTemplate.getType())
                        .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.title")))
                        .lore(List.of(
                                ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.active")),
                                ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.clear_hint"))
                        ));
            }
            return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.title")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.empty")),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.fuel_filter.set_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT) {
                if (fuelTemplate != null) {
                    fuelTemplate = null;
                    notifyWindows();
                    modeToggleItem.notifyWindows();
                }
            } else {
                ItemStack cursor = player.getItemOnCursor();
                if (!cursor.isEmpty()) {
                    fuelTemplate = cursor.asOne().clone();
                    notifyWindows();
                    modeToggleItem.notifyWindows();
                }
            }
        }
    }

    private final class ModeToggleItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = switch (pushMode) {
                case INGREDIENT -> Material.LIME_STAINED_GLASS_PANE;
                case AUTO       -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
                case FUEL       -> Material.ORANGE_STAINED_GLASS_PANE;
                case OFF        -> Material.GRAY_STAINED_GLASS_PANE;
            };
            String modeKey = pushMode.name().toLowerCase();
            List<Component> lore = pushMode == PushMode.AUTO
                    ? List.of(
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode." + modeKey)),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode.auto_hint")),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode.hint")))
                    : List.of(
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode." + modeKey)),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode.hint")));
            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.mode.title")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            pushMode = switch (pushMode) {
                case INGREDIENT -> PushMode.AUTO;
                case AUTO       -> PushMode.FUEL;
                case FUEL       -> PushMode.OFF;
                case OFF        -> PushMode.INGREDIENT;
            };
            notifyWindows();
        }
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
