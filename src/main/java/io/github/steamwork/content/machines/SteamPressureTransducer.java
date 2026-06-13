package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.util.SteamLogicSupport;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class SteamPressureTransducer extends RebarBlock implements
        BlockBreakRebarBlockHandler,
        GuiRebarBlock,
        TickingRebarBlock {

    private static final String KEY_KIND = "transducer_kind";
    private static final String KEY_FACE = "transducer_face";
    private static final int BARREL_SIZE = 27;
    private static final int MAX_STACK = 64;
    private static final int TOTAL_UNITS = BARREL_SIZE * MAX_STACK;
    private static final NamespacedKey SIGNAL_ITEM_KEY = steamworkKey("pressure_transducer_signal_item");

    private final int tickInterval = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 1);

    private SteamKind steamKind = SteamKind.STEAM;
    private BlockFace readFace = BlockFace.NORTH;
    private int lastLevel = -1;
    private int lastWrittenUnits = -1;

    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final ReadFaceItem readFaceItem = new ReadFaceItem();
    private final StatusItem statusItem = new StatusItem();

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
    public SteamPressureTransducer(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setTickInterval(tickInterval);
    }

    @SuppressWarnings("unused")
    public SteamPressureTransducer(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(tickInterval);
        steamKind = SteamKind.fromOrdinal(pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, 0));
        readFace = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_FACE), BlockFace.NORTH);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, steamKind.ordinal());
        pdc.set(steamworkKey(KEY_FACE), PersistentDataType.STRING, readFace.name());
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        clearBarrelInventory();
    }

    @Override
    public void tick() {
        int level = currentLevel();
        if (level != lastLevel || barrelInventoryDirty()) {
            writeComparatorInventory(level);
            lastLevel = level;
        }
        steamKindItem.notifyWindows();
        readFaceItem.notifyWindows();
        statusItem.notifyWindows();
    }

    private int currentLevel() {
        var source = currentSource();
        double ratio = SteamLogicSupport.fillRatio(source, steamKind);
        return Math.max(0, Math.min(15, (int) Math.floor(ratio * 15.0)));
    }

    private double currentAmount() {
        var source = currentSource();
        return source == null ? 0.0 : SteamLogicSupport.amount(source, steamKind);
    }

    private double currentCapacity() {
        var source = currentSource();
        return source == null ? 0.0 : SteamLogicSupport.capacity(source, steamKind);
    }

    private @Nullable FluidBufferRebarBlock currentSource() {
        return SteamLogicSupport.fluidNeighbor(getBlock(), readFace);
    }

    private boolean barrelInventoryDirty() {
        if (!(getBlock().getState() instanceof Container container)) return false;
        Inventory inventory = container.getInventory();
        int actualUnits = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null) continue;
            if (!isSignalItem(stack)) return true;
            actualUnits += stack.getAmount();
        }
        return actualUnits != lastWrittenUnits;
    }

    private void writeComparatorInventory(int level) {
        if (!(getBlock().getState() instanceof Container container)) return;
        Inventory inventory = container.getInventory();
        inventory.clear();
        int remaining = unitsForLevel(level);
        lastWrittenUnits = remaining;
        for (int slot = 0; slot < BARREL_SIZE && remaining > 0; slot++) {
            int amount = Math.min(MAX_STACK, remaining);
            inventory.setItem(slot, signalItem(amount));
            remaining -= amount;
        }
    }

    private int unitsForLevel(int level) {
        if (level <= 0) return 0;
        if (level == 1) return 1;
        if (level >= 15) return TOTAL_UNITS;
        return (int) Math.ceil(((double) (level - 1) / 14.0) * TOTAL_UNITS);
    }

    private @NotNull ItemStack signalItem(int amount) {
        ItemStack stack = ItemStackBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .amount(amount)
                .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_pressure_transducer.signal_item")
                        .color(NamedTextColor.AQUA)))
                .build();
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(SIGNAL_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isSignalItem(@NotNull ItemStack stack) {
        if (stack.getType() != Material.LIGHT_BLUE_STAINED_GLASS_PANE) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte marker = meta.getPersistentDataContainer().get(SIGNAL_ITEM_KEY, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void clearBarrelInventory() {
        if (getBlock().getState() instanceof Container container) {
            container.getInventory().clear();
        }
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(steamKind.component())
                .add(Component.text(String.valueOf(currentLevel())))
                .add(ProgressBar.fluidContentsWithName(steamKind.fluid(), Math.max(1.0, currentCapacity()), currentAmount()));
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # F # R # S # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('F', steamKindItem)
                .addIngredient('R', readFaceItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_pressure_transducer.title"));
    }

    private final class SteamKindItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.COMPARATOR)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.steam_tier")))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKind.component()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle_steam"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            steamKind = steamKind.next();
            lastLevel = -1;
            tick();
        }
    }

    private final class ReadFaceItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean connected = SteamLogicSupport.fluidNeighbor(getBlock(), readFace) != null;
            return ItemStackBuilder.of(connected ? Material.PISTON : Material.BARRIER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.read_face",
                            RebarArgument.of("face", SteamLogicSupport.faceComponent(readFace)))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(connected
                                            ? "steamwork.gui.common.connected_fluid_buffer"
                                            : "steamwork.gui.common.no_fluid_buffer")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            BlockFace next = SteamLogicSupport.nextFace(readFace);
            readFace = next == null ? BlockFace.NORTH : next;
            lastLevel = -1;
            tick();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(currentLevel() > 0 ? Material.REDSTONE : Material.GUNPOWDER)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_pressure_transducer.comparator_output",
                            RebarArgument.of("level", String.valueOf(currentLevel())))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKind.component()))),
                            SteamLogicSupport.ni(SteamLogicSupport.pressureLine(currentAmount(), currentCapacity())),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_pressure_transducer.inventory_repairs"))
                    ));
        }

        @Override public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }
}
