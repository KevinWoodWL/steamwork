package io.github.steamwork.util;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.content.machines.PneumaticDuct;
import io.github.steamwork.content.machines.PneumaticGateValve;
import io.github.steamwork.content.machines.PneumaticInput;
import io.github.steamwork.content.machines.PneumaticOutput;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared display and facing helpers for pneumatic input/output endpoints.
 */
public final class PneumaticEndpointSupport {

    public static final double DISPLAY_SCAN_RADIUS = 1.25D;

    private static final BlockFace[] ALL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST,  BlockFace.WEST,
            BlockFace.UP,    BlockFace.DOWN
    };
    private static final double ENDPOINT_DUCT_THICKNESS = 0.35D;
    private static final double DIRECT_CONNECTION_THICKNESS = 0.3505D;

    private PneumaticEndpointSupport() {}

    public static boolean isChunkLoaded(@NotNull Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    public static @Nullable RebarBlock loadedRebarBlock(@NotNull Block block) {
        if (!isChunkLoaded(block)) return null;
        try {
            return BlockStorage.get(block);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static @NotNull BlockFace resolvePlacementFacing(@NotNull BlockCreateContext context) {
        BlockFace vertical = context.getFacingVertical();
        return vertical != BlockFace.SELF ? vertical : context.getFacing();
    }

    /**
     * Returns the face currently used by the pneumatic network.
     *
     * <p>Priority:
     * <ol>
     *   <li>Adjacent {@link PneumaticDuct} — normal networked setup.</li>
     *   <li>Adjacent aligned {@link PneumaticGateValve} — treated like a duct.</li>
     *   <li>Adjacent {@link PneumaticInput} or {@link PneumaticOutput} — direct
     *       endpoint-to-endpoint connection without a duct in between.</li>
     *   <li>Adjacent vanilla container — auto-orient so the endpoint pushes/pulls from it.</li>
     *   <li>Fallback: the back side of the endpoint body ({@code facing.getOppositeFace()}).</li>
     * </ol>
     * </p>
     */
    public static @NotNull BlockFace pneumaticConnectionFace(@NotNull Block block, @NotNull BlockFace facing) {
        // 对齐 Pylon CargoInteractor：容器贴合面固定在 facing.getOppositeFace()（长方形端），
        // 永远不作为网络连接面，扫描时直接跳过。
        BlockFace containerFace = facing.getOppositeFace();
        BlockFace directEndpointFace = null;
        for (BlockFace face : ALL_FACES) {
            if (face == containerFace) continue;
            RebarBlock rb = loadedRebarBlock(block.getRelative(face));
            if (rb instanceof PneumaticDuct) {
                return face;          // duct always takes priority
            }
            if (rb instanceof PneumaticGateValve valve && valve.isPneumaticAligned(face.getOppositeFace())) {
                return face;          // aligned gate valve treated like a duct
            }
            if (directEndpointFace == null && isDirectEndpoint(block, face)) {
                directEndpointFace = face;   // 首个双向都接受连接的相邻端点
            }
        }
        if (directEndpointFace != null) return directEndpointFace;
        // 无网络邻居：默认朝正前方（getFacing）——连接桩伸出、导管应连接的主网络面。
        return facing;
    }

    /**
     * Legacy free connector transform: point a short duct segment toward a known
     * connection face. Kept for callers that do not have endpoint-facing context.
     */
    public static @NotNull TransformBuilder ductTransform(@NotNull Block block, @NotNull BlockFace face) {
        boolean connected = PneumaticDuct.isNetworkConnector(block.getRelative(face));
        if (connected) {
            return new TransformBuilder()
                    .lookAlong(face)
                    .translate(0, 0, 0.1)
                    .scale(0.35, 0.35, 0.80);
        }
        return new TransformBuilder()
                .lookAlong(face)
                .translate(0, 0, 0.0625)
                .scale(0.35, 0.35, 0.475);
    }

    /**
     * Pylon cargo-extractor style endpoint duct: the endpoint owns only the short
     * body connector, while a duct block or direct connection line draws the
     * network segment into it.
     */
    public static @NotNull TransformBuilder ductTransform(@NotNull Block block,
                                                          @NotNull BlockFace face,
                                                          @NotNull BlockFace endpointFacing) {
        return new TransformBuilder()
                .lookAlong(endpointFacing)
                .translate(0, 0, -0.0625)
                .scale(ENDPOINT_DUCT_THICKNESS, ENDPOINT_DUCT_THICKNESS, 0.475);
    }

    public static boolean isDirectEndpoint(@NotNull Block block, @NotNull BlockFace face) {
        // 端点直连：本端在 face、对端在 face 的反面都必须接受汽动连接
        //（双方都不能用各自的容器贴合面），否则不视为可直连——避免长方形端误连。
        return endpointAcceptsOn(loadedRebarBlock(block), face)
                && endpointAcceptsOn(loadedRebarBlock(block.getRelative(face)), face.getOppositeFace());
    }

    private static boolean endpointAcceptsOn(@Nullable RebarBlock rb, @NotNull BlockFace face) {
        if (rb instanceof PneumaticInput in) return in.acceptsPneumaticConnection(face);
        if (rb instanceof PneumaticOutput out) return out.acceptsPneumaticConnection(face);
        return false;
    }

    public static boolean shouldOwnDirectConnection(@NotNull Block block, @NotNull BlockFace face) {
        Block neighbor = block.getRelative(face);
        if (!isDirectEndpoint(block, face)) return false;
        if (block.getX() != neighbor.getX()) return block.getX() < neighbor.getX();
        if (block.getY() != neighbor.getY()) return block.getY() < neighbor.getY();
        return block.getZ() < neighbor.getZ();
    }

    public static @NotNull TransformBuilder directConnectionTransform(@NotNull BlockFace face) {
        return new LineBuilder()
                .from(new Vector3d())
                .to(new Vector3d(face.getModX(), face.getModY(), face.getModZ()))
                .thickness(DIRECT_CONNECTION_THICKNESS)
                .extraLength(DIRECT_CONNECTION_THICKNESS)
                .build();
    }

    public static @NotNull ItemDisplay createDisplay(
            @NotNull Block block,
            @NotNull Material material,
            @NotNull String model,
            @NotNull TransformBuilder transform,
            @NotNull NamespacedKey markerKey,
            @NotNull NamespacedKey ownerKey) {
        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(material).addCustomModelDataString(model).build())
                .transformation(transform)
                .persistent(true)
                .build(block.getLocation().toCenterLocation());
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(markerKey, RebarSerializers.BOOLEAN, true);
        pdc.set(ownerKey, RebarSerializers.INTEGER_ARRAY, new int[] {
                block.getX(), block.getY(), block.getZ()
        });
        return display;
    }

    public static @NotNull List<ItemDisplay> findManagedDisplays(
            @NotNull Block block,
            @NotNull NamespacedKey markerKey,
            @NotNull NamespacedKey ownerKey) {
        BoundingBox box = BoundingBox.of(block).expand(DISPLAY_SCAN_RADIUS);
        List<ItemDisplay> displays = new ArrayList<>();
        for (Entity entity : block.getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof ItemDisplay display)) continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!Boolean.TRUE.equals(pdc.get(markerKey, RebarSerializers.BOOLEAN))) continue;
            int[] owner = pdc.get(ownerKey, RebarSerializers.INTEGER_ARRAY);
            if (owner == null || owner.length != 3) continue;
            if (owner[0] == block.getX() && owner[1] == block.getY() && owner[2] == block.getZ()) {
                displays.add(display);
            }
        }
        return displays;
    }

    public static void clearManagedDisplays(
            @NotNull Block block,
            @NotNull NamespacedKey markerKey,
            @NotNull NamespacedKey ownerKey) {
        for (ItemDisplay display : findManagedDisplays(block, markerKey, ownerKey)) {
            if (display.isValid()) display.remove();
        }
    }

    // ── 容器访问面（独立于导管位置）────────────────────────────────────────────

    /**
     * 返回端点应当推入/抽取的相邻容器/机器所在面。
     *
     * <p>与 {@link #pneumaticConnectionFace} 的区别：本方法完全不依赖相邻导管的位置，
     * 因此平行放置的导管不会把容器识别方向搞错。
     *
     * <p>优先级：
     * <ol>
     *   <li>放置朝向（{@code facing}）有合法物品目标，且不是汽动网络方块。</li>
     *   <li>扫描全部 6 个面，跳过所有汽动网络方块，返回第一个合法物品目标面。</li>
     *   <li>回退：放置朝向（无容器时的默认值）。</li>
     * </ol>
     * </p>
     */
    public static @NotNull BlockFace containerAccessFace(@NotNull Block block, @NotNull BlockFace facing) {
        Block facingBlock = block.getRelative(facing);
        if (!isPneumaticNetworkBlock(facingBlock) && PneumaticUtils.isItemTarget(facingBlock)) {
            return facing;
        }
        for (BlockFace face : ALL_FACES) {
            Block neighbor = block.getRelative(face);
            if (isPneumaticNetworkBlock(neighbor)) continue;
            if (PneumaticUtils.isItemTarget(neighbor)) return face;
        }
        return facing;
    }

    private static boolean isPneumaticNetworkBlock(@NotNull Block block) {
        RebarBlock rb = loadedRebarBlock(block);
        return rb instanceof PneumaticDuct
                || rb instanceof PneumaticGateValve
                || rb instanceof PneumaticInput
                || rb instanceof PneumaticOutput;
    }
}
