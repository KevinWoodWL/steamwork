package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarBreakHandler;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarEntityCulledBlock;
import io.github.pylonmc.rebar.block.base.RebarFacadeBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.PneumaticUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.inventory.VirtualInventory;

import java.util.ArrayList;
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
 */
public class PneumaticInput extends RebarBlock implements
        RebarBreakHandler,
        RebarDirectionalBlock,
        RebarEntityCulledBlock,
        RebarFacadeBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    private static final NamespacedKey DISPLAY_MARKER_KEY = steamworkKey("pneumatic_input_display");
    private static final NamespacedKey DISPLAY_OWNER_KEY  = steamworkKey("pneumatic_input_display_owner");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);

    /** 缓冲背包：网络将物品推入此处，本机再转发到相邻容器。 */
    private final VirtualInventory bufferInventory = new VirtualInventory(4);

    private volatile List<UUID> displayUuids = List.of();

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
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public PneumaticInput(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        setTickInterval(tickInterval);
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

        MachineUpdateReason reason = new MachineUpdateReason();
        for (int i = 0; i < bufferInventory.getSize(); i++) {
            ItemStack s = bufferInventory.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            int pushed = PneumaticUtils.tryPushItems(target, s, s.getAmount());
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

    // ── 接口实现 ──────────────────────────────────────────────────────────────

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("input", bufferInventory);
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey());
    }
}
