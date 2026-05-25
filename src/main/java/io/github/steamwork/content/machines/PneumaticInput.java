package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarBreakHandler;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarEntityCulledBlock;
import io.github.pylonmc.rebar.block.base.RebarFacadeBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.PneumaticUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 汽动输入端 —— 汽动导管网络的终点，接收网络推送的物品并插入朝向方向的相邻容器/机器。
 *
 * <p>工作流程：
 * <ol>
 *   <li>分发器/分拣器/汽动输出端通过 BFS 找到本机，把物品塞入内部缓冲背包（{@code "input"} VI）</li>
 *   <li>本机每 tick 把缓冲中的物品推入 {@link #getFacing()} 方向的相邻容器/机器</li>
 * </ol>
 * 无需消耗蒸汽，是纯粹的终点中继。</p>
 *
 * <p>可选黑/白名单过滤：玩家在 GUI 中放入最多 3 个物品作为过滤模板，并切换模式。
 * 网络推送时（{@link PneumaticUtils}）会先调用 {@link #isAllowed(ItemStack)} 检查过滤。</p>
 */
public class PneumaticInput extends RebarBlock implements
        RebarBreakHandler,
        RebarDirectionalBlock,
        RebarEntityCulledBlock,
        RebarFacadeBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    // ── 过滤模式 ──────────────────────────────────────────────────────────────

    public enum FilterMode { WHITELIST, BLACKLIST }

    /**
     * 栏位模式：决定缓冲中的物品投入相邻容器时去哪一格。
     * <ul>
     *   <li>{@link #AUTO} —— 默认，让 Bukkit 自动选择空位（与原版漏斗一样的行为）</li>
     *   <li>{@link #INGREDIENT} —— 强制投入熔炉/高炉/烟熏炉的原料槽（slot 0），或酿造台的原料槽（slot 3）</li>
     *   <li>{@link #FUEL} —— 强制投入熔炉/高炉/烟熏炉的燃料槽（slot 1），或酿造台的烈焰粉槽（slot 4）</li>
     * </ul>
     * 对不支持燃料/原料区分的容器（如箱子），非 AUTO 模式下直接不推送。
     */
    public enum SlotMode { AUTO, INGREDIENT, FUEL }

    // ── 常量 ──────────────────────────────────────────────────────────────────

    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_input_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_input_display_owner");
    private static final NamespacedKey FILTER_MODE_KEY    = steamworkKey("pin_filter_mode");
    private static final NamespacedKey SLOT_MODE_KEY      = steamworkKey("pin_slot_mode");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);

    // ── 状态字段 ──────────────────────────────────────────────────────────────

    /** 缓冲背包：网络将物品推入此处，本机再转发到相邻容器。 */
    private final VirtualInventory bufferInventory = new VirtualInventory(4);

    /** 过滤槽：最多放 3 种物品作为黑/白名单模板（仅检查 Material，忽略 NBT）。 */
    private final VirtualInventory filterInventory = new VirtualInventory(3);

    /** 当前过滤模式，默认白名单（空过滤 = 全通）。 */
    private FilterMode filterMode = FilterMode.WHITELIST;

    /** 当前栏位模式，默认自动。 */
    private SlotMode slotMode = SlotMode.AUTO;

    private volatile List<UUID> displayUuids = List.of();

    private final FilterModeItem filterModeItem = new FilterModeItem();
    private final SlotModeItem   slotModeItem   = new SlotModeItem();

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticInput(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(PneumaticEndpointSupport.resolvePlacementFacing(context));
        filterMode = FilterMode.WHITELIST;
        slotMode   = SlotMode.AUTO;
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public PneumaticInput(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        int modeOrdinal = pdc.getOrDefault(FILTER_MODE_KEY, PersistentDataType.INTEGER, 0);
        filterMode = (modeOrdinal >= 0 && modeOrdinal < FilterMode.values().length)
                ? FilterMode.values()[modeOrdinal]
                : FilterMode.WHITELIST;
        int slotOrdinal = pdc.getOrDefault(SLOT_MODE_KEY, PersistentDataType.INTEGER, 0);
        slotMode = (slotOrdinal >= 0 && slotOrdinal < SlotMode.values().length)
                ? SlotMode.values()[slotOrdinal]
                : SlotMode.AUTO;
        setTickInterval(tickInterval);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(FILTER_MODE_KEY, PersistentDataType.INTEGER, filterMode.ordinal());
        pdc.set(SLOT_MODE_KEY,   PersistentDataType.INTEGER, slotMode.ordinal());
    }

    @Override
    public @NotNull Material getFacadeDefaultBlockType() {
        return Material.STRUCTURE_VOID;
    }

    @Override
    public void postInitialise() {
        setDisableBlockTextureEntity(true);
        refreshDisplays();
        PneumaticDuct.notifyNeighboringDucts(getBlock());
        // 1 tick 后再刷一次，确保导管初始化完成（避免 first-tick race）
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                io.github.steamwork.Steamwork.getInstance(),
                () -> {
                    if (!getBlock().getChunk().isLoaded()) return;
                    if (BlockStorage.get(getBlock()) != this) return;
                    refreshDisplays();
                    PneumaticDuct.notifyNeighboringDucts(getBlock());
                },
                4L);
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        clearDisplays();
    }

    @Override
    public void postBreak(@NotNull BlockBreakContext context) {
        PneumaticDuct.notifyNeighboringDucts(getBlock());
    }

    @Override
    public @NotNull Iterable<UUID> getCulledEntityIds() {
        return displayUuids;
    }

    // ── 过滤 ──────────────────────────────────────────────────────────────────

    /**
     * 判断某物品是否允许被网络推入本输入端。
     *
     * <ul>
     *   <li>过滤槽全空 → 始终允许</li>
     *   <li>白名单：物品 Material 与任意过滤槽匹配 → 允许</li>
     *   <li>黑名单：物品 Material 与任意过滤槽匹配 → 拒绝</li>
     * </ul>
     */
    public boolean isAllowed(@NotNull ItemStack item) {
        boolean hasFilter = false;
        boolean matched = false;
        for (ItemStack f : filterInventory.getItems()) {
            if (f == null || f.getType().isAir()) continue;
            hasFilter = true;
            if (f.getType() == item.getType()) {
                matched = true;
                break;
            }
        }
        if (!hasFilter) return true;
        return filterMode == FilterMode.WHITELIST ? matched : !matched;
    }

    // ── 显示实体 ──────────────────────────────────────────────────────────────

    void refreshDisplays() {
        clearDisplays();
        BlockFace ductFace = pneumaticConnectionFace();
        List<UUID> newUuids = new ArrayList<>();
        newUuids.add(buildDisplay(Material.LIGHT_GRAY_CONCRETE, ":main",
                new TransformBuilder()
                        .lookAlong(getFacing())
                        .translate(0, 0, -0.4)
                        .scale(0.65, 0.65, 0.2)));
        newUuids.add(buildDisplay(Material.LIME_TERRACOTTA, ":input",
                new TransformBuilder()
                        .lookAlong(getFacing())
                        .translate(0, 0, -0.3)
                        .scale(0.45, 0.45, 0.05)));
        newUuids.add(buildDisplay(Material.GRAY_CONCRETE, ":duct",
                PneumaticEndpointSupport.ductTransform(getBlock(), ductFace)));
        displayUuids = List.copyOf(newUuids);
    }

    private @NotNull UUID buildDisplay(@NotNull Material material, @NotNull String modelSuffix,
                                       @NotNull TransformBuilder transform) {
        ItemDisplay d = PneumaticEndpointSupport.createDisplay(
                getBlock(), material, SteamworkKeys.PNEUMATIC_INPUT + modelSuffix, transform,
                DISPLAY_MARKER_KEY, DISPLAY_OWNER_KEY);
        return d.getUniqueId();
    }

    private void clearDisplays() {
        PneumaticEndpointSupport.clearManagedDisplays(getBlock(), DISPLAY_MARKER_KEY, DISPLAY_OWNER_KEY);
        displayUuids = List.of();
    }

    // ── 朝向计算 ──────────────────────────────────────────────────────────────

    boolean acceptsPneumaticConnection(@NotNull BlockFace face) {
        return face == pneumaticConnectionFace();
    }

    private @NotNull BlockFace pneumaticConnectionFace() {
        return PneumaticEndpointSupport.pneumaticConnectionFace(getBlock(), getFacing());
    }

    // ── tick：将缓冲转发到目标容器 ─────────────────────────────────────────────

    @Override
    public void tick() {
        Block target = getBlock().getRelative(pneumaticConnectionFace().getOppositeFace());
        if (!PneumaticUtils.isItemTarget(target)) return;

        // 非 AUTO 模式：解析目标槽位；解析失败（容器不支持）就跳过本 tick
        int forcedSlot = -1;
        if (slotMode != SlotMode.AUTO) {
            forcedSlot = resolveTargetSlot(target);
            if (forcedSlot < 0) return;
        }

        MachineUpdateReason reason = new MachineUpdateReason();
        for (int i = 0; i < bufferInventory.getSize(); i++) {
            ItemStack s = bufferInventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;

            int pushed = (forcedSlot >= 0)
                    ? PneumaticUtils.tryPushItemsToSlot(target, s, s.getAmount(), forcedSlot)
                    : PneumaticUtils.tryPushItems(target, s, s.getAmount());

            if (pushed <= 0) continue;
            if (pushed >= s.getAmount()) {
                bufferInventory.setItem(reason, i, null);
            } else {
                ItemStack copy = s.clone();
                copy.setAmount(s.getAmount() - pushed);
                bufferInventory.setItem(reason, i, copy);
            }
        }
    }

    /**
     * 根据 {@link #slotMode} 解析目标容器对应的槽位下标，-1 表示当前模式与容器不兼容。
     *
     * <p>支持：熔炉 / 高炉 / 烟熏炉（原料 0、燃料 1）、酿造台（原料 3、燃料 4）。
     * 其他容器（箱子、漏斗、Rebar VI 机器等）在非 AUTO 模式下不予推送。</p>
     */
    private int resolveTargetSlot(@NotNull Block target) {
        if (!(target.getState() instanceof org.bukkit.block.Container c)) return -1;
        org.bukkit.inventory.Inventory inv = c.getInventory();
        if (inv instanceof org.bukkit.inventory.FurnaceInventory) {
            return slotMode == SlotMode.FUEL ? 1 : 0;
        }
        if (inv instanceof org.bukkit.inventory.BrewerInventory) {
            return slotMode == SlotMode.FUEL ? 4 : 3;
        }
        return -1;
    }

    // ── 接口实现 ──────────────────────────────────────────────────────────────

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        // 使用 LinkedHashMap 保证顺序；onBreak 会自动 drop 所有 VI 中的物品
        Map<String, VirtualInventory> map = new LinkedHashMap<>();
        map.put("input", bufferInventory);
        map.put("filter", filterInventory);
        return map;
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure("# # f f f m s # #")
                .addIngredient('#', GuiItems.background())
                .addIngredient('f', filterInventory)
                .addIngredient('m', filterModeItem)
                .addIngredient('s', slotModeItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_input.title"));
    }

    // ── WAILA ─────────────────────────────────────────────────────────────────

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey());
    }

    // ── GUI 物品 ──────────────────────────────────────────────────────────────

    private final class FilterModeItem extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean isWhitelist = filterMode == FilterMode.WHITELIST;
            Material mat = isWhitelist ? Material.WHITE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
            String modeKey = isWhitelist
                    ? "steamwork.gui.pneumatic_input.whitelist"
                    : "steamwork.gui.pneumatic_input.blacklist";
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(modeKey)))
                    .lore(noItalic(Component.translatable("steamwork.gui.pneumatic_input.mode_hint")));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            filterMode = (filterMode == FilterMode.WHITELIST) ? FilterMode.BLACKLIST : FilterMode.WHITELIST;
            notifyWindows();
        }
    }

    private final class SlotModeItem extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat;
            String key;
            switch (slotMode) {
                case INGREDIENT -> { mat = Material.IRON_ORE;   key = "steamwork.gui.pneumatic_input.slot_mode_ingredient"; }
                case FUEL       -> { mat = Material.COAL;       key = "steamwork.gui.pneumatic_input.slot_mode_fuel"; }
                default         -> { mat = Material.COMPASS;    key = "steamwork.gui.pneumatic_input.slot_mode_auto"; }
            }
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(key)))
                    .lore(noItalic(Component.translatable("steamwork.gui.pneumatic_input.slot_mode_hint")));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            SlotMode[] values = SlotMode.values();
            slotMode = values[(slotMode.ordinal() + 1) % values.length];
            notifyWindows();
        }
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
