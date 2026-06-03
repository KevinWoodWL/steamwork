package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarEntityCulledBlock;
import io.github.pylonmc.rebar.block.base.RebarFacadeBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 汽动输出端 —— 带 9 格内置存储仓的网络入口适配器。
 *
 * <p>工作流程：
 * <ol>
 *   <li>每 tick 从非导管侧（来源侧）的相邻容器/机器自动抽取物品补充 9 格存储仓</li>
 *   <li>每隔 tickIntervalOverride ticks 从存储仓取出最多 itemsPerExtract 个物品，
 *       批量发送到导管网络的所有可达输入端或直连容器</li>
 * </ol>
 * </p>
 */
public class PneumaticOutput extends RebarBlock implements
        RebarDirectionalBlock,
        RebarEntityCulledBlock,
        RebarFacadeBlock,
        RebarInventoryBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    public static final int MIN_EXTRACT = 1;
    public static final int MAX_EXTRACT = 64;

    public static final int MIN_TICK_INTERVAL = 1;
    public static final int MAX_TICK_INTERVAL = 200;

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST,  BlockFace.UP,    BlockFace.DOWN
    };

    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_output_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_output_display_owner");
    private static final NamespacedKey EXTRACT_KEY        = steamworkKey("pout_extract");
    private static final NamespacedKey TICK_INTERVAL_KEY  = steamworkKey("pout_tick_interval");

    private final int defaultTickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final int defaultExtract      = getSettings().getOrThrow("default-extract", ConfigAdapter.INTEGER);

    /** 9 格内置存储仓：GUI 中直接展示，Distributor/Sorter 也可推入。 */
    private final VirtualInventory storageInventory = new VirtualInventory(9);

    private volatile List<UUID> displayUuids = List.of();
    private boolean lastActive = false;
    private int roundRobinCursor = 0;
    /** 每次发送最多转发的物品数，GUI 可调。 */
    private int itemsPerExtract;
    /** 发送触发间隔（ticks），GUI 可调。 */
    private int tickIntervalOverride;
    /** 内部 tick 计数器，达到 tickIntervalOverride 时触发一次批量发送。 */
    private int sendCounter = 0;

    private final ExtractAmountItem extractAmountItem = new ExtractAmountItem();
    private final TickIntervalItem   tickIntervalItem  = new TickIntervalItem();

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        private final int defaultExtract      = getSettings().getOrThrow("default-extract",  ConfigAdapter.INTEGER);
        private final int defaultTickInterval = getSettings().getOrThrow("tick-interval",    ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("extract",  String.valueOf(defaultExtract)),
                    RebarArgument.of("interval", String.valueOf(defaultTickInterval))
            );
        }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticOutput(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(PneumaticEndpointSupport.resolvePlacementFacing(context));
        itemsPerExtract    = Math.max(MIN_EXTRACT, Math.min(MAX_EXTRACT, defaultExtract));
        tickIntervalOverride = defaultTickInterval;
        setTickInterval(1);
    }

    @SuppressWarnings("unused")
    public PneumaticOutput(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        itemsPerExtract = Math.max(MIN_EXTRACT, Math.min(MAX_EXTRACT,
                pdc.getOrDefault(EXTRACT_KEY, PersistentDataType.INTEGER, defaultExtract)));
        tickIntervalOverride = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL,
                pdc.getOrDefault(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, defaultTickInterval)));
        setTickInterval(1);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(EXTRACT_KEY,       PersistentDataType.INTEGER, itemsPerExtract);
        pdc.set(TICK_INTERVAL_KEY, PersistentDataType.INTEGER, tickIntervalOverride);
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
        newUuids.add(buildDisplay(Material.RED_TERRACOTTA, ":output",
                new TransformBuilder()
                        .lookAlong(getFacing())
                        .translate(0, 0, -0.3)
                        .scale(0.45, 0.45, 0.05)));
        newUuids.add(buildDisplay(Material.GRAY_CONCRETE, ":duct",
                PneumaticEndpointSupport.ductTransform(getBlock(), ductFace, getFacing())));
        displayUuids = List.copyOf(newUuids);
    }

    private @NotNull UUID buildDisplay(@NotNull Material material, @NotNull String modelSuffix,
                                       @NotNull TransformBuilder transform) {
        ItemDisplay d = PneumaticEndpointSupport.createDisplay(
                getBlock(), material, SteamworkKeys.PNEUMATIC_OUTPUT + modelSuffix, transform,
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

    /**
     * 来源侧：Output 朝向（{@link #getFacing()}）始终指向需要抽取物品的容器。
     *
     * <p>注意：不要从 {@link #pneumaticConnectionFace()} 反推来源侧——后者现在会
     * 优先识别直连的 {@link PneumaticInput}，导致在直连场景下来源侧被反转。</p>
     */
    private @NotNull BlockFace sourceFace() {
        return getFacing();
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // 1. 每 tick 从来源侧容器补充存储仓
        Block source = getBlock().getRelative(sourceFace());
        if (!PneumaticDuct.isNetworkConnector(source)) {
            PneumaticUtils.pullFromContainer(source, storageInventory, 64);
        }

        // 2. 计数器未到间隔，只补货不发送
        sendCounter++;
        if (sendCounter < tickIntervalOverride) return;
        sendCounter = 0;

        // 3. 存储仓中同类物品总量必须 >= itemsPerExtract 才触发发送
        ItemStack template = peekStorage();
        if (template == null) { setActive(false); return; }

        int available = countInStorage(template);
        if (available < itemsPerExtract) { setActive(false); return; }

        // 4. 批量发送到目标
        List<Block> destinations = collectDestinations();
        if (destinations.isEmpty()) { setActive(false); return; }

        int remaining = itemsPerExtract;
        int totalPushed = 0;
        while (remaining > 0) {
            ItemStack cur = peekStorage();
            if (cur == null) break;

            Block dest = pickRoundRobinDestination(destinations, cur);
            if (dest == null) break;

            int canPush = countSpaceFor(dest, cur, remaining);
            if (canPush <= 0) break;

            int pushed = PneumaticUtils.tryPushItems(dest, cur, canPush);
            if (pushed <= 0) break;

            consumeFromStorage(cur, pushed);
            remaining -= pushed;
            totalPushed += pushed;
        }

        setActive(totalPushed > 0);
    }

    /** 统计 storageInventory 中与 template 相似的物品总数。 */
    private int countInStorage(@NotNull ItemStack template) {
        int total = 0;
        for (ItemStack s : storageInventory.getItems()) {
            if (s != null && s.isSimilar(template)) total += s.getAmount();
        }
        return total;
    }

    private @Nullable ItemStack peekStorage() {
        for (ItemStack s : storageInventory.getItems()) {
            if (s != null && !s.getType().isAir()) return s.clone().asQuantity(1);
        }
        return null;
    }

    private void consumeFromStorage(@NotNull ItemStack one, int count) {
        MachineUpdateReason reason = new MachineUpdateReason();
        ItemStack[] items = storageInventory.getItems();
        int remaining = count;
        for (int i = 0; i < items.length && remaining > 0; i++) {
            ItemStack s = items[i];
            if (s == null || s.getType().isAir() || !s.isSimilar(one)) continue;
            int take = Math.min(remaining, s.getAmount());
            if (take >= s.getAmount()) {
                storageInventory.setItem(reason, i, null);
            } else {
                ItemStack reduced = s.clone();
                reduced.setAmount(s.getAmount() - take);
                storageInventory.setItem(reason, i, reduced);
            }
            remaining -= take;
        }
    }

    private int countSpaceFor(@NotNull Block block, @NotNull ItemStack item, int max) {
        RebarBlock rb = BlockStorage.get(block);
        if (rb instanceof RebarVirtualInventoryBlock vib) {
            VirtualInventory vi = vib.getVirtualInventories().get("input");
            if (vi != null) {
                int space = 0;
                for (ItemStack s : vi.getItems()) {
                    if (s == null || s.getType().isAir()) space += item.getMaxStackSize();
                    else if (s.isSimilar(item)) space += Math.max(0, s.getMaxStackSize() - s.getAmount());
                    if (space >= max) return max;
                }
                return Math.min(space, max);
            }
        }
        if (block.getState() instanceof org.bukkit.block.Container c) {
            int space = 0;
            for (ItemStack s : c.getInventory().getContents()) {
                if (s == null || s.getType().isAir()) space += item.getMaxStackSize();
                else if (s.isSimilar(item)) space += Math.max(0, s.getMaxStackSize() - s.getAmount());
                if (space >= max) return max;
            }
            return Math.min(space, max);
        }
        return 0;
    }

    /** 收集所有发送目标：导管网络末端的汽动输入端 + 非来源侧的直连容器/机器。 */
    private @NotNull List<Block> collectDestinations() {
        Set<Block> seen = new HashSet<>();
        List<Block> destinations = new ArrayList<>();
        BlockFace src = sourceFace();

        for (BlockFace face : FACES) {
            if (face == src) continue;
            Block neighbor = getBlock().getRelative(face);

            if (PneumaticDuct.isNetworkDuct(neighbor)) {
                for (Block endpoint : PneumaticDuct.findReachableEndpoints(neighbor)) {
                    if (BlockStorage.get(endpoint) instanceof PneumaticInput && seen.add(endpoint)) {
                        destinations.add(endpoint);
                    }
                }
                continue;
            }

            if (!(BlockStorage.get(neighbor) instanceof PneumaticOutput)
                    && PneumaticUtils.isItemTarget(neighbor)
                    && seen.add(neighbor)) {
                destinations.add(neighbor);
            }
        }

        destinations.sort(Comparator
                .<Block>comparingInt(Block::getX)
                .thenComparingInt(Block::getY)
                .thenComparingInt(Block::getZ));
        return destinations;
    }

    private @Nullable Block pickRoundRobinDestination(@NotNull List<Block> destinations,
                                                       @NotNull ItemStack item) {
        if (destinations.isEmpty()) return null;
        int size = destinations.size();
        int start = Math.floorMod(roundRobinCursor, size);
        for (int i = 0; i < size; i++) {
            int idx = (start + i) % size;
            Block candidate = destinations.get(idx);
            if (PneumaticUtils.hasSpace(candidate, item)) {
                roundRobinCursor = Math.floorMod(idx + 1, size);
                return candidate;
            }
        }
        return null;
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    // ── 接口实现 ──────────────────────────────────────────────────────────────

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("input", storageInventory);
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "s s s s s s s s s",
                        "# # # t # e # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', storageInventory)
                .addIngredient('t', tickIntervalItem)
                .addIngredient('e', extractAmountItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_output.title"));
    }

    // ── WAILA ─────────────────────────────────────────────────────────────────

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("extract",  String.valueOf(itemsPerExtract)),
                RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    // ── GUI 物品 ──────────────────────────────────────────────────────────────

    private final class TickIntervalItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_output.tick_interval",
                    RebarArgument.of("interval", String.valueOf(tickIntervalOverride)),
                    RebarArgument.of("max",       String.valueOf(MAX_TICK_INTERVAL))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_output.tick_interval_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_output.tick_interval_decrease")));
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.pneumatic_output.tick_interval_title")))
                    .amount(Math.min(tickIntervalOverride, 64))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            int delta = 0;
            if (clickType == ClickType.LEFT)             delta =   1;
            else if (clickType == ClickType.SHIFT_LEFT)  delta =  10;
            else if (clickType == ClickType.RIGHT)       delta =  -1;
            else if (clickType == ClickType.SHIFT_RIGHT) delta = -10;
            if (delta == 0) return;
            int next = Math.max(MIN_TICK_INTERVAL, Math.min(MAX_TICK_INTERVAL, tickIntervalOverride + delta));
            if (next == tickIntervalOverride) return;
            tickIntervalOverride = next;
            notifyWindows();
        }
    }

    private final class ExtractAmountItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_output.extract_amount",
                    RebarArgument.of("amount", String.valueOf(itemsPerExtract)),
                    RebarArgument.of("max",    String.valueOf(MAX_EXTRACT))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_output.extract_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_output.extract_decrease")));
            return ItemStackBuilder.of(Material.HOPPER)
                    .name(noItalic(Component.translatable("steamwork.gui.pneumatic_output.extract_title")))
                    .amount(itemsPerExtract)
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            int delta = 0;
            if (clickType == ClickType.LEFT)              delta =   1;
            else if (clickType == ClickType.SHIFT_LEFT)   delta =  10;
            else if (clickType == ClickType.RIGHT)        delta =  -1;
            else if (clickType == ClickType.SHIFT_RIGHT)  delta = -10;
            if (delta == 0) return;
            int next = Math.max(MIN_EXTRACT, Math.min(MAX_EXTRACT, itemsPerExtract + delta));
            if (next == itemsPerExtract) return;
            itemsPerExtract = next;
            notifyWindows();
        }
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
