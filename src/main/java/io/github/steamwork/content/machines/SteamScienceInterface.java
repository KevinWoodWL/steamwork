package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.item.research.Research;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamResearchRecipe;
import io.github.steamwork.util.SteamworkDiscipline;
import io.github.steamwork.util.SteamworkDisciplineResearch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.window.Window;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽科研接口。
 *
 * <p>分析研究样本并将点数存入对应学科的待领取池；
 * 玩家左键「领取」按钮后，各学科待领取点数转入玩家 PDC 中的学科点数池。
 * 部分高阶研究（★ 标注）仅能通过本机 GUI 消耗学科点数解锁，
 * 无法在 Rebar 指南中用全局研究点购买。</p>
 */
public class SteamScienceInterface extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GuiRebarBlock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock {

    // ===== PDC 键 =====
    private static final NamespacedKey CURRENT_RECIPE_KEY  = steamworkKey("steam_science_interface_recipe");
    private static final NamespacedKey TICKS_REMAINING_KEY = steamworkKey("steam_science_interface_ticks");
    // 每个学科独立存储（旧版单字段 STORED_POINTS_KEY 已废弃）
    private static final NamespacedKey STORED_MATERIAL_KEY  = steamworkKey("sci_stored_material");
    private static final NamespacedKey STORED_BIOLOGY_KEY   = steamworkKey("sci_stored_biology");
    private static final NamespacedKey STORED_PRECISION_KEY = steamworkKey("sci_stored_precision");
    private static final NamespacedKey STORED_CHEMISTRY_KEY = steamworkKey("sci_stored_chemistry");
    /** 待领取的全局研究点奖励（每次样本分析完成时积累，领取学科点数时一并发放）。 */
    private static final NamespacedKey PENDING_RESEARCH_KEY = steamworkKey("sci_pending_research");

    // ===== 配置 =====
    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final int maxStoredPoints = getSettings().getOrThrow("max-stored-points", ConfigAdapter.INTEGER);

    // ===== 物品栏 =====
    private final VirtualInventory inputInventory  = new VirtualInventory(4);
    private final VirtualInventory outputInventory = new VirtualInventory(4);

    // ===== 状态 =====
    private @Nullable NamespacedKey currentRecipeKey = null;
    private int recipeTicksRemaining = 0;
    /** 各学科待领取点数（存储在机器 PDC，领取后转入玩家 PDC）。 */
    private final Map<SteamworkDiscipline, Integer> storedByDiscipline =
            new EnumMap<>(SteamworkDiscipline.class);
    /** 待领取的全局研究点奖励（每次样本分析完成时积累：researchPoints / 5，最少 1）。 */
    private int pendingResearchBonus = 0;
    private boolean lastActive = false;
    private StopReason currentReason = StopReason.READY;

    // ===== GUI 物品实例 =====
    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();
    private final ClaimPointsItem claimPointsItem = new ClaimPointsItem();
    private final DisciplineResearchButton disciplineResearchButton = new DisciplineResearchButton();

    // ===== 停摆原因 =====
    public enum StopReason {
        READY("ready"),
        NO_SAMPLE("no_sample"),
        NO_STEAM("no_steam"),
        OUTPUT_FULL("output_full"),
        POINT_STORAGE_FULL("point_storage_full"),
        PROCESSING("processing");

        private final String key;
        StopReason(String key) { this.key = key; }
        public String key() { return key; }
    }

