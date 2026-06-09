package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;

/**
 * 蒸汽加压机 —— 将普通蒸汽加压成加压蒸汽，供气动货运系统使用。
 * 默认转化比：2mB 普通蒸汽 → 1mB 加压蒸汽。
 */
public class SteamCompressor extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double steamInputPerCycle = getSettings().getOrThrow("steam-input-per-cycle", ConfigAdapter.DOUBLE);
    private final double pressurizedOutputPerCycle = getSettings().getOrThrow("pressurized-output-per-cycle", ConfigAdapter.DOUBLE);

    private boolean lastActive = false;
    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final OutputGaugeItem outputGaugeItem = new OutputGaugeItem();

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
        private final double steamInputPerCycle = getSettings().getOrThrow("steam-input-per-cycle", ConfigAdapter.DOUBLE);
        private final double pressurizedOutputPerCycle = getSettings().getOrThrow("pressurized-output-per-cycle", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("pressurized-buffer", UnitFormat.MILLIBUCKETS.format(pressurizedBuffer)),
                    RebarArgument.of("input", UnitFormat.MILLIBUCKETS.format(steamInputPerCycle)),
                    RebarArgument.of("output", UnitFormat.MILLIBUCKETS.format(pressurizedOutputPerCycle))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamCompressor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, false, true);
    }

    @SuppressWarnings("unused")
    public SteamCompressor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    @Override
    public void tick() {
        boolean canWork = fluidAmount(SteamworkFluids.STEAM) >= steamInputPerCycle
                && fluidSpaceRemaining(SteamworkFluids.PRESSURIZED_STEAM) >= pressurizedOutputPerCycle;
        if (canWork) {
            removeFluid(SteamworkFluids.STEAM, steamInputPerCycle);
            addFluid(SteamworkFluids.PRESSURIZED_STEAM, pressurizedOutputPerCycle);
            spawnCompressFx();
        }
        setActive(canWork);
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        outputGaugeItem.notifyWindows();
    }

    private void spawnCompressFx() {
        var loc = getBlock().getLocation().add(0.5, 0.8, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.15, 0.05, 0.15, 0.01);
        getBlock().getWorld().spawnParticle(Particle.CRIT, loc, 2, 0.1, 0.05, 0.1, 0.04);
        if (Math.random() < 0.2) {
            getBlock().getWorld().playSound(getBlock().getLocation(),
                    Sound.BLOCK_PISTON_EXTEND, 0.2f, 1.6f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # i # a # o # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('o', outputGaugeItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_compressor.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(ProgressBar.fluidContents(SteamworkFluids.STEAM, fluidCapacity(SteamworkFluids.STEAM), fluidAmount(SteamworkFluids.STEAM)))
                .add(ProgressBar.fluidContents(SteamworkFluids.PRESSURIZED_STEAM, fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM), fluidAmount(SteamworkFluids.PRESSURIZED_STEAM)))
                .add(Component.translatable("steamwork.state." + (lastActive ? "active" : "idle")));
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_compressor.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_compressor.ratio",
                            RebarArgument.of("input", UnitFormat.MILLIBUCKETS.format(steamInputPerCycle).decimalPlaces(0)),
                            RebarArgument.of("output", UnitFormat.MILLIBUCKETS.format(pressurizedOutputPerCycle).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return fluidGauge(Material.LIGHT_BLUE_STAINED_GLASS,
                    "steamwork.gui.steam_compressor.input_gauge", SteamworkFluids.STEAM);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class OutputGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return fluidGauge(Material.CYAN_STAINED_GLASS,
                    "steamwork.gui.steam_compressor.output_gauge", SteamworkFluids.PRESSURIZED_STEAM);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private ItemProvider fluidGauge(Material material, String titleKey,
                                    io.github.pylonmc.rebar.fluid.RebarFluid fluid) {
        double amount = fluidAmount(fluid);
        double capacity = fluidCapacity(fluid);
        return ItemStackBuilder.of(material)
                .name(noItalic(Component.translatable(titleKey)))
                .lore(List.of(noItalic(Component.translatable(
                        "steamwork.gui.steam_compressor.fluid_amount",
                        RebarArgument.of("amount", UnitFormat.MILLIBUCKETS.format(amount).decimalPlaces(0)),
                        RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0))
                ))));
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
