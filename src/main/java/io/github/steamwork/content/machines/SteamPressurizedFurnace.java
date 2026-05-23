package io.github.steamwork.content.machines;

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
import io.github.pylonmc.rebar.entity.display.ItemDisplayBuilder;
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder;
import io.github.pylonmc.rebar.fluid.FluidPointType;
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
import io.github.steamwork.recipes.SteamPressurizingRecipe;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SteamPressurizedFurnace extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);

    // 配方缓存 - 缓存配方列表
    private static List<SteamPressurizingRecipe> recipeListCache = null;
    private static int recipeCacheVersion = 0;
    private int lastRecipeCacheVersion = -1;

    private static final NamespacedKey GUI_OUTPUT_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("pressurized_furnace_gui_output");
    private static final NamespacedKey CURRENT_RECIPE_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("pressurized_furnace_recipe");
    private static final NamespacedKey RECIPE_TICKS_REMAINING_KEY =
            io.github.steamwork.util.SteamworkUtils.steamworkKey("pressurized_furnace_ticks_remaining");
    private static final int PROGRESS_BAR_WIDTH = 20;
    private static final int PROGRESS_MAX_DAMAGE = 1000;
    private boolean lastActive = false;
    private boolean processingToGuiOutput = true;
    private @Nullable NamespacedKey currentRecipeKey = null;
    private int recipeTicksRemaining = 0;

    // TODO: Persist these inventories before the machine becomes survival-safe.
    private final VirtualInventory inputInventory = new VirtualInventory(5);
    private final VirtualInventory outputInventory = new VirtualInventory(1);

    // Dynamic GUI items.
    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressStatusItem progressItem = new ProgressStatusItem();

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        public Item(@NotNull ItemStack stack) { super(stack); }
        @Override public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)));
        }
    }

    @SuppressWarnings("unused")
    public SteamPressurizedFurnace(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
    }

    @SuppressWarnings("unused")
    public SteamPressurizedFurnace(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        processingToGuiOutput = pdc.getOrDefault(GUI_OUTPUT_KEY, RebarSerializers.BOOLEAN, true);
        currentRecipeKey = pdc.get(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(RECIPE_TICKS_REMAINING_KEY, PersistentDataType.INTEGER, 0);
        try { getFacing(); } catch (IllegalStateException e) { setFacing(org.bukkit.block.BlockFace.SOUTH); }
    }

    @Override
    public void postInitialise() {
        super.postInitialise();
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
        
        // 添加方块拼装外观 - 炉体（下界合金块）放在铜块上方
        addEntity("body", new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(Material.NETHERITE_BLOCK).build())
                .transformation(new TransformBuilder()
                        .translate(0, 0.3, 0)
                        .scale(0.6)
                )
                .build(getBlock().getLocation().toCenterLocation())
        );
        
        // 添加方块拼装外观 - 左侧压力表（时钟）平放
        addEntity("gauge_left", new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(Material.CLOCK).build())
                .transformation(new TransformBuilder()
                        .translate(0, -0.25, -0.5)
                        .rotate(0, 0, 0)  // Z轴旋转90度，使时钟平放
                        .scale(0.3)
                )
                .build(getBlock().getLocation().toCenterLocation())
        );
        
        // 添加方块拼装外观 - 右侧压力表（时钟）平放
        addEntity("gauge_right", new ItemDisplayBuilder()
                .itemStack(ItemStackBuilder.of(Material.CLOCK).build())
                .transformation(new TransformBuilder()
                        .translate(0, -0.25, 0.5)
                        .rotate(0, 0, 0)  // Z轴旋转90度，使时钟平放
                        .scale(0.3)
                )
                .build(getBlock().getLocation().toCenterLocation())
        );
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(GUI_OUTPUT_KEY, RebarSerializers.BOOLEAN, processingToGuiOutput);
        if (currentRecipeKey != null && recipeTicksRemaining > 0) {
            pdc.set(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY, currentRecipeKey);
            pdc.set(RECIPE_TICKS_REMAINING_KEY, PersistentDataType.INTEGER, recipeTicksRemaining);
        } else {
            pdc.remove(CURRENT_RECIPE_KEY);
            pdc.remove(RECIPE_TICKS_REMAINING_KEY);
        }
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i i i i p o #",
                        "# # # # # # # # #",
                        "# # # # s # a # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.pressurized_furnace.title"));
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
        boolean active = isProcessingRecipe();
        if (isProcessingRecipe()) {
            SteamPressurizingRecipe recipe = getCurrentRecipe();
            if (recipe == null) {
                currentRecipeKey = null;
                recipeTicksRemaining = 0;
                active = false;
                setActive(active);
                notifyGuiItems();
                return;
            }
            if (!canStoreFinishedRecipe(recipe)) {
                setActive(active);
                notifyGuiItems();
                return;
            }

            double steamPerTick = recipe.steamCost() / recipe.timeTicks();
            int progressTicks = Math.min(tickInterval, (int) Math.floor(fluidAmount(SteamworkFluids.STEAM) / steamPerTick));
            if (progressTicks <= 0) {
                setActive(active);
                notifyGuiItems();
                return;
            }

            removeFluid(SteamworkFluids.STEAM, steamPerTick * progressTicks);
            progressRecipe(progressTicks);
            spawnSteamParticles(6);
        } else if (tryStartFromGUI() || tryStartFromAbove()) {
            active = true;
        }
        setActive(active);
        notifyGuiItems();
    }

    private boolean tryStartFromGUI() {
        // 尝试使用缓存快速查找配方
        SteamPressurizingRecipe cachedRecipe = findCachedRecipe(inputInventory, 5);
        if (cachedRecipe != null && canInsertOutput(cachedRecipe.result())) {
            Map<Integer, Integer> toRemove = reserveIngredients(inputInventory, 5, cachedRecipe);
            if (toRemove != null) {
                removeReservedIngredients(inputInventory, toRemove);
                processingToGuiOutput = true;
                startPressurizing(cachedRecipe);
                spawnSteamParticles(10);
                return true;
            }
        }

        // 回退到完整遍历（处理复杂配方）
        for (SteamPressurizingRecipe recipe : SteamPressurizingRecipe.RECIPE_TYPE) {
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) continue;
            if (!canInsertOutput(recipe.result())) continue;

            Map<Integer, Integer> toRemove = reserveIngredients(inputInventory, 5, recipe);
            if (toRemove == null) continue;

            removeReservedIngredients(inputInventory, toRemove);
            processingToGuiOutput = true;
            startPressurizing(recipe);
            spawnSteamParticles(10);
            return true;
        }
        return false;
    }

    private boolean tryStartFromAbove() {
        Inventory source = getContainerAbove();
        if (source == null) return false;

        // 尝试使用缓存快速查找配方
        List<SteamPressurizingRecipe> recipes = getRecipeCache();
        for (int slot = 0; slot < source.getSize(); slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                for (SteamPressurizingRecipe recipe : recipes) {
                    if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) continue;
                    Map<Integer, Integer> toRemove = reserveIngredients(source, recipe);
                    if (toRemove != null) {
                        Inventory below = getContainerBelow();
                        ItemStack result = recipe.result().clone();
                        if (below == null || canFit(below, result)) {
                            removeReservedIngredients(source, toRemove);
                            processingToGuiOutput = false;
                            startPressurizing(recipe);
                            spawnSteamParticles(10);
                            return true;
                        }
                    }
                }
            }
        }

        // 回退到完整遍历
        for (SteamPressurizingRecipe recipe : SteamPressurizingRecipe.RECIPE_TYPE) {
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) continue;

            Map<Integer, Integer> toRemove = reserveIngredients(source, recipe);
            if (toRemove == null) continue;

            Inventory below = getContainerBelow();
            ItemStack result = recipe.result().clone();
            if (below != null && !canFit(below, result)) continue;

            removeReservedIngredients(source, toRemove);
            processingToGuiOutput = false;
            startPressurizing(recipe);
            spawnSteamParticles(10);
            return true;
        }
        return false;
    }

    private @Nullable Map<Integer, Integer> reserveIngredients(
            @NotNull VirtualInventory inventory,
            int size,
            @NotNull SteamPressurizingRecipe recipe
    ) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeInput.Item need : recipe.ingredients()) {
            int stillNeeded = need.getAmount();
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = inventory.getItem(slot);
                int alreadyReserved = reserved.getOrDefault(slot, 0);
                if (!canReserve(stack, alreadyReserved, need)) continue;
                int available = stack.getAmount() - alreadyReserved;
                int amountToReserve = Math.min(stillNeeded, available);
                reserved.merge(slot, amountToReserve, Integer::sum);
                stillNeeded -= amountToReserve;
                if (stillNeeded <= 0) break;
            }
            if (stillNeeded > 0) return null;
        }
        return reserved;
    }

    private @Nullable Map<Integer, Integer> reserveIngredients(
            @NotNull Inventory inventory,
            @NotNull SteamPressurizingRecipe recipe
    ) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeInput.Item need : recipe.ingredients()) {
            int stillNeeded = need.getAmount();
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                int alreadyReserved = reserved.getOrDefault(slot, 0);
                if (!canReserve(stack, alreadyReserved, need)) continue;
                int available = stack.getAmount() - alreadyReserved;
                int amountToReserve = Math.min(stillNeeded, available);
                reserved.merge(slot, amountToReserve, Integer::sum);
                stillNeeded -= amountToReserve;
                if (stillNeeded <= 0) break;
            }
            if (stillNeeded > 0) return null;
        }
        return reserved;
    }

    private boolean canReserve(@Nullable ItemStack stack, int alreadyReserved, @NotNull RecipeInput.Item need) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= alreadyReserved) return false;
        return need.matchesIgnoringAmount(stack);
    }

    private void removeReservedIngredients(
            @NotNull VirtualInventory inventory,
            @NotNull Map<Integer, Integer> reserved
    ) {
        for (Map.Entry<Integer, Integer> remove : reserved.entrySet()) {
            ItemStack stack = inventory.getItem(remove.getKey());
            if (stack == null) continue;
            inventory.setItem(new MachineUpdateReason(), remove.getKey(), stack.subtract(remove.getValue()));
        }
    }

    private void removeReservedIngredients(
            @NotNull Inventory inventory,
            @NotNull Map<Integer, Integer> reserved
    ) {
        for (Map.Entry<Integer, Integer> remove : reserved.entrySet()) {
            ItemStack stack = inventory.getItem(remove.getKey());
            if (stack == null) continue;
            stack.setAmount(stack.getAmount() - remove.getValue());
            inventory.setItem(remove.getKey(), stack.getAmount() > 0 ? stack : null);
        }
    }

    private boolean canInsertOutput(@NotNull ItemStack result) {
        return outputInventory.canHold(result);
    }

    private void insertOutput(@NotNull ItemStack result) {
        outputInventory.addItem(new MachineUpdateReason(), result);
    }

    private boolean canStoreFinishedRecipe(@NotNull SteamPressurizingRecipe recipe) {
        if (processingToGuiOutput) {
            return canInsertOutput(recipe.result());
        }
        Inventory below = getContainerBelow();
        return below == null || canFit(below, recipe.result());
    }

    private void onRecipeFinished(@NotNull SteamPressurizingRecipe recipe) {
        ItemStack result = recipe.result().clone();
        if (processingToGuiOutput) {
            insertOutput(result);
        } else {
            Inventory below = getContainerBelow();
            if (below != null && canFit(below, result)) {
                below.addItem(result);
            } else {
                dropResult(result);
            }
        }
        processingToGuiOutput = true;
        spawnSteamParticles(14);
    }

    private boolean isProcessingRecipe() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    private @Nullable SteamPressurizingRecipe getCurrentRecipe() {
        return currentRecipeKey == null ? null : SteamPressurizingRecipe.RECIPE_TYPE.getRecipe(currentRecipeKey);
    }

    private void startPressurizing(@NotNull SteamPressurizingRecipe recipe) {
        currentRecipeKey = recipe.key();
        recipeTicksRemaining = recipe.timeTicks();
    }

    private void progressRecipe(int ticks) {
        SteamPressurizingRecipe recipe = getCurrentRecipe();
        if (recipe == null) {
            currentRecipeKey = null;
            recipeTicksRemaining = 0;
            return;
        }

        recipeTicksRemaining -= ticks;
        if (recipeTicksRemaining <= 0) {
            currentRecipeKey = null;
            recipeTicksRemaining = 0;
            onRecipeFinished(recipe);
        }
    }

    private boolean canFit(@NotNull Inventory inventory, @NotNull ItemStack result) {
        int remaining = result.getAmount();
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.isEmpty()) {
                remaining -= result.getMaxStackSize();
            } else if (stack.isSimilar(result)) {
                remaining -= Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private Inventory getContainerAbove() {
        Block above = getBlock().getRelative(BlockFace.UP);
        if (above.getState() instanceof org.bukkit.block.Container container) {
            return container.getInventory();
        }
        return null;
    }

    private Inventory getContainerBelow() {
        Block below = getBlock().getRelative(BlockFace.DOWN);
        if (below.getState() instanceof org.bukkit.block.Container container) {
            return container.getInventory();
        }
        return null;
    }

    private void dropResult(@NotNull ItemStack result) {
        Location dropLoc = getBlock().getRelative(BlockFace.UP).getLocation().add(0.5, 0.5, 0.5);
        getBlock().getWorld().dropItem(dropLoc, result);
    }

    /**
     * 获取配方缓存列表
     */
    private List<SteamPressurizingRecipe> getRecipeCache() {
        if (recipeListCache == null || lastRecipeCacheVersion != recipeCacheVersion) {
            recipeListCache = new ArrayList<>();
            for (SteamPressurizingRecipe recipe : SteamPressurizingRecipe.RECIPE_TYPE) {
                recipeListCache.add(recipe);
            }
            lastRecipeCacheVersion = recipeCacheVersion;
        }
        return recipeListCache;
    }

    /**
     * 刷新配方缓存
     */
    public static void refreshRecipeCache() {
        recipeCacheVersion++;
        recipeListCache = null;
    }

    /**
     * 使用缓存快速查找配方
     */
    private SteamPressurizingRecipe findCachedRecipe(VirtualInventory inventory, int size) {
        List<SteamPressurizingRecipe> recipes = getRecipeCache();

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;

            for (SteamPressurizingRecipe recipe : recipes) {
                if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) continue;
                if (!canInsertOutput(recipe.result())) continue;

                Map<Integer, Integer> toRemove = reserveIngredients(inventory, size, recipe);
                if (toRemove != null) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /**
     * 验证完整配方是否可用
     */
    private boolean canStartRecipe(VirtualInventory inventory, int size, SteamPressurizingRecipe recipe) {
        Map<Integer, Integer> reserved = reserveIngredients(inventory, size, recipe);
        if (reserved == null) return false;

        // 清理预占位
        for (Map.Entry<Integer, Integer> entry : reserved.entrySet()) {
            // 不需要实际移除，只是验证
        }
        return true;
    }

    private void spawnSteamParticles(int count) {
        getBlock().getWorld().spawnParticle(
                org.bukkit.Particle.CLOUD, getBlock().getLocation().add(0.5, 0.8, 0.5),
                count, 0.3, 0.1, 0.3, 0.02);
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
    }

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

    private static @NotNull Component steamLine(double steam, double cap) {
        return noItalic(Component.translatable(
                "steamwork.gui.pressurized_furnace.steam",
                RebarArgument.of("steam", UnitFormat.MILLIBUCKETS.format(steam).decimalPlaces(0)),
                RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(cap).decimalPlaces(0))
        ));
    }

    private @NotNull Component inputSlotsLine() {
        int filledSlots = 0;
        for (int slot = 0; slot < inputInventory.getSize(); slot++) {
            ItemStack stack = inputInventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                filledSlots++;
            }
        }
        return noItalic(Component.translatable(
                "steamwork.gui.pressurized_furnace.input_slots",
                RebarArgument.of("filled", filledSlots),
                RebarArgument.of("total", inputInventory.getSize())
        ));
    }

    private @NotNull Component outputSlotLine() {
        ItemStack output = outputInventory.getItem(0);
        Component state;
        if (output == null || output.isEmpty()) {
            state = noItalic(Component.translatable("steamwork.gui.pressurized_furnace.output_state.empty"));
        } else {
            int amount = output.getAmount();
            int max = output.getMaxStackSize();
            state = noItalic(Component.translatable(
                    "steamwork.gui.pressurized_furnace.output_state." + (amount >= max ? "full" : "amount"),
                    RebarArgument.of("amount", amount),
                    RebarArgument.of("max", max)
            ));
        }

        return noItalic(Component.translatable(
                "steamwork.gui.pressurized_furnace.output_slot",
                RebarArgument.of("output", state)
        ));
    }

    private @NotNull Component outputDestinationLine() {
        Component destination = noItalic(Component.translatable(
                "steamwork.gui.pressurized_furnace.destination." + (processingToGuiOutput ? "gui" : "below")
        ));
        return noItalic(Component.translatable(
                "steamwork.gui.pressurized_furnace.output_destination",
                RebarArgument.of("destination", destination)
        ));
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean processing = isProcessingRecipe();
            Material mat = processing ? Material.GREEN_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            List<Component> lore = new ArrayList<>();
            lore.add(inputSlotsLine());
            lore.add(outputSlotLine());
            if (processing) {
                lore.add(outputDestinationLine());
            }

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.pressurized_furnace.status."
                            + (processing ? "active" : "idle"))))
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

            String bar = steamBarColors(pct);
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.pressurized_furnace.steam_gauge")))
                    .lore(List.of(
                            steamLine(steam, cap),
                            noItalic(Component.translatable(
                                    "steamwork.gui.pressurized_furnace.progress_bar",
                                    RebarArgument.of("bar", barComponent(pct, PROGRESS_BAR_WIDTH)),
                                    RebarArgument.of("percent", percentComponent(pct))
                            ))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }

        private String steamBarColors(int pct) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                if (i * 5 < pct) {
                    sb.append(i < 10 ? "<aqua>" : "<dark_aqua>").append("|");
                } else {
                    sb.append("<dark_gray>|");
                }
            }
            return sb.toString();
        }
    }

    private final class ProgressStatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            SteamPressurizingRecipe recipe = getCurrentRecipe();
            boolean processing = recipe != null && recipeTicksRemaining > 0;

            ItemStackBuilder builder = ItemStackBuilder.of(processing ? Material.CLOCK : Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(noItalic(Component.translatable("steamwork.gui.pressurized_furnace.progress")));

            if (!processing) {
                return builder.lore(noItalic(Component.translatable(
                        "steamwork.gui.pressurized_furnace.progress_idle"
                )));
            }

            int totalTicks = Math.max(1, recipe.timeTicks());
            int remainingTicks = Math.max(0, Math.min(recipeTicksRemaining, totalTicks));
            int pct = (int) Math.round(100.0 * (totalTicks - remainingTicks) / totalTicks);
            int damage = Math.max(1, PROGRESS_MAX_DAMAGE - (int) Math.round((PROGRESS_MAX_DAMAGE - 1) * pct / 100.0));
            Duration remaining = Duration.ofMillis(remainingTicks * 50L);

            return builder
                    .set(DataComponentTypes.MAX_DAMAGE, PROGRESS_MAX_DAMAGE)
                    .set(DataComponentTypes.DAMAGE, damage)
                    .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                    .set(
                            DataComponentTypes.TOOLTIP_DISPLAY,
                            TooltipDisplay.tooltipDisplay()
                                    .addHiddenComponents(DataComponentTypes.DAMAGE, DataComponentTypes.MAX_DAMAGE)
                    )
                    .lore(List.of(
                            noItalic(Component.translatable(
                                    "steamwork.gui.pressurized_furnace.progress_bar",
                                    RebarArgument.of("bar", barComponent(pct, PROGRESS_BAR_WIDTH)),
                                    RebarArgument.of("percent", percentComponent(pct))
                            )),
                            noItalic(Component.translatable(
                                    "steamwork.gui.pressurized_furnace.time_remaining",
                                    RebarArgument.of("time", UnitFormat.formatDuration(remaining, true, false))
                            ))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        Map<String, kotlin.Pair<String, Integer>> p = super.getBlockTextureProperties();
        p.put("active", new kotlin.Pair<>(Boolean.toString(isProcessingRecipe()), 2));
        return p;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("steam-bar", legacySteamBar()),
                RebarArgument.of("state", Component.translatable("steamwork.state." + (isProcessingRecipe() ? "active" : "idle")))));
    }

    private String legacySteamBar() {
        double a = fluidAmount(SteamworkFluids.STEAM), c = fluidCapacity(SteamworkFluids.STEAM);
        int f = (int) Math.round(16.0 * a / Math.max(1.0, c));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) b.append(i < f ? "|" : ".");
        return b.toString();
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            scheduleBlockTextureItemRefresh();
        }
    }
}
