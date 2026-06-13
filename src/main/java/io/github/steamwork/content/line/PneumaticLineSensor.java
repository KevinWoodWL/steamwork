package io.github.steamwork.content.line;

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
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkResearches;
import io.github.steamwork.util.SteamLogicSupport.SteamKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.UUID;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 汽动产线传感器 —— 汽动逻辑与产线系统的桥接方块（物品→逻辑）。
 *
 * <p>实现 {@link ProductionLineMember}，可作为产线的一环。
 * 物品从上游 acceptFromLine 进入后立即转发到下游（透传），
 * 同时在信号面输出蒸汽脉冲/持续信号，供下游逻辑元件读取。</p>
 *
 * <p>三种工作模式：</p>
 * <ul>
 *   <li>PULSE：每次物品经过时输出一个蒸汽脉冲</li>
 *   <li>JAMMED：产线堵塞时持续输出蒸汽</li>
 *   <li>IDLE：产线空闲（无物品经过超过一定 tick）时持续输出蒸汽</li>
 * </ul>
 */
public class PneumaticLineSensor extends RebarBlock implements
        DirectionalRebarBlock, FluidBufferRebarBlock,
        GuiRebarBlock, TickingRebarBlock, ProductionLineMember {

    public enum SensorMode {
        PULSE("steamwork.gui.pneumatic_line_sensor.mode.pulse"),
        JAMMED("steamwork.gui.pneumatic_line_sensor.mode.jammed"),
        IDLE("steamwork.gui.pneumatic_line_sensor.mode.idle");

        private final String translationKey;
        SensorMode(@NotNull String key) { this.translationKey = key; }
        public @NotNull Component component() { return Component.translatable(translationKey); }
    }

    private static final RebarFluid[] STEAMS = {
        SteamworkFluids.STEAM, SteamworkFluids.SUPERHEATED_STEAM, SteamworkFluids.PRESSURIZED_STEAM
    };

    private static final String KEY_MODE        = "pls_mode";
    private static final String KEY_STEAM_KIND  = "pls_kind";

    private final int tickInterval     = getSettings().get("tick-interval", ConfigAdapter.INTEGER, 2);
    private final double pulseAmount   = getSettings().get("pulse-amount", ConfigAdapter.DOUBLE, 100.0);
    private final int pulseDuration    = getSettings().get("pulse-duration", ConfigAdapter.INTEGER, 10);
    private final int idleThreshold    = getSettings().get("idle-threshold", ConfigAdapter.INTEGER, 40);
    private final double bufferCapacity = getSettings().get("buffer", ConfigAdapter.DOUBLE, 500.0);

    // ===== 状态 =====

    private @NotNull SensorMode sensorMode = SensorMode.PULSE;
    private @Nullable SteamKind steamKind = null; // null = 普通蒸汽
    private boolean signalActive = false;
    private int pulseTicksRemaining = 0;
    private int idleCounter = 0;

    // 产线成员字段
    @Nullable private UUID lineId = null;
    private int linePosition = 0;
    @NotNull private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String lineCreator = null;
    private int lineNumber = 0;
    private boolean lineJammed = false;

    // GUI 元素
    private final ModeItem modeItem = new ModeItem();
    private final SteamKindItem steamKindItem = new SteamKindItem();
    private final StatusItem statusItem = new StatusItem();

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() { return List.of(); }

        @Override
        public boolean prePlace(@NotNull BlockCreateContext context) {
            // 货运 × 逻辑 桥：货运研究由框架按物品门控，这里额外要求已解锁汽动逻辑研究
            if (SteamworkResearches.denyLineBridgePlacement(context.getPlayer())) {
                return false;
            }
            return super.prePlace(context);
        }
    }

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public PneumaticLineSensor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing().getOppositeFace());
        setTickInterval(tickInterval);
        // 信号输出端点：为全部三种蒸汽各建一个缓存，否则选择过热/加压档位时
        // hasFluid(outputFluid) 恒为 false，信号永远发不出去。
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false, 0.5f);
        for (RebarFluid fluid : STEAMS) {
            createFluidBuffer(fluid, bufferCapacity, false, true);
        }
    }

    @SuppressWarnings("unused")
    public PneumaticLineSensor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        setTickInterval(tickInterval);
        int modeOrd = pdc.getOrDefault(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, 0);
        SensorMode[] modes = SensorMode.values();
        sensorMode = (modeOrd >= 0 && modeOrd < modes.length) ? modes[modeOrd] : SensorMode.PULSE;
        int kindOrd = pdc.getOrDefault(steamworkKey(KEY_STEAM_KIND), PersistentDataType.INTEGER, -1);
        SteamKind[] kinds = SteamKind.values();
        steamKind = (kindOrd >= 0 && kindOrd < kinds.length) ? kinds[kindOrd] : null;
        try { getFacing(); } catch (IllegalStateException e) { setFacing(BlockFace.NORTH); }

        // 产线信息
        String id = pdc.get(LINE_ID_KEY, PersistentDataType.STRING);
        if (id != null) {
            try {
                lineId = UUID.fromString(id);
                linePosition = pdc.getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
                String dir = pdc.get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
                try { lineDirection = dir != null ? BlockFace.valueOf(dir) : BlockFace.SELF; }
                catch (IllegalArgumentException ignored) { lineDirection = BlockFace.SELF; }
                lineCreator = pdc.get(LINE_CREATOR_KEY, PersistentDataType.STRING);
                lineNumber = pdc.getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
            } catch (IllegalArgumentException ignored) { lineId = null; }
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(steamworkKey(KEY_MODE), PersistentDataType.INTEGER, sensorMode.ordinal());
        pdc.set(steamworkKey(KEY_STEAM_KIND), PersistentDataType.INTEGER, steamKind != null ? steamKind.ordinal() : -1);
        if (lineId != null) {
            pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
            pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, lineDirection.name());
            pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, lineCreator);
            else pdc.remove(LINE_CREATOR_KEY);
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== Tick =====

    @Override
    public void tick() {
        if (!isInLine()) {
            // 产线未启用：传感器不工作——复位计数、清空所有信号输出缓存
            signalActive = false;
            pulseTicksRemaining = 0;
            idleCounter = 0;
            for (RebarFluid fluid : STEAMS) {
                if (hasFluid(fluid) && fluidAmount(fluid) > 0) removeFluid(fluid, fluidAmount(fluid));
            }
            notifyGuiItems();
            return;
        }
        if (idleCounter < idleThreshold) idleCounter += tickInterval;  // 到阈值封顶，避免无上界累加

        // 脉冲倒计时
        if (pulseTicksRemaining > 0) {
            pulseTicksRemaining -= tickInterval;
            if (pulseTicksRemaining <= 0) {
                pulseTicksRemaining = 0;
            }
        }

        // 判断信号输出
        boolean shouldOutput = switch (sensorMode) {
            case PULSE -> pulseTicksRemaining > 0;
            case JAMMED -> lineJammed;
            case IDLE -> idleCounter >= idleThreshold;
        };

        signalActive = shouldOutput;

        // 输出蒸汽到自身缓存（供下游抽取）
        RebarFluid outputFluid = (steamKind != null) ? steamKind.fluid() : SteamworkFluids.STEAM;
        if (signalActive) {
            if (hasFluid(outputFluid)) {
                double space = fluidSpaceRemaining(outputFluid);
                if (space > 0) addFluid(outputFluid, Math.min(pulseAmount, space));
            }
        } else {
            // 无信号时排空缓存
            if (hasFluid(outputFluid) && fluidAmount(outputFluid) > 0) {
                removeFluid(outputFluid, fluidAmount(outputFluid));
            }
        }

        notifyGuiItems();
    }

    // ===== ProductionLineMember =====

    @Override public @Nullable UUID getLineId() { return lineId; }
    @Override public int getLinePosition() { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection() { return lineDirection; }
    @Override public @Nullable String getLineCreator() { return lineCreator; }
    @Override public int getLineNumber() { return lineNumber; }
    @Override public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }
    @Override public void setLineNumber(int number) { this.lineNumber = number; }

    @Override
    public void joinLine(@NotNull UUID id, int position, @NotNull BlockFace direction) {
        this.lineId = id;
        this.linePosition = position;
        this.lineDirection = direction;
    }

    @Override
    public void leaveLine() {
        this.lineId = null;
        this.linePosition = 0;
        this.lineDirection = BlockFace.SELF;
        this.lineCreator = null;
        this.lineNumber = 0;
        this.lineJammed = false;
        this.pulseTicksRemaining = 0;
        this.idleCounter = 0;
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        // 物品透传：立即转发到下游
        idleCounter = 0; // 重置空闲计数器

        if (sensorMode == SensorMode.PULSE) {
            pulseTicksRemaining = pulseDuration;
        }

        if (!isInLine() || lineDirection == BlockFace.SELF) return false;
        ItemStack remaining = ProductionLineMember.deliverToNextMember(getBlock(), this, item);
        return remaining.isEmpty();
    }

    @Override public void onLineJammed() { this.lineJammed = true; }
    @Override public void onLineResumed() { this.lineJammed = false; }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # M # K # S # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('M', modeItem)
                .addIngredient('K', steamKindItem)
                .addIngredient('S', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return ni(Component.translatable("steamwork.gui.pneumatic_line_sensor.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        if (!isInLine()) {
            return WailaDisplay.of(this, player)
                    .add(Component.translatable("steamwork.gui.common.line_disabled").color(NamedTextColor.GRAY));
        }
        return WailaDisplay.of(this, player)
                .add(sensorMode.component().color(NamedTextColor.AQUA))
                .add(Component.translatable(signalActive
                        ? "steamwork.gui.pneumatic_line_sensor.state.active"
                        : "steamwork.gui.pneumatic_line_sensor.state.idle")
                        .color(signalActive ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(signalActive), 2));
        return props;
    }

    private void notifyGuiItems() {
        modeItem.notifyWindows();
        steamKindItem.notifyWindows();
        statusItem.notifyWindows();
    }

    private @NotNull Component steamKindLabel() {
        return steamKind != null
                ? steamKind.component()
                : Component.translatable("steamwork.gui.common.steam_kind.all");
    }

    // ===== GUI 内部类 =====

    private final class ModeItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.COMPARATOR)
                    .name(ni(Component.translatable("steamwork.gui.pneumatic_line_sensor.mode_label")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", sensorMode.component()))),
                            ni(Component.translatable("steamwork.gui.pneumatic_line_sensor.mode_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            SensorMode[] modes = SensorMode.values();
            sensorMode = modes[(sensorMode.ordinal() + 1) % modes.length];
            notifyGuiItems();
        }
    }

    private final class SteamKindItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.NETHER_STAR)
                    .name(ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.common.current",
                                    RebarArgument.of("value", steamKindLabel()))),
                            ni(Component.translatable("steamwork.gui.pneumatic_logic_gate.steam_filter_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            if (steamKind == null) { steamKind = SteamKind.STEAM; }
            else {
                SteamKind[] kinds = SteamKind.values();
                int next = steamKind.ordinal() + 1;
                steamKind = (next >= kinds.length) ? null : kinds[next];
            }
            notifyGuiItems();
        }
    }

    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            if (!isInLine()) {
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(ni(Component.translatable("steamwork.gui.common.line_disabled")))
                        .lore(List.of(ni(Component.translatable("steamwork.gui.common.line_disabled_hint"))));
            }
            return ItemStackBuilder.of(signalActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(ni(Component.translatable(signalActive
                            ? "steamwork.gui.pneumatic_line_sensor.state.active"
                            : "steamwork.gui.pneumatic_line_sensor.state.idle")))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.pneumatic_line_sensor.status_hint"))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }
}
