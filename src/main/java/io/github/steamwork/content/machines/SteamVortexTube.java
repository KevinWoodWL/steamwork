package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.SteamLogicSupport;
import net.kyori.adventure.text.Component;
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
 * 蒸汽涡流分离器 —— 借鉴 PneumaticCraft 涡流管（Vortex Tube）。
 *
 * <p>输入过热蒸汽，靠 Ranque–Hilsch 涡流效应分离成两股：
 * <ul>
 *   <li>热端 → 加压蒸汽（{@link SteamworkFluids#PRESSURIZED_STEAM}）</li>
 *   <li>冷端 → 蒸馏水（{@link SteamworkFluids#DISTILLED_WATER}，蒸汽冷凝）</li>
 * </ul>
 * 给三档蒸汽补上「过热 → 加压」这一环，并副产蒸馏水。分离有约 10% 损耗。</p>
 */
public class SteamVortexTube extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double superheatedBuffer = getSettings().getOrThrow("superheated-buffer", ConfigAdapter.DOUBLE);
    private final double pressurizedBuffer = getSettings().getOrThrow("pressurized-buffer", ConfigAdapter.DOUBLE);
    private final double waterBuffer       = getSettings().getOrThrow("water-buffer", ConfigAdapter.DOUBLE);
    private final double inputPerCycle       = getSettings().getOrThrow("input-per-cycle", ConfigAdapter.DOUBLE);
    private final double pressurizedPerCycle = getSettings().getOrThrow("pressurized-per-cycle", ConfigAdapter.DOUBLE);
    private final double waterPerCycle       = getSettings().getOrThrow("water-per-cycle", ConfigAdapter.DOUBLE);

    private boolean lastActive = false;
    private final StatusItem statusItem = new StatusItem();
    private final InputGaugeItem inputGaugeItem = new InputGaugeItem();
    private final PressurizedGaugeItem pressurizedGaugeItem = new PressurizedGaugeItem();
    private final WaterGaugeItem waterGaugeItem = new WaterGaugeItem();

    public static class Item extends RebarItem {
        private final double inputPerCycle       = getSettings().getOrThrow("input-per-cycle", ConfigAdapter.DOUBLE);
        private final double pressurizedPerCycle = getSettings().getOrThrow("pressurized-per-cycle", ConfigAdapter.DOUBLE);
        private final double waterPerCycle       = getSettings().getOrThrow("water-per-cycle", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("input", UnitFormat.MILLIBUCKETS.format(inputPerCycle)),
                    RebarArgument.of("pressurized", UnitFormat.MILLIBUCKETS.format(pressurizedPerCycle)),
                    RebarArgument.of("water", UnitFormat.MILLIBUCKETS.format(waterPerCycle))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamVortexTube(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.EAST, context, false);
        createFluidBuffer(SteamworkFluids.SUPERHEATED_STEAM, superheatedBuffer, true, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, pressurizedBuffer, false, true);
        createFluidBuffer(SteamworkFluids.DISTILLED_WATER, waterBuffer, false, true);
    }

    @SuppressWarnings("unused")
    public SteamVortexTube(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    @Override
    public void tick() {
        boolean canWork = fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) >= inputPerCycle
                && fluidSpaceRemaining(SteamworkFluids.PRESSURIZED_STEAM) >= pressurizedPerCycle
                && fluidSpaceRemaining(SteamworkFluids.DISTILLED_WATER) >= waterPerCycle;
        if (canWork) {
            removeFluid(SteamworkFluids.SUPERHEATED_STEAM, inputPerCycle);
            addFluid(SteamworkFluids.PRESSURIZED_STEAM, pressurizedPerCycle);
            addFluid(SteamworkFluids.DISTILLED_WATER, waterPerCycle);
            spawnVortexFx();
        }
        setActive(canWork);
        statusItem.notifyWindows();
        inputGaugeItem.notifyWindows();
        pressurizedGaugeItem.notifyWindows();
        waterGaugeItem.notifyWindows();
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i # a # p # w #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('p', pressurizedGaugeItem)
                .addIngredient('w', waterGaugeItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_vortex_tube.title"));
    }

    private void spawnVortexFx() {
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD,
                getBlock().getLocation().add(0.5, 0.8, 0.5),
                5, 0.25, 0.1, 0.25, 0.03);
        if (Math.random() < 0.12) {
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.25f, 1.9f);
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
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(ProgressBar.fluidContentsWithName(SteamworkFluids.SUPERHEATED_STEAM, fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM), fluidAmount(SteamworkFluids.SUPERHEATED_STEAM)))
                .add(ProgressBar.fluidContentsWithName(SteamworkFluids.PRESSURIZED_STEAM, fluidCapacity(SteamworkFluids.PRESSURIZED_STEAM), fluidAmount(SteamworkFluids.PRESSURIZED_STEAM)))
                .add(Component.translatable("steamwork.state." + (lastActive ? "active" : "idle")));
    }

    // ── GUI 元素 ──────────────────────────────────────────────────────────────

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_vortex_tube.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(SteamLogicSupport.ni(Component.translatable(
                            "steamwork.gui.steam_vortex_tube.ratio",
                            RebarArgument.of("input", UnitFormat.MILLIBUCKETS.format(inputPerCycle).decimalPlaces(0)),
                            RebarArgument.of("pressurized", UnitFormat.MILLIBUCKETS.format(pressurizedPerCycle).decimalPlaces(0)),
                            RebarArgument.of("water", UnitFormat.MILLIBUCKETS.format(waterPerCycle).decimalPlaces(0))
                    ))));
        }
        @Override public void handleClick(@NotNull ClickType c, @NotNull Player p, @NotNull Click k) {}
    }

    private final class InputGaugeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return fluidGauge(Material.ORANGE_STAINED_GLASS, "steamwork.gui.steam_vortex_tube.input_gauge", SteamworkFluids.SUPERHEATED_STEAM);
        }
        @Override public void handleClick(@NotNull ClickType c, @NotNull Player p, @NotNull Click k) {}
    }

    private final class PressurizedGaugeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return fluidGauge(Material.CYAN_STAINED_GLASS, "steamwork.gui.steam_vortex_tube.pressurized_gauge", SteamworkFluids.PRESSURIZED_STEAM);
        }
        @Override public void handleClick(@NotNull ClickType c, @NotNull Player p, @NotNull Click k) {}
    }

    private final class WaterGaugeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return fluidGauge(Material.LIGHT_BLUE_STAINED_GLASS, "steamwork.gui.steam_vortex_tube.water_gauge", SteamworkFluids.DISTILLED_WATER);
        }
        @Override public void handleClick(@NotNull ClickType c, @NotNull Player p, @NotNull Click k) {}
    }

    private ItemProvider fluidGauge(Material material, String titleKey, RebarFluid fluid) {
        return ItemStackBuilder.of(material)
                .name(SteamLogicSupport.ni(Component.translatable(titleKey)))
                .lore(List.of(SteamLogicSupport.ni(Component.translatable(
                        "steamwork.gui.steam_vortex_tube.fluid_amount",
                        RebarArgument.of("amount", UnitFormat.MILLIBUCKETS.format(fluidAmount(fluid)).decimalPlaces(0)),
                        RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(fluidCapacity(fluid)).decimalPlaces(0))
                ))));
    }

}
