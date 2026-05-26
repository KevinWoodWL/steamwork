package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarProcessor;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.content.machines.upgrade.UpgradeModule;
import io.github.steamwork.content.machines.upgrade.UpgradeType;
import io.github.steamwork.content.machines.upgrade.UpgradeableMachine;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.window.Window;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 蒸汽涡轮基类 —— 用蒸汽加速附近"在工作的机器"。
 * <p>
 * 抽出 {@link SimpleSteamTurbine} / {@link AdvancedSteamTurbine} 90% 重复的：
 * 字段、构造、缓存扫描、tick、粒子、GUI、WAILA、加速实现。
 * <p>
 * 子类只需提供三处差异：
 * <ul>
 *   <li>{@link #translationPrefix()} 翻译键前缀</li>
 *   <li>{@link #particleCount()} 每 tick 喷的粒子数</li>
 *   <li>{@link #identifyTarget(Block)} 识别哪种方块可以被加速</li>
 * </ul>
 */
public abstract class AbstractSteamBooster extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock,
        UpgradeableMachine {

    protected final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    protected final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    protected final double steamPerOperation = getSettings().getOrThrow("steam-per-operation", ConfigAdapter.DOUBLE);
    protected final int speedBoostTicks = getSettings().getOrThrow("speed-boost-ticks", ConfigAdapter.INTEGER);
    protected final int scanRadius = getSettings().getOrThrow("scan-radius", ConfigAdapter.INTEGER);

    @Nullable protected final VirtualInventory upgradeInventory;

    protected boolean lastActive = false;
    protected int lastTargetsFound = 0;
    protected int lastTargetsBoosted = 0;
    protected double lastSteamUsed = 0.0;

    // 扫描缓存：每 SCAN_CACHE_TICKS tick 重扫一次，或环境变化时立即重扫。
    private static final int SCAN_CACHE_TICKS = 20;
    private int scanCacheTicks = 0;
    private Map<Block, TargetType> cachedTargets = null;
    private int cachedEnvironmentHash = 0;

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ScanItem scanItem = new ScanItem();

    private static final Set<Material> VANILLA_FURNACES = new HashSet<>(List.of(
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER
    ));

    /** 涡轮能加速的目标类型。 */
    public enum TargetType {
        /** 原版熔炉 / 高炉 / 烟熏炉。 */
        VANILLA_FURNACE,
        /** Steamwork 自家加工机器（实现 {@link SteamBoostable}）。 */
        STEAMWORK_BOOSTABLE,
        /** 任何实现 {@link RebarProcessor} 的 Rebar/Pylon 机器。 */
        REBAR_PROCESSOR
    }

    /** 共享物品基类：相同的 placeholder 集合。 */
    public static abstract class BaseItem extends RebarItem {

        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerOperation = getSettings().getOrThrow("steam-per-operation", ConfigAdapter.DOUBLE);
        private final int scanRadius = getSettings().getOrThrow("scan-radius", ConfigAdapter.INTEGER);

        protected BaseItem(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("scan-radius", UnitFormat.BLOCKS.format(scanRadius)),
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("steam-per-operation", UnitFormat.MILLIBUCKETS.format(steamPerOperation))
            );
        }
    }

    protected AbstractSteamBooster(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        this.upgradeInventory = upgradeSlotCount() > 0 ? new VirtualInventory(upgradeSlotCount()) : null;
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
    }

    protected AbstractSteamBooster(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        this.upgradeInventory = upgradeSlotCount() > 0 ? new VirtualInventory(upgradeSlotCount()) : null;
    }

    @Override
    public void postInitialise() {
        if (upgradeInventory != null) {
            upgradeInventory.addPreUpdateHandler(event -> {
                ItemStack newItem = event.getNewItem();
                if (newItem == null || newItem.isEmpty()) return;
                if (!(RebarItem.fromStack(newItem) instanceof UpgradeModule module)) {
                    event.setCancelled(true);
                    return;
                }
                // 涡轮只接受节能、扫描半径、加速力度三种模组；
                // AUTO_INPUT / AUTO_OUTPUT / BULK / PYLON_COMPAT 对涡轮无效，拒绝入槽。
                UpgradeType type = module.getUpgradeType();
                if (type != UpgradeType.ENERGY_SAVE
                        && type != UpgradeType.RANGE
                        && type != UpgradeType.BOOST) {
                    event.setCancelled(true);
                }
            });
        }
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        if (upgradeInventory != null) {
            return Map.of("upgrades", upgradeInventory);
        }
        return Map.of();
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull io.github.pylonmc.rebar.block.context.BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        RebarFluidBufferBlock.super.onBreak(drops, context);
    }

    @Override
    public void openUpgradeGui(@NotNull Player player) {
        if (upgradeInventory == null) return;
        Window.builder()
                .setUpperGui(buildUpgradeGui())
                .setTitle(noItalic(Component.translatable("steamwork.gui.upgrade.title")))
                .setViewer(player)
                .build()
                .open();
    }

    private @NotNull Gui buildUpgradeGui() {
        String middleRow = switch (upgradeSlotCount()) {
            case 1 -> "# # # # u # # # #";
            case 2 -> "# # # u u # # # #";
            case 3 -> "# # # u u u # # #";
            default -> "# # u u u u # # #";
        };
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        middleRow,
                        "# # # # c # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('u', upgradeInventory)
                .addIngredient('c', new CloseItem())
                .build();
    }

    private final class CloseItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BARRIER)
                    .name(noItalic(Component.translatable("steamwork.gui.upgrade.close")));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            player.closeInventory();
        }
    }

    // ===== 升级生效辅助 =====

    private int countUpgrade(@NotNull UpgradeType type) {
        if (upgradeInventory == null) return 0;
        int count = 0;
        for (int i = 0; i < upgradeInventory.getSize(); i++) {
            ItemStack stack = upgradeInventory.getItem(i);
            if (stack != null && !stack.isEmpty()
                    && RebarItem.fromStack(stack) instanceof UpgradeModule module
                    && module.getUpgradeType() == type) {
                count++;
            }
        }
        return count;
    }

    /** 实际扫描半径 = 基础半径 + RANGE 模组数。 */
    private int effectiveScanRadius() {
        return scanRadius + countUpgrade(UpgradeType.RANGE);
    }

    /** 实际 boost tick 数 = 基础 tick + BOOST 模组数。 */
    private int effectiveBoostTicks() {
        return speedBoostTicks + countUpgrade(UpgradeType.BOOST);
    }

    /** 蒸汽消耗倍率：每个 ENERGY_SAVE 模组 -20%，最低保留 20%。 */
    private double effectiveSteamCost() {
        return steamPerOperation * Math.max(0.2, 1.0 - 0.2 * countUpgrade(UpgradeType.ENERGY_SAVE));
    }

    // ===== 子类接口 =====

    /** i18n 前缀，例如 "steamwork.gui.simple_steam_turbine"。 */
    protected abstract @NotNull String translationPrefix();

    /** 工作时每 tick 喷的粒子数（强弱涡轮可以不同）。 */
    protected abstract int particleCount();

    /** 识别一个方块是否是可加速目标；返回 null 表示不识别（即不加速）。 */
    protected abstract @Nullable TargetType identifyTarget(@NotNull Block block);

    // ===== 子类可复用的 helper =====

    /** 升级槽数量，0 表示不支持升级。子类覆盖此方法来开启升级功能。 */
    @Override
    public int upgradeSlotCount() { return 0; }

    /** vanilla 熔炉识别 helper，让子类的 {@link #identifyTarget} 复用。 */
    protected final boolean isVanillaFurnace(@NotNull Block block) {
        return VANILLA_FURNACES.contains(block.getType());
    }

    // ===== Tick 主逻辑 =====

    @Override
    public void tick() {
        if (scanCacheTicks > 0) scanCacheTicks--;

        lastTargetsFound = 0;
        lastTargetsBoosted = 0;
        lastSteamUsed = 0.0;

        Map<Block, TargetType> targets = getCachedTargets();
        processCachedTargets(targets);

        setActive(lastTargetsBoosted > 0);
        notifyGuiItems();
        spawnSteamParticlesIfActive();
    }

    /** 目标是否在 "正在工作"，否则加速没意义。 */
    private boolean canBoost(@NotNull Block block, @NotNull TargetType type) {
        try {
            switch (type) {
                case VANILLA_FURNACE:
                    Furnace furnace = (Furnace) block.getState();
                    return furnace.getCookTime() > 0 && furnace.getCookTimeTotal() > 0;
                case STEAMWORK_BOOSTABLE:
                    // SteamBoostable 没有 isProcessing 探针；boostProcess 内部会自行判断空 op。
                    return BlockStorage.get(block) instanceof SteamBoostable;
                case REBAR_PROCESSOR:
                    RebarBlock rb = BlockStorage.get(block);
                    return rb instanceof RebarProcessor processor && processor.isProcessing();
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** 推进目标的进度。 */
    private void boost(@NotNull Block block, @NotNull TargetType type) {
        int boostTicks = effectiveBoostTicks();
        try {
            switch (type) {
                case VANILLA_FURNACE:
                    boostFurnace(block, boostTicks);
                    break;
                case STEAMWORK_BOOSTABLE:
                    if (BlockStorage.get(block) instanceof SteamBoostable boostable) {
                        boostable.boostProcess(boostTicks);
                    }
                    break;
                case REBAR_PROCESSOR:
                    RebarBlock rb = BlockStorage.get(block);
                    if (rb instanceof RebarProcessor processor) {
                        processor.progressProcess(boostTicks);
                    }
                    break;
            }
        } catch (Exception ignored) {
            // 单个目标加速失败不影响其他目标。
        }
    }

    private void boostFurnace(@NotNull Block block, int boostTicks) {
        Furnace furnace = (Furnace) block.getState();
        int newCookTime = Math.min(furnace.getCookTimeTotal() - 1, furnace.getCookTime() + boostTicks);
        furnace.setCookTime((short) newCookTime);
        furnace.update(true, false);
    }

    private void spawnSteamParticlesIfActive() {
        if (lastActive) {
            getBlock().getWorld().spawnParticle(
                    Particle.CLOUD,
                    getBlock().getLocation().add(0.5, 0.8, 0.5),
                    particleCount(), 0.2, 0.1, 0.2, 0.02);
        }
    }

    // ===== 扫描缓存 =====

    /** 环境哈希用于检测周围方块是否变化（便宜的 invalidation 策略）。 */
    private int calculateEnvironmentHash() {
        int hash = 0;
        int radius = effectiveScanRadius();
        int checkRadius = Math.min(radius + 2, 12);
        for (int x = -checkRadius; x <= checkRadius; x++) {
            for (int y = -checkRadius; y <= checkRadius; y++) {
                for (int z = -checkRadius; z <= checkRadius; z++) {
                    if (x * x + y * y + z * z > (checkRadius + 1) * (checkRadius + 1)) continue;
                    Block block = getBlock().getRelative(x, y, z);
                    hash = hash * 31 + (block.isEmpty() ? 0 : block.getType().hashCode());
                }
            }
        }
        return hash;
    }

    private Map<Block, TargetType> getCachedTargets() {
        int currentHash = calculateEnvironmentHash();

        boolean needsRefresh = cachedTargets == null
                || scanCacheTicks <= 0
                || currentHash != cachedEnvironmentHash;

        if (needsRefresh) {
            cachedTargets = scanForTargets();
            cachedEnvironmentHash = currentHash;
            scanCacheTicks = SCAN_CACHE_TICKS;
        }

        return cachedTargets != null ? cachedTargets : new HashMap<>();
    }

    private Map<Block, TargetType> scanForTargets() {
        int radius = effectiveScanRadius();
        Map<Block, TargetType> targets = new HashMap<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (x * x + y * y + z * z > radius * radius) continue;

                    Block target = getBlock().getRelative(x, y, z);
                    TargetType type = identifyTarget(target);
                    if (type != null) {
                        targets.put(target, type);
                    }
                }
            }
        }
        return targets;
    }

    private void processCachedTargets(Map<Block, TargetType> targets) {
        double steamCost = effectiveSteamCost();
        for (Map.Entry<Block, TargetType> entry : targets.entrySet()) {
            Block target = entry.getKey();
            TargetType type = entry.getValue();

            lastTargetsFound++;
            if (!canBoost(target, type) || fluidAmount(SteamworkFluids.STEAM) < steamCost) {
                continue;
            }

            removeFluid(SteamworkFluids.STEAM, steamCost);
            boost(target, type);
            lastTargetsBoosted++;
            lastSteamUsed += steamCost;
        }
    }

    // ===== 状态 / WAILA / GUI =====

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        Map<String, kotlin.Pair<String, Integer>> properties = super.getBlockTextureProperties();
        properties.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return properties;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", createSteamBar()),
                RebarArgument.of("targets", lastTargetsFound),
                RebarArgument.of("boosted", lastTargetsBoosted),
                RebarArgument.of("state", Component.translatable("steamwork.state." + (lastActive ? "active" : "idle")))
        ));
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # s # a # r # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('r', scanItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable(translationPrefix() + ".title"));
    }

    private String createSteamBar() {
        double amount = fluidAmount(SteamworkFluids.STEAM);
        double capacity = fluidCapacity(SteamworkFluids.STEAM);
        int filled = (int) Math.round(16.0 * amount / Math.max(1.0, capacity));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            bar.append(i < filled ? "|" : ".");
        }
        return bar.toString();
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        scanItem.notifyWindows();
    }

    // ===== Component helpers =====

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull Component percentComponent(int pct) {
        TextColor color = pct >= 50 ? NamedTextColor.GREEN : pct >= 20 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        return Component.text(pct + "%", color);
    }

    private static @NotNull Component barComponent(int pct, int width) {
        Component bar = Component.empty();
        int filled = (int) Math.round(width * pct / 100.0);
        for (int i = 0; i < width; i++) {
            bar = bar.append(Component.text("|", i < filled ? progressColor(pct) : NamedTextColor.DARK_GRAY));
        }
        return bar;
    }

    private static @NotNull TextColor progressColor(int pct) {
        if (pct >= 85) return NamedTextColor.GREEN;
        if (pct >= 50) return NamedTextColor.YELLOW;
        if (pct >= 20) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    private @NotNull Component steamLine(double steam, double cap) {
        return noItalic(Component.translatable(
                translationPrefix() + ".steam",
                RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steam).decimalPlaces(0)),
                RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(cap).decimalPlaces(0))
        ));
    }

    // ===== GUI items（用 translationPrefix() 动态生成翻译键） =====

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    translationPrefix() + ".last_boosted",
                    RebarArgument.of("boosted", lastTargetsBoosted)
            )));
            lore.add(noItalic(Component.translatable(
                    translationPrefix() + ".last_steam_used",
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(lastSteamUsed).decimalPlaces(0))
            )));
            return ItemStackBuilder.of(lastActive ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable(translationPrefix() + ".status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.STEAM);
            double cap = fluidCapacity(SteamworkFluids.STEAM);
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));

            Material mat = pct >= 75 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct >= 50 ? Material.CYAN_STAINED_GLASS
                    : pct >= 25 ? Material.BLUE_STAINED_GLASS
                    : pct > 0 ? Material.GRAY_STAINED_GLASS
                    : Material.BLACK_STAINED_GLASS;

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(translationPrefix() + ".steam_gauge")))
                    .lore(List.of(
                            steamLine(steam, cap),
                            noItalic(Component.translatable(
                                    translationPrefix() + ".progress_bar",
                                    RebarArgument.of("bar", barComponent(pct, 20)),
                                    RebarArgument.of("percent", percentComponent(pct))
                            ))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class ScanItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            int effectiveRadius = effectiveScanRadius();
            int effectiveTicks  = effectiveBoostTicks();
            double effectiveSteam = effectiveSteamCost();
            return ItemStackBuilder.of(Material.SPYGLASS)
                    .name(noItalic(Component.translatable(translationPrefix() + ".scan")))
                    .lore(List.of(
                            noItalic(Component.translatable(
                                    translationPrefix() + ".scan_radius",
                                    RebarArgument.of("radius", UnitFormat.BLOCKS.format(effectiveRadius))
                            )),
                            noItalic(Component.translatable(
                                    translationPrefix() + ".targets_found",
                                    RebarArgument.of("targets", lastTargetsFound)
                            )),
                            noItalic(Component.translatable(
                                    translationPrefix() + ".boost",
                                    RebarArgument.of("ticks", effectiveTicks),
                                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(effectiveSteam).decimalPlaces(0))
                            ))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }
}
