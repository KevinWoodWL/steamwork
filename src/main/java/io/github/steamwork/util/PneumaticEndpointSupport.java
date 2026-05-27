package io.github.steamwork.util;

import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.content.machines.PneumaticDuct;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享给 {@code PneumaticInput} / {@code PneumaticOutput} 的显示实体与朝向计算工具。
 *
 * <p>两端点都是 STRUCTURE_VOID 适配器方块，使用同一套 ItemDisplay 渲染（主体 + 标志 + 一段导管），
 * 朝向/管道长度计算也完全一致。这里把高度重复的 PDC 标记 + 朝向 + 实体回收逻辑集中到一处。</p>
 */
public final class PneumaticEndpointSupport {

    public  static final double DISPLAY_SCAN_RADIUS     = 1.25D;
    private PneumaticEndpointSupport() {}

    /** 优先使用玩家视线的竖直方向（俯视 → DOWN，仰视 → UP），否则回退到水平面。 */
    public static @NotNull BlockFace resolvePlacementFacing(@NotNull BlockCreateContext context) {
        BlockFace vertical = context.getFacingVertical();
        return vertical != BlockFace.SELF ? vertical : context.getFacing();
    }

    /**
     * 端点的导管侧朝向：若 {@code facing} 那一面紧邻一个网络连接器则取 facing，
     * 否则取其反面——保证玩家放置时朝任意方向都能自动对齐导管。
     */
    public static @NotNull BlockFace pneumaticConnectionFace(@NotNull Block block, @NotNull BlockFace facing) {
        return PneumaticDuct.isNetworkConnector(block.getRelative(facing))
                ? facing : facing.getOppositeFace();
    }

    /**
     * 朝指定面延伸的一段管道 Transform。
     *
     * <ul>
     *   <li><b>已连接</b>（face 侧紧邻导管）：管道从方块中心延伸到方块面（0.5），
     *       与 {@link PneumaticDuct} 的 line display（HALF_SEGMENT=0.5）无缝对接。</li>
     *   <li><b>未连接</b>：管道向内收，内侧端与主体背面（0.3 格）齐平，外侧端不超出方块面，
     *       视觉上贴合主体、不外露。</li>
     * </ul>
     */
    public static @NotNull TransformBuilder ductTransform(@NotNull Block block, @NotNull BlockFace face) {
        boolean connected = PneumaticDuct.isNetworkConnector(block.getRelative(face));
        if (connected) {
            // 管道从主体背面（-0.3）延伸到方块面（+0.5），总长 0.8，center 在 +0.1。
            // translate(+0.1) 在 world Z 上偏移；lookAlong 旋转把 +Z 对齐 ductFace 方向，
            // 因此 ±0.4 的延伸自然落在正确的侧面。
            return new TransformBuilder()
                    .lookAlong(face)
                    .translate(0, 0, 0.1)
                    .scale(0.35, 0.35, 0.80);
        } else {
            // 内侧端贴合主体背面（0.3），外侧端收在方块内（0.175 处），不外露
            return new TransformBuilder()
                    .lookAlong(face)
                    .translate(0, 0, 0.0625)
                    .scale(0.35, 0.35, 0.475);
        }
    }

    /**
     * 创建一个属于本方块的 ItemDisplay 并打上 PDC 标记，便于后续
     * {@link #findManagedDisplays} / {@link #clearManagedDisplays} 回收。
     */
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

    /** 扫描方块周围所有由本方块创建（marker + owner 坐标都匹配）的 ItemDisplay。 */
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

    /** 移除所有匹配的 ItemDisplay（用于刷新 / 破坏时回收）。 */
    public static void clearManagedDisplays(
            @NotNull Block block,
            @NotNull NamespacedKey markerKey,
            @NotNull NamespacedKey ownerKey) {
        for (ItemDisplay display : findManagedDisplays(block, markerKey, ownerKey)) {
            if (display.isValid()) display.remove();
        }
    }
}