    // ===== 物品（指南 lore 占位符）=====
    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final int maxStoredPoints = getSettings().getOrThrow("max-stored-points", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("max-points", UnitFormat.RESEARCH_POINTS.format(maxStoredPoints))
            );
        }
    }

    // ===== 构造 =====

    @SuppressWarnings("unused")
    public SteamScienceInterface(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
        for (SteamworkDiscipline d : SteamworkDiscipline.values()) {
            storedByDiscipline.put(d, 0);
        }
    }

    @SuppressWarnings("unused")
    public SteamScienceInterface(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        currentRecipeKey = pdc.get(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(TICKS_REMAINING_KEY, PersistentDataType.INTEGER, 0);
        storedByDiscipline.put(SteamworkDiscipline.MATERIAL,
                pdc.getOrDefault(STORED_MATERIAL_KEY,  PersistentDataType.INTEGER, 0));
        storedByDiscipline.put(SteamworkDiscipline.BIOLOGY,
                pdc.getOrDefault(STORED_BIOLOGY_KEY,   PersistentDataType.INTEGER, 0));
        storedByDiscipline.put(SteamworkDiscipline.PRECISION,
                pdc.getOrDefault(STORED_PRECISION_KEY, PersistentDataType.INTEGER, 0));
        storedByDiscipline.put(SteamworkDiscipline.CHEMISTRY,
                pdc.getOrDefault(STORED_CHEMISTRY_KEY, PersistentDataType.INTEGER, 0));
        pendingResearchBonus = pdc.getOrDefault(PENDING_RESEARCH_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void postInitialise() {
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (currentRecipeKey != null && recipeTicksRemaining > 0) {
            pdc.set(CURRENT_RECIPE_KEY,  RebarSerializers.NAMESPACED_KEY, currentRecipeKey);
            pdc.set(TICKS_REMAINING_KEY, PersistentDataType.INTEGER, recipeTicksRemaining);
        } else {
            pdc.remove(CURRENT_RECIPE_KEY);
            pdc.remove(TICKS_REMAINING_KEY);
        }
        pdc.set(STORED_MATERIAL_KEY,  PersistentDataType.INTEGER, stored(SteamworkDiscipline.MATERIAL));
        pdc.set(STORED_BIOLOGY_KEY,   PersistentDataType.INTEGER, stored(SteamworkDiscipline.BIOLOGY));
        pdc.set(STORED_PRECISION_KEY, PersistentDataType.INTEGER, stored(SteamworkDiscipline.PRECISION));
        pdc.set(STORED_CHEMISTRY_KEY, PersistentDataType.INTEGER, stored(SteamworkDiscipline.CHEMISTRY));
        pdc.set(PENDING_RESEARCH_KEY, PersistentDataType.INTEGER, pendingResearchBonus);
    }

    // ===== GUI =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i # p # o o #",
                        "# # # # # # # # #",
                        "# # s # a # c # #",
                        "# # # # r # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('c', claimPointsItem)
                .addIngredient('r', disciplineResearchButton)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_science_interface.title"));
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("input", inputInventory, "output", outputInventory);
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
    }

    // ===== Tick =====

    @Override
    public void tick() {
        if (isProcessing()) {
            SteamResearchRecipe recipe = getCurrentRecipe();
            if (recipe == null) {
                resetRecipe();
                currentReason = StopReason.READY;
                notifyGuiItems();
                return;
            }
            if (!outputInventory.canHold(recipe.residue())) {
                currentReason = StopReason.OUTPUT_FULL;
                notifyGuiItems();
                return;
            }

            double steamPerTick = recipe.steamCost() / recipe.timeTicks();
            int progressTicks = Math.min(tickInterval,
                    (int) Math.floor(fluidAmount(SteamworkFluids.STEAM) / steamPerTick));
            if (progressTicks <= 0) {
                currentReason = StopReason.NO_STEAM;
                notifyGuiItems();
                return;
            }

            removeFluid(SteamworkFluids.STEAM, steamPerTick * progressTicks);
            recipeTicksRemaining -= progressTicks;
            currentReason = StopReason.PROCESSING;
            setActive(true);
            spawnAnalyzeFx(4);

            if (recipeTicksRemaining <= 0) {
                // 配方完成：将点数存入对应学科的待领取池，同时积累全局研究点奖励
                SteamworkDiscipline disc = SteamworkDiscipline.fromKey(recipe.disciplineKey());
                if (disc != null) {
                    storedByDiscipline.merge(disc, recipe.researchPoints(), Integer::sum);
                }
                pendingResearchBonus += Math.max(2, recipe.researchPoints() / 4);
                outputInventory.addItem(new MachineUpdateReason(), recipe.residue().clone());
                resetRecipe();
                spawnAnalyzeFx(12);
                getBlock().getWorld().playSound(
                        getBlock().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
            }
        } else {
            currentReason = tryStartRecipe();
        }
        notifyGuiItems();
    }

    private @NotNull StopReason tryStartRecipe() {
        // 若所有学科都已满，停止分析
        boolean allFull = true;
        for (SteamworkDiscipline d : SteamworkDiscipline.values()) {
            if (stored(d) < maxStoredPoints) { allFull = false; break; }
        }
        if (allFull) return StopReason.POINT_STORAGE_FULL;

        for (SteamResearchRecipe recipe : SteamResearchRecipe.RECIPE_TYPE) {
            // 检查对应学科是否还有空间
            SteamworkDiscipline disc = SteamworkDiscipline.fromKey(recipe.disciplineKey());
            if (disc != null && stored(disc) + recipe.researchPoints() > maxStoredPoints) continue;

            Map<Integer, Integer> reserved = reserveSample(recipe.sample());
            if (reserved == null) continue;
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) return StopReason.NO_STEAM;
            if (!outputInventory.canHold(recipe.residue()))             return StopReason.OUTPUT_FULL;

            consumeReserved(reserved);
            currentRecipeKey = recipe.key();
            recipeTicksRemaining = recipe.timeTicks();
            setActive(true);
            spawnAnalyzeFx(8);
            return StopReason.PROCESSING;
        }
        setActive(false);
        return StopReason.NO_SAMPLE;
    }

    // ===== 内部工具 =====

    private int stored(@NotNull SteamworkDiscipline d) {
        return storedByDiscipline.getOrDefault(d, 0);
    }

    private int totalStored() {
        return storedByDiscipline.values().stream().mapToInt(Integer::intValue).sum();
    }

    private @Nullable Map<Integer, Integer> reserveSample(@NotNull RecipeInput.Item need) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        int stillNeeded = need.getAmount();
        for (int slot = 0; slot < inputInventory.getSize(); slot++) {
            ItemStack stack = inputInventory.getItem(slot);
            int already = reserved.getOrDefault(slot, 0);
            if (stack == null || stack.isEmpty() || stack.getAmount() <= already) continue;
            if (!need.matchesIgnoringAmount(stack)) continue;
            int take = Math.min(stillNeeded, stack.getAmount() - already);
            reserved.merge(slot, take, Integer::sum);
            stillNeeded -= take;
            if (stillNeeded <= 0) return reserved;
        }
        return null;
    }

    private void consumeReserved(@NotNull Map<Integer, Integer> reserved) {
        for (Map.Entry<Integer, Integer> e : reserved.entrySet()) {
            ItemStack stack = inputInventory.getItem(e.getKey());
            if (stack != null) {
                inputInventory.setItem(new MachineUpdateReason(), e.getKey(), stack.subtract(e.getValue()));
            }
        }
    }

    private void resetRecipe() {
        currentRecipeKey = null;
        recipeTicksRemaining = 0;
        setActive(false);
    }

    private boolean isProcessing() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    private @Nullable SteamResearchRecipe getCurrentRecipe() {
        return currentRecipeKey == null ? null
                : SteamResearchRecipe.RECIPE_TYPE.getRecipe(currentRecipeKey);
    }

    private void spawnAnalyzeFx(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.ENCHANT,
                getBlock().getLocation().add(0.5, 0.9, 0.5),
                count, 0.25, 0.2, 0.25, 0.02);
    }

    /**
     * 领取所有待领取学科点数，转入玩家对应学科的 PDC 点数池。
     */
    private void claimPoints(@NotNull Player player) {
        if (totalStored() <= 0) return;

        boolean any = false;
        for (SteamworkDiscipline d : SteamworkDiscipline.values()) {
            int pts = stored(d);
            if (pts <= 0) continue;
            d.addPoints(player, pts);
            storedByDiscipline.put(d, 0);
            // 每个学科单独发一条消息，学科名走翻译
            player.sendMessage(Component.translatable(
                    "steamwork.message.steam_science_interface.claimed_discipline",
                    RebarArgument.of("points", pts),
                    RebarArgument.of("discipline",
                            Component.translatable("steamwork.research_type." + d.key))
            ));
            any = true;
        }
        if (any) {
            // 一并发放积累的全局研究点奖励
            if (pendingResearchBonus > 0) {
                Research.setResearchPoints(player,
                        Research.getResearchPoints(player) + pendingResearchBonus);
                player.sendMessage(Component.translatable(
                        "steamwork.message.steam_science_interface.analysis_research_bonus",
                        RebarArgument.of("points", pendingResearchBonus)));
                pendingResearchBonus = 0;
            }
            getBlock().getWorld().playSound(
                    player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            notifyGuiItems();
        }
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
        claimPointsItem.notifyWindows();
        disciplineResearchButton.notifyWindows();
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
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.STEAM),
                        fluidCapacity(SteamworkFluids.STEAM),
                        16, TextColor.fromHexString("#d8edf0")
                )),
                RebarArgument.of("material-pts",  stored(SteamworkDiscipline.MATERIAL)),
                RebarArgument.of("biology-pts",   stored(SteamworkDiscipline.BIOLOGY)),
                RebarArgument.of("precision-pts", stored(SteamworkDiscipline.PRECISION)),
                RebarArgument.of("chemistry-pts", stored(SteamworkDiscipline.CHEMISTRY)),
                RebarArgument.of("state", Component.translatable("steamwork.state." + currentReason.key()))
        ));
    }

    // ===== GUI 内部类 =====

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = currentReason == StopReason.PROCESSING
                    ? Material.GREEN_STAINED_GLASS_PANE
                    : currentReason == StopReason.NO_STEAM
                    ? Material.RED_STAINED_GLASS_PANE
                    : (currentReason == StopReason.OUTPUT_FULL || currentReason == StopReason.POINT_STORAGE_FULL)
                    ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.status." + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.reason." + currentReason.key()))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.STEAM);
            double cap   = fluidCapacity(SteamworkFluids.STEAM);
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));
            Material mat = pct >= 75 ? Material.WHITE_STAINED_GLASS
                    : pct >= 40 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct > 0 ? Material.GRAY_STAINED_GLASS
                    : Material.BLACK_STAINED_GLASS;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.steam",
                            RebarArgument.of("steam",    UnitFormat.MILLIBUCKETS.format(steam).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(cap).decimalPlaces(0))
                    ))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private final class ProgressItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            SteamResearchRecipe recipe = getCurrentRecipe();
            if (recipe == null || recipeTicksRemaining <= 0) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress")))
                        .lore(List.of(noItalic(Component.translatable(
                                "steamwork.gui.steam_science_interface.progress_idle"))));
            }
            int totalTicks = Math.max(1, recipe.timeTicks());
            int remaining  = Math.max(0, recipeTicksRemaining);
            int pct = (int) Math.round(100.0 * (totalTicks - remaining) / totalTicks);
            Duration timeLeft = Duration.ofMillis(remaining * 50L);
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress")))
                    .lore(List.of(
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress_percent",
                                    RebarArgument.of("bar",     barComponent(pct, 20)),
                                    RebarArgument.of("percent", pct + "%"))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.time_remaining",
                                    RebarArgument.of("time", UnitFormat.formatDuration(timeLeft, true, false)))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.points_pending",
                                    RebarArgument.of("points", recipe.researchPoints()))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.discipline_tag",
                                    RebarArgument.of("discipline", Component.translatable(
                                            "steamwork.research_type." + recipe.disciplineKey()))))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private final class ClaimPointsItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            int total = totalStored();
            Material mat = total > 0 ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE;
            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_science_interface.stored_header")));
            // 每个学科一行
            for (SteamworkDiscipline d : SteamworkDiscipline.values()) {
                int pts = stored(d);
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.stored_discipline." + d.key,
                        RebarArgument.of("points", pts),
                        RebarArgument.of("max", maxStoredPoints)
                )));
            }
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_science_interface.claim_hint")));
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.claim")))
                    .lore(lore);
        }
        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                claimPoints(player);
            }
        }
    }

    /**
     * 学科研究解锁按钮。点击后打开第二个 GUI 窗口，
     * 列出所有学科门控研究及玩家当前的学科点数，
     * 点击对应研究项即可消耗点数解锁。
     */
    private final class DisciplineResearchButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BOOKSHELF)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.discipline_research")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.discipline_research_hint"))));
        }
        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                openDisciplineResearchWindow(player);
            }
        }
    }

    /**
     * 打开学科选择窗口（第一层）。
     *
     * <p>布局：4 个学科按钮，显示当前积累点数与研究进度概览；
     * 点击任意学科进入该学科的研究解锁子窗口。</p>
     */
    private void openDisciplineResearchWindow(@NotNull Player player) {
        Gui gui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# M # B # P # C #",
                        "# # # # X # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('M', new DisciplineButton(SteamworkDiscipline.MATERIAL))
                .addIngredient('B', new DisciplineButton(SteamworkDiscipline.BIOLOGY))
                .addIngredient('P', new DisciplineButton(SteamworkDiscipline.PRECISION))
                .addIngredient('C', new DisciplineButton(SteamworkDiscipline.CHEMISTRY))
                .addIngredient('X', new CloseItem())
                .build();

        Window.builder()
                .setUpperGui(gui)
                .setTitle(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.discipline_research_title")))
                .setViewer(player)
                .build()
                .open();
    }

    /**
     * 打开指定学科的研究解锁子窗口（第二层）。
     *
     * <p>列出该学科下所有学科门控研究，底部提供返回按钮。</p>
     */
    private void openDisciplineSubWindow(@NotNull Player player,
                                         @NotNull SteamworkDiscipline discipline) {
        List<Map.Entry<Research, SteamworkDisciplineResearch.Requirement>> entries =
                SteamworkDisciplineResearch.getRequirements().entrySet().stream()
                        .filter(e -> e.getValue().discipline() == discipline)
                        .collect(Collectors.toList());

        // 最多 5 个研究槽（A-E），超出部分填背景
        char[] rc = {'A', 'B', 'C', 'D', 'E'};
        xyz.xenondevs.invui.item.Item[] rItems = new xyz.xenondevs.invui.item.Item[rc.length];
        for (int i = 0; i < rc.length; i++) {
            rItems[i] = i < entries.size()
                    ? new ResearchUnlockItem(entries.get(i).getKey(), entries.get(i).getValue())
                    : GuiItems.background();
        }

        Gui gui = Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# A B C D E # # #",
                        "< # # # # # # # X"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('A', rItems[0])
                .addIngredient('B', rItems[1])
                .addIngredient('C', rItems[2])
                .addIngredient('D', rItems[3])
                .addIngredient('E', rItems[4])
                .addIngredient('<', new BackItem())
                .addIngredient('X', new CloseItem())
                .build();

        Window.builder()
                .setUpperGui(gui)
                .setTitle(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.discipline_sub_title",
                        RebarArgument.of("discipline",
                                Component.translatable("steamwork.research_type." + discipline.key)))))
                .setViewer(player)
                .build()
                .open();
    }

    // ── 学科选择按钮 ────────────────────────────────────────────────────────

    /** 学科选择器按钮：显示当前积累点数与研究进度概览，左键进入该学科子窗口。 */
    private final class DisciplineButton extends AbstractItem {
        private final SteamworkDiscipline disc;

        DisciplineButton(@NotNull SteamworkDiscipline disc) {
            this.disc = disc;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            long pts = disc.getPoints(viewer);
            Material mat = switch (disc) {
                case MATERIAL  -> Material.IRON_INGOT;
                case BIOLOGY   -> Material.WHEAT_SEEDS;
                case PRECISION -> Material.AMETHYST_SHARD;
                case CHEMISTRY -> Material.BLAZE_POWDER;
            };

            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_science_interface.discipline_pts",
                    RebarArgument.of("points", pts))));
            boolean hasAny = false;
            for (Map.Entry<Research, SteamworkDisciplineResearch.Requirement> e
                    : SteamworkDisciplineResearch.getRequirements().entrySet()) {
                if (e.getValue().discipline() != disc) continue;
                int cost = e.getValue().points();
                boolean unlocked = e.getKey().isResearchedBy(viewer);
                int pct = (int) Math.min(100, pts * 100L / Math.max(1, cost));
                lore.add(noItalic(Component.translatable(
                        unlocked
                                ? "steamwork.gui.steam_science_interface.disc_threshold_done"
                                : "steamwork.gui.steam_science_interface.disc_threshold",
                        RebarArgument.of("name",  e.getKey().getName()),
                        RebarArgument.of("bar",   barComponent(pct, 10)),
                        RebarArgument.of("pts",   Math.min(pts, cost)),
                        RebarArgument.of("cost",  cost))));
                hasAny = true;
            }
            if (!hasAny) {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.disc_no_research")));
            }
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_science_interface.discipline_click_hint")));

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.research_type." + disc.key)))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player player, @NotNull Click c) {
            if (t.isLeftClick()) {
                openDisciplineSubWindow(player, disc);
            }
        }
    }

    // ── 返回按钮 ──────────────────────────────────────────────────────────

    /** 子窗口返回按钮：左键返回学科选择器。 */
    private final class BackItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.ARROW)
                    .name(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.back")));
        }
        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player player, @NotNull Click c) {
            openDisciplineResearchWindow(player);
        }
    }

    // ── 研究解锁槽 ────────────────────────────────────────────────────────

    private final class ResearchUnlockItem extends AbstractItem {
        private final Research research;
        private final SteamworkDisciplineResearch.Requirement req;

        ResearchUnlockItem(@NotNull Research research,
                           @NotNull SteamworkDisciplineResearch.Requirement req) {
            this.research = research;
            this.req = req;
        }

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean unlocked  = research.isResearchedBy(viewer);
            boolean canUnlock = SteamworkDisciplineResearch.canUnlock(viewer, research);
            long playerPts    = req.discipline().getPoints(viewer);
            int  cost         = req.points();

            List<Component> lore = new ArrayList<>();

            // ── 学科标签 ────────────────────────────────────────────────
            lore.add(noItalic(Component.translatable(
                    "steamwork.gui.steam_science_interface.research_cost",
                    RebarArgument.of("discipline",
                            Component.translatable("steamwork.research_type." + req.discipline().key)),
                    RebarArgument.of("cost", cost))));

            // ── 进度条 ──────────────────────────────────────────────────
            if (!unlocked) {
                int pct = (int) Math.min(100, playerPts * 100L / Math.max(1, cost));
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.research_progress",
                        RebarArgument.of("bar",     barComponent(pct, 18)),
                        RebarArgument.of("current", playerPts),
                        RebarArgument.of("cost",    cost))));
            }

            // ── 状态行 ──────────────────────────────────────────────────
            if (unlocked) {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.research_already_unlocked")));
            } else if (canUnlock) {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.research_unlockable")));
                // 提前告知解锁后能获得的全局研究点奖励
                long bonus = Math.max(1L, cost / 3L);
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.research_bonus_hint",
                        RebarArgument.of("points", bonus))));
            } else {
                lore.add(noItalic(Component.translatable(
                        "steamwork.gui.steam_science_interface.research_insufficient")));
            }

            // ── 图标选择 ─────────────────────────────────────────────────
            // 已解锁用绿色光芒，可解锁用研究代表物品，不足用红色玻璃
            if (unlocked) {
                // 已解锁：用研究代表物品 + 附魔光芒效果
                try {
                    ItemStack researchItem = research.getItem();
                    if (researchItem != null && !researchItem.isEmpty()) {
                        return ItemStackBuilder.of(researchItem)
                                .name(noItalic(research.getName()))
                                .set(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
                                .lore(lore);
                    }
                } catch (Exception ignored) {}
                return ItemStackBuilder.of(Material.GREEN_STAINED_GLASS_PANE)
                        .name(noItalic(research.getName())).lore(lore);
            } else {
                // 未解锁：始终用研究代表物品（让玩家看到目标），仅状态栏颜色区分
                try {
                    ItemStack researchItem = research.getItem();
                    if (researchItem != null && !researchItem.isEmpty()) {
                        return ItemStackBuilder.of(researchItem)
                                .name(noItalic(research.getName()))
                                .lore(lore);
                    }
                } catch (Exception ignored) {}
                return ItemStackBuilder.of(canUnlock
                                ? Material.LIME_STAINED_GLASS_PANE
                                : Material.RED_STAINED_GLASS_PANE)
                        .name(noItalic(research.getName())).lore(lore);
            }
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (!clickType.isLeftClick()) return;
            boolean success = SteamworkDisciplineResearch.unlock(player, research);
            if (success) {
                // 额外奖励全局研究点（约 1/3 学科消耗，最少 1 点）
                long bonus = Math.max(1L, req.points() / 3L);
                Research.setResearchPoints(player, Research.getResearchPoints(player) + bonus);
                player.sendMessage(Component.translatable(
                        "steamwork.message.steam_science_interface.discipline_unlock_bonus",
                        RebarArgument.of("points", bonus)));
                notifyWindows();
                player.playSound(player.getLocation(),
                        Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            }
        }
    }

    // ── 关闭按钮 ──────────────────────────────────────────────────────────

    private final class CloseItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            return ItemStackBuilder.of(Material.BARRIER)
                    .name(noItalic(Component.translatable("steamwork.gui.upgrade.close")));
        }
        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player player, @NotNull Click c) {
            player.closeInventory();
        }
    }

    // ===== 共用渲染工具 =====

    private static @NotNull Component barComponent(int pct, int width) {
        Component bar = Component.empty();
        int filled = (int) Math.round(width * pct / 100.0);
        for (int i = 0; i < width; i++) {
            TextColor color = i < filled
                    ? (pct >= 85 ? NamedTextColor.GREEN
                    : pct >= 50 ? NamedTextColor.YELLOW
                    : pct >= 20 ? NamedTextColor.GOLD
                    : NamedTextColor.RED)
                    : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text("|", color));
        }
        return bar;
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
