package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GhostBlockHolderRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3i;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 气动货运站 —— 多方块结构底座，正上方必须放置一台蒸汽弹射器。
 * 每 5 秒批量发货一次：从内部 7 格物品栏取最多 batchSize 件物品送到目标货运站。
 * 蒸汽按实际成功传输件数 × steamPerItem 扣除（不足/接收方空间不足时退还）。
 */
public class PneumaticCargoHub extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock,
        GhostBlockHolderRebarBlock {

    public static final int MIN_BATCH = 1;
    public static final int MAX_BATCH = 64;

    private static final org.bukkit.NamespacedKey TARGET_KEY = steamworkKey("pch_target");
    private static final org.bukkit.NamespacedKey BATCH_KEY = steamworkKey("pch_batch");
    private static final org.bukkit.NamespacedKey DISPLAY_KEY = steamworkKey("pch_display");
    private static final org.bukkit.NamespacedKey DISPLAY_MARKER = steamworkKey("pch_display_marker");
    private static final Vector3i CATAPULT_OFFSET = new Vector3i(0, 1, 0);

    /** 玩家 -> 正在选择目标的源货运站坐标。 */
    private static final Map<UUID, int[]> SELECTING = new HashMap<>();
    private static final Set<UUID> PLAYERS_IN_SELECTION = new HashSet<>();

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
    private final int maxDistance = getSettings().getOrThrow("max-distance", ConfigAdapter.INTEGER);
    private final int flightTicks = getSettings().getOrThrow("flight-ticks", ConfigAdapter.INTEGER);
    private final int defaultBatchSize = getSettings().getOrThrow("default-batch-size", ConfigAdapter.INTEGER);

    private @Nullable int[] targetPos;
    private int batchSize;
    private boolean lastActive = false;
    /** 物品在途时为 true，防止 tick 重复发射 */
    private boolean inFlight = false;
    /** 发货节拍计数器：每个机器 tick +1，累计到 tickInterval 触发一次发货。 */
    private int sendCounter = 0;
    /** 展示内部物品的 ItemDisplay UUID（停在弹射器顶部） */
    private @Nullable UUID displayUuid;

    private final VirtualInventory sendInventory = new VirtualInventory(7);
    private final StatusItem statusItem = new StatusItem();
    private final PressurizedGaugeItem pressurizedGaugeItem = new PressurizedGaugeItem();
    private final TargetItem targetItem = new TargetItem();
    private final BatchSizeItem batchItem = new BatchSizeItem();

    // ── 全局事件监听 ──────────────────────────────────────────────────────────

    public static void registerGlobalListeners() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onClick(PlayerInteractEvent event) {
                UUID playerId = event.getPlayer().getUniqueId();
                if (!PLAYERS_IN_SELECTION.contains(playerId)) return;
                if (event.getClickedBlock() == null) return;

                Player player = event.getPlayer();
                Block clicked = event.getClickedBlock();
                String action = event.getAction().toString();

                int[] sourcePos = SELECTING.get(playerId);
                if (sourcePos == null) {
                    PLAYERS_IN_SELECTION.remove(playerId);
                    return;
                }

                Block sourceBlock = clicked.getWorld().getBlockAt(sourcePos[0], sourcePos[1], sourcePos[2]);
                RebarBlock sourceRb = BlockStorage.get(sourceBlock);
                if (!(sourceRb instanceof PneumaticCargoHub source)) {
                    cancelSelection(playerId);
                    return;
                }

                if (action.contains("RIGHT_CLICK") && clicked.equals(sourceBlock)) {
                    cancelSelection(playerId);
                    source.targetItem.notifyWindows();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.selection_cancelled")));
                    return;
                }

                if (action.contains("LEFT_CLICK")) {
                    event.setCancelled(true);
                    if (clicked.equals(sourceBlock)) return;

                    RebarBlock targetRb = BlockStorage.get(clicked);
                    if (!(targetRb instanceof PneumaticCargoHub targetHub)) {
                        player.sendMessage(noItalic(Component.translatable(
                                "steamwork.message.pneumatic_cargo_hub.invalid_target")));
                        return;
                    }

                    if (targetHub.getCatapultAbove() == null) {
                        player.sendMessage(noItalic(Component.translatable(
                                "steamwork.message.pneumatic_cargo_hub.target_no_catapult")));
                        return;
                    }

                    double dist = sourceBlock.getLocation().distance(clicked.getLocation());
                    if (dist > source.maxDistance) {
                        player.sendMessage(noItalic(Component.translatable(
                                "steamwork.message.pneumatic_cargo_hub.too_far")));
                        return;
                    }

                    source.targetPos = new int[]{clicked.getX(), clicked.getY(), clicked.getZ()};
                    cancelSelection(playerId);
                    source.targetItem.notifyWindows();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.bound",
                            RebarArgument.of("x", String.valueOf(clicked.getX())),
                            RebarArgument.of("y", String.valueOf(clicked.getY())),
                            RebarArgument.of("z", String.valueOf(clicked.getZ()))
                    )));
                }
            }
        }, io.github.steamwork.Steamwork.getInstance());
    }

    private static void cancelSelection(@NotNull UUID playerId) {
        SELECTING.remove(playerId);
        PLAYERS_IN_SELECTION.remove(playerId);
    }

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerItem = getSettings().getOrThrow("steam-per-item", ConfigAdapter.DOUBLE);
        private final int maxDistance = getSettings().getOrThrow("max-distance", ConfigAdapter.INTEGER);
        private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("pressurized-buffer", UnitFormat.MILLIBUCKETS.format(pressurizedBuffer)),
                    RebarArgument.of("steam-per-item", UnitFormat.MILLIBUCKETS.format(steamPerItem)),
                    RebarArgument.of("max-distance", UnitFormat.BLOCKS.format(maxDistance)),
                    RebarArgument.of("interval", String.valueOf(tickInterval / 20.0))
            );
        }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticCargoHub(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(8);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, true, false);
        this.targetPos = null;
        this.batchSize = clampBatch(defaultBatchSize);
    }

    @SuppressWarnings("unused")
    public PneumaticCargoHub(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        int[] saved = pdc.get(TARGET_KEY, PersistentDataType.INTEGER_ARRAY);
        this.targetPos = (saved != null && saved.length >= 3) ? saved : null;
        this.batchSize = clampBatch(pdc.getOrDefault(BATCH_KEY, PersistentDataType.INTEGER, defaultBatchSize));
        long uuidMsb = pdc.getOrDefault(DISPLAY_KEY, PersistentDataType.LONG, 0L);
        if (uuidMsb != 0L) this.displayUuid = new UUID(uuidMsb, pdc.getOrDefault(
                steamworkKey("pch_display_lsb"), PersistentDataType.LONG, 0L));
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (targetPos != null) {
            pdc.set(TARGET_KEY, PersistentDataType.INTEGER_ARRAY, targetPos);
        } else {
            pdc.remove(TARGET_KEY);
        }
        pdc.set(BATCH_KEY, PersistentDataType.INTEGER, batchSize);
        if (displayUuid != null) {
            pdc.set(DISPLAY_KEY, PersistentDataType.LONG, displayUuid.getMostSignificantBits());
            pdc.set(steamworkKey("pch_display_lsb"), PersistentDataType.LONG, displayUuid.getLeastSignificantBits());
        } else {
            pdc.remove(DISPLAY_KEY);
            pdc.remove(steamworkKey("pch_display_lsb"));
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
        if (hasGhostBlockAt(CATAPULT_OFFSET)) removeGhostBlock(CATAPULT_OFFSET);
        removeDisplayEntity();
    }

    private static int clampBatch(int n) {
        return Math.max(MIN_BATCH, Math.min(MAX_BATCH, n));
    }

    // ── 多方块结构检查 ────────────────────────────────────────────────────────

    private @Nullable SteamCatapult getCatapultAbove() {
        Block above = getBlock().getRelative(BlockFace.UP);
        RebarBlock rb = BlockStorage.get(above);
        return rb instanceof SteamCatapult c ? c : null;
    }

    private void syncGhostBlock() {
        boolean hasCatapult = getCatapultAbove() != null;
        boolean ghostPresent = hasGhostBlockAt(CATAPULT_OFFSET);
        if (!hasCatapult && !ghostPresent) {
            addGhostBlock(CATAPULT_OFFSET,
                    List.of(org.bukkit.Bukkit.createBlockData(Material.CUT_COPPER_SLAB)),
                    List.of());
        } else if (hasCatapult && ghostPresent) {
            removeGhostBlock(CATAPULT_OFFSET);
        }
    }

    // ── 内部物品 ItemDisplay ──────────────────────────────────────────────────

    private @Nullable ItemDisplay getDisplayEntity() {
        if (displayUuid == null) return null;
        Entity e = getBlock().getWorld().getEntity(displayUuid);
        if (e instanceof ItemDisplay id) return id;
        displayUuid = null;
        return null;
    }

    private void syncDisplayEntity() {
        SteamCatapult catapult = getCatapultAbove();
        ItemStack representative = firstStack();
        ItemDisplay existing = getDisplayEntity();

        if (catapult == null || representative == null || inFlight) {
            if (existing != null) {
                existing.remove();
                displayUuid = null;
            }
            return;
        }

        if (existing == null) {
            Location pos = catapult.getBlock().getLocation().add(0.5, 0.55, 0.5);
            ItemDisplay d = pos.getWorld().spawn(pos, ItemDisplay.class, e -> {
                e.setItemStack(representative.clone().asQuantity(1));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setPersistent(true);
                // 横躺：先绕 X 90° 让物品贴台阶顶面（与 SteamPress 内部物品同款）
                e.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0)),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
                ));
                e.getPersistentDataContainer().set(DISPLAY_MARKER, PersistentDataType.BYTE, (byte) 1);
            });
            displayUuid = d.getUniqueId();
        } else {
            ItemStack current = existing.getItemStack();
            if (current == null || current.getType() != representative.getType()) {
                existing.setItemStack(representative.clone().asQuantity(1));
            }
            Location desired = catapult.getBlock().getLocation().add(0.5, 0.55, 0.5);
            if (existing.getLocation().distanceSquared(desired) > 0.01) {
                existing.teleport(desired);
            }
        }
    }

    private void removeDisplayEntity() {
        ItemDisplay d = getDisplayEntity();
        if (d != null) d.remove();
        displayUuid = null;
    }

    private @Nullable ItemStack firstStack() {
        for (ItemStack s : sendInventory.getItems()) {
            if (s != null && !s.getType().isAir()) return s;
        }
        return null;
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        syncGhostBlock();
        syncDisplayEntity();
        io.github.steamwork.util.PneumaticUtils.pullFromAdjacentHoppers(getBlock(), sendInventory);

        if (inFlight) return;

        // 发货节拍：每 tickInterval ticks（默认 200 = 10s）触发一次发货逻辑，
        // 之间的 tick 仅负责漏斗注入与状态同步。
        sendCounter += 8;
        if (sendCounter < tickInterval) return;
        sendCounter = 0;

        SteamCatapult catapult = getCatapultAbove();
        if (catapult == null) { setActive(false); statusItem.notifyWindows(); return; }
        if (targetPos == null || !hasItemsToSend()) { setActive(false); statusItem.notifyWindows(); return; }

        Block targetBlock = getBlock().getWorld().getBlockAt(targetPos[0], targetPos[1], targetPos[2]);
        RebarBlock targetRb = BlockStorage.get(targetBlock);
        if (!(targetRb instanceof PneumaticCargoHub target)) {
            targetPos = null;
            targetItem.notifyWindows();
            setActive(false);
            return;
        }

        // 目标必须也有弹射器（多方块完整）才能接收
        if (target.getCatapultAbove() == null) {
            setActive(false);
            statusItem.notifyWindows();
            return;
        }

        double dist = getBlock().getLocation().distance(targetBlock.getLocation());
        if (dist > maxDistance) { setActive(false); return; }

        // 检查至少能发一件（蒸汽 >= steamPerItem）
        if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) < steamPerItem) {
            setActive(false);
            pressurizedGaugeItem.notifyWindows();
            return;
        }

        // 选定第一种物品；批量取最多 batchSize 件相同物品
        ItemStack template = firstStack();
        if (template == null) { setActive(false); return; }

        int requested = Math.min(batchSize, countAvailable(template));
        // 蒸汽允许的最大数量
        int affordable = (int) Math.floor(fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) / steamPerItem);
        int toTake = Math.min(requested, affordable);
        if (toTake <= 0) { setActive(false); return; }

        // 试摆放目标侧能容纳多少
        int placeable = countPlaceable(target.sendInventory, template, toTake);
        if (placeable <= 0) {
            setActive(false);
            statusItem.notifyWindows();
            return;
        }

        int finalCount = Math.min(toTake, placeable);
        ItemStack batch = template.clone();
        batch.setAmount(finalCount);

        // 扣物品和蒸汽（按实际数量）
        consumeItem(template, finalCount);
        removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerItem * finalCount);

        Location from = catapult.getBlock().getLocation().add(0.5, 1.1, 0.5);
        Location to = targetBlock.getLocation().add(0.5, 1.0, 0.5);

        inFlight = true;
        setActive(true);
        statusItem.notifyWindows();
        pressurizedGaugeItem.notifyWindows();
        // 飞行期间移除展示实体（物品已经从内部库存里"飞走"）
        removeDisplayEntity();

        ItemStack flying = batch.clone();
        catapult.launch(from, to, flying, flightTicks, () -> {
            MachineUpdateReason reason = new MachineUpdateReason();
            VirtualInventory targetInv = target.sendInventory;
            ItemStack remaining = flying.clone();
            for (int j = 0; j < targetInv.getSize() && remaining.getAmount() > 0; j++) {
                ItemStack slot = targetInv.getItem(j);
                if (slot == null || slot.getType().isAir()) {
                    int take = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                    ItemStack placed = remaining.clone();
                    placed.setAmount(take);
                    targetInv.setItem(reason, j, placed);
                    remaining.setAmount(remaining.getAmount() - take);
                } else if (slot.isSimilar(remaining)) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    if (space > 0) {
                        int take = Math.min(space, remaining.getAmount());
                        ItemStack merged = slot.clone();
                        merged.setAmount(slot.getAmount() + take);
                        targetInv.setItem(reason, j, merged);
                        remaining.setAmount(remaining.getAmount() - take);
                    }
                }
            }
            // 极小概率有溢出，掉落
            if (remaining.getAmount() > 0) {
                targetBlock.getWorld().dropItemNaturally(to, remaining);
            }
            inFlight = false;
            setActive(false);
            statusItem.notifyWindows();
            // 触发新的展示
            syncDisplayEntity();
        });
    }

    private int countAvailable(@NotNull ItemStack template) {
        int sum = 0;
        for (ItemStack s : sendInventory.getItems()) {
            if (s != null && s.isSimilar(template)) sum += s.getAmount();
        }
        return sum;
    }

    private int countPlaceable(@NotNull VirtualInventory inv, @NotNull ItemStack template, int desired) {
        int room = 0;
        for (ItemStack s : inv.getItems()) {
            if (s == null || s.getType().isAir()) {
                room += template.getMaxStackSize();
            } else if (s.isSimilar(template)) {
                room += Math.max(0, s.getMaxStackSize() - s.getAmount());
            }
            if (room >= desired) return desired;
        }
        return room;
    }

    private void consumeItem(@NotNull ItemStack template, int count) {
        MachineUpdateReason reason = new MachineUpdateReason();
        int remaining = count;
        ItemStack[] items = sendInventory.getItems();
        for (int i = 0; i < items.length && remaining > 0; i++) {
            ItemStack s = items[i];
            if (s == null || s.getType().isAir() || !s.isSimilar(template)) continue;
            int take = Math.min(remaining, s.getAmount());
            if (take >= s.getAmount()) {
                sendInventory.setItem(reason, i, null);
            } else {
                ItemStack r = s.clone();
                r.setAmount(s.getAmount() - take);
                sendInventory.setItem(reason, i, r);
            }
            remaining -= take;
        }
    }

    private boolean hasItemsToSend() {
        for (ItemStack item : sendInventory.getItems()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        return false;
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# s s s s s s s #",
                        "# # # # # # # # #",
                        "# # # # # # # # #",
                        "# p # a # b # t #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', sendInventory)
                .addIngredient('p', pressurizedGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('b', batchItem)
                .addIngredient('t', targetItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_cargo_hub.title"));
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        boolean hasCatapult = getCatapultAbove() != null;
        String targetStr = targetPos != null
                ? targetPos[0] + ", " + targetPos[1] + ", " + targetPos[2]
                : "—";
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("pressurized-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12, TextColor.fromHexString("#00cfff")
                )),
                RebarArgument.of("catapult", Component.translatable(
                        hasCatapult ? "steamwork.state.active" : "steamwork.state.missing")),
                RebarArgument.of("target", Component.text(targetStr)),
                RebarArgument.of("batch", String.valueOf(batchSize)),
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("send", sendInventory);
    }

    /** 暴露给 PneumaticUtils：汽动输入端推入 / 汽动输出端抽取的工作槽。 */
    public @NotNull VirtualInventory getSendInventory() {
        return sendInventory;
    }

    // ── GUI 内部物品 ──────────────────────────────────────────────────────────

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean hasCatapult = getCatapultAbove() != null;
            Material mat = hasCatapult && lastActive ? Material.GREEN_STAINED_GLASS_PANE
                    : hasCatapult ? Material.GRAY_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            String key = hasCatapult
                    ? "steamwork.gui.pneumatic_cargo_hub.status." + (lastActive ? "active" : "idle")
                    : "steamwork.gui.pneumatic_cargo_hub.status.no_catapult";
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(key)))
                    .lore(List.of(
                            noItalic(Component.translatable(
                                    "steamwork.gui.pneumatic_cargo_hub.steam_cost",
                                    RebarArgument.of("cost",
                                            UnitFormat.MILLIBUCKETS.format(steamPerItem * batchSize).decimalPlaces(0)),
                                    RebarArgument.of("per-item",
                                            UnitFormat.MILLIBUCKETS.format(steamPerItem).decimalPlaces(0)),
                                    RebarArgument.of("batch", String.valueOf(batchSize))
                            )),
                            noItalic(Component.translatable(
                                    "steamwork.gui.pneumatic_cargo_hub.interval",
                                    RebarArgument.of("seconds", String.valueOf(tickInterval / 20.0))
                            ))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class PressurizedGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double amount = fluidAmount(SteamworkFluids.PRESSURIZED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM);
            return ItemStackBuilder.of(Material.CYAN_STAINED_GLASS)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.pressurized_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.fluid_amount",
                            RebarArgument.of("amount",
                                    UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                            RebarArgument.of("capacity",
                                    UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class BatchSizeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_cargo_hub.batch_amount",
                    RebarArgument.of("amount", String.valueOf(batchSize)),
                    RebarArgument.of("max", String.valueOf(MAX_BATCH))
            )));
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_cargo_hub.batch_cost",
                    RebarArgument.of("cost",
                            UnitFormat.MILLIBUCKETS.format(steamPerItem * batchSize).decimalPlaces(0))
            )));
            lore.add(noItalic(Component.empty()));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_cargo_hub.batch_increase")));
            lore.add(noItalic(Component.translatable("steamwork.gui.pneumatic_cargo_hub.batch_decrease")));
            return ItemStackBuilder.of(Material.HOPPER)
                    .name(noItalic(Component.translatable("steamwork.gui.pneumatic_cargo_hub.batch_title")))
                    .amount(batchSize)
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            int delta = 0;
            if (clickType == ClickType.LEFT) delta = 1;
            else if (clickType == ClickType.SHIFT_LEFT) delta = 10;
            else if (clickType == ClickType.RIGHT) delta = -1;
            else if (clickType == ClickType.SHIFT_RIGHT) delta = -10;
            if (delta == 0) return;
            int next = clampBatch(batchSize + delta);
            if (next == batchSize) return;
            batchSize = next;
            notifyWindows();
            statusItem.notifyWindows();
        }
    }

    private final class TargetItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            int[] myPos = new int[]{getBlock().getX(), getBlock().getY(), getBlock().getZ()};
            boolean selecting = false;
            for (int[] sp : SELECTING.values()) {
                if (sp[0] == myPos[0] && sp[1] == myPos[1] && sp[2] == myPos[2]) {
                    selecting = true; break;
                }
            }

            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.pneumatic_cargo_hub.target_range",
                    RebarArgument.of("range", String.valueOf(maxDistance))
            )));
            lore.add(noItalic(Component.empty()));
            if (targetPos != null) {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.pneumatic_cargo_hub.target_coords",
                        RebarArgument.of("x", String.valueOf(targetPos[0])),
                        RebarArgument.of("y", String.valueOf(targetPos[1])),
                        RebarArgument.of("z", String.valueOf(targetPos[2]))
                )));
                lore.add(noItalic(Component.empty()));
            }
            if (selecting) {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.pneumatic_cargo_hub.selecting_hint")
                        .color(NamedTextColor.YELLOW)));
            } else {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.pneumatic_cargo_hub.select_hint")));
                if (targetPos != null) {
                    lore.add(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.target_clear_hint")));
                }
            }

            Material mat = selecting ? Material.GOLD_INGOT
                    : targetPos != null ? Material.LIME_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            String nameKey = selecting ? "steamwork.gui.pneumatic_cargo_hub.target_selecting"
                    : targetPos != null ? "steamwork.gui.pneumatic_cargo_hub.target_set"
                    : "steamwork.gui.pneumatic_cargo_hub.target_none";
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(nameKey)))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            UUID playerId = player.getUniqueId();
            int[] myPos = new int[]{getBlock().getX(), getBlock().getY(), getBlock().getZ()};

            if (clickType.isLeftClick()) {
                int[] cur = SELECTING.get(playerId);
                boolean already = cur != null && cur[0] == myPos[0] && cur[1] == myPos[1] && cur[2] == myPos[2];
                if (already) {
                    cancelSelection(playerId);
                    notifyWindows();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.selection_cancelled")));
                } else {
                    SELECTING.put(playerId, myPos);
                    PLAYERS_IN_SELECTION.add(playerId);
                    notifyWindows();
                    player.closeInventory();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.selecting",
                            RebarArgument.of("range", String.valueOf(maxDistance)))));
                }
            } else if (clickType.isRightClick()) {
                if (targetPos != null) {
                    targetPos = null;
                    notifyWindows();
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.unbound")));
                }
            }
        }
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
