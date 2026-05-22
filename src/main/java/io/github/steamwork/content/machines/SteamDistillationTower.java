package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonFluids;
import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
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
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.recipes.SteamDistillationRecipe;
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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽精馏塔（多方块）：
 * 控制器（底部）+ 1~4 节精馏段（向上叠加）+ 冷凝头（顶部，因瓦盘管）。
 * 段数决定能跑哪些配方（{@link SteamDistillationRecipe#requiredSections()}），
 * 段数越多解锁的配方越复杂。
 */
public class SteamDistillationTower extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    public static final int MAX_SECTIONS = 4;

    private static final NamespacedKey CURRENT_RECIPE_KEY = steamworkKey("steam_distillation_tower_recipe");
    private static final NamespacedKey TICKS_REMAINING_KEY = steamworkKey("steam_distillation_tower_ticks");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double inputFluidBuffer = getSettings().getOrThrow("input-fluid-buffer", ConfigAdapter.DOUBLE);
    private final double outputFluidBuffer = getSettings().getOrThrow("output-fluid-buffer", ConfigAdapter.DOUBLE);

    private final VirtualInventory inputInventory = new VirtualInventory(2);
    private final VirtualInventory outputInventory = new VirtualInventory(6);

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();
    private final StructureItem structureItem = new StructureItem();

    private @Nullable NamespacedKey currentRecipeKey = null;
    private int recipeTicksRemaining = 0;
    private int detectedSections = 0;
    private boolean lastActive = false;
    private StopReason currentReason = StopReason.READY;

    public enum StopReason {
        READY("ready"),
        STRUCTURE_MISSING("structure_missing"),
        NO_INGREDIENTS("no_ingredients"),
        NO_STEAM("no_steam"),
        OUTPUT_FULL("output_full"),
        PROCESSING("processing");

        private final String key;

        StopReason(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double inputFluidBuffer = getSettings().getOrThrow("input-fluid-buffer", ConfigAdapter.DOUBLE);
        private final double outputFluidBuffer = getSettings().getOrThrow("output-fluid-buffer", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("input-fluid-buffer", UnitFormat.MILLIBUCKETS.format(inputFluidBuffer)),
                    RebarArgument.of("output-fluid-buffer", UnitFormat.MILLIBUCKETS.format(outputFluidBuffer)),
                    RebarArgument.of("max-sections", MAX_SECTIONS)
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamDistillationTower(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidPoint(FluidPointType.INPUT, BlockFace.WEST, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.SOUTH, context, false);
        createFluidPoint(FluidPointType.OUTPUT, BlockFace.EAST, context, false);
        createFluidBuffer(SteamworkFluids.SUPERHEATED_STEAM, steamBuffer, true, false);
        // 通用入料缓冲（任意非过热蒸汽的输入流体）。本机器的 fluidBuffer 用于多种流体，
        // 但 RebarFluidBufferBlock 的 buffer 是按流体类型注册的，所以我们在这里
        // 为所有"可能成为输入"的流体都登记一份小容量。
        for (RebarFluid fluid : possibleInputFluids()) {
            createFluidBuffer(fluid, inputFluidBuffer, true, false);
        }
        for (RebarFluid fluid : possibleOutputFluids()) {
            createFluidBuffer(fluid, outputFluidBuffer, false, true);
        }
    }

    @SuppressWarnings("unused")
    public SteamDistillationTower(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        currentRecipeKey = pdc.get(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(TICKS_REMAINING_KEY, PersistentDataType.INTEGER, 0);
    }

    private static List<RebarFluid> possibleInputFluids() {
        return List.of(SteamworkFluids.DISTILLED_WATER, PylonFluids.PLANT_OIL);
    }

    private static List<RebarFluid> possibleOutputFluids() {
        return List.of(
                SteamworkFluids.DISTILLED_WATER,
                SteamworkFluids.MINERAL_LEACHATE,
                SteamworkFluids.WASTE_ACID,
                SteamworkFluids.LIGHT_FRACTION,
                SteamworkFluids.MEDIUM_FRACTION,
                SteamworkFluids.HEAVY_FRACTION
        );
    }

    @Override
    public void postInitialise() {
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (currentRecipeKey != null && recipeTicksRemaining > 0) {
            pdc.set(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY, currentRecipeKey);
            pdc.set(TICKS_REMAINING_KEY, PersistentDataType.INTEGER, recipeTicksRemaining);
        } else {
            pdc.remove(CURRENT_RECIPE_KEY);
            pdc.remove(TICKS_REMAINING_KEY);
        }
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("input", inputInventory, "output", outputInventory);
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        RebarFluidBufferBlock.super.onBreak(drops, context);
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i # # p # o o o",
                        "# i # # # # o o o",
                        "# # s a t # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('t', structureItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.title"));
    }

    @Override
    public void tick() {
        detectedSections = countSections();
        if (isProcessing()) {
            SteamDistillationRecipe recipe = getCurrentRecipe();
            if (recipe == null) {
                resetRecipe();
                currentReason = StopReason.READY;
                notifyGuiItems();
                return;
            }
            if (detectedSections < recipe.requiredSections()) {
                currentReason = StopReason.STRUCTURE_MISSING;
                notifyGuiItems();
                return;
            }
            if (!canHoldOutputs(recipe)) {
                currentReason = StopReason.OUTPUT_FULL;
                notifyGuiItems();
                return;
            }

            double steamPerTick = recipe.superheatedSteamCost() / recipe.timeTicks();
            int progressTicks = Math.min(tickInterval, (int) Math.floor(fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) / steamPerTick));
            if (progressTicks <= 0) {
                currentReason = StopReason.NO_STEAM;
                notifyGuiItems();
                return;
            }

            removeFluid(SteamworkFluids.SUPERHEATED_STEAM, steamPerTick * progressTicks);
            recipeTicksRemaining -= progressTicks;
            currentReason = StopReason.PROCESSING;
            setActive(true);
            spawnDistillFx(2);

            if (recipeTicksRemaining <= 0) {
                emitOutputs(recipe);
                resetRecipe();
                spawnDistillFx(10);
                getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.6f, 1.4f);
            }
        } else {
            currentReason = tryStartRecipe();
        }
        notifyGuiItems();
    }

    private @NotNull StopReason tryStartRecipe() {
        if (detectedSections <= 0) {
            setActive(false);
            return StopReason.STRUCTURE_MISSING;
        }
        StopReason fallback = StopReason.NO_INGREDIENTS;
        for (SteamDistillationRecipe recipe : SteamDistillationRecipe.RECIPE_TYPE) {
            if (recipe.requiredSections() > detectedSections) {
                continue;
            }
            Map<Integer, Integer> reserved = reserveIngredient(recipe.ingredient());
            if (reserved == null) {
                continue;
            }
            if (recipe.inputFluid() != null) {
                RebarFluid pickedFluid = pickAvailableFluid(recipe.inputFluid());
                if (pickedFluid == null) {
                    continue;
                }
            }
            if (fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) < recipe.superheatedSteamCost() / recipe.timeTicks()) {
                fallback = StopReason.NO_STEAM;
                continue;
            }
            if (!canHoldOutputs(recipe)) {
                fallback = StopReason.OUTPUT_FULL;
                continue;
            }
            consumeReserved(reserved);
            if (recipe.inputFluid() != null) {
                RebarFluid pickedFluid = pickAvailableFluid(recipe.inputFluid());
                if (pickedFluid != null) {
                    removeFluid(pickedFluid, recipe.inputFluid().amountMillibuckets());
                }
            }
            currentRecipeKey = recipe.key();
            recipeTicksRemaining = recipe.timeTicks();
            setActive(true);
            spawnDistillFx(6);
            return StopReason.PROCESSING;
        }
        setActive(false);
        return fallback;
    }

    private boolean canHoldOutputs(@NotNull SteamDistillationRecipe recipe) {
        for (ItemStack item : recipe.itemResults()) {
            if (!outputInventory.canHold(item)) {
                return false;
            }
        }
        for (SteamDistillationRecipe.FluidOutput fluid : recipe.fluidResults()) {
            if (fluidSpaceRemaining(fluid.fluid()) < fluid.amount()) {
                return false;
            }
        }
        return true;
    }

    private void emitOutputs(@NotNull SteamDistillationRecipe recipe) {
        for (ItemStack item : recipe.itemResults()) {
            outputInventory.addItem(new MachineUpdateReason(), item.clone());
        }
        for (SteamDistillationRecipe.FluidOutput fluid : recipe.fluidResults()) {
            addFluid(fluid.fluid(), fluid.amount());
        }
    }

    private @Nullable Map<Integer, Integer> reserveIngredient(@NotNull RecipeInput.Item need) {
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
        for (Map.Entry<Integer, Integer> entry : reserved.entrySet()) {
            ItemStack stack = inputInventory.getItem(entry.getKey());
            if (stack != null) {
                inputInventory.setItem(new MachineUpdateReason(), entry.getKey(), stack.subtract(entry.getValue()));
            }
        }
    }

    private @Nullable RebarFluid pickAvailableFluid(@NotNull RecipeInput.Fluid input) {
        for (RebarFluid fluid : input.fluids()) {
            if (fluidAmount(fluid) >= input.amountMillibuckets()) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * 自下而上扫描方块：1~MAX_SECTIONS 节因瓦塔节，最顶端必须是冷凝头。
     * 返回检测到的塔节数量；若结构不合规返回 0。
     */
    private int countSections() {
        Block above = getBlock().getRelative(BlockFace.UP);
        int sections = 0;
        for (int i = 0; i < MAX_SECTIONS; i++) {
            RebarBlock rb = BlockStorage.get(above);
            if (rb == null) break;
            if (!SteamworkKeys.DISTILLATION_TOWER_SECTION.equals(rb.getKey())) break;
            sections++;
            above = above.getRelative(BlockFace.UP);
        }
        if (sections == 0) return 0;
        RebarBlock cap = BlockStorage.get(above);
        if (cap == null || !SteamworkKeys.DISTILLATION_CONDENSER.equals(cap.getKey())) {
            return 0;
        }
        return sections;
    }

    private void resetRecipe() {
        currentRecipeKey = null;
        recipeTicksRemaining = 0;
        setActive(false);
    }

    private boolean isProcessing() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    private @Nullable SteamDistillationRecipe getCurrentRecipe() {
        return currentRecipeKey == null ? null : SteamDistillationRecipe.RECIPE_TYPE.getRecipe(currentRecipeKey);
    }

    private void spawnDistillFx(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.CLOUD,
                getBlock().getLocation().add(0.5, 1.2, 0.5),
                count, 0.2, 0.4, 0.2, 0.02);
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
        structureItem.notifyWindows();
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
                        fluidAmount(SteamworkFluids.SUPERHEATED_STEAM),
                        fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM),
                        16,
                        TextColor.fromHexString("#fff4d8")
                )),
                RebarArgument.of("sections", detectedSections),
                RebarArgument.of("max-sections", MAX_SECTIONS),
                RebarArgument.of("structure", Component.translatable("steamwork.structure."
                        + (detectedSections > 0 ? "formed" : "missing"))),
                RebarArgument.of("state", Component.translatable("steamwork.state." + currentReason.key()))
        ));
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = currentReason == StopReason.PROCESSING ? Material.GREEN_STAINED_GLASS_PANE
                    : currentReason == StopReason.NO_STEAM ? Material.RED_STAINED_GLASS_PANE
                    : currentReason == StopReason.OUTPUT_FULL ? Material.YELLOW_STAINED_GLASS_PANE
                    : currentReason == StopReason.STRUCTURE_MISSING ? Material.RED_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.reason."
                            + currentReason.key()))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.SUPERHEATED_STEAM);
            double cap = fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM);
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));
            Material mat = pct >= 75 ? Material.WHITE_STAINED_GLASS
                    : pct >= 40 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct > 0 ? Material.GRAY_STAINED_GLASS
                    : Material.BLACK_STAINED_GLASS;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_distillation_tower.steam",
                            RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steam).decimalPlaces(0)),
                            RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(cap).decimalPlaces(0))
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class ProgressItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            SteamDistillationRecipe recipe = getCurrentRecipe();
            if (recipe == null || recipeTicksRemaining <= 0) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.progress")))
                        .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.progress_idle"))));
            }
            int totalTicks = Math.max(1, recipe.timeTicks());
            int remaining = Math.max(0, recipeTicksRemaining);
            int pct = (int) Math.round(100.0 * (totalTicks - remaining) / totalTicks);
            Duration timeLeft = Duration.ofMillis(remaining * 50L);
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.progress")))
                    .lore(List.of(
                            noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.progress_percent",
                                    RebarArgument.of("percent", pct + "%"))),
                            noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.time_remaining",
                                    RebarArgument.of("time", UnitFormat.formatDuration(timeLeft, true, false))))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class StructureItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = detectedSections >= MAX_SECTIONS ? Material.LIME_STAINED_GLASS_PANE
                    : detectedSections > 0 ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_distillation_tower.structure")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_distillation_tower.sections",
                            RebarArgument.of("sections", detectedSections),
                            RebarArgument.of("max", MAX_SECTIONS)
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
