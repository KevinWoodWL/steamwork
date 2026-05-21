package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.*;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.logistics.LogisticGroup;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.logistics.slot.LogisticSlot;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkKeys;
import kotlin.jvm.functions.Function1;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class SteamArm extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarVirtualInventoryBlock,
        RebarTickingBlock,
        RebarInteractBlock {

    /**
     * 注册 SteamArm 的全局选择模式监听器。
     * <p>
     * 必须由 {@link io.github.steamwork.Steamwork#onEnable()} 显式调用一次。
     * 之前版本是用类的 {@code static {}} 块在 SteamArm 首次被加载时注册，
     * 但类加载时机不可控（依赖第一次有人引用 SteamArm.class），
     * 且 reload 后无法清理。现在改为显式注册更稳。
     */
    public static void registerGlobalListeners() {
        io.github.steamwork.Steamwork.getInstance().getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
            public void onPlayerInteract(PlayerInteractEvent event) {
                UUID playerId = event.getPlayer().getUniqueId();

                // Check if right-clicking on a Steam Arm block - allow to open GUI
                if (event.getAction().toString().contains("RIGHT_CLICK")) {
                    org.bukkit.block.Block clickedBlock = event.getClickedBlock();
                    if (clickedBlock != null) {
                        io.github.pylonmc.rebar.block.RebarBlock rb = io.github.pylonmc.rebar.block.BlockStorage.get(clickedBlock);
                        if (rb instanceof SteamArm steamArm) {
                            // Allow right-click on Steam Arm to open GUI
                            // Exit selection mode if active - clear both global sets and instance flags
                            PLAYERS_IN_INPUT_SELECTION.remove(playerId);
                            PLAYERS_IN_OUTPUT_SELECTION.remove(playerId);
                            if (steamArm.selectingInputMode || steamArm.selectingOutputMode) {
                                steamArm.selectingInputMode = false;
                                steamArm.selectingOutputMode = false;
                                steamArm.notifyGuiItems();
                                event.getPlayer().sendMessage(noItalic(net.kyori.adventure.text.Component.translatable("steamwork.message.steam_arm.selection_cancelled")));
                            }
                            return;
                        }
                    }
                }

                // Check input selection mode
                if (PLAYERS_IN_INPUT_SELECTION.contains(playerId)) {
                    event.setCancelled(true);
                    if (event.getAction().toString().contains("LEFT_CLICK")) {
                        handleInputSelectionClick(event);
                    }
                    return;
                }

                // Check output selection mode
                if (PLAYERS_IN_OUTPUT_SELECTION.contains(playerId)) {
                    event.setCancelled(true);
                    if (event.getAction().toString().contains("LEFT_CLICK")) {
                        handleOutputSelectionClick(event);
                    }
                }
            }

            private void handleInputSelectionClick(PlayerInteractEvent event) {
                handleSelectionClick(event, true);
            }

            private void handleOutputSelectionClick(PlayerInteractEvent event) {
                handleSelectionClick(event, false);
            }

            private void handleSelectionClick(PlayerInteractEvent event, boolean isInput) {
                org.bukkit.block.Block clickedBlock = event.getClickedBlock();
                if (clickedBlock == null) return;

                org.bukkit.entity.Player player = event.getPlayer();
                UUID playerId = player.getUniqueId();

                // Check if clicking on a Steam Arm block itself - skip it, don't add as target
                io.github.pylonmc.rebar.block.RebarBlock rb = io.github.pylonmc.rebar.block.BlockStorage.get(clickedBlock);
                if (rb instanceof SteamArm) {
                    return; // Let the block's onInteract handle it, don't add as target
                }

                // Find the SteamArm block at range from the clicked block
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            org.bukkit.block.Block checkBlock = clickedBlock.getRelative(dx, dy, dz);
                            if (checkBlock.isEmpty()) continue;

                            io.github.pylonmc.rebar.block.RebarBlock rbCheck = io.github.pylonmc.rebar.block.BlockStorage.get(checkBlock);
                            if (!(rbCheck instanceof SteamArm steamArm)) continue;

                            // Skip if this block itself is a SteamArm (e.g., when placing a new SteamArm in selection mode)
                            if (checkBlock.equals(clickedBlock)) {
                                continue;
                            }

                            int distSq = dx * dx + dy * dy + dz * dz;
                            if (distSq <= steamArm.range * steamArm.range) {
                                // Check if the clicked block is already in the target list
                                java.util.List<BlockLocation> targets = isInput ? steamArm.inputTargets : steamArm.outputTargets;
                                BlockLocation clickedLoc = new BlockLocation(clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());

                                // Find and remove if already exists
                                int existingIndex = -1;
                                for (int i = 0; i < targets.size(); i++) {
                                    if (targets.get(i).containsPosition(clickedLoc.x(), clickedLoc.y(), clickedLoc.z())) {
                                        existingIndex = i;
                                        break;
                                    }
                                }

                                if (existingIndex >= 0) {
                                    // Remove from list (toggle off selected state)
                                    targets.remove(existingIndex);
                                    steamArm.invalidateCache();
                                    steamArm.notifyGuiItems();
                                    net.kyori.adventure.text.Component typeComponent = net.kyori.adventure.text.Component.translatable("steamwork.message.steam_arm.target_type." + (isInput ? "input" : "output"));
                                    net.kyori.adventure.text.Component locationInfo = net.kyori.adventure.text.Component.text("(" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ() + ")");
                                    net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text()
                                            .append(MiniMessage.miniMessage().deserialize("<yellow>已移除"))
                                            .append(typeComponent)
                                            .append(MiniMessage.miniMessage().deserialize("目标：<white>"))
                                            .append(locationInfo)
                                            .build();
                                    player.sendMessage(noItalic(message));
                                    return;
                                }

                                // Add new target (stay in selection mode for continuous selection)
                                steamArm.addTarget(clickedBlock, targets, isInput ? "input" : "output", player, true);
                                // 不退出选择模式，支持连续选择
                                return;
                            }
                        }
                    }
                }

                player.sendMessage(noItalic(net.kyori.adventure.text.Component.translatable("steamwork.message.steam_arm.no_steam_arm_nearby")));
            }

            private void cancelInputSelection(org.bukkit.entity.Player player) {
                cancelSelection(player, true);
            }

            private void cancelOutputSelection(org.bukkit.entity.Player player) {
                cancelSelection(player, false);
            }

            private void cancelSelection(org.bukkit.entity.Player player, boolean isInput) {
                UUID playerId = player.getUniqueId();
                if (isInput) {
                    PLAYERS_IN_INPUT_SELECTION.remove(playerId);
                } else {
                    PLAYERS_IN_OUTPUT_SELECTION.remove(playerId);
                }

                // Find and update the SteamArm block
                org.bukkit.Location loc = player.getLocation();
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        for (int dz = -5; dz <= 5; dz++) {
                            org.bukkit.block.Block block = loc.getWorld().getBlockAt(
                                    (int) (loc.getX() + dx), (int) (loc.getY() + dy), (int) (loc.getZ() + dz));
                            if (block.isEmpty()) continue;

                            io.github.pylonmc.rebar.block.RebarBlock rb = io.github.pylonmc.rebar.block.BlockStorage.get(block);
                            if (rb instanceof SteamArm steamArm) {
                                if (isInput) {
                                    steamArm.selectingInputMode = false;
                                } else {
                                    steamArm.selectingOutputMode = false;
                                }
                                steamArm.notifyGuiItems();
                            }
                        }
                    }
                }

                player.sendMessage(noItalic(net.kyori.adventure.text.Component.translatable("steamwork.message.steam_arm.selection_cancelled")));
            }
        }, io.github.steamwork.Steamwork.getInstance());
    }

    private static final String MOTOR_SLOT_KEY = "motor";
    private static final NamespacedKey ITEMS_PER_TRANSFER_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("items_per_transfer");
    private static final NamespacedKey INPUT_TARGETS_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("input_targets");
    private static final NamespacedKey OUTPUT_TARGETS_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("output_targets");
    private static final NamespacedKey SELECTING_MODE_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("selecting_mode");
    private static final NamespacedKey DISTRIBUTION_MODE_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("distribution_mode");
    private static final NamespacedKey INPUT_ROUND_ROBIN_INDEX_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("input_round_robin_index");
    private static final NamespacedKey OUTPUT_ROUND_ROBIN_INDEX_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("output_round_robin_index");

    private static final int MIN_ITEMS_PER_TRANSFER = 1;
    private static final int MAX_ITEMS_PER_TRANSFER = 64;
    private static final int DEFAULT_ITEMS_PER_TRANSFER = 16;
    private static final int MAX_TARGETS = 9;
    private static final double DEFAULT_STEAM_PER_ITEM = 3.125;

    // Static set to track players in selection mode globally
    private static final Set<UUID> PLAYERS_IN_INPUT_SELECTION = new HashSet<>();
    private static final Set<UUID> PLAYERS_IN_OUTPUT_SELECTION = new HashSet<>();

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final int range = getSettings().getOrThrow("range", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem = getSettings().get("steam-per-item", ConfigAdapter.DOUBLE, DEFAULT_STEAM_PER_ITEM);

    private int itemsPerTransfer;
    private DistributionMode distributionMode;
    private int inputRoundRobinIndex;
    private int outputRoundRobinIndex;
    private List<BlockLocation> inputTargets;
    private List<BlockLocation> outputTargets;
    private transient boolean selectingInputMode = false;
    private transient boolean selectingOutputMode = false;

    private final VirtualInventory motorInventory;
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ItemsPerTransferItem itemsPerTransferItem = new ItemsPerTransferItem();
    private final InputTargetItem inputTargetItem = new InputTargetItem();
    private final OutputTargetItem outputTargetItem = new OutputTargetItem();
    private final DistributionModeItem distributionModeItem = new DistributionModeItem();
    private final StatusItem statusItem = new StatusItem();

    private boolean lastActive = false;
    private boolean lastHasMotor = false;

    // 异步扫描缓存 - 避免每tick都扫描大量方块
    private static final int SCAN_CACHE_TICKS = 10;  // 每10tick重新扫描一次
    private int scanCacheTicks = 0;
    private List<Endpoint> cachedInputEndpoints = null;
    private List<Endpoint> cachedOutputEndpoints = null;
    private int cachedInputHash = 0;
    private int cachedOutputHash = 0;

    public enum TargetSelectionMode {
        NONE,
        INPUT,
        OUTPUT
    }

    private enum DistributionMode {
        ROUND_ROBIN("round_robin", Material.COMPASS),
        STRICT_ROUND_ROBIN("strict_round_robin", Material.REPEATER),
        FIRST_PRIORITY("first_priority", Material.TARGET);

        private final String key;
        private final Material icon;

        DistributionMode(String key, Material icon) {
            this.key = key;
            this.icon = icon;
        }

        private DistributionMode next() {
            DistributionMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private static DistributionMode fromKey(@Nullable String key) {
            for (DistributionMode mode : values()) {
                if (mode.key.equals(key)) {
                    return mode;
                }
            }
            return ROUND_ROBIN;
        }
    }

    public static class Item extends RebarItem {

        private final int range = getSettings().getOrThrow("range", ConfigAdapter.INTEGER);
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem = getSettings().get("steam-per-item", ConfigAdapter.DOUBLE, DEFAULT_STEAM_PER_ITEM);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("range", UnitFormat.BLOCKS.format(range)),
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("steam-per-transfer", UnitFormat.MILLIBUCKETS.format(steamPerItem))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamArm(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);

        this.motorInventory = new VirtualInventory(1);
        this.itemsPerTransfer = getSettings().getOrThrow("items-per-transfer", ConfigAdapter.INTEGER);
        this.distributionMode = DistributionMode.ROUND_ROBIN;
        this.inputRoundRobinIndex = 0;
        this.outputRoundRobinIndex = 0;
        this.inputTargets = new ArrayList<>();
        this.outputTargets = new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public SteamArm(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        this.motorInventory = new VirtualInventory(1);
        this.itemsPerTransfer = pdc.getOrDefault(ITEMS_PER_TRANSFER_KEY, PersistentDataType.INTEGER, DEFAULT_ITEMS_PER_TRANSFER);
        this.distributionMode = DistributionMode.fromKey(pdc.get(DISTRIBUTION_MODE_KEY, PersistentDataType.STRING));
        this.inputRoundRobinIndex = pdc.getOrDefault(INPUT_ROUND_ROBIN_INDEX_KEY, PersistentDataType.INTEGER, 0);
        this.outputRoundRobinIndex = pdc.getOrDefault(OUTPUT_ROUND_ROBIN_INDEX_KEY, PersistentDataType.INTEGER, 0);
        this.inputTargets = loadTargets(pdc, INPUT_TARGETS_KEY);
        this.outputTargets = loadTargets(pdc, OUTPUT_TARGETS_KEY);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(ITEMS_PER_TRANSFER_KEY, PersistentDataType.INTEGER, itemsPerTransfer);
        pdc.set(DISTRIBUTION_MODE_KEY, PersistentDataType.STRING, distributionMode.key);
        pdc.set(INPUT_ROUND_ROBIN_INDEX_KEY, PersistentDataType.INTEGER, inputRoundRobinIndex);
        pdc.set(OUTPUT_ROUND_ROBIN_INDEX_KEY, PersistentDataType.INTEGER, outputRoundRobinIndex);
        saveTargets(pdc, INPUT_TARGETS_KEY, inputTargets);
        saveTargets(pdc, OUTPUT_TARGETS_KEY, outputTargets);
    }

    private List<BlockLocation> loadTargets(PersistentDataContainer pdc, NamespacedKey key) {
        int[] data = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }
        List<BlockLocation> result = new ArrayList<>();

        // Check if new format (6 values per entry for large chests) or old format (3 values)
        if (data.length % 6 == 0) {
            // New format with large chest support
            for (int i = 0; i < data.length; i += 6) {
                int x1 = data[i];
                int y1 = data[i + 1];
                int z1 = data[i + 2];
                int partnerX = data[i + 3];
                int partnerY = data[i + 4];
                int partnerZ = data[i + 5];

                // Check if this is a large chest (partner coordinates are not -1)
                if (partnerX == -1 && partnerY == -1 && partnerZ == -1) {
                    result.add(new BlockLocation(x1, y1, z1));
                } else {
                    result.add(new BlockLocation(x1, y1, z1, partnerX, partnerY, partnerZ));
                }
            }
        } else if (data.length % 3 == 0) {
            // Old format without large chest support
            for (int i = 0; i < data.length; i += 3) {
                result.add(new BlockLocation(data[i], data[i + 1], data[i + 2]));
            }
        }

        return result;
    }

    private void saveTargets(PersistentDataContainer pdc, NamespacedKey key, List<BlockLocation> targets) {
        if (targets.isEmpty()) {
            pdc.remove(key);
            return;
        }
        // Save 6 values per location: x, y, z, partnerX, partnerY, partnerZ
        int[] data = new int[targets.size() * 6];
        for (int i = 0; i < targets.size(); i++) {
            BlockLocation loc = targets.get(i);
            int idx = i * 6;
            data[idx] = loc.x();
            data[idx + 1] = loc.y();
            data[idx + 2] = loc.z();
            data[idx + 3] = loc.isLargeChest() ? loc.partnerX() : -1;
            data[idx + 4] = loc.isLargeChest() ? loc.partnerY() : -1;
            data[idx + 5] = loc.isLargeChest() ? loc.partnerZ() : -1;
        }
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, data);
    }

    @Override
    public void postInitialise() {
        motorInventory.addPreUpdateHandler(event -> notifyGuiItems());
        motorInventory.addPostUpdateHandler(event -> notifyGuiItems());
    }

    @Override
    public void onInteract(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.LOWEST) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.equals(getBlock())) return;

        Player player = event.getPlayer();

        // Left click to add/remove target during selection mode
        if (event.getAction().toString().contains("LEFT_CLICK")) {
            if (selectingInputMode) {
                event.setCancelled(true);
                handleTargetToggle(clickedBlock, inputTargets, true, player);
                return;
            }
            if (selectingOutputMode) {
                event.setCancelled(true);
                handleTargetToggle(clickedBlock, outputTargets, false, player);
                return;
            }
        }
    }

    /**
     * 切换目标容器的选择状态
     * 如果已存在则移除，否则添加
     */
    private void handleTargetToggle(Block clickedBlock, List<BlockLocation> targets, boolean isInput, Player player) {
        BlockLocation loc = new BlockLocation(clickedBlock.getX(), clickedBlock.getY(), clickedBlock.getZ());

        // Check if already in list - if so, remove it
        if (targets.contains(loc)) {
            targets.remove(loc);
            invalidateCache();
            notifyGuiItems();
            Component typeComponent = Component.translatable("steamwork.message.steam_arm.target_type." + (isInput ? "input" : "output"));
            Component locationInfo = Component.text("(" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ() + ")");
            Component message = Component.text()
                    .append(MiniMessage.miniMessage().deserialize("<yellow>已移除"))
                    .append(typeComponent)
                    .append(MiniMessage.miniMessage().deserialize("目标：<white>"))
                    .append(locationInfo)
                    .build();
            player.sendMessage(noItalic(message));
            // 不退出选择模式，支持连续选择/移除
            return;
        }

        // Otherwise add the target (stay in selection mode)
        addTarget(clickedBlock, targets, isInput ? "input" : "output", player, true);
    }

    /**
     * 添加目标容器
     * @param target 目标方块
     * @param list 目标列表
     * @param type 输入或输出
     * @param player 玩家
     * @param stayInSelectionMode 添加后是否保持选择模式
     */
    private void addTarget(Block target, List<BlockLocation> list, String type, Player player, boolean stayInSelectionMode) {
        // Check if already at max
        if (list.size() >= MAX_TARGETS) {
            player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.target_max")));
            return;
        }

        // Check if target is self
        if (target.equals(getBlock())) {
            player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.target_self")));
            return;
        }

        // Check if target is another SteamArm (prevent adding other steam arms as targets)
        RebarBlock targetBlock = BlockStorage.get(target);
        if (targetBlock instanceof SteamArm) {
            player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.target_self")));
            return;
        }

        // Check if target is already in list (including large chest partner)
        BlockLocation loc = new BlockLocation(target.getX(), target.getY(), target.getZ());
        if (containsAnyPosition(list, loc)) {
            player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.target_duplicate")));
            return;
        }

        // Check if target conflicts with the other list (input vs output)
        List<BlockLocation> otherList = type.equals("input") ? outputTargets : inputTargets;
        if (containsAnyPosition(otherList, loc)) {
            String conflictType = type.equals("input") ? "output" : "input";
            player.sendMessage(noItalic(Component.translatable(
                    "steamwork.message.steam_arm.target_conflict",
                    RebarArgument.of("type", Component.translatable("steamwork.message.steam_arm.target_type." + conflictType))
            )));
            return;
        }

        // Check range
        int distSq = distanceSquared(target);
        if (distSq > range * range) {
            player.sendMessage(noItalic(Component.translatable(
                    "steamwork.message.steam_arm.target_out_of_range",
                    RebarArgument.of("range", String.valueOf(range))
            )));
            return;
        }

        // Check if target is a valid container (has logistic groups)
        Map<String, LogisticGroup> groups = getLogisticGroups(target);
        if (groups.isEmpty()) {
            player.sendMessage(noItalic(Component.translatable(
                    "steamwork.message.steam_arm.target_not_container",
                    RebarArgument.of("x", String.valueOf(target.getX())),
                    RebarArgument.of("y", String.valueOf(target.getY())),
                    RebarArgument.of("z", String.valueOf(target.getZ()))
            )));
            return;
        }

        // Check if this is a large chest and find partner
        BlockLocation targetLoc = detectLargeChest(target);
        list.add(targetLoc);
        invalidateCache();  // 清除缓存，因为目标已更改
        notifyGuiItems();

        // Build location string
        Component locationInfo;
        if (targetLoc.isLargeChest()) {
            locationInfo = Component.text("(" + target.getX() + ", " + target.getY() + ", " + target.getZ()
                    + " ~ " + targetLoc.partnerX() + ", " + targetLoc.partnerY() + ", " + targetLoc.partnerZ() + ")");
        } else {
            locationInfo = Component.text("(" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")");
        }

        // Build and send message directly
        Component typeComponent = Component.translatable("steamwork.message.steam_arm.target_type." + type);
        Component message = Component.text()
                .append(MiniMessage.miniMessage().deserialize("<green>已添加"))
                .append(typeComponent)
                .append(MiniMessage.miniMessage().deserialize("目标：<white>"))
                .append(locationInfo)
                .build();
        player.sendMessage(noItalic(message));

        // Exit selection mode only if not staying in selection mode
        if (!stayInSelectionMode) {
            if (type.equals("input")) {
                selectingInputMode = false;
            } else {
                selectingOutputMode = false;
            }
            notifyGuiItems();
        }
    }

    /**
     * 添加目标容器（兼容旧调用）
     */
    private void addTarget(Block target, List<BlockLocation> list, String type, Player player) {
        addTarget(target, list, type, player, false);
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarFluidBufferBlock.super.onBreak(drops, context);
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of(MOTOR_SLOT_KEY, motorInventory);
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        itemsPerTransferItem.notifyWindows();
        inputTargetItem.notifyWindows();
        outputTargetItem.notifyWindows();
        distributionModeItem.notifyWindows();
    }

    private boolean hasMotor() {
        ItemStack motor = motorInventory.getItem(0);
        if (motor == null) return false;
        RebarItem item = RebarItem.fromStack(motor);
        return item != null && item.getKey().equals(SteamworkKeys.STEAM_MOTOR);
    }

    private double steamPerItem() {
        return steamPerItem;
    }

    private double steamPerOperation() {
        return steamPerItem * itemsPerTransfer;
    }

    @Override
    public void tick() {
        // 递减缓存计时器
        if (scanCacheTicks > 0) {
            scanCacheTicks--;
        }

        boolean hasMotor = hasMotor();

        // Update motor state change
        if (lastHasMotor != hasMotor) {
            lastHasMotor = hasMotor;
            scheduleBlockTextureItemRefresh();
        }

        // Check if motor is installed
        if (!hasMotor) {
            setActive(false);
            notifyGuiItems();
            return;
        }

        // Check if has enough steam
        double steamCost = steamPerOperation();
        if (fluidAmount(SteamworkFluids.STEAM) < steamCost) {
            setActive(false);
            notifyGuiItems();
            return;
        }

        // Check if has both input and output targets (required for operation)
        // If no inputs or no outputs, the arm should not work
        List<Block> inputBlocks = getInputBlocks();
        List<Block> outputBlocks = getOutputBlocks();
        if (inputBlocks.isEmpty() || outputBlocks.isEmpty()) {
            setActive(false);
            notifyGuiItems();
            return;
        }

        boolean transferred = tryTransfer();
        if (transferred) {
            removeFluid(SteamworkFluids.STEAM, steamCost);
        }
        setActive(transferred);
        notifyGuiItems();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# m # s q a # d #",
                        "# # # # # # # # #",
                        "# # i # # # o # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('m', motorInventory)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('q', itemsPerTransferItem)
                .addIngredient('a', statusItem)
                .addIngredient('d', distributionModeItem)
                .addIngredient('i', inputTargetItem)
                .addIngredient('o', outputTargetItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_arm.title"));
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        Map<String, kotlin.Pair<String, Integer>> properties = super.getBlockTextureProperties();
        properties.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        properties.put("has_motor", new kotlin.Pair<>(Boolean.toString(lastHasMotor), 2));
        return properties;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        boolean hasMotor = hasMotor();
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.STEAM),
                        fluidCapacity(SteamworkFluids.STEAM),
                        16,
                        TextColor.fromHexString("#d8edf0")
                )),
                RebarArgument.of("motor-status", Component.translatable(
                        hasMotor ? "steamwork.gui.steam_arm.motor_installed" : "steamwork.gui.steam_arm.motor_missing"
                )),
                RebarArgument.of("state", Component.translatable("steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    private boolean tryTransfer() {
        // 修正：inputTargets = 源（物品从这里取），outputTargets = 目标（物品放到这里）
        if (inputTargets.isEmpty() || outputTargets.isEmpty()) {
            return false;
        }

        List<Block> sourceBlocks = getInputBlocks();  // input = 物品进来 = 源
        List<Block> targetBlocks = getOutputBlocks(); // output = 物品出去 = 目标
        
        if (sourceBlocks.isEmpty() || targetBlocks.isEmpty()) {
            return false;
        }

        List<Block> orderedSourceBlocks = orderedSources(sourceBlocks);

        for (int sourceOffset = 0; sourceOffset < orderedSourceBlocks.size(); sourceOffset++) {
            Block sourceBlock = orderedSourceBlocks.get(sourceOffset);
            int sourceIndex = sourceBlocks.indexOf(sourceBlock);
            List<Block> orderedTargetBlocks = orderedTargets(targetBlocks);

            // Special handling for furnaces - only take from result slot
            if (isFurnace(sourceBlock)) {
                TransferResult result = tryTransferFromFurnaceResult(sourceBlock, orderedTargetBlocks);
                if (result.transferred()) {
                    markTransferSuccess(sourceBlocks.size(), sourceIndex, targetBlocks.size(), result.targetOffset());
                    return true;
                }
                if (shouldWaitForBlockedRoute()) return false;
                continue;
            }

            // Normal inventory handling for non-furnace blocks
            org.bukkit.inventory.Inventory sourceInv = getBlockInventory(sourceBlock);
            if (sourceInv == null) {
                continue;
            }

            // Search for items in source inventory
            for (int i = 0; i < sourceInv.getSize(); i++) {
                ItemStack sourceStack = sourceInv.getItem(i);
                if (sourceStack == null || sourceStack.getAmount() <= 0) continue;

                int amount = Math.min(itemsPerTransfer, sourceStack.getAmount());
                ItemStack toTransfer = sourceStack.clone();
                toTransfer.setAmount(amount);

                // Try to insert into each target block
                for (int targetOffset = 0; targetOffset < orderedTargetBlocks.size(); targetOffset++) {
                    Block targetBlock = orderedTargetBlocks.get(targetOffset);
                    // Skip if same block
                    if (sourceBlock.getLocation().equals(targetBlock.getLocation())) {
                        continue;
                    }

                    org.bukkit.inventory.Inventory targetInv = getBlockInventory(targetBlock);
                    if (targetInv == null) {
                        continue;
                    }
                    if (!canFit(targetInv, toTransfer)) {
                        continue;
                    }

                    // Try to add items to target inventory
                    HashMap<Integer, ItemStack> remaining = targetInv.addItem(toTransfer);
                    if (remaining.isEmpty()) {
                        // Successfully added all items - remove from source
                        int originalAmount = sourceStack.getAmount();
                        sourceStack.setAmount(originalAmount - amount);
                        sourceInv.setItem(i, sourceStack.getAmount() <= 0 ? null : sourceStack);
                        markTransferSuccess(sourceBlocks.size(), sourceIndex, targetBlocks.size(), targetOffset);
                        return true;
                    }
                }

                if (shouldWaitForBlockedRoute()) return false;
            }
        }

        return false;
    }

    private TransferResult tryTransferFromFurnaceResult(Block sourceBlock, List<Block> targetBlocks) {
        org.bukkit.inventory.Inventory inventory = getBlockInventory(sourceBlock);
        if (!(inventory instanceof org.bukkit.inventory.FurnaceInventory furnaceInv)) {
            return TransferResult.failed();
        }

        ItemStack currentResult = furnaceInv.getResult();
        if (currentResult == null || currentResult.isEmpty()) {
            return TransferResult.failed();
        }

        int amount = Math.min(itemsPerTransfer, currentResult.getAmount());
        ItemStack toTransfer = currentResult.clone();
        toTransfer.setAmount(amount);

        for (int targetOffset = 0; targetOffset < targetBlocks.size(); targetOffset++) {
            Block targetBlock = targetBlocks.get(targetOffset);
            if (sourceBlock.getLocation().equals(targetBlock.getLocation())) {
                continue;
            }

            org.bukkit.inventory.Inventory targetInv = getBlockInventory(targetBlock);
            if (targetInv == null || !canFit(targetInv, toTransfer)) {
                continue;
            }

            ItemStack latestResult = furnaceInv.getResult();
            if (latestResult == null || latestResult.isEmpty()) {
                return TransferResult.failed();
            }
            if (!latestResult.isSimilar(toTransfer) || latestResult.getAmount() < amount) {
                return TransferResult.failed();
            }

            ItemStack originalResult = latestResult.clone();
            ItemStack remainingResult = latestResult.clone();
            remainingResult.setAmount(latestResult.getAmount() - amount);
            furnaceInv.setResult(remainingResult.getAmount() <= 0 ? null : remainingResult);

            HashMap<Integer, ItemStack> remaining = targetInv.addItem(toTransfer.clone());
            if (remaining.isEmpty()) {
                return TransferResult.success(targetOffset);
            }

            furnaceInv.setResult(originalResult);
        }

        return TransferResult.failed();
    }

    private List<Block> orderedSources(List<Block> sourceBlocks) {
        if (distributionMode != DistributionMode.STRICT_ROUND_ROBIN || sourceBlocks.isEmpty()) {
            return sourceBlocks;
        }
        return List.of(sourceBlocks.get(Math.floorMod(inputRoundRobinIndex, sourceBlocks.size())));
    }

    private List<Block> orderedTargets(List<Block> targetBlocks) {
        if (targetBlocks.isEmpty()) {
            return targetBlocks;
        }

        return switch (distributionMode) {
            case FIRST_PRIORITY -> targetBlocks;
            case STRICT_ROUND_ROBIN -> List.of(targetBlocks.get(Math.floorMod(outputRoundRobinIndex, targetBlocks.size())));
            case ROUND_ROBIN -> {
                int start = Math.floorMod(outputRoundRobinIndex, targetBlocks.size());
                List<Block> ordered = new ArrayList<>(targetBlocks.size());
                for (int i = 0; i < targetBlocks.size(); i++) {
                    ordered.add(targetBlocks.get((start + i) % targetBlocks.size()));
                }
                yield ordered;
            }
        };
    }

    private void markTransferSuccess(int sourceCount, int sourceIndex, int targetCount, int targetOffset) {
        if (distributionMode == DistributionMode.FIRST_PRIORITY) {
            return;
        }
        if (sourceCount > 0 && sourceIndex >= 0) {
            inputRoundRobinIndex = (sourceIndex + 1) % sourceCount;
        }
        if (targetCount > 0) {
            int targetIndex = switch (distributionMode) {
                case STRICT_ROUND_ROBIN -> Math.floorMod(outputRoundRobinIndex, targetCount);
                case ROUND_ROBIN -> Math.floorMod(outputRoundRobinIndex + targetOffset, targetCount);
                case FIRST_PRIORITY -> 0;
            };
            outputRoundRobinIndex = (targetIndex + 1) % targetCount;
        }
    }

    private boolean shouldWaitForBlockedRoute() {
        return distributionMode == DistributionMode.STRICT_ROUND_ROBIN;
    }

    private record TransferResult(boolean transferred, int targetOffset) {
        private static TransferResult success(int targetOffset) {
            return new TransferResult(true, targetOffset);
        }

        private static TransferResult failed() {
            return new TransferResult(false, -1);
        }
    }

    private boolean canFit(org.bukkit.inventory.Inventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        int maxStackSize = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize());

        for (ItemStack slotStack : inventory.getStorageContents()) {
            if (slotStack == null || slotStack.isEmpty()) {
                remaining -= maxStackSize;
            } else if (slotStack.isSimilar(stack)) {
                remaining -= Math.max(0, maxStackSize - slotStack.getAmount());
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the inventory of a block (chest, hopper, etc.)
     */
    private org.bukkit.inventory.Inventory getBlockInventory(Block block) {
        if (block == null || block.isEmpty()) return null;
        
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            return ((org.bukkit.block.Container) block.getState()).getInventory();
        } else if (type == Material.HOPPER) {
            return ((org.bukkit.block.Hopper) block.getState()).getInventory();
        }
        // Try generic container interface
        if (block.getState() instanceof org.bukkit.block.Container container) {
            return container.getInventory();
        }
        return null;
    }

    /**
     * Check if a block is a furnace
     */
    private boolean isFurnace(Block block) {
        if (block == null || block.isEmpty()) return false;
        Material type = block.getType();
        return type == Material.FURNACE 
            || type == Material.BLAST_FURNACE 
            || type == Material.SMOKER;
    }

    /**
     * Get source block list (from input targets or auto-scan)
     * input = 物品进来 = 源
     */
    private List<Block> getInputBlocks() {
        if (!hasCustomInputTargets()) {
            return new ArrayList<>();
        }
        return getBlocksFromTargets(inputTargets);
    }

    /**
     * Get target block list (from output targets or auto-scan)
     * output = 物品出去 = 目标
     */
    private List<Block> getOutputBlocks() {
        if (!hasCustomOutputTargets()) {
            return new ArrayList<>();
        }
        return getBlocksFromTargets(outputTargets);
    }

    /**
     * Get blocks from target locations
     */
    private List<Block> getBlocksFromTargets(List<BlockLocation> targets) {
        List<Block> blocks = new ArrayList<>();
        World world = getBlock().getWorld();
        
        for (BlockLocation loc : targets) {
            Block mainBlock = new Location(world, loc.x(), loc.y(), loc.z()).getBlock();
            if (!mainBlock.isEmpty() && isContainer(mainBlock)) {
                blocks.add(mainBlock);
            }
            if (loc.isLargeChest()) {
                Block partnerBlock = new Location(world, loc.partnerX(), loc.partnerY(), loc.partnerZ()).getBlock();
                if (!partnerBlock.isEmpty() && isContainer(partnerBlock)) {
                    blocks.add(partnerBlock);
                }
            }
        }
        return blocks;
    }

    /**
     * Check if a block is a container
     */
    private boolean isContainer(Block block) {
        if (block == null || block.isEmpty()) return false;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST 
            || type == Material.HOPPER || block.getState() instanceof org.bukkit.block.Container;
    }

    private boolean hasCustomInputTargets() {
        return !inputTargets.isEmpty();
    }

    private boolean hasCustomOutputTargets() {
        return !outputTargets.isEmpty();
    }

    private List<Endpoint> findEndpointsFromTargets(List<BlockLocation> targets, boolean isInput) {
        List<Endpoint> endpoints = new ArrayList<>();
        World world = getBlock().getWorld();

        for (BlockLocation loc : targets) {
            // Handle main block
            addEndpointsFromBlock(endpoints, world, loc.x(), loc.y(), loc.z(), isInput);

            // Handle partner block for large chests
            if (loc.isLargeChest()) {
                addEndpointsFromBlock(endpoints, world, loc.partnerX(), loc.partnerY(), loc.partnerZ(), isInput);
            }
        }

        endpoints.sort(Comparator
                .comparingInt(Endpoint::distanceSquared)
                .thenComparing(endpoint -> endpoint.block().getX())
                .thenComparing(endpoint -> endpoint.block().getY())
                .thenComparing(endpoint -> endpoint.block().getZ())
                .thenComparing(endpoint -> endpoint.groupName() != null ? endpoint.groupName() : ""));
        return endpoints;
    }

    /**
     * Add endpoints from a single block
     */
    private void addEndpointsFromBlock(List<Endpoint> endpoints, World world, int x, int y, int z, boolean isInput) {
        Block targetBlock = new Location(world, x, y, z).getBlock();
        if (targetBlock.isEmpty()) {
            return;
        }

        Map<String, LogisticGroup> groups = getLogisticGroups(targetBlock);
        for (Map.Entry<String, LogisticGroup> entry : groups.entrySet()) {
            if (matchesDirection(entry.getValue(), isInput)) {
                int distSq = distanceSquared(targetBlock);
                endpoints.add(new Endpoint(targetBlock, entry.getKey(), entry.getValue(), distSq));
            }
        }
    }

    /**
     * Represents a target location, supporting both single blocks and large chests (double chests).
     * For large chests, both positions are stored and treated as a single logical container.
     */
    public static class BlockLocation {
        private final int x, y, z;
        private final int partnerX, partnerY, partnerZ; // -1 means no partner (single block)
        private final boolean isLargeChest;

        public BlockLocation(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.partnerX = -1;
            this.partnerY = -1;
            this.partnerZ = -1;
            this.isLargeChest = false;
        }

        public BlockLocation(int x, int y, int z, int partnerX, int partnerY, int partnerZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.partnerX = partnerX;
            this.partnerY = partnerY;
            this.partnerZ = partnerZ;
            this.isLargeChest = true;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int partnerX() { return partnerX; }
        public int partnerY() { return partnerY; }
        public int partnerZ() { return partnerZ; }
        public boolean isLargeChest() { return isLargeChest; }

        /**
         * Check if this location contains the given position
         */
        public boolean containsPosition(int checkX, int checkY, int checkZ) {
            if (x == checkX && y == checkY && z == checkZ) {
                return true;
            }
            if (isLargeChest && partnerX == checkX && partnerY == checkY && partnerZ == checkZ) {
                return true;
            }
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof BlockLocation other) {
                // For large chests, compare both positions regardless of order
                if (this.isLargeChest && other.isLargeChest) {
                    return (this.x == other.x && this.y == other.y && this.z == other.z &&
                            this.partnerX == other.partnerX && this.partnerY == other.partnerY && this.partnerZ == other.partnerZ)
                            ||
                           (this.x == other.partnerX && this.y == other.partnerY && this.z == other.partnerZ &&
                            this.partnerX == other.x && this.partnerY == other.y && this.partnerZ == other.z);
                }
                // For single blocks
                return !this.isLargeChest && !other.isLargeChest &&
                       this.x == other.x && this.y == other.y && this.z == other.z;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // Normalize for large chests (always store smaller coords first)
            if (isLargeChest) {
                int minX = Math.min(x, partnerX);
                int minY = Math.min(y, partnerY);
                int maxX = Math.max(x, partnerX);
                int maxY = Math.max(y, partnerY);
                return java.util.Objects.hash(minX, minY, z, maxX, maxY, partnerZ);
            }
            return java.util.Objects.hash(x, y, z);
        }
    }

    /**
     * Check if a list of BlockLocations contains the given position (including large chest partners)
     */
    private boolean containsAnyPosition(List<BlockLocation> list, BlockLocation toCheck) {
        for (BlockLocation loc : list) {
            if (loc.containsPosition(toCheck.x(), toCheck.y(), toCheck.z())) {
                return true;
            }
            // Also check if the partner of the new location matches any existing location
            if (toCheck.isLargeChest() && loc.containsPosition(toCheck.partnerX(), toCheck.partnerY(), toCheck.partnerZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测并获取箱子位置，如果是大箱子则返回包含伙伴位置的位置对象
     */
    private BlockLocation detectLargeChest(Block target) {
        Material mat = target.getType();

        // Check if it's a chest type that can form large chests
        if (mat != Material.CHEST && mat != Material.TRAPPED_CHEST) {
            return new BlockLocation(target.getX(), target.getY(), target.getZ());
        }

        // Try to find partner chest in all 4 directions
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block adjacent = target.getRelative(face);
            if (adjacent.getType() == mat) {
                // Found a partner - return large chest location
                return new BlockLocation(
                        target.getX(), target.getY(), target.getZ(),
                        adjacent.getX(), adjacent.getY(), adjacent.getZ()
                );
            }
        }

        // No partner found - return single block location
        return new BlockLocation(target.getX(), target.getY(), target.getZ());
    }

    private List<Endpoint> findEndpoints(boolean input) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (x * x + y * y + z * z > range * range) {
                        continue;
                    }

                    Block candidate = getBlock().getRelative(x, y, z);
                    if (candidate.isEmpty()) {
                        continue;
                    }

                    Map<String, LogisticGroup> groups = getLogisticGroups(candidate);
                    for (Map.Entry<String, LogisticGroup> entry : groups.entrySet()) {
                        if (matchesDirection(entry.getValue(), input)) {
                            endpoints.add(new Endpoint(candidate, entry.getKey(), entry.getValue(), distanceSquared(candidate)));
                        }
                    }
                }
            }
        }

        endpoints.sort(Comparator
                .comparingInt(Endpoint::distanceSquared)
                .thenComparing(endpoint -> endpoint.block().getX())
                .thenComparing(endpoint -> endpoint.block().getY())
                .thenComparing(endpoint -> endpoint.block().getZ())
                .thenComparing(endpoint -> endpoint.groupName() != null ? endpoint.groupName() : ""));
        return endpoints;
    }

    private Map<String, LogisticGroup> getLogisticGroups(Block candidate) {
        RebarLogisticBlock logisticBlock = BlockStorage.getAs(RebarLogisticBlock.class, candidate);
        if (logisticBlock != null) {
            return logisticBlock.getLogisticGroups();
        }

        // Check for vanilla logistic slots (chests, hoppers, etc.)
        Map<String, LogisticGroup> groups = LogisticGroup.getVanillaLogisticSlots(candidate);
        if (!groups.isEmpty()) {
            return groups;
        }

        // Detect if this is a chest - try to get its logistic groups
        Material mat = candidate.getType();
        if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST) {
            // Try to find adjacent chest to merge with
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block adjacent = candidate.getRelative(face);
                if (adjacent.getType() == mat) {
                    // Check if this forms a valid double chest (proper orientation)
                    if (isValidDoubleChestPartner(candidate, adjacent, face)) {
                        // Try to get logistic groups from the adjacent chest
                        groups = LogisticGroup.getVanillaLogisticSlots(adjacent);
                        if (!groups.isEmpty()) {
                            return groups;
                        }
                    }
                }
            }
            // If no groups found from adjacent chest, try directly on this chest
            // The chest might be standalone
        }

        // For non-container blocks or standalone chests, return empty
        return groups;
    }

    /**
     * 检查两个箱子是否构成有效的大箱子（保持原有Bukkit行为）
     */
    private boolean isValidDoubleChestPartner(Block chest1, Block chest2, BlockFace face) {
        // 大箱子朝向检查：南北朝向时，合并NORTH-SOUTH
        // 东西朝向时，合并EAST-WEST
        // 这个检查确保只有正确的配对才能合并
        Material mat = chest1.getType();

        // 检查是否是箱子类型
        if (mat != Material.CHEST && mat != Material.TRAPPED_CHEST) {
            return false;
        }

        // 使用Bukkit原生的方块数据值来检查箱子朝向
        // Minecraft中箱子朝向：
        // 0=西, 1=东, 2=北, 3=南, 4=单向(小箱子或独立大箱子), 5=单向

        // 对于大箱子：
        // 北/南朝向：左边是NORTH，右边是SOUTH
        // 东/西朝向：左边是WEST，右边是EAST

        // 简化的检查：如果是CHEST类型
        if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST) {
            // 水平和垂直配对都是有效的
            return (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                    face == BlockFace.EAST || face == BlockFace.WEST);
        }

        return false;
    }

    private boolean matchesDirection(LogisticGroup group, boolean input) {
        return group.getSlotType() == LogisticGroupType.BOTH
                || group.getSlotType() == (input ? LogisticGroupType.INPUT : LogisticGroupType.OUTPUT);
    }

    private int distanceSquared(Block candidate) {
        int dx = candidate.getX() - getBlock().getX();
        int dy = candidate.getY() - getBlock().getY();
        int dz = candidate.getZ() - getBlock().getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * 计算周围环境的哈希值，用于检测环境变化
     * 如果哈希值改变，说明周围方块有变化，需要重新扫描
     */
    private int calculateSurroundingHash() {
        int hash = 0;
        int checkRadius = Math.min(range + 2, 8); // 限制检查范围以提高性能
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x * x + y * y + z * z > (checkRadius + 1) * (checkRadius + 1)) continue;
                    Block block = getBlock().getRelative(x, y, z);
                    hash = hash * 31 + (block.isEmpty() ? 0 : block.getType().hashCode());
                }
            }
        }
        return hash;
    }

    /**
     * 获取输入端点列表，使用缓存机制
     */
    private List<Endpoint> getInputEndpoints() {
        if (hasCustomInputTargets()) {
            return findEndpointsFromTargets(inputTargets, true);
        }
        return new ArrayList<>();
    }

    /**
     * 获取输出端点列表，使用缓存机制
     */
    private List<Endpoint> getOutputEndpoints() {
        if (hasCustomOutputTargets()) {
            return findEndpointsFromTargets(outputTargets, false);
        }
        return new ArrayList<>();
    }

    /**
     * 获取缓存的端点列表
     * 只有当缓存过期或环境变化时才重新扫描
     */
    private List<Endpoint> getCachedEndpoints(boolean isInput) {
        int currentHash = calculateSurroundingHash();
        int cachedHash = isInput ? cachedInputHash : cachedOutputHash;
        List<Endpoint> cachedList = isInput ? cachedInputEndpoints : cachedOutputEndpoints;

        // 检查是否需要刷新缓存
        boolean needsRefresh = cachedList == null
                || scanCacheTicks <= 0
                || currentHash != cachedHash;

        if (needsRefresh) {
            List<Endpoint> newList = findEndpoints(isInput);
            if (isInput) {
                cachedInputEndpoints = newList;
                cachedInputHash = currentHash;
            } else {
                cachedOutputEndpoints = newList;
                cachedOutputHash = currentHash;
            }
            scanCacheTicks = SCAN_CACHE_TICKS;
            return newList;
        }

        return cachedList != null ? cachedList : new ArrayList<>();
    }

    /**
     * 当目标被修改时清除缓存
     */
    private void invalidateCache() {
        cachedInputEndpoints = null;
        cachedOutputEndpoints = null;
        scanCacheTicks = 0;
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    private record Endpoint(Block block, String groupName, LogisticGroup group, int distanceSquared) {
    }

    private void openTargetListMenu(Player player, boolean input) {
        List<BlockLocation> targets = input ? inputTargets : outputTargets;
        var builder = Gui.builder()
                .setStructure(
                        "#########",
                        "#acdefgh#",
                        "###ij####",
                        "####b####",
                        "#########"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('b', new BackToMainGuiItem());

        char[] slots = {'a', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'};
        for (int i = 0; i < slots.length; i++) {
            if (i < targets.size()) {
                builder.addIngredient(slots[i], new TargetEntryItem(input, i));
            } else {
                builder.addIngredient(slots[i], GuiItems.background());
            }
        }

        Window.mergedBuilder()
                .setViewer(player)
                .setTitle(noItalic(Component.translatable("steamwork.gui.steam_arm.target_list."
                        + (input ? "input_title" : "output_title"))))
                .setGui(builder.build())
                .open(player);
    }

    private ItemStack targetDisplayStack(BlockLocation location) {
        Block block = new Location(getBlock().getWorld(), location.x(), location.y(), location.z()).getBlock();
        Material material = block.isEmpty() ? Material.BARRIER : block.getType();
        if (!material.isItem()) {
            material = Material.BARRIER;
        }
        return new ItemStack(material);
    }

    private Component targetCoordinateLine(BlockLocation location) {
        if (location.isLargeChest()) {
            return Component.translatable(
                    "steamwork.gui.steam_arm.target_list.coordinates_large",
                    RebarArgument.of("x", String.valueOf(location.x())),
                    RebarArgument.of("y", String.valueOf(location.y())),
                    RebarArgument.of("z", String.valueOf(location.z())),
                    RebarArgument.of("px", String.valueOf(location.partnerX())),
                    RebarArgument.of("py", String.valueOf(location.partnerY())),
                    RebarArgument.of("pz", String.valueOf(location.partnerZ()))
            );
        }
        return Component.translatable(
                "steamwork.gui.steam_arm.target_list.coordinates",
                RebarArgument.of("x", String.valueOf(location.x())),
                RebarArgument.of("y", String.valueOf(location.y())),
                RebarArgument.of("z", String.valueOf(location.z()))
        );
    }

    private final class BackToMainGuiItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.gui(Material.ARROW, SteamworkKeys.STEAM_ARM)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.target_list.back")))
                    .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_arm.target_list.back_hint"))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            Window.mergedBuilder()
                    .setViewer(player)
                    .setTitle(getGuiTitle())
                    .setGui(createGui())
                    .open(player);
        }
    }

    private final class TargetEntryItem extends AbstractItem {
        private final boolean input;
        private final int index;

        private TargetEntryItem(boolean input, int index) {
            this.input = input;
            this.index = index;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<BlockLocation> targets = input ? inputTargets : outputTargets;
            if (index >= targets.size()) {
                return GuiItems.background().getItemProvider(viewer);
            }

            BlockLocation location = targets.get(index);
            return ItemStackBuilder.of(targetDisplayStack(location))
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.target_list.entry",
                            RebarArgument.of("index", String.valueOf(index + 1)))))
                    .lore(List.of(
                            noItalic(targetCoordinateLine(location)),
                            noItalic(Component.translatable("steamwork.gui.steam_arm.target_list.remove_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (!clickType.isLeftClick()) {
                return;
            }

            List<BlockLocation> targets = input ? inputTargets : outputTargets;
            if (index >= targets.size()) {
                return;
            }

            targets.remove(index);
            invalidateCache();
            notifyGuiItems();
            openTargetListMenu(player, input);
        }
    }

    private final class ItemsPerTransferItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.items_per_transfer")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.items_hint")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.items_hint_shift")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.current_amount",
                    RebarArgument.of("amount", String.valueOf(itemsPerTransfer))
            ).color(NamedTextColor.AQUA)));

            return ItemStackBuilder.gui(Material.HOPPER, SteamworkKeys.STEAM_ARM)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.items_setting")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            int delta;
            if (clickType.isShiftClick()) {
                delta = clickType.isLeftClick() ? 10 : -10;
            } else {
                delta = clickType.isLeftClick() ? 1 : -1;
            }

            int newValue = itemsPerTransfer + delta;
            newValue = Math.max(MIN_ITEMS_PER_TRANSFER, Math.min(MAX_ITEMS_PER_TRANSFER, newValue));

            if (newValue != itemsPerTransfer) {
                itemsPerTransfer = newValue;
                notifyWindows();
            }
        }
    }

    private final class InputTargetItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.input_target.title")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.input_target.count",
                    RebarArgument.of("count", String.valueOf(inputTargets.size())),
                    RebarArgument.of("max", String.valueOf(MAX_TARGETS))
            )));

            if (selectingInputMode) {
                // Show selection mode hint
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_arm.range_limit",
                        RebarArgument.of("range", String.valueOf(range))
                )));
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.input_target.hint_select").color(NamedTextColor.YELLOW)));
            } else {
                // Show normal hints
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_arm.range_limit",
                        RebarArgument.of("range", String.valueOf(range))
                )));
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.input_target.hint")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.input_target.list_hint")));
            }

            Material mat = inputTargets.isEmpty() ? Material.MAP : Material.MAP;
            if (selectingInputMode) {
                mat = Material.GOLD_INGOT;
            }

            return ItemStackBuilder.gui(mat, SteamworkKeys.STEAM_ARM)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.input_target.name")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                if (selectingInputMode || PLAYERS_IN_INPUT_SELECTION.contains(player.getUniqueId())) {
                    // Cancel selection mode
                    selectingInputMode = false;
                    PLAYERS_IN_INPUT_SELECTION.remove(player.getUniqueId());
                    notifyWindows();
                    player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.selection_cancelled")));
                } else {
                    // Enter selection mode and close GUI
                    selectingInputMode = true;
                    selectingOutputMode = false;
                    PLAYERS_IN_OUTPUT_SELECTION.remove(player.getUniqueId());
                    PLAYERS_IN_INPUT_SELECTION.add(player.getUniqueId());
                    notifyWindows();
                    player.closeInventory();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.steam_arm.selecting_input_range",
                            RebarArgument.of("range", String.valueOf(range))
                    )));
                }
            } else if (clickType.isRightClick()) {
                openTargetListMenu(player, true);
            }
        }
    }

    private final class OutputTargetItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.output_target.title")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.output_target.count",
                    RebarArgument.of("count", String.valueOf(outputTargets.size())),
                    RebarArgument.of("max", String.valueOf(MAX_TARGETS))
            )));

            if (selectingOutputMode) {
                // Show selection mode hint
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_arm.range_limit",
                        RebarArgument.of("range", String.valueOf(range))
                )));
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.output_target.hint_select").color(NamedTextColor.YELLOW)));
            } else {
                // Show normal hints
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_arm.range_limit",
                        RebarArgument.of("range", String.valueOf(range))
                )));
                lore.add(noItalic(Component.text("")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.output_target.hint")));
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.output_target.list_hint")));
            }

            Material mat = outputTargets.isEmpty() ? Material.ENDER_EYE : Material.ENDER_EYE;
            if (selectingOutputMode) {
                mat = Material.GOLD_INGOT;
            }

            return ItemStackBuilder.gui(mat, SteamworkKeys.STEAM_ARM)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.output_target.name")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                if (selectingOutputMode || PLAYERS_IN_OUTPUT_SELECTION.contains(player.getUniqueId())) {
                    // Cancel selection mode
                    selectingOutputMode = false;
                    PLAYERS_IN_OUTPUT_SELECTION.remove(player.getUniqueId());
                    notifyWindows();
                    player.sendMessage(noItalic(Component.translatable("steamwork.message.steam_arm.selection_cancelled")));
                } else {
                    // Enter selection mode and close GUI
                    selectingOutputMode = true;
                    selectingInputMode = false;
                    PLAYERS_IN_INPUT_SELECTION.remove(player.getUniqueId());
                    PLAYERS_IN_OUTPUT_SELECTION.add(player.getUniqueId());
                    notifyWindows();
                    player.closeInventory();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.steam_arm.selecting_output_range",
                            RebarArgument.of("range", String.valueOf(range))
                    )));
                }
            } else if (clickType.isRightClick()) {
                openTargetListMenu(player, false);
            }
        }
    }

    private final class DistributionModeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.current",
                    RebarArgument.of("mode", Component.translatable(
                            "steamwork.gui.steam_arm.distribution.mode." + distributionMode.key
                    )))));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.mode.round_robin")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.desc.round_robin")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.mode.strict_round_robin")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.desc.strict_round_robin")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.mode.first_priority")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.desc.first_priority")));
            lore.add(noItalic(Component.text("")));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.hint")));

            return ItemStackBuilder.gui(distributionMode.icon, SteamworkKeys.STEAM_ARM)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.distribution.name")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                distributionMode = distributionMode.next();
            } else if (clickType.isRightClick()) {
                DistributionMode[] values = DistributionMode.values();
                distributionMode = values[Math.floorMod(distributionMode.ordinal() - 1, values.length)];
            } else {
                return;
            }
            inputRoundRobinIndex = 0;
            outputRoundRobinIndex = 0;
            notifyWindows();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean hasMotor = hasMotor();
            Material mat = !hasMotor ? Material.RED_STAINED_GLASS_PANE
                    : lastActive ? Material.GREEN_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;

            String statusKey = !hasMotor ? "missing_motor"
                    : lastActive ? "active" : "idle";

            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.steam_per_item",
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamPerItem()))
            )));
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.steam_per_operation",
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steamPerOperation()))
            )));

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.status." + statusKey)))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class SteamGaugeItem extends AbstractItem {
        private static final int PROGRESS_BAR_WIDTH = 20;

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.STEAM);
            double cap = fluidCapacity(SteamworkFluids.STEAM);
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));

            Material mat = pct >= 75 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct >= 50 ? Material.CYAN_STAINED_GLASS
                    : pct >= 25 ? Material.BLUE_STAINED_GLASS
                    : pct > 0 ? Material.GRAY_STAINED_GLASS
                    : Material.BLACK_STAINED_GLASS;

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_arm.steam_gauge")))
                    .lore(List.of(
                            steamLine(steam, cap),
                            progressBarLine(pct)
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }

        private @NotNull Component steamLine(double steam, double cap) {
            return noItalic(Component.translatable(
                    "steamwork.gui.steam_arm.steam",
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steam)),
                    RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(cap))
            ));
        }

        private @NotNull Component progressBarLine(int pct) {
            Component bar = barComponent(pct, PROGRESS_BAR_WIDTH);
            Component percent = percentComponent(pct);
            return noItalic(Component.text("")
                    .append(bar)
                    .append(Component.text(" ", NamedTextColor.GRAY))
                    .append(percent));
        }

        private @NotNull Component barComponent(int pct, int width) {
            Component bar = Component.empty();
            int filled = (int) Math.round(width * pct / 100.0);
            for (int i = 0; i < width; i++) {
                bar = bar.append(Component.text("|", i < filled ? progressColor(pct) : NamedTextColor.DARK_GRAY));
            }
            return bar;
        }

        private @NotNull TextColor progressColor(int pct) {
            if (pct >= 85) return NamedTextColor.GREEN;
            if (pct >= 50) return NamedTextColor.YELLOW;
            if (pct >= 20) return NamedTextColor.GOLD;
            return NamedTextColor.RED;
        }

        private @NotNull Component percentComponent(int pct) {
            return Component.text(pct + "%", NamedTextColor.AQUA);
        }
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
