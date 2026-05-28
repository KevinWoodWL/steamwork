package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.steamwork.content.line.ProductionLineMember;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
import io.github.pylonmc.rebar.block.base.RebarLogisticBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.logistics.LogisticGroupType;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamProcessRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.window.Window;
import io.github.steamwork.content.machines.upgrade.UpgradeModule;
import io.github.steamwork.content.machines.upgrade.UpgradeType;
import io.github.steamwork.content.machines.upgrade.UpgradeableMachine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽加工机器（灭菌箱 / 浸煮桶 / 洗选槽 等）的共享基类。
 * 抽出三台机器 70-80% 重复的：tick/配方查找/消费/持久化/GUI/WAILA。
 *
 * 子类只需提供：
 * - {@link #recipeType()} 配方类型
 * - {@link #pdcKeyPrefix()} 持久化键前缀（例如 "sterilizer"）
 * - {@link #translationPrefix()} 翻译键前缀（例如 "steamwork.gui.steam_sterilizer"）
 * - {@link #progressActionName()} 进度面板的中文动作名（例如 "灭菌"）
 *
 * 可覆盖的 hook：
 * - {@link #spawnProcessingParticles(int)} 自定义工作粒子
 * - {@link #additionalProgressLore(SteamProcessRecipe)} 在进度条 lore 末尾追加内容（例如洗选槽显示产出数量）
 */
public abstract class AbstractSteamProcessor<R extends SteamProcessRecipe> extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarInventoryBlock,
        RebarLogisticBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock,
        SteamBoostable,
        UpgradeableMachine,
        ProductionLineMember {

    protected final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    protected final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    /** 每完成一条配方产出"机器废屑"的概率（0~1）。yml 没写默认 10%。 */
    protected final double scrapChance = getSettings().get("scrap-chance", ConfigAdapter.DOUBLE, 0.1);
    /**
     * 加工过程中断汽的容忍 tick 数。在这个时间窗内供汽恢复，进度不损失；
     * 超过窗口后每 tick 反向回退进度，完全回退到 0 时配方损坏并产出 1 个机器废屑。
     * yml 没写默认 60 tick（3 秒）。设 0 关闭该机制。
     */
    protected final int interruptionGraceTicks = getSettings().get("interruption-grace-ticks", ConfigAdapter.INTEGER, 60);

    // 配方缓存（实例级，避免每 tick 都 iterate RecipeType）
    private List<R> recipeListCache = null;
    // Pylon 配方包装器缓存（仅在安装了 PYLON_COMPAT 模组时填充）
    private List<io.github.steamwork.recipes.SteamProcessRecipe> pylonRecipeCache = null;

    private final NamespacedKey currentRecipePdcKey;
    private final NamespacedKey ticksPdcKey;
    private final NamespacedKey lockedRecipePdcKey;

    protected boolean lastActive = false;
    /** 机器当前停摆原因。{@link StopReason#PROCESSING} 表示在加工，其它值都表示停摆且原因明确。 */
    @NotNull protected StopReason currentReason = StopReason.READY;
    @Nullable protected NamespacedKey currentRecipeKey = null;
    protected int recipeTicksRemaining = 0;
    /** 加工过程中累计的"断汽 tick 数"；超过 {@link #interruptionGraceTicks} 后开始回退进度。 */
    protected int interruptionTicks = 0;
    /** 玩家手动锁定的配方 key；非 null 时机器只执行该配方。 */
    @Nullable protected NamespacedKey lockedRecipeKey = null;

    /**
     * 机器停摆/工作的原因枚举。
     * <p>
     * 玩家在 GUI 状态栏和 WAILA 上能看到具体原因，避免"机器为什么不动"的猜谜。
     * 翻译键格式：{@code steamwork.gui.steam_processor.reason.<key>} 和
     * {@code steamwork.state.<key>}。
     */
    public enum StopReason {
        /** 多方块结构未完成，机器拒绝工作。 */
        NO_STRUCTURE("structure_missing"),
        /** 输入槽为空，等待玩家投料。 */
        READY("ready"),
        /** 输入槽有物品但都不匹配任何配方。 */
        NO_INGREDIENTS("no_ingredients"),
        /** 配方匹配上了但蒸汽不够推一 tick。 */
        NO_STEAM("no_steam"),
        /** 配方匹配上了但输出槽放不下产物。 */
        OUTPUT_FULL("output_full"),
        /** 输出槽中存在与当前配方产物不同类型的物品。 */
        MIXED_OUTPUT("mixed_output"),
        /** 正在加工中（非停摆状态）。 */
        PROCESSING("processing");

        private final String key;
        StopReason(String key) { this.key = key; }
        public @NotNull String key() { return key; }
    }

    protected final VirtualInventory inputInventory = new VirtualInventory(9);
    protected final VirtualInventory outputInventory = new VirtualInventory(9);
    @Nullable protected final VirtualInventory upgradeInventory;

    private static final org.bukkit.block.BlockFace[] CARDINAL_FACES = {
        org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN,
        org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
        org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST
    };

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();
    private final RecipeLockItem recipeLockItem = new RecipeLockItem();
    private final PylonCompatItem pylonCompatItem = new PylonCompatItem();

    // ===== 产线成员字段 =====
    @Nullable private UUID lineId = null;
    private int linePosition = 0;
    @NotNull private BlockFace lineDirection = BlockFace.SELF;
    @Nullable private String lineCreator = null;
    private int lineNumber = 0;
    private final LineStatusItem lineStatusItem = new LineStatusItem();

    /**
     * 本机当前配方已锁定的产出物品。在 {@link #startRecipe(SteamProcessRecipe)} 时
     * 于 {@code onRecipeStart()} 之后立即固定，确保离心机等随机产出配方在整个加工周期
     * 内（从 canStoreOutput 检查到实际放出）看到同一个物品。
     *
     * <p>存储在实例字段而非共享的配方对象上，支持多台机器同时执行同一配方互不干扰。</p>
     *
     * <p>TODO: 服务器重启时若配方正在进行中，此字段重置为 null，完成时会重新随机。
     * 属于极低频边缘情况，暂不做 PDC 持久化。</p>
     */
    @Nullable private ItemStack fixedRecipeOutput = null;

    /** 蒸汽机器物品基类，子类只需写一行构造即可。 */
    public static abstract class BaseItem extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);

        protected BaseItem(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)));
        }
    }

    protected AbstractSteamProcessor(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        this.currentRecipePdcKey = steamworkKey(pdcKeyPrefix() + "_recipe");
        this.ticksPdcKey = steamworkKey(pdcKeyPrefix() + "_ticks");
        this.lockedRecipePdcKey = steamworkKey(pdcKeyPrefix() + "_locked_recipe");
        this.upgradeInventory = upgradeSlotCount() > 0 ? new VirtualInventory(upgradeSlotCount()) : null;
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(steamFluid(), steamBuffer, true, false);
    }

    protected AbstractSteamProcessor(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        this.currentRecipePdcKey = steamworkKey(pdcKeyPrefix() + "_recipe");
        this.ticksPdcKey = steamworkKey(pdcKeyPrefix() + "_ticks");
        this.lockedRecipePdcKey = steamworkKey(pdcKeyPrefix() + "_locked_recipe");
        this.upgradeInventory = upgradeSlotCount() > 0 ? new VirtualInventory(upgradeSlotCount()) : null;
        currentRecipeKey = pdc.get(currentRecipePdcKey, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(ticksPdcKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(org.bukkit.block.BlockFace.SOUTH); }
        lockedRecipeKey = pdc.get(lockedRecipePdcKey, RebarSerializers.NAMESPACED_KEY);
        String lineIdStr = pdc.get(LINE_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        if (lineIdStr != null) {
            try {
                lineId = UUID.fromString(lineIdStr);
                linePosition = pdc.getOrDefault(LINE_POSITION_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                String lineDir = pdc.get(LINE_DIRECTION_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                try {
                    lineDirection = lineDir != null ? BlockFace.valueOf(lineDir) : BlockFace.SELF;
                } catch (IllegalArgumentException ignored) {
                    lineDirection = BlockFace.SELF;
                }
                lineCreator = pdc.get(LINE_CREATOR_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                lineNumber = pdc.getOrDefault(LINE_NUMBER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            } catch (IllegalArgumentException ignored) {
                // PDC 中的 UUID 字符串损坏，视为未加入产线，方块正常加载
                lineId = null;
            }
        }
    }

    // ===== 抽象接口 =====

    /** 此机器使用的配方类型。 */
    protected abstract @NotNull RecipeType<R> recipeType();

    /** PDC 键前缀，例如 "sterilizer"、"steeping"、"washing"。 */
    protected abstract @NotNull String pdcKeyPrefix();

    /** 翻译键前缀，例如 "steamwork.gui.steam_sterilizer"。 */
    protected abstract @NotNull String translationPrefix();

    /** 升级槽数量，0 表示不支持升级。子类覆盖此方法来开启升级功能。 */
    @Override
    public int upgradeSlotCount() { return 0; }

    // ===== 可覆盖 hook =====

    /**
     * 是否满足多方块结构要求。默认返回 true（单方块机器）；
     * 多方块子类覆盖此方法实现结构校验，并可在此更新 ghost 提示块。
     * 每 tick 在主逻辑之前调用，结构缺失时机器立即停摆。
     */
    protected boolean hasValidStructure() {
        return true;
    }

    /**
     * 本机器消耗的流体类型。默认为普通蒸汽；需要过热蒸汽的子类（如精密铣床）覆盖此方法。
     */
    protected @NotNull io.github.pylonmc.rebar.fluid.RebarFluid steamFluid() {
        return SteamworkFluids.STEAM;
    }

    /**
     * 蒸汽量仪的颜色主题。返回四个 Material，对应 [75%以上, 50%以上, 25%以上, 低于25%]。
     * 默认为蓝色系（普通蒸汽）；过热蒸汽机器覆盖为橙红色系。
     */
    protected @NotNull Material[] steamGaugeMaterials() {
        return new Material[]{
            Material.LIGHT_BLUE_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS,
            Material.GRAY_STAINED_GLASS
        };
    }

    /**
     * WAILA 蒸汽条的颜色（hex 字符串）。默认淡蓝色；过热蒸汽机器覆盖为橙色。
     */
    protected @NotNull String steamBarColor() {
        return "#d8edf0";
    }

    /** 默认仅喷云雾粒子；洗选类可加水泡粒子。 */
    protected void spawnProcessingParticles(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD,
                getBlock().getLocation().add(0.5, 0.8, 0.5),
                count, 0.2, 0.1, 0.2, 0.02);
    }

    /**
     * 子类工作音效的统一播放器：按 {@code chance} 概率触发，避免每 tick 都响导致吵闹。
     * 在默认 5 tick 间隔 + 15% 概率下，约每 1.7 秒一次音效。
     */
    protected void playProcessingSound(@NotNull Sound sound, float volume, float pitch, double chance) {
        if (Math.random() >= chance) return;
        getBlock().getWorld().playSound(getBlock().getLocation(), sound, volume, pitch);
    }

    /** 进度条 lore 末尾追加额外行（默认无）。 */
    protected @NotNull List<Component> additionalProgressLore(@NotNull SteamProcessRecipe recipe) {
        return List.of();
    }

    // ===== 通用生命周期 =====

    @Override
    public void postInitialise() {
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
        createLogisticGroup("input", LogisticGroupType.INPUT, inputInventory);
        createLogisticGroup("output", LogisticGroupType.OUTPUT, outputInventory);
        if (upgradeInventory != null) {
            upgradeInventory.addPreUpdateHandler(event -> {
                ItemStack newItem = event.getNewItem();
                if (newItem == null || newItem.isEmpty()) return;
                if (!(RebarItem.fromStack(newItem) instanceof UpgradeModule module)) {
                    event.setCancelled(true);
                    return;
                }
                // 涡轮专用模组（扫描半径、加速力度）和产线入口专用模组（自动生产）不能装入加工机
                UpgradeType type = module.getUpgradeType();
                if (type == UpgradeType.RANGE || type == UpgradeType.BOOST || type == UpgradeType.AUTO_PRODUCTION) {
                    event.setCancelled(true);
                }
            });
        }
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (currentRecipeKey != null && recipeTicksRemaining > 0) {
            pdc.set(currentRecipePdcKey, RebarSerializers.NAMESPACED_KEY, currentRecipeKey);
            pdc.set(ticksPdcKey, org.bukkit.persistence.PersistentDataType.INTEGER, recipeTicksRemaining);
        } else {
            pdc.remove(currentRecipePdcKey);
            pdc.remove(ticksPdcKey);
        }
        if (lockedRecipeKey != null) {
            pdc.set(lockedRecipePdcKey, RebarSerializers.NAMESPACED_KEY, lockedRecipeKey);
        } else {
            pdc.remove(lockedRecipePdcKey);
        }
        if (lineId != null) {
            pdc.set(LINE_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING, lineId.toString());
            pdc.set(LINE_POSITION_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, linePosition);
            pdc.set(LINE_DIRECTION_KEY, org.bukkit.persistence.PersistentDataType.STRING, lineDirection.name());
            pdc.set(LINE_NUMBER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER, lineNumber);
            if (lineCreator != null) {
                pdc.set(LINE_CREATOR_KEY, org.bukkit.persistence.PersistentDataType.STRING, lineCreator);
            } else {
                pdc.remove(LINE_CREATOR_KEY);
            }
        } else {
            pdc.remove(LINE_ID_KEY);
            pdc.remove(LINE_POSITION_KEY);
            pdc.remove(LINE_DIRECTION_KEY);
            pdc.remove(LINE_CREATOR_KEY);
            pdc.remove(LINE_NUMBER_KEY);
        }
    }

    @Override
    public @NotNull Gui createGui() {
        boolean hasPylon = !pylonCompatItem.getPreview().isEmpty();
        boolean inLine = isInLine();
        // 'L' 仅在产线模式下插入第 4 行末尾；'P' 仅在支持 Pylon 联动时出现。
        String row4 = inLine ? "# # l # s # a L #" : "# # l # s # a # #";
        String row5 = hasPylon ? "# # # # P # # # #" : "# # # # # # # # #";

        var builder = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i i i i p o #",
                        "# # # # # # # # #",
                        row4,
                        row5
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('l', recipeLockItem);

        if (hasPylon) builder.addIngredient('P', pylonCompatItem);
        if (inLine)   builder.addIngredient('L', lineStatusItem);

        return builder.build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable(translationPrefix() + ".title"));
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        if (upgradeInventory != null) {
            return Map.of("input", inputInventory, "output", outputInventory, "upgrades", upgradeInventory);
        }
        return Map.of("input", inputInventory, "output", outputInventory);
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        RebarFluidBufferBlock.super.onBreak(drops, context);
    }

    @Override
    public void tick() {
        // 多方块结构检查（单方块机器默认 true，直接跳过）。
        if (!hasValidStructure()) {
            resetRecipe();
            currentReason = StopReason.NO_STRUCTURE;
            if (isInLine()) {
                // 多方块结构损坏 → 自动解散整条产线，避免产线永久阻塞。
                disbandLine();
            }
            notifyGuiItems();
            return;
        }

        if (isInLine()) {
            // 产线模式：跳过漏斗/容器交互，由上游主动推送输入；主动推送输出给下游。
            pushToNextInLine();
        } else {
            if (hasAutoInput()) {
                pullFromAllAdjacent();
            } else {
                pullFromHopperAbove();
            }
            if (hasAutoOutput()) {
                pushToAllAdjacent();
            } else {
                pushToContainerBelow();
            }
        }

        if (isProcessing()) {
            SteamProcessRecipe recipe = getCurrentRecipe();
            if (recipe == null) {
                resetRecipe();
                currentReason = StopReason.READY;
                interruptionTicks = 0;
                notifyGuiItems();
                return;
            }

            if (!canStoreOutput(recipe)) {
                // 输出满不算"中断"（机器没在被剥夺资源），不累计 interruptionTicks。
                currentReason = StopReason.OUTPUT_FULL;
                notifyGuiItems();
                return;
            }

            double steamPerProgressTick = recipe.steamCost() / recipe.timeTicks() * getSteamMultiplier();
            int maxProgressBySpeed = tickInterval;
            int maxProgressBySteam = (int) Math.floor(fluidAmount(steamFluid()) / steamPerProgressTick);
            int progressTicks = Math.min(maxProgressBySpeed, maxProgressBySteam);

            if (progressTicks <= 0) {
                // 蒸汽不足 —— 进入"中断"状态，超过宽限期后开始回退进度。
                currentReason = StopReason.NO_STEAM;
                handleInterruption(recipe);
                notifyGuiItems();
                return;
            }

            removeFluid(steamFluid(), steamPerProgressTick * progressTicks);
            progressRecipe(progressTicks);
            currentReason = StopReason.PROCESSING;
            interruptionTicks = 0; // 有进度就清零中断计数
            spawnProcessingParticles(4);
        } else {
            currentReason = tryStartRecipe();
            interruptionTicks = 0;
        }
        notifyGuiItems();
    }

    /**
     * 处理加工中断（蒸汽不足）：
     * <ul>
     *   <li>累计 {@link #interruptionTicks}</li>
     *   <li>未超过 {@link #interruptionGraceTicks}：什么也不做，等供汽恢复</li>
     *   <li>超过宽限期：每 tick interval 反向回退 tickInterval 个 tick 的进度</li>
     *   <li>进度完全回退到 0：reset + 喷烟 + 产 1 个机器废屑（"配方崩溃"）</li>
     * </ul>
     */
    private void handleInterruption(@NotNull SteamProcessRecipe recipe) {
        if (interruptionGraceTicks <= 0) return; // yml 关闭该机制
        interruptionTicks += tickInterval;
        if (interruptionTicks <= interruptionGraceTicks) return;

        // 反向推进 = 增加 recipeTicksRemaining（因为 remaining 越大代表越靠前）。
        recipeTicksRemaining += tickInterval;
        if (recipeTicksRemaining >= recipe.timeTicks()) {
            // 完全回退：配方崩溃，损失原料，产出 1 个废屑作为"事故痕迹"。
            spawnCrashEffects();
            dropScrap();
            resetRecipe();
            currentReason = StopReason.READY;
            interruptionTicks = 0;
        }
    }

    /** 配方崩溃的视觉/音效：大烟 + 灭火嘶嘶声。 */
    private void spawnCrashEffects() {
        Block b = getBlock();
        b.getWorld().spawnParticle(
                Particle.SMOKE,
                b.getLocation().add(0.5, 1.0, 0.5),
                10, 0.3, 0.2, 0.3, 0.05);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.6f);
    }

    /**
     * 蒸汽涡轮加速回调（实现 {@link SteamBoostable}）。
     * 涡轮直接推进配方进度若干 tick，不消耗机器自己的蒸汽 buffer。
     */
    @Override
    public void boostProcess(int ticks) {
        if (isProcessing()) {
            progressRecipe(ticks);
        }
    }

    // ===== 配方核心 =====

    /**
     * 尝试启动一个配方。返回 {@link StopReason#PROCESSING} 表示已启动；
     * 其它值表示具体的停摆原因，会被 caller 写到 {@link #currentReason} 让玩家看到。
     */
    private @NotNull StopReason tryStartRecipe() {
        if (isInputEmpty()) return StopReason.READY;

        double currentSteam = fluidAmount(steamFluid());

        // 锁定模式：只尝试该配方，失败时返回具体原因。
        if (lockedRecipeKey != null) {
            R locked = recipeType().getRecipe(lockedRecipeKey);
            if (locked == null) return StopReason.NO_INGREDIENTS;
            Map<Integer, Integer> reserved = findIngredients(locked);
            if (reserved == null) return StopReason.NO_INGREDIENTS;
            if (currentSteam < locked.steamCost() * getSteamMultiplier()) return StopReason.NO_STEAM;
            if (hasMixedOutput(locked.producedStack())) return StopReason.MIXED_OUTPUT;
            if (!outputInventory.canHold(locked.producedStack())) return StopReason.OUTPUT_FULL;
            consumeReserved(reserved);
            startRecipe(locked);
            spawnProcessingParticles(8);
            return StopReason.PROCESSING;
        }

        boolean anyMatchedInputs = false;
        boolean anyHadEnoughSteam = false;

        // 先用缓存命中常见配方；如果原料/汽/输出有任一不满足就 fall through 到全表遍历，
        // 顺便把"有匹配过原料"这种诊断信号记下来给最终的失败判定用。
        R cached = findCachedRecipe();
        if (cached != null) {
            Map<Integer, Integer> reserved = findIngredients(cached);
            if (reserved != null) {
                anyMatchedInputs = true;
                if (currentSteam >= cached.steamCost() * getSteamMultiplier()) {
                    anyHadEnoughSteam = true;
                    if (canStoreOutput(cached)) {
                        consumeReserved(reserved);
                        startRecipe(cached);
                        spawnProcessingParticles(8);
                        return StopReason.PROCESSING;
                    }
                }
            }
        }

        boolean anyCanStore = false;
        boolean anyMixed = false;
        for (R recipe : recipeType()) {
            Map<Integer, Integer> reserved = findIngredients(recipe);
            if (reserved == null) continue;
            anyMatchedInputs = true;

            if (currentSteam < recipe.steamCost() * getSteamMultiplier()) continue;
            anyHadEnoughSteam = true;

            if (hasMixedOutput(recipe.producedStack())) { anyMixed = true; continue; }
            if (!outputInventory.canHold(recipe.producedStack())) continue;
            anyCanStore = true;

            consumeReserved(reserved);
            startRecipe(recipe);
            spawnProcessingParticles(8);
            return StopReason.PROCESSING;
        }

        // Pylon 联动配方（仅在安装了 PYLON_COMPAT 模组时扫描）
        for (SteamProcessRecipe recipe : getPylonRecipeCache()) {
            Map<Integer, Integer> reserved = findIngredients(recipe);
            if (reserved == null) continue;
            anyMatchedInputs = true;

            if (currentSteam < recipe.steamCost() * getSteamMultiplier()) continue;
            anyHadEnoughSteam = true;

            if (hasMixedOutput(recipe.producedStack())) { anyMixed = true; continue; }
            if (!outputInventory.canHold(recipe.producedStack())) continue;
            anyCanStore = true;

            consumeReserved(reserved);
            startRecipe(recipe);
            spawnProcessingParticles(8);
            return StopReason.PROCESSING;
        }

        // 按"最接近成功"的顺序输出最具体的失败原因。
        if (!anyMatchedInputs) return StopReason.NO_INGREDIENTS;
        if (!anyHadEnoughSteam) return StopReason.NO_STEAM;
        if (anyMixed && !anyCanStore) return StopReason.MIXED_OUTPUT;
        if (!anyCanStore) return StopReason.OUTPUT_FULL;
        return StopReason.READY;
    }

    private boolean isInputEmpty() {
        for (int i = 0; i < inputInventory.getSize(); i++) {
            ItemStack stack = inputInventory.getItem(i);
            if (stack != null && !stack.isEmpty()) return false;
        }
        return true;
    }

    /** 检查输入槽能否凑齐 {@code recipe} 的原料，凑得齐返回每槽的预约扣减表，凑不齐返回 null。本方法不修改库存。 */
    @Nullable
    private Map<Integer, Integer> findIngredients(@NotNull SteamProcessRecipe recipe) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeInput.Item need : recipe.ingredients()) {
            int stillNeeded = need.getAmount();

            for (int slot = 0; slot < inputInventory.getSize(); slot++) {
                ItemStack stack = inputInventory.getItem(slot);
                int alreadyReserved = reserved.getOrDefault(slot, 0);
                if (stack == null || stack.isEmpty() || stack.getAmount() <= alreadyReserved) continue;
                if (!need.matchesIgnoringAmount(stack)) continue;

                int available = stack.getAmount() - alreadyReserved;
                int amountToTake = Math.min(stillNeeded, available);
                reserved.merge(slot, amountToTake, Integer::sum);
                stillNeeded -= amountToTake;
                if (stillNeeded <= 0) break;
            }

            if (stillNeeded > 0) return null;
        }

        return reserved;
    }

    /** 按预约表实际扣减输入槽。前提：{@link #findIngredients(SteamProcessRecipe)} 返回了非 null。 */
    private void consumeReserved(@NotNull Map<Integer, Integer> reserved) {
        for (Map.Entry<Integer, Integer> entry : reserved.entrySet()) {
            ItemStack stack = inputInventory.getItem(entry.getKey());
            if (stack != null) {
                inputInventory.setItem(new MachineUpdateReason(), entry.getKey(), stack.subtract(entry.getValue()));
            }
        }
    }

    private boolean canStoreOutput(@NotNull SteamProcessRecipe recipe) {
        if (hasMixedOutput(recipe.producedStack())) return false;
        return outputInventory.canHold(recipe.producedStack());
    }

    /** 检查输出槽中是否存在与 {@code produced} 不同类型的物品。 */
    private boolean hasMixedOutput(@NotNull ItemStack produced) {
        for (int i = 0; i < outputInventory.getSize(); i++) {
            ItemStack existing = outputInventory.getItem(i);
            if (existing == null || existing.isEmpty()) continue;
            if (!existing.isSimilar(produced)) return true;
        }
        return false;
    }

    private void startRecipe(@NotNull SteamProcessRecipe recipe) {
        recipe.onRecipeStart(); // 让随机产出配方（如砂轮包装器）提前固定本轮结果
        fixedRecipeOutput = recipe.producedStack(); // 固定本机本轮产出，与后续 progressRecipe 保持一致
        currentRecipeKey = recipe.getKey();
        recipeTicksRemaining = recipe.timeTicks();
        setActive(true);
    }

    /**
     * 返回本轮已锁定的产出物品。
     * 正常流程下 {@link #fixedRecipeOutput} 由 {@link #startRecipe} 设置，此处仅作防御回退。
     */
    private @NotNull ItemStack getLockedOutput(@NotNull SteamProcessRecipe recipe) {
        return fixedRecipeOutput != null ? fixedRecipeOutput.clone() : recipe.producedStack();
    }

    private void progressRecipe(int ticks) {
        recipeTicksRemaining -= ticks;
        if (recipeTicksRemaining <= 0) {
            SteamProcessRecipe recipe = getCurrentRecipe();
            if (recipe != null) {
                ItemStack output = getLockedOutput(recipe);
                outputInventory.addItem(new MachineUpdateReason(), output);
                tryProduceScrap();
                int bulkBonus = countUpgrade(UpgradeType.BULK);
                for (int i = 0; i < bulkBonus; i++) {
                    Map<Integer, Integer> extra = findIngredients(recipe);
                    if (extra == null || !outputInventory.canHold(output)) break;
                    consumeReserved(extra);
                    outputInventory.addItem(new MachineUpdateReason(), output.clone());
                    tryProduceScrap();
                }
                spawnProcessingParticles(12);
            }
            resetRecipe();
        }
    }

    /**
     * 配方完成时按 {@link #scrapChance} 概率产出 1 个机器废屑。
     * 输出槽满则放弃（不影响主产物），让玩家偶尔捡到"工业副产物"的发现感。
     */
    private void tryProduceScrap() {
        if (scrapChance <= 0) return;
        if (Math.random() >= scrapChance) return;
        dropScrap();
    }

    private void dropScrap() {
        Block b = getBlock();
        ItemStack scrap = SteamworkItems.MACHINE_SCRAP.clone();
        if (isInLine()) {
            scrap = io.github.steamwork.content.line.ProductionLineMember.deliverToNextMember(b, this, scrap);
        }
        if (!scrap.isEmpty()) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 1.0, 0.5), scrap);
        }
    }

    protected void resetRecipe() {
        currentRecipeKey = null;
        recipeTicksRemaining = 0;
        fixedRecipeOutput = null;
        setActive(false);
    }

    @Nullable
    private SteamProcessRecipe getCurrentRecipe() {
        if (currentRecipeKey == null) return null;
        R native_ = recipeType().getRecipe(currentRecipeKey);
        if (native_ != null) return native_;
        // 当前配方是 Pylon 包装器
        List<SteamProcessRecipe> pylon = getPylonRecipeCache();
        for (SteamProcessRecipe p : pylon) {
            if (p.getKey().equals(currentRecipeKey)) return p;
        }
        return null;
    }

    private boolean isProcessing() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    // ===== 配方缓存 =====

    private List<R> getRecipeCache() {
        if (recipeListCache == null) {
            recipeListCache = new ArrayList<>();
            for (R recipe : recipeType()) {
                recipeListCache.add(recipe);
            }
        }
        return recipeListCache;
    }

    /** 全局刷新本机器实例的配方缓存（建议在每次注册新配方后由 SteamworkRecipes 调用对应子类的此方法）。 */
    public void clearRecipeCache() {
        recipeListCache = null;
        pylonRecipeCache = null;
    }

    /**
     * 子类返回该机器支持的 Pylon 配方包装器列表（默认空列表）。
     * 仅在 PYLON_COMPAT 升级模组已插入时会被调用。
     */
    protected @NotNull List<SteamProcessRecipe> buildPylonRecipes() {
        return List.of();
    }

    private @NotNull List<SteamProcessRecipe> getPylonRecipeCache() {
        if (countUpgrade(io.github.steamwork.content.machines.upgrade.UpgradeType.PYLON_COMPAT) == 0) {
            pylonRecipeCache = null;
            return List.of();
        }
        if (pylonRecipeCache == null) {
            pylonRecipeCache = buildPylonRecipes();
        }
        return pylonRecipeCache;
    }

    @Nullable
    private R findCachedRecipe() {
        List<R> recipes = getRecipeCache();
        for (R recipe : recipes) {
            if (fluidAmount(steamFluid()) < recipe.steamCost()) continue;
            if (findIngredients(recipe) != null && canStoreOutput(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    // ===== 原版漏斗 / 容器交互 =====

    /**
     * 从机器正上方的漏斗中拉取一个物品到输入槽。
     * 每次 tick 最多转移一个物品，模拟原版漏斗节奏。
     * Pylon CargoInserter 优先级更高，但二者并行工作互不干扰。
     */
    private void pullFromHopperAbove() {
        Block above = getBlock().getRelative(BlockFace.UP);
        if (!(above.getState() instanceof Hopper hopperState)) return;
        Inventory hopperInv = hopperState.getInventory();
        for (int i = 0; i < hopperInv.getSize(); i++) {
            ItemStack stack = hopperInv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack single = stack.clone();
            single.setAmount(1);
            if (!inputInventory.canHold(single)) continue;
            inputInventory.addItem(new MachineUpdateReason(), single);
            if (stack.getAmount() <= 1) {
                hopperInv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                hopperInv.setItem(i, stack);
            }
            return; // 每 tick 最多转移一个
        }
    }

    /**
     * 将输出槽中的一个物品推入机器正下方的容器（包括漏斗）。
     * 每次 tick 最多转移一个物品。
     */
    private void pushToContainerBelow() {
        Block below = getBlock().getRelative(BlockFace.DOWN);
        if (!(below.getState() instanceof Container containerState)) return;
        Inventory targetInv = containerState.getInventory();
        for (int i = 0; i < outputInventory.getSize(); i++) {
            ItemStack stack = outputInventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack single = stack.clone();
            single.setAmount(1);
            Map<Integer, ItemStack> leftover = targetInv.addItem(single);
            if (leftover.isEmpty()) {
                if (stack.getAmount() <= 1) {
                    outputInventory.setItem(new MachineUpdateReason(), i, null);
                } else {
                    ItemStack updated = stack.clone();
                    updated.setAmount(stack.getAmount() - 1);
                    outputInventory.setItem(new MachineUpdateReason(), i, updated);
                }
                return; // 每 tick 最多转移一个
            }
        }
    }

    /** 自动进料：从所有相邻容器拉取物品，每 tick 最多转移一个。 */
    private void pullFromAllAdjacent() {
        for (org.bukkit.block.BlockFace face : CARDINAL_FACES) {
            Block adjacent = getBlock().getRelative(face);
            if (!(adjacent.getState() instanceof Container containerState)) continue;
            Inventory adjInv = containerState.getInventory();
            for (int i = 0; i < adjInv.getSize(); i++) {
                ItemStack stack = adjInv.getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                ItemStack single = stack.clone();
                single.setAmount(1);
                if (!inputInventory.canHold(single)) continue;
                inputInventory.addItem(new MachineUpdateReason(), single);
                if (stack.getAmount() <= 1) {
                    adjInv.setItem(i, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                    adjInv.setItem(i, stack);
                }
                return;
            }
        }
    }

    /** 自动出料：向所有相邻容器推送物品，每 tick 最多转移一个。 */
    private void pushToAllAdjacent() {
        for (int i = 0; i < outputInventory.getSize(); i++) {
            ItemStack stack = outputInventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack single = stack.clone();
            single.setAmount(1);
            for (org.bukkit.block.BlockFace face : CARDINAL_FACES) {
                Block adjacent = getBlock().getRelative(face);
                if (!(adjacent.getState() instanceof Container containerState)) continue;
                Map<Integer, ItemStack> leftover = containerState.getInventory().addItem(single.clone());
                if (leftover.isEmpty()) {
                    if (stack.getAmount() <= 1) {
                        outputInventory.setItem(new MachineUpdateReason(), i, null);
                    } else {
                        ItemStack updated = stack.clone();
                        updated.setAmount(stack.getAmount() - 1);
                        outputInventory.setItem(new MachineUpdateReason(), i, updated);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 产线模式下：将输出槽中的一个物品推给产线下游成员。
     * 每 tick 最多转移一个物品（与入口节奏保持一致）。
     * 若下游无法接收（背压/阻塞），本帧跳过，让输出槽暂时堆积。
     */
    private void pushToNextInLine() {
        if (lineDirection == BlockFace.SELF) return;
        ProductionLineMember next = null;
        for (int i = 1; i <= ProductionLineMember.DISBAND_MAX_GAP + 1; i++) {
            ProductionLineMember candidate = ProductionLineMember.of(getBlock().getRelative(lineDirection, i));
            if (candidate != null) { next = candidate; break; }
        }
        if (next == null) return;

        for (int i = 0; i < outputInventory.getSize(); i++) {
            ItemStack stack = outputInventory.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            ItemStack single = stack.clone();
            single.setAmount(1);
            if (next.acceptFromLine(single)) {
                if (stack.getAmount() <= 1) {
                    outputInventory.setItem(new MachineUpdateReason(), i, null);
                } else {
                    ItemStack updated = stack.clone();
                    updated.setAmount(stack.getAmount() - 1);
                    outputInventory.setItem(new MachineUpdateReason(), i, updated);
                }
            }
            return; // 无论成否，每 tick 只尝试一次
        }
    }

    // ===== 升级辅助 =====

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

    private double getSteamMultiplier() {
        return Math.max(0.2, 1.0 - 0.2 * countUpgrade(UpgradeType.ENERGY_SAVE));
    }

    private boolean hasAutoInput() {
        return countUpgrade(UpgradeType.AUTO_INPUT) > 0;
    }

    private boolean hasAutoOutput() {
        return countUpgrade(UpgradeType.AUTO_OUTPUT) > 0;
    }

    /**
     * 解散整条产线（向上下游扫描所有同 lineId 成员并调用 leaveLine）。
     * 通常由多方块结构损坏触发，防止产线永久阻塞。
     */
    private void disbandLine() {
        if (lineId == null || lineDirection == BlockFace.SELF) {
            leaveLine();
            return;
        }
        UUID id = lineId;
        BlockFace dir = lineDirection;

        // 从 YAML 注册表中删除该产线（在 leaveLine 清空 lineId 之前执行）
        io.github.steamwork.content.line.ProductionLineRegistry registry =
                io.github.steamwork.content.line.ProductionLineRegistry.get();
        io.github.steamwork.content.line.ProductionLineRegistry.LineRecord record =
                registry != null ? registry.unregister(id) : null;
        if (record != null) {
            Player target = Bukkit.getPlayer(record.creatorUuid());
            if (target != null) {
                target.sendMessage(Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text("蒸汽工坊", NamedTextColor.GOLD))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.translatable(
                                "steamwork.line.blueprint.invalidated",
                                RebarArgument.of("number", Component.text(record.number()))
                        ).color(NamedTextColor.RED)));
            }
        }

        Block self = getBlock();
        BlockFace reverse = dir.getOppositeFace();
        // 向下游清除：使用 gap-aware 扫描，能跳过多方块附属方块间隙
        ProductionLineMember.disbandScan(self.getRelative(dir), dir, id, this);
        // 向上游清除：使用 gap-aware 扫描，能跳过多方块附属方块间隙
        ProductionLineMember.disbandScan(self.getRelative(reverse), reverse, id, this);
        // 清除自身
        leaveLine();
    }

    // ===== ProductionLineMember =====

    @Override public @Nullable UUID getLineId() { return lineId; }
    @Override public int getLinePosition() { return linePosition; }
    @Override public @NotNull BlockFace getLineDirection() { return lineDirection; }
    @Override public @Nullable String getLineCreator() { return lineCreator; }
    @Override public int getLineNumber() { return lineNumber; }

    @Override
    public void setLineCreator(@Nullable String creator) { this.lineCreator = creator; }

    @Override
    public void setLineNumber(int number) { this.lineNumber = number; }

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
    }

    /**
     * 接受来自产线上游的物品，放入输入槽。
     */
    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (!inputInventory.canHold(item)) return false;
        inputInventory.addItem(new MachineUpdateReason(), item);
        return true;
    }

    // ===== 升级 GUI =====

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

    // ===== 视觉 / 状态 =====

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
        pylonCompatItem.notifyWindows(); // 已安装/未安装状态会随模组变化更新
        lineStatusItem.notifyWindows();  // 产线运行/等待/阻塞状态
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
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
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(steamFluid()),
                        fluidCapacity(steamFluid()),
                        16,
                        TextColor.fromHexString(steamBarColor())
                )),
                // %state% 占位符现在显示具体停摆原因（5 种之一），不再只是 active/idle 二值。
                // 旧的 steamwork.state.active/idle 翻译键保留，给锅炉/涡轮等没有 StopReason 的方块继续用。
                RebarArgument.of("state", Component.translatable("steamwork.state." + currentReason.key()))
        ));
    }

    // ===== GUI 内部 Item =====

    /** 共享 i18n 前缀（所有蒸汽加工机器通用文案）。 */
    private static final String SHARED_KEY = "steamwork.gui.steam_processor";

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            int inputCount = countItems(inputInventory);
            int outputCount = countItems(outputInventory);

            // 玻璃板颜色按"严重程度"分级：红=蒸汽不足、黄=输出满、灰=待料、绿=运行。
            Material mat = switch (currentReason) {
                case PROCESSING -> Material.GREEN_STAINED_GLASS_PANE;
                case NO_STEAM, NO_STRUCTURE -> Material.RED_STAINED_GLASS_PANE;
                case OUTPUT_FULL, MIXED_OUTPUT -> Material.YELLOW_STAINED_GLASS_PANE;
                case NO_INGREDIENTS, READY -> Material.GRAY_STAINED_GLASS_PANE;
            };

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(translationPrefix() + ".status." + (lastActive ? "active" : "idle"))))
                    .lore(List.of(
                            noItalic(Component.translatable(SHARED_KEY + ".reason." + currentReason.key())),
                            noItalic(Component.translatable(SHARED_KEY + ".input_count",
                                    RebarArgument.of("count", Component.text(inputCount)))),
                            noItalic(Component.translatable(SHARED_KEY + ".output_count",
                                    RebarArgument.of("count", Component.text(outputCount))))
                    ));
        }

        private int countItems(VirtualInventory inv) {
            int count = 0;
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && !stack.isEmpty()) count++;
            }
            return count;
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(steamFluid());
            double cap = fluidCapacity(steamFluid());
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));

            Material[] mats = steamGaugeMaterials();
            Material mat = pct >= 75 ? mats[0]
                    : pct >= 50 ? mats[1]
                    : pct >= 25 ? mats[2]
                    : mats[3];

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(translationPrefix() + ".steam_gauge")))
                    .lore(List.of(
                            noItalic(Component.translatable(translationPrefix() + ".steam",
                                    RebarArgument.of("steam", Component.text(String.format("%.0f", steam))),
                                    RebarArgument.of("capacity", Component.text(String.format("%.0f", cap))))),
                            noItalic(Component.translatable(
                                    SHARED_KEY + (pct >= 25 ? ".steam_status_normal" : ".steam_status_low")))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class ProgressItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            SteamProcessRecipe recipe = getCurrentRecipe();
            boolean processing = recipe != null && recipeTicksRemaining > 0;

            if (!processing) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(translationPrefix() + ".progress")))
                        .lore(List.of(noItalic(Component.translatable(SHARED_KEY + ".progress_idle"))));
            }
            assert recipe != null;

            int totalTicks = Math.max(1, recipe.timeTicks());
            int remaining = Math.max(0, recipeTicksRemaining);
            int pct = (int) Math.round(100.0 * (totalTicks - remaining) / totalTicks);
            Duration timeLeft = Duration.ofMillis(remaining * 50L);
            // 当前配方的真实耗汽速率（mB/s）。tick → 秒换算：×20。
            double steamPerSecond = recipe.steamCost() / (double) totalTicks * 20.0;

            boolean inWarning = interruptionGraceTicks > 0 && interruptionTicks > interruptionGraceTicks;

            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(SHARED_KEY + ".progress_percent",
                    RebarArgument.of("percent", Component.text(pct + "%")))));
            lore.add(noItalic(Component.translatable(SHARED_KEY + ".time_remaining",
                    RebarArgument.of("time", UnitFormat.formatDuration(timeLeft, true, false)))));
            lore.add(noItalic(Component.translatable(SHARED_KEY + ".steam_rate",
                    RebarArgument.of("rate", Component.text(String.format("%.1f", steamPerSecond))))));
            if (inWarning) {
                lore.add(noItalic(Component.translatable(SHARED_KEY + ".interruption_warning")));
            }
            lore.addAll(additionalProgressLore(recipe));

            // 中断警告时把 CLOCK 切到红色玻璃板，玩家不打开 GUI 也能从 hotbar 颜色看出异常。
            Material mat = inWarning ? Material.RED_STAINED_GLASS_PANE : Material.CLOCK;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(translationPrefix() + ".progress")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private final class RecipeLockItem extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            if (lockedRecipeKey == null) {
                List<R> matchable = getMatchableRecipes();
                List<Component> lore = new ArrayList<>();
                lore.add(noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.unlocked")));
                if (!matchable.isEmpty()) {
                    lore.add(noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.available")));
                    for (R r : matchable) {
                        lore.add(noItalic(Component.text("  ").append(r.producedStack().effectiveName())));
                    }
                }
                lore.add(noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.hint_click")));
                return ItemStackBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.title")))
                        .lore(lore);
            }
            R locked = recipeType().getRecipe(lockedRecipeKey);
            if (locked == null) {
                lockedRecipeKey = null;
                return getItemProvider(viewer);
            }
            return ItemStackBuilder.of(locked.producedStack().getType())
                    .name(noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.title")))
                    .lore(List.of(
                            noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.locked",
                                    RebarArgument.of("recipe", locked.producedStack().effectiveName()))),
                            noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.hint_next")),
                            noItalic(Component.translatable(SHARED_KEY + ".recipe_lock.hint_unlock"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isRightClick()) {
                lockedRecipeKey = null;
                notifyWindows();
                return;
            }
            // 左键：在输入槽当前能匹配的配方里循环选择产物
            List<R> matchable = getMatchableRecipes();
            if (matchable.isEmpty()) return;
            int currentIdx = -1;
            if (lockedRecipeKey != null) {
                for (int i = 0; i < matchable.size(); i++) {
                    if (matchable.get(i).getKey().equals(lockedRecipeKey)) {
                        currentIdx = i;
                        break;
                    }
                }
            }
            lockedRecipeKey = matchable.get((currentIdx + 1) % matchable.size()).getKey();
            notifyWindows();
        }
    }

    /**
     * 右下角 Pylon 联动配方指示物品。
     * <ul>
     *   <li>仅对覆盖了 {@link #buildPylonRecipes()} 的机器出现（通过 getPreview() 检测）。</li>
     *   <li>未安装联动模组时显示可解锁配方列表（灰色提示）；已安装时高亮并显示已激活配方。</li>
     *   <li>previewCache 独立于 pylonRecipeCache，保证预览与实际执行互不干扰。</li>
     * </ul>
     */
    private final class PylonCompatItem extends AbstractItem {
        @Nullable private List<SteamProcessRecipe> previewCache = null;

        /** 懒加载 Pylon 配方预览列表（不受联动模组安装状态影响）。 */
        @NotNull List<SteamProcessRecipe> getPreview() {
            if (previewCache == null) {
                previewCache = buildPylonRecipes();
            }
            return previewCache;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<SteamProcessRecipe> recipes = getPreview();
            boolean installed = countUpgrade(UpgradeType.PYLON_COMPAT) > 0;

            List<Component> lore = new ArrayList<>();
            if (installed) {
                lore.add(noItalic(Component.translatable(SHARED_KEY + ".pylon_compat.status_active")));
            } else {
                lore.add(noItalic(Component.translatable(SHARED_KEY + ".pylon_compat.status_inactive")));
            }
            lore.add(Component.empty());
            lore.add(noItalic(Component.translatable(SHARED_KEY + ".pylon_compat.recipes_header",
                    RebarArgument.of("count", recipes.size()))));

            int max = Math.min(recipes.size(), 12);
            for (int i = 0; i < max; i++) {
                lore.add(noItalic(
                        Component.text("  ").append(recipes.get(i).producedStack().effectiveName())
                ));
            }
            if (recipes.size() > max) {
                lore.add(noItalic(Component.translatable(
                        SHARED_KEY + ".pylon_compat.more",
                        RebarArgument.of("count", recipes.size() - max)
                )));
            }

            Material mat = installed ? Material.NETHER_STAR : Material.ECHO_SHARD;
            String titleKey = SHARED_KEY + ".pylon_compat." + (installed ? "title_active" : "title_inactive");
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(titleKey)))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    /**
     * 产线状态指示物品（仅在机器已加入产线时显示）。
     * <ul>
     *   <li>青色 — 机器正在运行（有输入或正在加工）</li>
     *   <li>黄色 — 等待输入</li>
     *   <li>橙色 — 输出积压（下游阻塞）</li>
     * </ul>
     */
    private final class LineStatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            // 检查输出槽是否有积压（下游阻塞）
            boolean outputHasItems = false;
            for (int i = 0; i < outputInventory.getSize(); i++) {
                ItemStack stack = outputInventory.getItem(i);
                if (stack != null && !stack.isEmpty()) { outputHasItems = true; break; }
            }

            Material mat;
            String stateKey;
            if (outputHasItems) {
                mat = Material.ORANGE_STAINED_GLASS_PANE;
                stateKey = "jammed";
            } else if (isProcessing() || !isInputEmpty()) {
                mat = Material.CYAN_STAINED_GLASS_PANE;
                stateKey = "running";
            } else {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                stateKey = "waiting";
            }

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(SHARED_KEY + ".line_status.title")))
                    .lore(List.of(
                            noItalic(Component.translatable(SHARED_KEY + ".line_status." + stateKey)),
                            noItalic(Component.translatable(SHARED_KEY + ".line_status.position",
                                    RebarArgument.of("position", Component.text(linePosition))))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    /** 返回当前输入槽原料能匹配的所有配方（不重复）。 */
    private @NotNull List<R> getMatchableRecipes() {
        List<R> result = new ArrayList<>();
        for (R recipe : recipeType()) {
            if (findIngredients(recipe) != null) {
                result.add(recipe);
            }
        }
        return result;
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
