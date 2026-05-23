package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock,
        SteamBoostable {

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

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();
    private final RecipeLockItem recipeLockItem = new RecipeLockItem();

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
        currentRecipeKey = pdc.get(currentRecipePdcKey, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(ticksPdcKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        lockedRecipeKey = pdc.get(lockedRecipePdcKey, RebarSerializers.NAMESPACED_KEY);
    }

    // ===== 抽象接口 =====

    /** 此机器使用的配方类型。 */
    protected abstract @NotNull RecipeType<R> recipeType();

    /** PDC 键前缀，例如 "sterilizer"、"steeping"、"washing"。 */
    protected abstract @NotNull String pdcKeyPrefix();

    /** 翻译键前缀，例如 "steamwork.gui.steam_sterilizer"。 */
    protected abstract @NotNull String translationPrefix();

    // ===== 可覆盖 hook =====

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
    protected @NotNull List<Component> additionalProgressLore(@NotNull R recipe) {
        return List.of();
    }

    // ===== 通用生命周期 =====

    @Override
    public void postInitialise() {
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
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
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i i i i p o #",
                        "# # # # # # # # #",
                        "# # l # s # a # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('l', recipeLockItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable(translationPrefix() + ".title"));
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of(
                "input", inputInventory,
                "output", outputInventory
        );
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        RebarFluidBufferBlock.super.onBreak(drops, context);
    }

    @Override
    public void tick() {
        if (isProcessing()) {
            R recipe = getCurrentRecipe();
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

            double steamPerTick = recipe.steamCost() / recipe.timeTicks();
            int progressTicks = Math.min(tickInterval, (int) Math.floor(fluidAmount(steamFluid()) / steamPerTick));

            if (progressTicks <= 0) {
                // 蒸汽不足 —— 进入"中断"状态，超过宽限期后开始回退进度。
                currentReason = StopReason.NO_STEAM;
                handleInterruption(recipe);
                notifyGuiItems();
                return;
            }

            removeFluid(steamFluid(), steamPerTick * progressTicks);
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
    private void handleInterruption(@NotNull R recipe) {
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
            if (currentSteam < locked.steamCost()) return StopReason.NO_STEAM;
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
                if (currentSteam >= cached.steamCost()) {
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

            if (currentSteam < recipe.steamCost()) continue;
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
    private Map<Integer, Integer> findIngredients(@NotNull R recipe) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        RecipeInput.Item need = recipe.ingredient();
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

        return stillNeeded > 0 ? null : reserved;
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

    private boolean canStoreOutput(@NotNull R recipe) {
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

    private void startRecipe(@NotNull R recipe) {
        currentRecipeKey = recipe.getKey();
        recipeTicksRemaining = recipe.timeTicks();
        setActive(true);
    }

    private void progressRecipe(int ticks) {
        recipeTicksRemaining -= ticks;
        if (recipeTicksRemaining <= 0) {
            R recipe = getCurrentRecipe();
            if (recipe != null) {
                outputInventory.addItem(new MachineUpdateReason(), recipe.producedStack());
                tryProduceScrap();
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
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 1.0, 0.5), SteamworkItems.MACHINE_SCRAP.clone());
    }

    private void resetRecipe() {
        currentRecipeKey = null;
        recipeTicksRemaining = 0;
        setActive(false);
    }

    @Nullable
    private R getCurrentRecipe() {
        return currentRecipeKey == null ? null : recipeType().getRecipe(currentRecipeKey);
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
    }

    @Nullable
    private R findCachedRecipe() {
        List<R> recipes = getRecipeCache();
        for (int slot = 0; slot < inputInventory.getSize(); slot++) {
            ItemStack stack = inputInventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;

            for (R recipe : recipes) {
                if (fluidAmount(steamFluid()) < recipe.steamCost()) continue;
                if (recipe.ingredient().matchesIgnoringAmount(stack) && canStoreOutput(recipe)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    // ===== 视觉 / 状态 =====

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
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
                case NO_STEAM -> Material.RED_STAINED_GLASS_PANE;
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
            R recipe = getCurrentRecipe();
            boolean processing = recipe != null && recipeTicksRemaining > 0;

            if (!processing) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable(translationPrefix() + ".progress")))
                        .lore(List.of(noItalic(Component.translatable(SHARED_KEY + ".progress_idle"))));
            }

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
