package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarInteractBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 气动货运站 —— 消耗加压蒸汽，将内部物品槽远程发送到已配对的目标货运站。
 * 手持蒸汽扳手右键本站进入配对模式，再右键另一台货运站完成绑定。
 */
public class PneumaticCargoHub extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock,
        RebarInteractBlock {

    private static final org.bukkit.NamespacedKey TARGET_KEY = steamworkKey("pch_target");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerSend = getSettings().getOrThrow("steam-per-send", ConfigAdapter.DOUBLE);
    private final int maxDistance = getSettings().getOrThrow("max-distance", ConfigAdapter.INTEGER);

    private @Nullable int[] targetPos; // [x, y, z, 0]，null 表示未配对
    private boolean lastActive = false;
    private boolean bindingMode = false;

    private final VirtualInventory sendInventory = new VirtualInventory(9);
    private final StatusItem statusItem = new StatusItem();
    private final PressurizedGaugeItem pressurizedGaugeItem = new PressurizedGaugeItem();
    private final TargetItem targetItem = new TargetItem();

    // ── 物品描述 ──────────────────────────────────────────────────────────────

    public static class Item extends RebarItem {
        private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerSend = getSettings().getOrThrow("steam-per-send", ConfigAdapter.DOUBLE);
        private final int maxDistance = getSettings().getOrThrow("max-distance", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("pressurized-buffer", UnitFormat.MILLIBUCKETS.format(pressurizedBuffer)),
                    RebarArgument.of("steam-per-send", UnitFormat.MILLIBUCKETS.format(steamPerSend)),
                    RebarArgument.of("max-distance", UnitFormat.BLOCKS.format(maxDistance))
            );
        }
    }

    // ── 构造器 ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    public PneumaticCargoHub(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, true, false);
        this.targetPos = null;
    }

    @SuppressWarnings("unused")
    public PneumaticCargoHub(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.SOUTH); }
        int[] saved = pdc.get(TARGET_KEY, PersistentDataType.INTEGER_ARRAY);
        this.targetPos = (saved != null && saved.length >= 3) ? saved : null;
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (targetPos != null) {
            pdc.set(TARGET_KEY, PersistentDataType.INTEGER_ARRAY, targetPos);
        } else {
            pdc.remove(TARGET_KEY);
        }
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarFluidBufferBlock.super.onBreak(drops, context);
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    // ── tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        boolean worked = false;

        if (targetPos != null && hasItemsToSend()) {
            if (fluidAmount(SteamworkFluids.PRESSURIZED_STEAM) >= steamPerSend) {
                PneumaticCargoHub target = findTarget();
                if (target != null) {
                    worked = transferItems(target);
                    if (worked) {
                        removeFluid(SteamworkFluids.PRESSURIZED_STEAM, steamPerSend);
                        spawnLaunchFx();
                    }
                }
            }
        }

        setActive(worked);
        statusItem.notifyWindows();
        pressurizedGaugeItem.notifyWindows();
        targetItem.notifyWindows();
    }

    private boolean hasItemsToSend() {
        for (ItemStack item : sendInventory.getItems()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        return false;
    }

    private @Nullable PneumaticCargoHub findTarget() {
        if (targetPos == null) return null;
        Block targetBlock = getBlock().getWorld().getBlockAt(targetPos[0], targetPos[1], targetPos[2]);
        RebarBlock rb = BlockStorage.get(targetBlock);
        if (rb instanceof PneumaticCargoHub hub) return hub;
        targetPos = null; // 目标已被破坏，解除配对
        return null;
    }

    private boolean transferItems(@NotNull PneumaticCargoHub target) {
        MachineUpdateReason reason = new MachineUpdateReason();
        VirtualInventory targetInv = target.sendInventory;
        ItemStack[] items = sendInventory.getItems();

        for (int i = 0; i < items.length; i++) {
            ItemStack stack = items[i];
            if (stack == null || stack.getType().isAir()) continue;

            ItemStack toSend = stack.clone();
            for (int j = 0; j < targetInv.getSize(); j++) {
                ItemStack slot = targetInv.getItem(j);
                if (slot == null || slot.getType().isAir()) {
                    targetInv.setItem(reason, j, toSend);
                    sendInventory.setItem(reason, i, null);
                    return true;
                } else if (slot.isSimilar(toSend)) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    if (space > 0) {
                        int transfer = Math.min(space, toSend.getAmount());
                        ItemStack merged = slot.clone();
                        merged.setAmount(slot.getAmount() + transfer);
                        targetInv.setItem(reason, j, merged);
                        int remaining = toSend.getAmount() - transfer;
                        if (remaining <= 0) {
                            sendInventory.setItem(reason, i, null);
                        } else {
                            ItemStack leftover = toSend.clone();
                            leftover.setAmount(remaining);
                            sendInventory.setItem(reason, i, leftover);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void spawnLaunchFx() {
        Location loc = getBlock().getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 5, 0.2, 0.2, 0.2, 0.04);
        getBlock().getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.1, 0.1, 0.1, 0.05);
        if (Math.random() < 0.3) {
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.3f, 1.8f);
        }
        if (targetPos != null) {
            Location tLoc = new Location(getBlock().getWorld(),
                    targetPos[0] + 0.5, targetPos[1] + 0.5, targetPos[2] + 0.5);
            getBlock().getWorld().spawnParticle(Particle.POOF, tLoc, 4, 0.15, 0.15, 0.15, 0.01);
            getBlock().getWorld().playSound(tLoc, Sound.BLOCK_DISPENSER_DISPENSE, 0.25f, 1.4f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    // ── 配对交互 ──────────────────────────────────────────────────────────────

    @Override
    public void onInteract(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.LOWEST) return;
        if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        ItemStack hand = event.getItem();
        // 手持蒸汽扳手（暂用铁锭占位）触发配对
        if (hand != null && hand.getType() == Material.IRON_INGOT) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (!bindingMode) {
                bindingMode = true;
                player.sendMessage(noItalic(Component.translatable(
                        "steamwork.message.pneumatic_cargo_hub.binding_start")));
            } else {
                RebarBlock rb = BlockStorage.get(clicked);
                if (rb instanceof PneumaticCargoHub other && !clicked.equals(getBlock())) {
                    double dist = getBlock().getLocation().distance(clicked.getLocation());
                    if (dist > maxDistance) {
                        player.sendMessage(noItalic(Component.translatable(
                                "steamwork.message.pneumatic_cargo_hub.too_far")));
                    } else {
                        targetPos = new int[]{clicked.getX(), clicked.getY(), clicked.getZ(), 0};
                        bindingMode = false;
                        targetItem.notifyWindows();
                        player.sendMessage(noItalic(Component.translatable(
                                "steamwork.message.pneumatic_cargo_hub.bound",
                                RebarArgument.of("x", String.valueOf(clicked.getX())),
                                RebarArgument.of("y", String.valueOf(clicked.getY())),
                                RebarArgument.of("z", String.valueOf(clicked.getZ()))
                        )));
                    }
                } else {
                    bindingMode = false;
                    player.sendMessage(noItalic(Component.translatable(
                            "steamwork.message.pneumatic_cargo_hub.binding_cancel")));
                }
            }
        }
    }

    // ── 贴图 ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    // ── GUI ───────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# s s s # s s s #",
                        "# s s s # s s s #",
                        "# # # # # # # # #",
                        "# # p # a # t # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', sendInventory)
                .addIngredient('p', pressurizedGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', targetItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pneumatic_cargo_hub.title"));
    }

    // ── WAILA ─────────────────────────────────────────────────────────────────

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        String targetStr = targetPos != null
                ? targetPos[0] + ", " + targetPos[1] + ", " + targetPos[2]
                : "—";
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("pressurized-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.PRESSURIZED_STEAM),
                        fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM),
                        12,
                        TextColor.fromHexString("#00cfff")
                )),
                RebarArgument.of("target", Component.text(targetStr)),
                RebarArgument.of("state", Component.translatable(
                        "steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    // ── GUI 内部物品 ──────────────────────────────────────────────────────────

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(lastActive
                            ? Material.GREEN_STAINED_GLASS_PANE
                            : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.status."
                                    + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.steam_cost",
                            RebarArgument.of("cost",
                                    UnitFormat.MILLIBUCKETS.format(steamPerSend).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
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
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {}
    }

    private final class TargetItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (targetPos == null) {
                return ItemStackBuilder.of(Material.RED_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(
                                "steamwork.gui.pneumatic_cargo_hub.target_none")))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.pneumatic_cargo_hub.target_hint"))));
            }
            return ItemStackBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.pneumatic_cargo_hub.target_set")))
                    .lore(List.of(
                            noItalic(Component.translatable(
                                    "steamwork.gui.pneumatic_cargo_hub.target_coords",
                                    RebarArgument.of("x", String.valueOf(targetPos[0])),
                                    RebarArgument.of("y", String.valueOf(targetPos[1])),
                                    RebarArgument.of("z", String.valueOf(targetPos[2]))
                            )),
                            noItalic(Component.translatable(
                                    "steamwork.gui.pneumatic_cargo_hub.target_clear_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player,
                                @NotNull Click click) {
            if (clickType == ClickType.RIGHT) {
                targetPos = null;
                notifyWindows();
                player.sendMessage(noItalic(Component.translatable(
                        "steamwork.message.pneumatic_cargo_hub.unbound")));
            }
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────────────

    @Override
    public @NotNull Map<@NotNull String, @NotNull VirtualInventory> getVirtualInventories() {
        return Map.of("send", sendInventory);
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
