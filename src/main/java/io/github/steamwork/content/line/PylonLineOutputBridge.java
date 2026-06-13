package io.github.steamwork.content.line;

import io.github.pylonmc.pylon.recipes.GrindstoneRecipe;
import io.github.pylonmc.pylon.recipes.MixingPotRecipe;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import io.github.steamwork.Steamwork;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

import java.util.ArrayDeque;
import java.util.Deque;
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
    private static final double MIXING_POT_CAPTURE_RADIUS_SQUARED = 1.0;
    private static final int RETRY_INTERVAL_TICKS = 5;
    private static final long BUFFER_EXPIRY_MS = 60_000L;

    private static final Map<BlockKey, PendingOutput> PENDING_OUTPUTS = new HashMap<>();
    // Items that couldn't be pushed immediately (next member full); drained every RETRY_INTERVAL_TICKS.
    private static final Map<BlockKey, BufferedDelivery> BUFFERED = new HashMap<>();

    public PylonLineOutputBridge() {
        Bukkit.getScheduler().runTaskTimer(
                Steamwork.getInstance(),
                PylonLineOutputBridge::drainBuffered,
                RETRY_INTERVAL_TICKS, RETRY_INTERVAL_TICKS
        );
    }

    static void expectOutput(@NotNull Block block, @NotNull Object recipe) {
        ProductionLineMember member = ProductionLineMember.of(block);
        if (member == null || !member.isInLine()) return;

        UUID lineId = member.getLineId();
        BlockFace direction = member.getLineDirection();
        if (lineId == null || direction == BlockFace.SELF) return;

        if (recipe instanceof GrindstoneRecipe grindstoneRecipe) {
            PENDING_OUTPUTS.put(BlockKey.of(block), new PendingOutput(
                    lineId,
                    direction,
                    OutputSource.GRINDSTONE,
                    grindstoneRecipe.getKey(),
                    block.getWorld().getGameTime() + grindstoneRecipe.timeTicks() + MAX_OUTPUT_CAPTURE_TICKS
            ));
            return;
        }

        if (!(recipe instanceof MixingPotRecipe mixingPotRecipe)
                || !(mixingPotRecipe.output() instanceof FluidOrItem.Item)) {
            return;
        }

        PENDING_OUTPUTS.put(BlockKey.of(block), new PendingOutput(
                lineId,
                direction,
                OutputSource.MIXING_POT,
                mixingPotRecipe.getKey(),
                block.getWorld().getGameTime() + MAX_OUTPUT_CAPTURE_TICKS
        ));
    }

    static void cancelExpectedOutput(@NotNull Block block) {
        PENDING_OUTPUTS.remove(BlockKey.of(block));
    }

    static boolean hasPendingOrBufferedOutput(@NotNull Block block) {
        BlockKey key = BlockKey.of(block);
        return PENDING_OUTPUTS.containsKey(key) || BUFFERED.containsKey(key);
    }

    static void clearSource(@NotNull Block block) {
        BlockKey key = BlockKey.of(block);
        PENDING_OUTPUTS.remove(key);
        BUFFERED.remove(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(@NotNull ItemSpawnEvent event) {
        Item entity = event.getEntity();
        Location location = entity.getLocation();
        if (location.getWorld() == null) return;

        expireOldPendingOutputs(location.getWorld().getGameTime());
        Block source = findPendingSource(location);

        // 后备路径：没有 pending 条目时，检查附近是否有处于产线中的磨石（玩家手动操作时 expectOutput 未被调用）
        // 仅对机器自然掉落的物品生效（thrower == null），排除玩家手动丢弃的物品
        if (source == null) {
            if (entity.getThrower() != null) return;
            source = findLineMemberGrindstoneNear(location);
            if (source == null) return;
        }

        BlockKey key = BlockKey.of(source);
        PendingOutput pending = PENDING_OUTPUTS.get(key);

        // 获取产线信息：优先从 pending 条目读取，否则从方块本身读取
        UUID lineId;
        BlockFace direction;
        ProductionLineMember sourceMember = ProductionLineMember.of(source);
        if (pending != null) {
            if (sourceMember == null || !pending.lineId().equals(sourceMember.getLineId())) {
                PENDING_OUTPUTS.remove(key);
                return;
            }
            lineId = pending.lineId();
            direction = pending.direction();
        } else {
            if (sourceMember == null || !sourceMember.isInLine()) return;
            lineId = sourceMember.getLineId();
            direction = sourceMember.getLineDirection();
            if (lineId == null || direction == BlockFace.SELF) return;
        }

        ItemStack stack = entity.getItemStack();
        if (stack == null || stack.isEmpty()) {
            PENDING_OUTPUTS.remove(key);
            return;
        }

        // Always cancel: line grindstones must never drop items on the ground.
        event.setCancelled(true);
        PENDING_OUTPUTS.remove(key);

        int total = stack.getAmount();
        int delivered = 0;
        ProductionLineMember next = findNextMember(source, direction, lineId);
        if (next != null) {
            while (delivered < total) {
                if (!next.acceptFromLine(stack.asQuantity(1))) break;
                delivered++;
            }
        }

        int remaining = total - delivered;
        if (remaining > 0) {
            enqueueBuffer(key, lineId, direction, stack.asQuantity(remaining));
        }
    }

    private static void enqueueBuffer(
            @NotNull BlockKey key,
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull ItemStack item
    ) {
        long expiresAt = System.currentTimeMillis() + BUFFER_EXPIRY_MS;
        BufferedDelivery existing = BUFFERED.get(key);
        if (existing == null) {
            Deque<ItemStack> queue = new ArrayDeque<>();
            queue.add(item.clone());
            BUFFERED.put(key, new BufferedDelivery(lineId, direction, queue, expiresAt));
        } else {
            existing.items().add(item.clone());
            // Refresh expiry each time a new item is added.
            BUFFERED.put(key, new BufferedDelivery(existing.lineId(), existing.direction(), existing.items(), expiresAt));
        }
    }

    private static void drainBuffered() {
        if (BUFFERED.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockKey, BufferedDelivery>> it = BUFFERED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockKey, BufferedDelivery> entry = it.next();
            BufferedDelivery delivery = entry.getValue();

            if (delivery.expiresAt() < now) {
                it.remove();
                continue;
            }

            Block source = entry.getKey().toBlock();
            if (source == null) { it.remove(); continue; }

            ProductionLineMember sourceMember = ProductionLineMember.of(source);
            if (sourceMember == null || !delivery.lineId().equals(sourceMember.getLineId())) {
                it.remove();
                continue;
            }

            ProductionLineMember next = findNextMember(source, delivery.direction(), delivery.lineId());
            if (next == null) continue;

            Deque<ItemStack> items = delivery.items();
            while (!items.isEmpty()) {
                ItemStack head = items.peek();
                int unitsSent = 0;
                int total = head.getAmount();
                while (unitsSent < total) {
                    if (!next.acceptFromLine(head.asQuantity(1))) break;
                    unitsSent++;
                }
                head.setAmount(head.getAmount() - unitsSent);
                if (head.getAmount() <= 0) {
                    items.poll();
                } else {
                    break; // Next member still full; retry next interval.
                }
            }

            if (items.isEmpty()) it.remove();
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
    private static Block findPendingSource(@NotNull Location dropLocation) {
        Block center = dropLocation.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block candidate = center.getRelative(dx, dy, dz);
                    PendingOutput pending = PENDING_OUTPUTS.get(BlockKey.of(candidate));
                    if (pending == null) continue;
                    if (!isExpectedSource(candidate, pending.source())) continue;
                    if (!isNearExpectedOutput(candidate, dropLocation, pending.source())) continue;
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isExpectedSource(@NotNull Block block, @NotNull OutputSource source) {
        return switch (source) {
            case GRINDSTONE -> isPylonBlock(block, "grindstone");
            case MIXING_POT -> isPylonBlock(block, "mixing_pot");
        };
    }

    private static boolean isPylonBlock(@NotNull Block block, @NotNull String keyName) {
        RebarBlock rb = RebarBlock.getRebarBlock(block);
        NamespacedKey key = rb != null ? rb.getKey() : null;
        return key != null && "pylon".equals(key.getNamespace()) && keyName.equals(key.getKey());
    }

    private static boolean isNearExpectedOutput(
            @NotNull Block block,
            @NotNull Location dropLocation,
            @NotNull OutputSource source
    ) {
        return switch (source) {
            case GRINDSTONE -> isNearGrindstoneOutput(block, dropLocation);
            case MIXING_POT -> isNearMixingPotOutput(block, dropLocation);
        };
    }

    private static boolean isNearGrindstoneOutput(@NotNull Block block, @NotNull Location dropLocation) {
        Location expected = block.getLocation().toCenterLocation().add(0.0, 0.25, 0.0);
        if (!expected.getWorld().equals(dropLocation.getWorld())) return false;
        return expected.distanceSquared(dropLocation) <= GRINDSTONE_CAPTURE_RADIUS_SQUARED;
    }

    private static boolean isNearMixingPotOutput(@NotNull Block block, @NotNull Location dropLocation) {
        Location expected = block.getLocation().toCenterLocation();
        if (!expected.getWorld().equals(dropLocation.getWorld())) return false;
        return expected.distanceSquared(dropLocation) <= MIXING_POT_CAPTURE_RADIUS_SQUARED;
    }

    /**
     * 后备检测：在掉落物附近寻找处于产线中的磨石方块。
     * 用于玩家手动操作磨石时（expectOutput 未被调用）仍能拦截产物。
     */
    @Nullable
    private Block findLineMemberGrindstoneNear(@NotNull Location dropLocation) {
        Block center = dropLocation.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block candidate = center.getRelative(dx, dy, dz);
                    if (!isPylonBlock(candidate, "grindstone")) continue;
                    if (!isNearGrindstoneOutput(candidate, dropLocation)) continue;
                    ProductionLineMember member = ProductionLineMember.of(candidate);
                    if (member != null && member.isInLine()) return candidate;
                }
            }
        }
        return null;
    }

    @Nullable
    private static ProductionLineMember findNextMember(
            @NotNull Block source,
            @NotNull BlockFace direction,
            @NotNull UUID lineId
    ) {
        for (int i = 1; i <= MAX_NEXT_MEMBER_GAP; i++) {
            Block candidate = source.getRelative(direction, i);
            ProductionLineMember member = ProductionLineMember.of(candidate);
            if (member != null && lineId.equals(member.getLineId())) return member;
        }
        return null;
    }

    private record PendingOutput(
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull OutputSource source,
            @NotNull NamespacedKey recipeKey,
            long expiresAtGameTime
    ) {}

    private enum OutputSource {
        GRINDSTONE,
        MIXING_POT
    }

    private record BufferedDelivery(
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull Deque<ItemStack> items,
            long expiresAt
    ) {}

    private record BlockKey(@NotNull UUID worldId, int x, int y, int z) {
        static @NotNull BlockKey of(@NotNull Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        @Nullable Block toBlock() {
            World world = Bukkit.getWorld(worldId);
            return world != null ? world.getBlockAt(x, y, z) : null;
        }
    }
}
