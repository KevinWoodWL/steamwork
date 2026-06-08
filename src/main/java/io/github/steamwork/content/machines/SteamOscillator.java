package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidTankRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.SteamLogicSupport;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class SteamOscillator extends RebarBlock implements
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock {

    private enum CycleState {
        CHARGING("steamwork.gui.steam_oscillator.state.charging", NamedTextColor.YELLOW),
        BURSTING("steamwork.gui.steam_oscillator.state.bursting", NamedTextColor.GREEN);

        private final String translationKey;
        private final NamedTextColor color;

        CycleState(@NotNull String translationKey, @NotNull NamedTextColor color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        @NotNull Component component() {
            return Component.translatable(translationKey).color(color);
        }
    }

    private static final String KEY_KIND = "osc_kind";
    private static final String KEY_INPUT  = "osc_input";
    private static final String KEY_OUTPUT = "osc_output";
    private static final String KEY_STATE = "osc_state";
    private static final String KEY_BURST_LEFT = "osc_burst_left";

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double buffer = getSettings().getOrThrow("buffer", ConfigAdapter.DOUBLE);
    private final double upperThreshold = getSettings().getOrThrow("upper-threshold", ConfigAdapter.DOUBLE);
    private final double lowerThreshold = getSettings().getOrThrow("lower-threshold", ConfigAdapter.DOUBLE);
    private final int burstTicks = getSettings().getOrThrow("burst-ticks", ConfigAdapter.INTEGER);
    private final double burstTransferPerTick = getSettings().getOrThrow("burst-transfer-per-tick", ConfigAdapter.DOUBLE);
    private final double chargeRate = getSettings().get("charge-rate", ConfigAdapter.DOUBLE, 100.0);

    private SteamKind steamKind = SteamKind.STEAM;
    private BlockFace inputFace  = BlockFace.NORTH;
    private BlockFace outputFace = BlockFace.SOUTH;
    private CycleState state = CycleState.CHARGING;
    private int burstTicksLeft = 0;
    private boolean lastActive = false;
    private boolean outputBlocked = false;

    private final SteamKindItem  steamKindItem  = new SteamKindItem();
    private final InputFaceItem  inputFaceItem  = new InputFaceItem();
    private final OutputFaceItem outputFaceItem = new OutputFaceItem();
    private final StatusItem     statusItem     = new StatusItem();

    public static class Item extends RebarItem {
        private final double upperThreshold = getSettings().getOrThrow("upper-threshold", ConfigAdapter.DOUBLE);
        private final int burstTicks = getSettings().getOrThrow("burst-ticks", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("threshold", UnitFormat.MILLIBUCKETS.format(upperThreshold)),
                    RebarArgument.of("ticks", String.valueOf(burstTicks))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamOscillator(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setTickInterval(tickInterval);
        // 不走导管网络，所有缓存均为 input=false/output=false，靠 tick 主动抽/推
        createBuffers();
    }

    @SuppressWarnings("unused")
    public SteamOscillator(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(tickInterval);
        steamKind  = SteamKind.fromOrdinal(pdc.getOrDefault(steamworkKey(KEY_KIND), PersistentDataType.INTEGER, 0));
        inputFace  = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_INPUT),  BlockFace.NORTH);
        outputFace = SteamLogicSupport.loadFace(pdc, steamworkKey(KEY_OUTPUT), BlockFace.SOUTH);
        int stateOrdinal = pdc.getOrDefault(steamworkKey(KEY_STATE), PersistentDataType.INTEGER, 0);
        state = stateOrdinal == CycleState.BURSTING.ordinal() ? CycleState.BURSTING : CycleState.CHARGING;
        burstTicksLeft = Math.max(0, pdc.getOrDefault(steamworkKey(KEY_BURST_LEFT), PersistentDataType.INTEGER, 0));
    }

    private void createBuffers() {
        // input=false, output=false：不参与导管网络，仅供内部积攒使用
        createFluidBuffer(SteamworkFluids.STEAM,            buffer, false, false);
        createFluidBuffer(SteamworkFluids.SUPERHEATED_STEAM, buffer, false, false);
        createFluidBuffer(SteamworkFluids.PRESSURIZED_STEAM, buffer, false, false);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_KIND),       PersistentDataType.INTEGER, steamKind.ordinal());
        pdc.set(steamworkKey(KEY_INPUT),      PersistentDataType.STRING,  inputFace.name());
        pdc.set(steamworkKey(KEY_OUTPUT),     PersistentDataType.STRING,  outputFace.name());
        pdc.set(steamworkKey(KEY_STATE),      PersistentDataType.INTEGER, state.ordinal());
        pdc.set(steamworkKey(KEY_BURST_LEFT), PersistentDataType.INTEGER, burstTicksLeft);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    @Override
    public void tick() {
        outputBlocked = false;

        // 主动从输入面机器抽取选定蒸汽（不走导管网络）
        pullFromInput();

        double amount = fluidAmount(steamKind.fluid());
        boolean active = false;
        if (state == CycleState.CHARGING && amount >= upperThreshold) {
            state = CycleState.BURSTING;
            burstTicksLeft = burstTicks;
        }

        if (state == CycleState.BURSTING) {
            double moved = pushToOutput();
            burstTicksLeft--;
            if (moved > 0.0) {
                active = true;
                spawnBurstFx();
            } else {
                outputBlocked = fluidAmount(steamKind.fluid()) > lowerThreshold && burstTicksLeft > 0;
            }

            if (fluidAmount(steamKind.fluid()) <= lowerThreshold || burstTicksLeft <= 0) {
                state = CycleState.CHARGING;
                burstTicksLeft = 0;
                outputBlocked = false;
            }
        }

        setActive(active);
        notifyGuiItems();
    }

    /**
     * 从输入面相邻机器直接抽取选定蒸汽，不走导管网络。
     * 兼容两类流体接口：
     *   - {@link FluidBufferRebarBlock}（蒸汽工坊机器、大多数 rebar 机器）
     *   - {@link FluidTankRebarBlock}（Pylon 储罐 / 便携储罐）
     */
    private void pullFromInput() {
        if (state != CycleState.CHARGING) return;
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(inputFace));
        double space = fluidSpaceRemaining(steamKind.fluid());
        if (rb instanceof FluidBufferRebarBlock fb) {
            if (!fb.hasFluid(steamKind.fluid())) return;
            double amt = Math.min(chargeRate, Math.min(fb.fluidAmount(steamKind.fluid()), space));
            if (amt <= 0) return;
            fb.removeFluid(steamKind.fluid(), amt);
            addFluid(steamKind.fluid(), amt);
        } else if (rb instanceof FluidTankRebarBlock tank) {
            if (tank.getFluidType() != steamKind.fluid()) return;
            double amt = Math.min(chargeRate, Math.min(tank.getFluidAmount(), space));
            if (amt <= 0) return;
            tank.removeFluid(amt);
            addFluid(steamKind.fluid(), amt);
        }
    }

    /**
     * 向输出面相邻机器推送蒸汽，兼容 {@link FluidBufferRebarBlock} 和 {@link FluidTankRebarBlock}。
     */
    private double pushToOutput() {
        RebarBlock rb = PneumaticEndpointSupport.loadedRebarBlock(getBlock().getRelative(outputFace));
        double own = fluidAmount(steamKind.fluid());
        if (rb instanceof FluidBufferRebarBlock fb) {
            if (!fb.hasFluid(steamKind.fluid())) return 0.0;
            double amount = Math.min(burstTransferPerTick, Math.min(own, fb.fluidSpaceRemaining(steamKind.fluid())));
            if (amount <= 0.0) return 0.0;
            removeFluid(steamKind.fluid(), amount);
            fb.addFluid(steamKind.fluid(), amount);
            return amount;
        } else if (rb instanceof FluidTankRebarBlock tank) {
            // canAddFluid(fluid, 0) 检查流体类型兼容性（空罐或相同类型）
            if (!tank.canAddFluid(steamKind.fluid(), 0.0)) return 0.0;
            double amount = Math.min(burstTransferPerTick, Math.min(own, tank.getFluidSpaceRemaining()));
            if (amount <= 0.0 || !tank.canAddFluid(steamKind.fluid(), amount)) return 0.0;
            removeFluid(steamKind.fluid(), amount);
            tank.onFluidAdded(steamKind.fluid(), amount); // 处理类型设定 + 显示更新
            return amount;
        }
        return 0.0;
    }

    private void spawnBurstFx() {
        var loc = getBlock().getLocation().add(0.5, 0.75, 0.5);
        getBlock().getWorld().spawnParticle(Particle.CLOUD, loc, 4, 0.18, 0.08, 0.18, 0.025);
        if (Math.random() < 0.16) {
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 0.25f, 1.7f);
        }
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
        }
    }

    private void notifyGuiItems() {
        steamKindItem.notifyWindows();
        inputFaceItem.notifyWindows();
        outputFaceItem.notifyWindows();
        statusItem.notifyWindows();
    }

    private @NotNull Component statusComponent() {
        if (outputBlocked) {
            return Component.translatable("steamwork.gui.steam_oscillator.state.blocked").color(NamedTextColor.RED);
        }
        return state.component();
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("fluid", steamKind.component()),
                RebarArgument.of("pressure-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(steamKind.fluid()), fluidCapacity(steamKind.fluid()), 12,
                        steamKind.color())),
                RebarArgument.of("state", statusComponent())
        ));
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# F # I # O # S #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('F', steamKindItem)
                .addIngredient('I', inputFaceItem)
                .addIngredient('O', outputFaceItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_oscillator.title"));
    }


    private abstract class FaceItem extends AbstractItem {
        abstract @NotNull BlockFace face();
        abstract void set(@NotNull BlockFace face);
        abstract @NotNull String labelKey();

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean connected = SteamLogicSupport.fluidNeighbor(getBlock(), face()) != null;
            return ItemStackBuilder.of(connected ? Material.PISTON : Material.BARRIER)
                    .name(SteamLogicSupport.ni(Component.translatable(labelKey(),
                            RebarArgument.of("face", SteamLogicSupport.faceComponent(face())))))
                    .lore(List.of(
                            SteamLogicSupport.ni(Component.translatable(connected
                                            ? "steamwork.gui.common.connected_fluid_buffer"
                                            : "steamwork.gui.common.no_fluid_buffer")),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.common.left_click_cycle"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {
            BlockFace next = SteamLogicSupport.nextFace(face());
            set(next == null ? BlockFace.NORTH : next);
            notifyGuiItems();
        }
    }

    private final class SteamKindItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.CLOCK)
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
            notifyGuiItems();
        }
    }

    private final class InputFaceItem extends FaceItem {
        @Override @NotNull BlockFace face() { return inputFace; }
        @Override void set(@NotNull BlockFace face) { inputFace = face; }
        @Override @NotNull String labelKey() { return "steamwork.gui.common.input_face"; }
    }

    private final class OutputFaceItem extends FaceItem {
        @Override @NotNull BlockFace face() { return outputFace; }
        @Override void set(@NotNull BlockFace face) { outputFace = face; }
        @Override @NotNull String labelKey() { return "steamwork.gui.common.output_face"; }
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(outputBlocked
                            ? Material.RED_STAINED_GLASS_PANE
                            : lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(SteamLogicSupport.ni(statusComponent()))
                    .lore(List.of(
                            SteamLogicSupport.ni(SteamLogicSupport.pressureLine(fluidAmount(steamKind.fluid()), fluidCapacity(steamKind.fluid()))),
                            SteamLogicSupport.ni(Component.translatable("steamwork.gui.steam_oscillator.burst_left",
                                    RebarArgument.of("ticks", String.valueOf(burstTicksLeft))))
                    ));
        }

        @Override public void handleClick(@NotNull ClickType type, @NotNull Player player, @NotNull Click click) {}
    }
}
