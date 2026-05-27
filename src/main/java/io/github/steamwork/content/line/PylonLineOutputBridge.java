package io.github.steamwork.content.line;

import io.github.pylonmc.pylon.recipes.GrindstoneRecipe;
import io.github.pylonmc.rebar.block.RebarBlock;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Bridges Pylon machines that emit products as world drops back into Steamwork production lines.
 */
public final class PylonLineOutputBridge implements Listener {

    private static final int MAX_OUTPUT_CAPTURE_TICKS = 20 * 60;
    private static final int MAX_NEXT_MEMBER_GAP = 4;
    private static final double GRINDSTONE_CAPTURE_RADIUS_SQUARED = 1.0;

    private static final Map<BlockKey, PendingOutput> PENDING_OUTPUTS = new HashMap<>();

    static void expectOutput(@NotNull Block block, @NotNull Object recipe) {
        if (!(recipe instanceof GrindstoneRecipe grindstoneRecipe)) return;
        ProductionLineMember member = ProductionLineMember.of(block);
        if (member == null || !member.isInLine()) return;

        UUID lineId = member.getLineId();
        BlockFace direction = member.getLineDirection();
        if (lineId == null || direction == BlockFace.SELF) return;

        PENDING_OUTPUTS.put(BlockKey.of(block), new PendingOutput(
                lineId,
                direction,
                grindstoneRecipe.getKey(),
                block.getWorld().getGameTime() + grindstoneRecipe.timeTicks() + MAX_OUTPUT_CAPTURE_TICKS
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        Item entity = event.getEntity();
        Location location = entity.getLocation();
        if (location.getWorld() == null) return;

        expireOldPendingOutputs(location.getWorld().getGameTime());
        Block source = findPendingGrindstone(location);
        if (source == null) return;

        BlockKey key = BlockKey.of(source);
        PendingOutput pending = PENDING_OUTPUTS.get(key);
        if (pending == null) return;

        ProductionLineMember sourceMember = ProductionLineMember.of(source);
        if (sourceMember == null || !pending.lineId().equals(sourceMember.getLineId())) {
            PENDING_OUTPUTS.remove(key);
            return;
        }

        ProductionLineMember next = findNextMember(source, pending);
        if (next == null) return;

        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.isEmpty()) return;

        ItemStack single = stack.clone().asQuantity(1);
        if (!next.acceptFromLine(single)) return;

        if (stack.getAmount() <= 1) {
            event.setCancelled(true);
            PENDING_OUTPUTS.remove(key);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            entity.setItemStack(stack);
        }
    }

    private static void expireOldPendingOutputs(long currentGameTime) {
        Iterator<Map.Entry<BlockKey, PendingOutput>> it = PENDING_OUTPUTS.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expiresAtGameTime() < currentGameTime) {
                it.remove();
            }
        }
    }

    @Nullable
    private static Block findPendingGrindstone(@NotNull Location dropLocation) {
        Block center = dropLocation.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block candidate = center.getRelative(dx, dy, dz);
                    PendingOutput pending = PENDING_OUTPUTS.get(BlockKey.of(candidate));
                    if (pending == null) continue;
                    if (!isPylonGrindstone(candidate)) continue;
                    if (!isNearGrindstoneOutput(candidate, dropLocation)) continue;
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isPylonGrindstone(@NotNull Block block) {
        RebarBlock rb = RebarBlock.getRebarBlock(block);
        NamespacedKey key = rb != null ? rb.getKey() : null;
        return key != null && "pylon".equals(key.getNamespace()) && "grindstone".equals(key.getKey());
    }

    private static boolean isNearGrindstoneOutput(@NotNull Block block, @NotNull Location dropLocation) {
        Location expected = block.getLocation().toCenterLocation().add(0.0, 0.25, 0.0);
        if (!expected.getWorld().equals(dropLocation.getWorld())) return false;
        return expected.distanceSquared(dropLocation) <= GRINDSTONE_CAPTURE_RADIUS_SQUARED;
    }

    @Nullable
    private static ProductionLineMember findNextMember(@NotNull Block source, @NotNull PendingOutput pending) {
        for (int i = 1; i <= MAX_NEXT_MEMBER_GAP; i++) {
            Block candidate = source.getRelative(pending.direction(), i);
            ProductionLineMember member = ProductionLineMember.of(candidate);
            if (member != null && pending.lineId().equals(member.getLineId())) return member;
        }
        return null;
    }

    private record PendingOutput(
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull NamespacedKey recipeKey,
            long expiresAtGameTime
    ) {}

    private record BlockKey(@NotNull UUID worldId, int x, int y, int z) {
        static @NotNull BlockKey of(@NotNull Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
