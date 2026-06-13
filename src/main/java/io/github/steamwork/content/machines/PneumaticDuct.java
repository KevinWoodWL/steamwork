package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.EntityCulledRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FacadeRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.PneumaticEndpointSupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * Pneumatic duct block using CargoDuct-style item displays while retaining
 * Steamwork's multi-branch network behavior.
 */
public class PneumaticDuct extends RebarBlock implements
        BlockBreakRebarBlockHandler,
        EntityCulledRebarBlock,
        FacadeRebarBlock {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };

    private static final float SINGLE_SCALE = 0.35F;
    private static final double HALF_SEGMENT = 0.5D;
    private static final double ENDPOINT_SEGMENT = 1.0D;

    /**
     * Three slightly-different thicknesses assigned round-robin to face segments.
     * Prevents Z-fighting where multiple segments originate from the same duct centre
     * and their bounding boxes overlap in the joint area.
     * 0.35F is intentionally excluded — that value is reserved for blocks that create
     * seamless visual connections to endpoint connectors using CargoDuct's convention.
     */
    private static final float[] LINE_THICKNESSES = {0.3495F, 0.3490F, 0.3485F};
    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final int MAX_CONNECTIONS = 3;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_duct_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY = steamworkKey("pneumatic_duct_display_owner");

    /**
     * 缓存当前所有受管 Display 的 UUID。
     * volatile + 不可变快照：主线程写，culling 协程（异步）只读，无需同步块。
     */
    private volatile List<UUID> displayUuids = List.of();

    private List<BlockFace> lastConnectedFaces = List.of();
    private boolean refreshing = false;

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of();
        }
    }

    @SuppressWarnings("unused")
    public PneumaticDuct(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public PneumaticDuct(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void postInitialise() {
        setDisableBlockTextureEntity(true);
        refreshDisplays();
        notifyNeighbors();
        // 服务器启动 / 区块加载时邻居方块可能尚未注册到 BlockStorage，
        // 第一次连接判定会全部失败。延迟一拍再重算一次，等同步邻居都就绪。
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                io.github.steamwork.Steamwork.getInstance(),
                () -> {
                    if (!PneumaticEndpointSupport.isChunkLoaded(getBlock())) return;
                    if (PneumaticEndpointSupport.loadedRebarBlock(getBlock()) != this) return;
                    refreshDisplays();
                    notifyNeighbors();
                },
                4L);
    }

    @Override
    public @NotNull Iterable<UUID> getCulledEntityIds() {
        // 直接返回主线程维护的缓存快照，不调用任何 Bukkit API（异步安全）
        return displayUuids;
    }

    @Override
    public @NotNull Material getFacadeDefaultBlockType() {
        return Material.STRUCTURE_VOID;
    }

    public boolean isConnected(@NotNull BlockFace face) {
        return getConnectedFaces().contains(face);
    }

    private boolean canConnectRaw(@NotNull BlockFace face) {
        Block neighbor = getBlock().getRelative(face);
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(neighbor);
        return rb instanceof PneumaticDuct
                || rb instanceof PneumaticGateValve valve
                        && valve.acceptsPneumaticConnection(face.getOppositeFace())
                || rb instanceof PneumaticPressureModule module
                        && module.acceptsPneumaticConnection(face.getOppositeFace())
                || rb instanceof SteamCatapult
                || rb instanceof PneumaticInput input
                        && input.acceptsPneumaticConnection(face.getOppositeFace())
                || rb instanceof PneumaticOutput output
                        && output.acceptsPneumaticConnection(face.getOppositeFace());
    }

    private @NotNull List<BlockFace> getConnectedFaces() {
        List<BlockFace> connectedFaces = new ArrayList<>();
        for (BlockFace face : FACES) {
            if (canConnectRaw(face) && hasReciprocalConnection(face)) {
                connectedFaces.add(face);
                if (connectedFaces.size() == MAX_CONNECTIONS) {
                    break;
                }
            }
        }
        return connectedFaces;
    }

    private @NotNull List<BlockFace> getPreferredConnectableFaces() {
        List<BlockFace> faces = new ArrayList<>();
        for (BlockFace face : FACES) {
            if (canConnectRaw(face)) {
                faces.add(face);
                if (faces.size() == MAX_CONNECTIONS) {
                    break;
                }
            }
        }
        return faces;
    }

    private boolean hasReciprocalConnection(@NotNull BlockFace face) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(face));
        if (rb instanceof PneumaticDuct duct) {
            return duct.getPreferredConnectableFaces().contains(face.getOppositeFace());
        }
        if (rb instanceof PneumaticGateValve valve) {
            return valve.acceptsPneumaticConnection(face.getOppositeFace());
        }
        if (rb instanceof PneumaticPressureModule module) {
            return module.acceptsPneumaticConnection(face.getOppositeFace());
        }
        return true;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearDisplays();
    }

    @Override
    public void onPostBlockBreak(@NotNull BlockBreakContext context) {
        notifyNeighbors();
    }

    private void notifyNeighbors() {
        notifyNeighboringDucts(getBlock());
    }

    /** PneumaticInput/Output 放置或破坏时调用，通知相邻导管刷新显示。 */
    public static void notifyNeighboringDucts(@NotNull Block origin) {
        for (BlockFace face : FACES) {
            Block neighbor = origin.getRelative(face);
            RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(neighbor);
            if (rb instanceof PneumaticDuct duct) {
                duct.refreshDisplays();
            } else if (rb instanceof PneumaticGateValve valve) {
                valve.refreshDisplays();
            } else if (rb instanceof PneumaticInput input) {
                input.refreshDisplays();
            } else if (rb instanceof PneumaticOutput output) {
                output.refreshDisplays();
            } else if (rb instanceof PneumaticPressureModule module) {
                module.refreshDisplays();
            }
        }
    }

    private void refreshDisplays() {
        if (refreshing) return;
        refreshing = true;
        try {
            clearDisplays();

            List<BlockFace> connectedFaces = getConnectedFaces();
            List<UUID> newUuids = new ArrayList<>();

            if (connectedFaces.isEmpty()) {
                newUuids.add(createSingleDisplay().getUniqueId());
            } else {
                for (int i = 0; i < connectedFaces.size(); i++) {
                    newUuids.add(createFaceDisplay(connectedFaces.get(i), i).getUniqueId());
                }
            }

            // 原子性地替换快照，异步 culling 线程读到的永远是完整列表
            displayUuids = List.copyOf(newUuids);

            if (!connectedFaces.equals(lastConnectedFaces)) {
                lastConnectedFaces = List.copyOf(connectedFaces);
                notifyNeighbors();
            }
        } finally {
            refreshing = false;
        }
    }

    private void clearDisplays() {
        for (ItemDisplay display : findManagedDisplays()) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displayUuids = List.of();
    }

    private @NotNull ItemDisplay createSingleDisplay() {
        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ductSingleStack())
                .transformation(new TransformBuilder().scale(SINGLE_SCALE))
                .persistent(true)
                .build(getBlock().getLocation().toCenterLocation());
        markDisplay(display);
        return display;
    }

    private @NotNull ItemDisplay createFaceDisplay(@NotNull BlockFace face, int index) {
        float thickness = LINE_THICKNESSES[index % LINE_THICKNESSES.length];
        Location center = getBlock().getLocation().toCenterLocation();
        // LineBuilder 以 from→to 中点为中心、长度为 distance+extraLength，故 extraLength 会向两端
        // 各延伸一半。原本前端会越过方块边界（0.5）探进相邻方块约 thickness/2，口型/拐角连接时
        // 与邻居段过度交叠/穿模。把 to 缩短 thickness/2，使前端正好落在方块边界、不再越界探入。
        double reach = faceSegmentLength(face) - thickness / 2.0;
        Vector3d from = new Vector3d();
        Vector3d to = new Vector3d(face.getModX() * reach, face.getModY() * reach, face.getModZ() * reach);

        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ductLineStack())
                .transformation(new LineBuilder()
                        .from(from)
                        .to(to)
                        .thickness(thickness)
                        .extraLength(thickness)
                        .build())
                .persistent(true)
                .build(center);
        markDisplay(display);
        return display;
    }

    private double faceSegmentLength(@NotNull BlockFace face) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(face));
        return rb instanceof PneumaticInput || rb instanceof PneumaticOutput
                ? ENDPOINT_SEGMENT
                : HALF_SEGMENT;
    }

    private ItemStack ductLineStack() {
        return ItemStackBuilder.of(Material.GRAY_CONCRETE)
                .addCustomModelDataString(SteamworkKeys.PNEUMATIC_DUCT + ":line")
                .build();
    }

    private ItemStack ductSingleStack() {
        return ItemStackBuilder.of(Material.GRAY_CONCRETE)
                .addCustomModelDataString(SteamworkKeys.PNEUMATIC_DUCT + ":single")
                .build();
    }

    private void markDisplay(@NotNull ItemDisplay display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN, true);
        pdc.set(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY, new int[] {
                getBlock().getX(),
                getBlock().getY(),
                getBlock().getZ()
        });
    }

    private @NotNull List<ItemDisplay> findManagedDisplays() {
        BoundingBox box = BoundingBox.of(getBlock()).expand(DISPLAY_SCAN_RADIUS);
        List<ItemDisplay> displays = new ArrayList<>();
        for (Entity entity : getBlock().getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof ItemDisplay display)) {
                continue;
            }
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!Boolean.TRUE.equals(pdc.get(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN))) {
                continue;
            }

            int[] owner = pdc.get(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY);
            if (owner == null || owner.length != 3) {
                continue;
            }
            if (owner[0] == getBlock().getX() && owner[1] == getBlock().getY() && owner[2] == getBlock().getZ()) {
                displays.add(display);
            }
        }
        return displays;
    }

    public static boolean isNetworkDuct(@NotNull Block block) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(block);
        return rb instanceof PneumaticDuct
                || rb instanceof PneumaticGateValve
                || rb instanceof PneumaticPressureModule;
    }

    public static @NotNull List<Block> findReachableEndpoints(@NotNull Block origin) {
        List<Block> endpoints = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            for (BlockFace face : getTraversalFaces(current)) {
                Block neighbor = current.getRelative(face);
                if (visited.contains(neighbor)) {
                    continue;
                }
                visited.add(neighbor);

                RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(neighbor);
                if (rb instanceof PneumaticDuct) {
                    queue.add(neighbor);
                } else if (rb instanceof PneumaticGateValve valve
                        && valve.acceptsPneumaticConnection(face.getOppositeFace())) {
                    queue.add(neighbor);
                } else if (rb instanceof PneumaticPressureModule module
                        && module.acceptsPneumaticConnection(face.getOppositeFace())) {
                    queue.add(neighbor);
                } else if (rb instanceof SteamCatapult
                        || rb instanceof PneumaticInput input
                                && input.acceptsPneumaticConnection(face.getOppositeFace())) {
                    endpoints.add(neighbor);
                }
            }
        }
        return endpoints;
    }

    private static @NotNull Iterable<BlockFace> getTraversalFaces(@NotNull Block block) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(block);
        if (rb instanceof PneumaticDuct duct) {
            return duct.getConnectedFaces();
        } else if (rb instanceof PneumaticGateValve valve) {
            return valve.getTraversalFaces();
        } else if (rb instanceof PneumaticPressureModule module) {
            return module.getTraversalFaces();
        }
        return List.of(FACES);
    }

    public static boolean isReachable(@NotNull Block from, @NotNull Block to) {
        return findReachableEndpoints(from).contains(to)
                || findReachableEndpoints(to).contains(from);
    }

    public static boolean isNetworkConnector(@NotNull Block block) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(block);
        return rb instanceof PneumaticDuct
                || rb instanceof PneumaticGateValve
                || rb instanceof PneumaticPressureModule
                || rb instanceof PneumaticInput
                || rb instanceof PneumaticOutput;
    }

    // ===== 压力管道模块：传输路径感知 =====

    /**
     * 物品传输事件回调：找到 origin→dest 路径上经过的 {@link PneumaticPressureModule}，
     * 通知它们物品通过（delivered=true）或被下游拒收（delivered=false）。
     * 由 PneumaticOutput/SteamSorter/PneumaticDistributor 在每次 tryPushItem(s) 后调用。
     */
    public static void notifyPassage(@NotNull Block origin, @NotNull Block dest, int count, boolean delivered) {
        if (count <= 0 || origin.equals(dest)) return;
        for (PneumaticPressureModule module : modulesOnPath(origin, dest)) {
            if (delivered) {
                module.onItemsPassed(count);
            } else {
                module.onTransferBlocked(count);
            }
        }
    }

    /** 从端点 origin 经导管网络 BFS 找到到达端点 dest 的一条路径，返回路径上的压力模块。 */
    private static @NotNull List<PneumaticPressureModule> modulesOnPath(@NotNull Block origin, @NotNull Block dest) {
        Map<Block, Block> prev = new HashMap<>();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        boolean found = false;

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            if (current.equals(dest)) {
                found = true;
                break;
            }
            for (BlockFace face : pathTraversalFaces(current, origin, dest)) {
                Block neighbor = current.getRelative(face);
                if (visited.contains(neighbor)) continue;
                if (neighbor.equals(dest) || isNetworkRelay(neighbor)) {
                    visited.add(neighbor);
                    prev.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        List<PneumaticPressureModule> modules = new ArrayList<>();
        if (!found) return modules;
        Block step = prev.get(dest);
        while (step != null && !step.equals(origin)) {
            if (PneumaticEndpointSupport.loadedRebarBlock(step) instanceof PneumaticPressureModule module) {
                modules.add(module);
            }
            step = prev.get(step);
        }
        return modules;
    }

    /** 路径 BFS 的可遍历面：端点（origin/dest）走全部 6 面，网络中继走各自通行面。 */
    private static @NotNull Iterable<BlockFace> pathTraversalFaces(@NotNull Block block,
                                                                   @NotNull Block origin,
                                                                   @NotNull Block dest) {
        if (block.equals(origin) || block.equals(dest)) {
            return List.of(FACES);
        }
        return getTraversalFaces(block);
    }

    /** 是否是可中继物品流的网络节点（导管/截止阀/压力模块）。 */
    private static boolean isNetworkRelay(@NotNull Block block) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(block);
        return rb instanceof PneumaticDuct
                || rb instanceof PneumaticGateValve
                || rb instanceof PneumaticPressureModule;
    }
}
