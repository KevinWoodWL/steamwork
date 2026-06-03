package io.github.steamwork.content.machines;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
import io.github.pylonmc.rebar.block.base.RebarProcessor;
import io.github.pylonmc.rebar.block.base.RebarRecipeProcessor;
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
import io.github.pylonmc.rebar.fluid.RebarFluid;
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
import java.util.WeakHashMap;

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
        RebarInventoryBlock,
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

    /**
     * 跨涡轮叠加层数限制：同一 Block 在同一服务器 tick 内最多被加速的次数。
     * 超过此上限的加速请求会被静默丢弃（蒸汽仍不消耗）。
     */
    private static final int MAX_BOOST_STACKS = 3;

    /**
     * 全局 tick 计数器，用于判断 {@link #boostStacksThisTick} 是否属于当前 tick。
     * 每次任意涡轮 tick 时自增（实际上只需要与上次记录的值不同即可）。
     */
    private static long globalTickCounter = 0;

    /**
     * 记录本 tick 内每个 Block 已被加速的次数。
     * 使用 WeakHashMap 避免持有 Block 强引用导致区块无法卸载。
     * key = 目标方块，value = [tickCounter, stackCount]（用长整型打包：高32位=tick，低32位=count）
     */
    private static final Map<Block, long[]> boostStacksThisTick = new WeakHashMap<>();

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
        REBAR_PROCESSOR,
        /**
         * 任何实现 {@link RebarRecipeProcessor} 的 Rebar/Pylon 机器。
         * <p>这是与 {@link RebarProcessor} 并列的独立接口（不存在继承关系），
         * 主要用于液压表锯、液压管道弯折机、柴油炉、柴油砂轮等配方型机器。
         */
        REBAR_RECIPE_PROCESSOR
    }

    /**
     * 判断方块是否属于 Pylon 液压系列机器（按类名包路径识别）。
     * <p>Pylon 的 {@code HydraulicRefuelable} 接口未被任何液压机器实现，
     * 因此唯一可靠的识别方式是检查类所在包。</p>
     */
    protected static boolean isPylonHydraulicMachine(@NotNull Block block) {
        RebarBlock rb = BlockStorage.get(block);
        return rb != null && rb.getClass().getName()
                .startsWith("io.github.pylonmc.pylon.content.machines.hydraulics.");
    }

    /**
     * 判断方块是否属于 Pylon 柴油系列机器（按类名包路径识别）。
     * <p>Pylon 的 {@code DieselRefuelable} 接口未被任何柴油机器实现，
     * 因此唯一可靠的识别方式是检查类所在包。</p>
     */
    protected static boolean isPylonDieselMachine(@NotNull Block block) {
        RebarBlock rb = BlockStorage.get(block);
        return rb != null && rb.getClass().getName()
                .startsWith("io.github.pylonmc.pylon.content.machines.diesel.");
    }

    /**
     * 将一个已确认为处理机器的 {@link RebarBlock} 归类为
     * {@link TargetType#REBAR_PROCESSOR} 或 {@link TargetType#REBAR_RECIPE_PROCESSOR}。
     * 若两者都不实现则返回 {@code null}。
     */
    protected static @Nullable TargetType processorTargetType(@NotNull RebarBlock rb) {
        if (rb instanceof RebarProcessor) return TargetType.REBAR_PROCESSOR;
        if (rb instanceof RebarRecipeProcessor) return TargetType.REBAR_RECIPE_PROCESSOR;
        return null;
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
        createFluidBuffer(boosterFluid(), steamBuffer, true, false);
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
                    return;
                }
                // 增幅模组（BOOST）不可叠加：同一涡轮只允许装 1 个。
                if (type == UpgradeType.BOOST) {
                    int currentSlot = event.getSlot();
                    for (int i = 0; i < upgradeInventory.getSize(); i++) {
                        if (i == currentSlot) continue;
                        ItemStack existing = upgradeInventory.getItem(i);
                        if (existing != null && !existing.isEmpty()
                                && RebarItem.fromStack(existing) instanceof UpgradeModule em
                                && em.getUpgradeType() == UpgradeType.BOOST) {
                            event.setCancelled(true);
                            return;
                        }
                    }
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

    /**
     * 同时可加速的目标上限，0 = 无限制（向后兼容旧涡轮）。
     * 子类覆盖此方法来启用上限约束。
     */
    protected int maxTargets() { return 0; }

    /**
     * 是否可以突破全局叠加层数上限（{@link #MAX_BOOST_STACKS}）。
     * <p>普通涡轮返回 {@code false}，当一台机器本 tick 已被加速 MAX_BOOST_STACKS 次时，
     * 后续涡轮的加速请求会被静默丢弃。覆盖为 {@code true} 后，该涡轮的加速始终生效，
     * 不受叠加上限约束（但仍会增加叠加计数，用于其他涡轮的判断）。</p>
     */
    protected boolean canBypassBoostCap() { return false; }

    /**
     * 蒸汽消耗的协同折扣系数（基于上一 tick 实际加速的目标数量）。
     * <p>默认返回 {@code 1.0}（无折扣）。子类可覆盖实现"机器越多越经济"的协同效率特性。</p>
     */
    protected double synergyMultiplier() { return 1.0; }

    /**
     * 本涡轮消耗的流体类型。默认为普通蒸汽；子类可覆盖以使用过热蒸汽或加压蒸汽。
     *
     * <p>注意：此方法会在父类构造器中被调用（用于初始化流体缓冲区），
     * 因此实现必须只返回静态常量，不得引用子类实例字段。</p>
     */
    @SuppressWarnings("OverridableMethodCallDuringObjectConstruction")
    protected @NotNull RebarFluid boosterFluid() {
        return SteamworkFluids.STEAM;
    }

    /** vanilla 熔炉识别 helper，让子类的 {@link #identifyTarget} 复用。 */
    protected final boolean isVanillaFurnace(@NotNull Block block) {
        return VANILLA_FURNACES.contains(block.getType());
    }

    // ===== Tick 主逻辑 =====

    @Override
    public void tick() {
        // 推进全局 tick 计数器，使本实例的 boost 记录与当前 tick 关联。
        globalTickCounter++;

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
                case REBAR_RECIPE_PROCESSOR:
                    RebarBlock rb2 = BlockStorage.get(block);
                    return rb2 instanceof RebarRecipeProcessor<?> rp && rp.isProcessingRecipe();
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
                case REBAR_RECIPE_PROCESSOR:
                    RebarBlock rb2 = BlockStorage.get(block);
                    if (rb2 instanceof RebarRecipeProcessor<?> rp) {
                        rp.progressRecipe(boostTicks);
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
        // 协同效率折扣基于上一 tick 的实际加速数，本 tick 启动时已确定，全程保持不变。
        double steamCost = effectiveSteamCost() * synergyMultiplier();
        int limit = maxTargets();
        boolean bypassCap = canBypassBoostCap();
        for (Map.Entry<Block, TargetType> entry : targets.entrySet()) {
            Block target = entry.getKey();
            TargetType type = entry.getValue();

            lastTargetsFound++;
            // 达到本涡轮的目标上限后仍统计 found，但不再加速
            if (limit > 0 && lastTargetsBoosted >= limit) continue;
            if (!canBoost(target, type) || fluidAmount(boosterFluid()) < steamCost) {
                continue;
            }
            // 检查全局叠加层数上限：同一 Block 本 tick 已被加速 MAX_BOOST_STACKS 次则跳过。
            // 若 canBypassBoostCap() == true，仍增加计数（供其他涡轮判断）但不跳过。
            int stackCount = getAndIncrementBoostStack(target);
            if (!bypassCap && stackCount >= MAX_BOOST_STACKS) continue;

            removeFluid(boosterFluid(), steamCost);
            boost(target, type);
            lastTargetsBoosted++;
            lastSteamUsed += steamCost;
        }
    }

    /**
     * 获取目标方块本 tick 已被加速的次数，并将计数加一。
     * 若记录属于上一 tick，则重置为 0 再加一。
     *
     * @return 加一之前的叠加次数（即"这是第几层加速"，从 0 开始）
     */
    private static int getAndIncrementBoostStack(@NotNull Block block) {
        long[] entry = boostStacksThisTick.get(block);
        if (entry == null || entry[0] != globalTickCounter) {
            // 新 tick 或首次记录：重置
            boostStacksThisTick.put(block, new long[]{globalTickCounter, 1});
            return 0;
        }
        int current = (int) entry[1];
        entry[1] = current + 1;
        return current;
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
        double amount = fluidAmount(boosterFluid());
        double capacity = fluidCapacity(boosterFluid());
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
            double steam = fluidAmount(boosterFluid());
            double cap = fluidCapacity(boosterFluid());
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
            int limit = maxTargets();
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    translationPrefix() + ".scan_radius",
                    RebarArgument.of("radius", UnitFormat.BLOCKS.format(effectiveRadius))
            )));
            if (limit > 0) {
                lore.add(noItalic(Component.translatable(
                        translationPrefix() + ".max_targets",
                        RebarArgument.of("max", limit)
                )));
            }
            lore.add(noItalic(Component.translatable(
                    translationPrefix() + ".targets_found",
                    RebarArgument.of("targets", lastTargetsFound)
            )));
            lore.add(noItalic(Component.translatable(
                    translationPrefix() + ".boost",
                    RebarArgument.of("ticks", effectiveTicks),
                    RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(effectiveSteam).decimalPlaces(0))
            )));
            return ItemStackBuilder.of(Material.SPYGLASS)
                    .name(noItalic(Component.translatable(translationPrefix() + ".scan")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }
}
