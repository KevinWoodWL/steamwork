package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.EntityCulledRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FacadeRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.LineBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.Steamwork;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.PneumaticEndpointSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class PneumaticGateValve extends RebarBlock implements
        BlockBreakRebarBlockHandler,
        DirectionalRebarBlock,
        EntityCulledRebarBlock,
        FacadeRebarBlock,
        TickingRebarBlock {

    private static final double DISPLAY_SCAN_RADIUS = 1.25D;
    private static final double THICKNESS = 0.35D;
    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_gate_valve_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY = steamworkKey("pneumatic_gate_valve_display_owner");

    private final int tickInterval = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 2);

    private volatile List<UUID> displayUuids = List.of();
    private boolean open = true;

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
    public PneumaticGateValve(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public PneumaticGateValve(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try {
            getFacing();
        } catch (IllegalStateException e) {
            setFacing(BlockFace.SOUTH);
        }
        setTickInterval(tickInterval);
    }

    @Override
    public void postInitialise() {
        setDisableBlockTextureEntity(true);
        open = !getBlock().isBlockPowered();
        refreshDisplays();
        PneumaticDuct.notifyNeighboringDucts(getBlock());
        Bukkit.getScheduler().runTaskLater(
                Steamwork.getInstance(),
                () -> {
                    if (!PneumaticEndpointSupport.isChunkLoaded(getBlock())) return;
                    if (PneumaticEndpointSupport.loadedRebarBlock(getBlock()) != this) return;
                    open = !getBlock().isBlockPowered();
                    refreshDisplays();
                    PneumaticDuct.notifyNeighboringDucts(getBlock());
                },
                4L);
    }

    @Override
    public void tick() {
        boolean nextOpen = !getBlock().isBlockPowered();
        if (nextOpen == open) return;
        open = nextOpen;
        refreshDisplays();
        PneumaticDuct.notifyNeighboringDucts(getBlock());
        refreshBlockTextureItem();
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearDisplays();
    }

    @Override
    public void onPostBlockBreak(@NotNull BlockBreakContext context) {
        PneumaticDuct.notifyNeighboringDucts(getBlock());
    }

    @Override
    public @NotNull Iterable<UUID> getCulledEntityIds() {
        return displayUuids;
    }

    @Override
    public @NotNull Material getFacadeDefaultBlockType() {
        return Material.STRUCTURE_VOID;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean acceptsPneumaticConnection(@NotNull BlockFace face) {
        if (!open) return false;
        return face == getFacing() || face == getFacing().getOppositeFace();
    }

    public boolean isPneumaticAligned(@NotNull BlockFace face) {
        return face == getFacing() || face == getFacing().getOppositeFace();
    }

    public @NotNull List<BlockFace> getTraversalFaces() {
        return open ? List.of(getFacing(), getFacing().getOppositeFace()) : List.of();
    }

    @Override
    public @NotNull java.util.Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("open", new kotlin.Pair<>(Boolean.toString(open), 2));
        return props;
    }

    @Override
    public WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("state", Component.translatable(
                        open
                                ? "steamwork.gui.pneumatic_gate_valve.state.open"
                                : "steamwork.gui.pneumatic_gate_valve.state.closed"))
        ));
    }

    void refreshDisplays() {
        clearDisplays();
        BlockFace facing = getFacing();
        double negEnd = isEndpointNeighbor(facing.getOppositeFace()) ? -1.0D : -0.5D;
        double posEnd = isEndpointNeighbor(facing) ? 1.0D : 0.5D;

        List<UUID> ids = new ArrayList<>();
        if (open) {
            // 放行：导管完整连通 + 熄灭的红石灯核心（让阀门在不工作时也能被一眼认出）
            ids.add(createLineDisplay(negEnd, posEnd, Material.GRAY_CONCRETE).getUniqueId());
            ids.add(createCoreDisplay(false).getUniqueId());
        } else {
            // 截断：导管断开 + 点亮的红石灯核心
            ids.add(createLineDisplay(negEnd, -0.18D, Material.GRAY_CONCRETE).getUniqueId());
            ids.add(createLineDisplay(0.18D, posEnd, Material.GRAY_CONCRETE).getUniqueId());
            ids.add(createCoreDisplay(true).getUniqueId());
        }
        displayUuids = List.copyOf(ids);
    }

    private boolean isEndpointNeighbor(@NotNull BlockFace face) {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(face));
        return rb instanceof PneumaticInput || rb instanceof PneumaticOutput;
    }

    private @NotNull ItemDisplay createLineDisplay(double fromScale, double toScale, @NotNull Material material) {
        BlockFace facing = getFacing();
        Vector3d from = new Vector3d(facing.getModX() * fromScale, facing.getModY() * fromScale, facing.getModZ() * fromScale);
        Vector3d to = new Vector3d(facing.getModX() * toScale, facing.getModY() * toScale, facing.getModZ() * toScale);
        ItemDisplay display = new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(material)
                        .addCustomModelDataString(SteamworkKeys.PNEUMATIC_DUCT + ":line")
                        .build())
                .transformation(new LineBuilder()
                        .from(from)
                        .to(to)
                        .thickness(THICKNESS)
                        .extraLength(0.0)
                        .build())
                .persistent(true)
                .build(center());
        markDisplay(display);
        return display;
    }

    private @NotNull BlockDisplay createCoreDisplay(boolean lit) {
        BlockData lampData = Material.REDSTONE_LAMP.createBlockData();
        if (lampData instanceof Lightable lightable) {
            lightable.setLit(lit);
        }
        BlockDisplay display = new BlockDisplayBuilder()
                .blockData(lampData)
                .transformation(new TransformBuilder().scale(0.42))
                .persistent(true)
                .build(center());
        markDisplay(display);
        return display;
    }

    private @NotNull Location center() {
        return getBlock().getLocation().toCenterLocation();
    }

    private void clearDisplays() {
        for (Display display : findManagedDisplays()) {
            if (display.isValid()) display.remove();
        }
        displayUuids = List.of();
    }

    private void markDisplay(@NotNull Entity display) {
        PersistentDataContainer pdc = display.getPersistentDataContainer();
        pdc.set(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN, true);
        pdc.set(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY, new int[] {
                getBlock().getX(), getBlock().getY(), getBlock().getZ()
        });
    }

    private @NotNull List<Display> findManagedDisplays() {
        BoundingBox box = BoundingBox.of(getBlock()).expand(DISPLAY_SCAN_RADIUS);
        List<Display> displays = new ArrayList<>();
        for (Entity entity : getBlock().getWorld().getNearbyEntities(box)) {
            if (!(entity instanceof Display display)) continue;
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            if (!Boolean.TRUE.equals(pdc.get(DISPLAY_MARKER_KEY, RebarSerializers.BOOLEAN))) continue;
            int[] owner = pdc.get(DISPLAY_OWNER_KEY, RebarSerializers.INTEGER_ARRAY);
            if (owner == null || owner.length != 3) continue;
            if (owner[0] == getBlock().getX() && owner[1] == getBlock().getY() && owner[2] == getBlock().getZ()) {
                displays.add(display);
            }
        }
        return displays;
    }

    public static boolean isOpenValve(@NotNull Block block, @NotNull BlockFace entryFace) {
        return PneumaticEndpointSupport.loadedRebarBlock(block) instanceof PneumaticGateValve valve
                && valve.acceptsPneumaticConnection(entryFace);
    }
}
