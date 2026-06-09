package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
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
import xyz.xenondevs.invui.window.Window;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * A production-line member that decouples mixed outputs from the next machine.
 */
public class ProductionLineBufferChest extends RebarBlock implements
        DirectionalRebarBlock, TickingRebarBlock,
        VirtualInventoryRebarBlock, GuiRebarBlock,
        LogisticRebarBlock, ProductionLineMember {

    private static final int MAX_GAP = 4;

    private static final NamespacedKey KEY_PUSH_MODE      = steamworkKey("buffer_push_mode");
    private static final NamespacedKey KEY_FUEL_TEMPLATE  = steamworkKey("buffer_fuel_template");
    private static final NamespacedKey KEY_FILTER_MODE    = steamworkKey("buffer_filter_mode");
    private static final NamespacedKey KEY_BL_BEHAVIOR    = steamworkKey("buffer_blacklist_behavior");
    private static final NamespacedKey KEY_FILTER_LIST    = steamworkKey("buffer_filter_list");

    enum PushMode { INGREDIENT, AUTO, FUEL, OFF }

    /** OFF = 不过滤；WHITELIST = 仅放行名单内；BLACKLIST = 拦截名单内。 */
    enum FilterMode { OFF, WHITELIST, BLACKLIST }

    /** RETAIN = 黑名单物品留在缓存箱；EJECT = 直接传送到产线出口。 */
    enum BlacklistBehavior { RETAIN, EJECT }

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
    }

    private final VirtualInventory buffer     = new VirtualInventory(27);
    private final VirtualInventory filterList = new VirtualInventory(27);

    @Nullable private UUID      lineId    = null;
    private int                 linePosition  = 0;
    @NotNull  private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String    lineCreator   = null;
    private int                 lineNumber    = 0;
    private boolean             lastPushSucceeded = false;
    private boolean             lineJammed        = false;

    @NotNull  private PushMode          pushMode          = PushMode.INGREDIENT;
    @Nullable private ItemStack         fuelTemplate      = null;
    @NotNull  private FilterMode        filterMode        = FilterMode.OFF;
    @NotNull  private BlacklistBehavior blacklistBehavior = BlacklistBehavior.RETAIN;

    private final LineInfoItem          lineInfoItem          = new LineInfoItem();
    private final ModeToggleItem        modeToggleItem        = new ModeToggleItem();
    private final FuelFilterItem        fuelFilterItem        = new FuelFilterItem();
    private final FilterModeItem        filterModeItem        = new FilterModeItem();
    private final BlacklistBehaviorItem blacklistBehaviorItem = new BlacklistBehaviorItem();

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
        String fm = pdc.get(KEY_FILTER_MODE, PersistentDataType.STRING);
        if (fm != null) {
            try { filterMode = FilterMode.valueOf(fm); } catch (IllegalArgumentException ignored) {}
        }
        String blb = pdc.get(KEY_BL_BEHAVIOR, PersistentDataType.STRING);
        if (blb != null) {
            try { blacklistBehavior = BlacklistBehavior.valueOf(blb); } catch (IllegalArgumentException ignored) {}
        }
        // 名单列表持久化：用 TAG_CONTAINER 存每格序列化字节
        PersistentDataContainer flc = pdc.get(KEY_FILTER_LIST, PersistentDataType.TAG_CONTAINER);
        if (flc != null) {
            for (int i = 0; i < filterList.getSize(); i++) {
                NamespacedKey slotKey = steamworkKey("fl_" + i);
                byte[] bytes = flc.get(slotKey, PersistentDataType.BYTE_ARRAY);
                if (bytes != null) {
                    try { filterList.setItem(new MachineUpdateReason(), i, ItemStack.deserializeBytes(bytes)); }
                    catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    public void postInitialise() {
        createLogisticGroup("buffer", LogisticGroupType.BOTH, buffer);
        // 不在产线内时禁止向 buffer 放入物品（玩家手动放入和气动网络推送均拦截）
        buffer.addPreUpdateHandler(event -> {
            if (!isInLine() && event.getNewItem() != null && !event.getNewItem().isEmpty()) {
                event.setCancelled(true);
            }
        });
        // 名单列表幽灵槽：放入时不消耗玩家物品，取出时不给玩家物品；自动去重并整理
        filterList.addPreUpdateHandler(event -> {
            if (!(event.getUpdateReason() instanceof xyz.xenondevs.invui.inventory.event.PlayerUpdateReason)) return;
            if (event.isAdd() || event.isSwap()) {
                event.setCancelled(true);
                ItemStack newItem = event.getNewItem();
                if (newItem == null || newItem.isEmpty()) return;
                // 去重：已存在相同物品则不写入
                for (int i = 0; i < filterList.getSize(); i++) {
                    ItemStack existing = filterList.getItem(i);
                    if (existing != null && !existing.isEmpty() && existing.isSimilar(newItem)) return;
                }
                filterList.setItem(new MachineUpdateReason(), event.getSlot(), newItem.asQuantity(1));
                compactFilterList();
            } else if (event.isRemove()) {
                event.setCancelled(true);
                filterList.setItem(new MachineUpdateReason(), event.getSlot(), null);
                compactFilterList();
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
        pdc.set(KEY_FILTER_MODE, PersistentDataType.STRING, filterMode.name());
        pdc.set(KEY_BL_BEHAVIOR, PersistentDataType.STRING, blacklistBehavior.name());
        // 保存名单列表
        boolean hasFilterItems = false;
        PersistentDataContainer flc = pdc.getAdapterContext().newPersistentDataContainer();
        for (int i = 0; i < filterList.getSize(); i++) {
            ItemStack slot = filterList.getItem(i);
            if (slot != null && !slot.isEmpty()) {
                flc.set(steamworkKey("fl_" + i), PersistentDataType.BYTE_ARRAY, slot.serializeAsBytes());
                hasFilterItems = true;
            }
        }
        if (hasFilterItems) pdc.set(KEY_FILTER_LIST, PersistentDataType.TAG_CONTAINER, flc);
        else pdc.remove(KEY_FILTER_LIST);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
        // filterList 是幽灵槽，物品不是真实持有，不掉落
    }

    @Override
    public void tick() {
        if (lineJammed) {
            lineInfoItem.notifyWindows();
            return;
        }
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

            // 过滤逻辑
            if (filterMode != FilterMode.OFF) {
                boolean inList = isInFilterList(single);
                if (filterMode == FilterMode.WHITELIST && !inList) continue;
                if (filterMode == FilterMode.BLACKLIST && inList) {
                    // 黑名单物品：按行为处理
                    if (blacklistBehavior == BlacklistBehavior.EJECT) {
                        ProductionLineMember outlet = findOutlet();
                        if (outlet != null && outlet.acceptFromLine(single)) {
                            decrementBuffer(i, stack);
                            lastPushSucceeded = true;
                            return;
                        }
                    }
                    // RETAIN 或 EJECT 但出口满：跳过此格
                    continue;
                }
            }

            boolean accepted;
            if (pushMode == PushMode.FUEL) {
                accepted = next.acceptFuelFromLine(single);
            } else if (pushMode == PushMode.AUTO && hasTemplate && single.isSimilar(fuelTemplate) && next.hasFuelSlot()) {
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

    /** 判断物品是否在名单列表中（按物品类型匹配，忽略数量）。 */
    private boolean isInFilterList(@NotNull ItemStack item) {
        for (int i = 0; i < filterList.getSize(); i++) {
            ItemStack slot = filterList.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.isSimilar(item)) return true;
        }
        return false;
    }

    /** 将名单列表中的非空槽位紧凑排列到前面，空槽位移到末尾。 */
    private void compactFilterList() {
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < filterList.getSize(); i++) {
            ItemStack slot = filterList.getItem(i);
            if (slot != null && !slot.isEmpty()) items.add(slot.clone());
        }
        for (int i = 0; i < filterList.getSize(); i++) {
            filterList.setItem(new MachineUpdateReason(), i, i < items.size() ? items.get(i) : null);
        }
    }

    /** 沿产线方向找到出口。 */
    @Nullable
    private ProductionLineMember findOutlet() {
        if (lineId == null || lineDirection == BlockFace.SELF) return null;
        Block cursor = getBlock().getRelative(lineDirection);
        for (int i = 0; i < 64; i++) {
            ProductionLineMember m = ProductionLineMember.of(cursor);
            if (m != null && lineId.equals(m.getLineId())) {
                if (m instanceof ProductionLineOutlet) return m;
                cursor = cursor.getRelative(lineDirection);
                continue;
            }
            cursor = cursor.getRelative(lineDirection);
        }
        return null;
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

    @Override public @Nullable UUID getLineId()              { return lineId; }
    @Override public int getLinePosition()                    { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection()    { return lineDirection; }
    @Override public @Nullable String getLineCreator()        { return lineCreator; }
    @Override public int getLineNumber()                      { return lineNumber; }

    @Override public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }
    @Override public void setLineNumber(int number)                { this.lineNumber = number; }

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
        this.lineJammed = false;
    }

    @Override
    public void onLineJammed() { this.lineJammed = true; }

    @Override
    public void onLineResumed() { this.lineJammed = false; }

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
            return WailaDisplay.of(Component.translatable("steamwork.item.production_line_buffer_chest.waila_idle"));
        }
        String stateKey = hasBufferedItems()
                ? lastPushSucceeded ? "pushing" : "buffering"
                : "waiting";
        Component creatorComp = lineCreator != null
                ? Component.text(lineCreator)
                : Component.translatable("steamwork.line.unknown_creator");
        return WailaDisplay.of(Component.translatable(
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
                        "# # L M F W B # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('b', buffer)
                .addIngredient('L', lineInfoItem)
                .addIngredient('M', modeToggleItem)
                .addIngredient('F', fuelFilterItem)
                .addIngredient('W', filterModeItem)
                .addIngredient('B', blacklistBehaviorItem)
                .build();
    }

    /** 打开名单管理子界面。 */
    private void openFilterListGui(@NotNull Player player) {
        Gui gui = Gui.builder()
                .setStructure(
                        "f f f f f f f f f",
                        "f f f f f f f f f",
                        "f f f f f f f f f",
                        "# # # # < # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('f', filterList)
                .addIngredient('<', new BackItem(player))
                .build();
        Window.builder()
                .setUpperGui(gui)
                .setTitle(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_list.title"))
                .setViewer(player)
                .build()
                .open();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return Component.translatable("steamwork.gui.production_line_buffer_chest.title");
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("buffer", buffer, "filter_list", filterList);
    }

    private boolean hasBufferedItems() {
        for (int i = 0; i < buffer.getSize(); i++) {
            ItemStack stack = buffer.getItem(i);
            if (stack != null && !stack.isEmpty()) return true;
        }
        return false;
    }

    // ===== GUI 项目 =====

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
                mat = Material.YELLOW_STAINED_GLASS_PANE; stateKey = "waiting";
            } else if (lastPushSucceeded) {
                mat = Material.GREEN_STAINED_GLASS_PANE;  stateKey = "pushing";
            } else {
                mat = Material.ORANGE_STAINED_GLASS_PANE; stateKey = "buffering";
            }
            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status.title")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.line_status." + stateKey))));
        }
        @Override public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
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
            String modeKey = pushMode == PushMode.OFF ? "disabled" : pushMode.name().toLowerCase();
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

    private final class FilterModeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = switch (filterMode) {
                case OFF       -> Material.GRAY_STAINED_GLASS_PANE;
                case WHITELIST -> Material.LIME_STAINED_GLASS_PANE;
                case BLACKLIST -> Material.RED_STAINED_GLASS_PANE;
            };
            String modeKey = filterMode == FilterMode.OFF ? "disabled" : filterMode.name().toLowerCase();
            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_mode.title")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_mode." + modeKey)),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_mode.left_hint")),
                            ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_mode.right_hint"))
                    ));
        }
        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT) {
                // 右键打开名单子界面
                openFilterListGui(player);
            } else {
                // 左键循环切换模式
                filterMode = switch (filterMode) {
                    case OFF       -> FilterMode.WHITELIST;
                    case WHITELIST -> FilterMode.BLACKLIST;
                    case BLACKLIST -> FilterMode.OFF;
                };
                notifyWindows();
                blacklistBehaviorItem.notifyWindows();
            }
        }
    }

    private final class BlacklistBehaviorItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean active = filterMode == FilterMode.BLACKLIST;
            Material mat = !active
                    ? Material.GRAY_STAINED_GLASS_PANE
                    : blacklistBehavior == BlacklistBehavior.RETAIN
                        ? Material.YELLOW_STAINED_GLASS_PANE
                        : Material.CYAN_STAINED_GLASS_PANE;
            String behavKey = blacklistBehavior.name().toLowerCase();
            List<Component> lore = active
                    ? List.of(
                        ni(Component.translatable("steamwork.gui.production_line_buffer_chest.bl_behavior." + behavKey)),
                        ni(Component.translatable("steamwork.gui.production_line_buffer_chest.bl_behavior.hint")))
                    : List.of(
                        ni(Component.translatable("steamwork.gui.production_line_buffer_chest.bl_behavior.inactive")));
            return ItemStackBuilder.of(mat)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.bl_behavior.title")))
                    .lore(lore);
        }
        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            if (filterMode != FilterMode.BLACKLIST) return;
            blacklistBehavior = blacklistBehavior == BlacklistBehavior.RETAIN
                    ? BlacklistBehavior.EJECT
                    : BlacklistBehavior.RETAIN;
            notifyWindows();
        }
    }

    private final class BackItem extends AbstractItem {
        private final Player player;
        BackItem(@NotNull Player player) { this.player = player; }
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.ARROW)
                    .name(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_list.back")))
                    .lore(List.of(ni(Component.translatable("steamwork.gui.production_line_buffer_chest.filter_list.back_hint"))));
        }
        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            Window.builder()
                    .setUpperGui(createGui())
                    .setTitle(getGuiTitle())
                    .setViewer(this.player)
                    .build()
                    .open();
        }
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
