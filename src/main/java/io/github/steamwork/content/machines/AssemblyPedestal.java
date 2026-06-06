package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

/**
 * 装配祭坛的底座方块（锰钢块）。本身不存储状态——右键时向上/相邻寻找所属的
 * {@link SteamAssemblyBench}（已成型的祭坛），把"放入/取出物品"交给装配台处理。
 *
 * <p>若该锰钢块不属于任何已成型祭坛，则不拦截事件，保持普通方块行为（可正常建造）。</p>
 */
public class AssemblyPedestal extends RebarBlock implements InteractRebarBlockHandler {

    @SuppressWarnings("unused")
    public AssemblyPedestal(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public AssemblyPedestal(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void onInteractedWith(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block self = getBlock();
        SteamAssemblyBench owner = findOwner(self);
        if (owner == null) return; // 不属于祭坛 → 不拦截，保持普通方块行为

        event.setCancelled(true);
        owner.handlePedestalInteract(self, event.getPlayer(), event.getPlayer().isSneaking());
    }

    /** 扫描该底座周围 3×3×3 邻居，寻找拥有它的已成型装配祭坛。 */
    private static SteamAssemblyBench findOwner(@NotNull Block pedestal) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block neighbor = pedestal.getRelative(dx, dy, dz);
                    if (BlockStorage.get(neighbor) instanceof SteamAssemblyBench bench
                            && bench.ownsPedestal(pedestal)) {
                        return bench;
                    }
                }
            }
        }
        return null;
    }
}
