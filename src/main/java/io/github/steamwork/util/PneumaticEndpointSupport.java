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
     *   <li>Adjacent {@link PneumaticInput} or {@link PneumaticOutput} — direct
     *       endpoint-to-endpoint connection without a duct in between.</li>
     *   <li>Fallback: the back side of the endpoint body ({@code facing.getOppositeFace()}).</li>
     * </ol>
     * </p>
     */
    public static @NotNull BlockFace pneumaticConnectionFace(@NotNull Block block, @NotNull BlockFace facing) {
        BlockFace directEndpointFace = null;
        for (BlockFace face : ALL_FACES) {
            RebarBlock rb = BlockStorage.get(block.getRelative(face));
            if (rb instanceof PneumaticDuct) {
                return face;          // duct always takes priority
            }
            if (directEndpointFace == null
                    && (rb instanceof PneumaticInput || rb instanceof PneumaticOutput)) {
                directEndpointFace = face;   // remember first direct endpoint hit
            }
        }
        return directEndpointFace != null ? directEndpointFace : facing.getOppositeFace();
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
        RebarBlock neighbor = BlockStorage.get(block.getRelative(face));
        return neighbor instanceof PneumaticInput || neighbor instanceof PneumaticOutput;
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
}
