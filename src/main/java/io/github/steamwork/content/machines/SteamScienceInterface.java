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
import io.github.pylonmc.rebar.item.research.Research;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.recipes.SteamResearchRecipe;
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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public class SteamScienceInterface extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarGuiBlock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    private static final NamespacedKey CURRENT_RECIPE_KEY = steamworkKey("steam_science_interface_recipe");
    private static final NamespacedKey TICKS_REMAINING_KEY = steamworkKey("steam_science_interface_ticks");
    private static final NamespacedKey STORED_POINTS_KEY = steamworkKey("steam_science_interface_points");

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final int maxStoredPoints = getSettings().getOrThrow("max-stored-points", ConfigAdapter.INTEGER);

    private final VirtualInventory inputInventory = new VirtualInventory(4);
    private final VirtualInventory outputInventory = new VirtualInventory(4);

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();
    private final ClaimPointsItem claimPointsItem = new ClaimPointsItem();

    private @Nullable NamespacedKey currentRecipeKey = null;
    private int recipeTicksRemaining = 0;
    private int storedPoints = 0;
    private boolean lastActive = false;
    private StopReason currentReason = StopReason.READY;

    public enum StopReason {
        READY("ready"),
        NO_SAMPLE("no_sample"),
        NO_STEAM("no_steam"),
        OUTPUT_FULL("output_full"),
        POINT_STORAGE_FULL("point_storage_full"),
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
        private final int maxStoredPoints = getSettings().getOrThrow("max-stored-points", ConfigAdapter.INTEGER);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("max-points", UnitFormat.RESEARCH_POINTS.format(maxStoredPoints))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamScienceInterface(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
    }

    @SuppressWarnings("unused")
    public SteamScienceInterface(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        currentRecipeKey = pdc.get(CURRENT_RECIPE_KEY, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(TICKS_REMAINING_KEY, PersistentDataType.INTEGER, 0);
        storedPoints = pdc.getOrDefault(STORED_POINTS_KEY, PersistentDataType.INTEGER, 0);
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
        pdc.set(STORED_POINTS_KEY, PersistentDataType.INTEGER, storedPoints);
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i # p # o o #",
                        "# # # # # # # # #",
                        "# # s # a # c # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('p', progressItem)
                .addIngredient('s', steamGaugeItem)
                .addIngredient('a', statusItem)
                .addIngredient('c', claimPointsItem)
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
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
        RebarFluidBufferBlock.super.onBreak(drops, context);
    }

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
            int progressTicks = Math.min(tickInterval, (int) Math.floor(fluidAmount(SteamworkFluids.STEAM) / steamPerTick));
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
                outputInventory.addItem(new MachineUpdateReason(), recipe.residue().clone());
                storedPoints += recipe.researchPoints();
                resetRecipe();
                spawnAnalyzeFx(12);
                getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
            }
        } else {
            currentReason = tryStartRecipe();
        }
        notifyGuiItems();
    }

    private @NotNull StopReason tryStartRecipe() {
        if (storedPoints >= maxStoredPoints) {
            return StopReason.POINT_STORAGE_FULL;
        }
        for (SteamResearchRecipe recipe : SteamResearchRecipe.RECIPE_TYPE) {
            if (storedPoints + recipe.researchPoints() > maxStoredPoints) {
                continue;
            }
            Map<Integer, Integer> reserved = reserveSample(recipe.sample());
            if (reserved == null) {
                continue;
            }
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) {
                return StopReason.NO_STEAM;
            }
            if (!outputInventory.canHold(recipe.residue())) {
                return StopReason.OUTPUT_FULL;
            }
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

    private @Nullable Map<Integer, Integer> reserveSample(@NotNull RecipeInput.Item need) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        int stillNeeded = need.getAmount();
        for (int slot = 0; slot < inputInventory.getSize(); slot++) {
            ItemStack stack = inputInventory.getItem(slot);
            int alreadyReserved = reserved.getOrDefault(slot, 0);
            if (stack == null || stack.isEmpty() || stack.getAmount() <= alreadyReserved) {
                continue;
            }
            if (!need.matchesIgnoringAmount(stack)) {
                continue;
            }
            int take = Math.min(stillNeeded, stack.getAmount() - alreadyReserved);
            reserved.merge(slot, take, Integer::sum);
            stillNeeded -= take;
            if (stillNeeded <= 0) {
                return reserved;
            }
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

    private void resetRecipe() {
        currentRecipeKey = null;
        recipeTicksRemaining = 0;
        setActive(false);
    }

    private boolean isProcessing() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    private @Nullable SteamResearchRecipe getCurrentRecipe() {
        return currentRecipeKey == null ? null : SteamResearchRecipe.RECIPE_TYPE.getRecipe(currentRecipeKey);
    }

    private void spawnAnalyzeFx(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.ENCHANT,
                getBlock().getLocation().add(0.5, 0.9, 0.5),
                count, 0.25, 0.2, 0.25, 0.02);
    }

    private void claimPoints(@NotNull Player player) {
        if (storedPoints <= 0) {
            return;
        }
        long total = Research.getResearchPoints(player) + storedPoints;
        int claimed = storedPoints;
        Research.setResearchPoints(player, total);
        storedPoints = 0;
        player.sendMessage(Component.translatable(
                "steamwork.message.steam_science_interface.claimed",
                RebarArgument.of("points", claimed),
                RebarArgument.of("total", total)
        ));
        getBlock().getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        notifyGuiItems();
    }

    private void notifyGuiItems() {
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
        progressItem.notifyWindows();
        claimPointsItem.notifyWindows();
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
                        fluidAmount(SteamworkFluids.STEAM),
                        fluidCapacity(SteamworkFluids.STEAM),
                        16,
                        TextColor.fromHexString("#d8edf0")
                )),
                RebarArgument.of("points", storedPoints),
                RebarArgument.of("state", Component.translatable("steamwork.state." + currentReason.key()))
        ));
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = currentReason == StopReason.PROCESSING ? Material.GREEN_STAINED_GLASS_PANE
                    : currentReason == StopReason.NO_STEAM ? Material.RED_STAINED_GLASS_PANE
                    : currentReason == StopReason.OUTPUT_FULL || currentReason == StopReason.POINT_STORAGE_FULL
                    ? Material.YELLOW_STAINED_GLASS_PANE
                    : Material.GRAY_STAINED_GLASS_PANE;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_science_interface.reason." + currentReason.key()))));
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
            Material mat = pct >= 75 ? Material.WHITE_STAINED_GLASS
                    : pct >= 40 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct > 0 ? Material.GRAY_STAINED_GLASS
                    : Material.BLACK_STAINED_GLASS;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_science_interface.steam",
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
            SteamResearchRecipe recipe = getCurrentRecipe();
            if (recipe == null || recipeTicksRemaining <= 0) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress")))
                        .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress_idle"))));
            }
            int totalTicks = Math.max(1, recipe.timeTicks());
            int remaining = Math.max(0, recipeTicksRemaining);
            int pct = (int) Math.round(100.0 * (totalTicks - remaining) / totalTicks);
            Duration timeLeft = Duration.ofMillis(remaining * 50L);
            return ItemStackBuilder.of(Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress")))
                    .lore(List.of(
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.progress_percent",
                                    RebarArgument.of("bar", barComponent(pct, 20)),
                                    RebarArgument.of("percent", pct + "%"))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.time_remaining",
                                    RebarArgument.of("time", UnitFormat.formatDuration(timeLeft, true, false)))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.points_pending",
                                    RebarArgument.of("points", recipe.researchPoints())))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        }
    }

    private final class ClaimPointsItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = storedPoints > 0 ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_science_interface.claim")))
                    .lore(List.of(
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.stored_points",
                                    RebarArgument.of("points", storedPoints),
                                    RebarArgument.of("max", maxStoredPoints))),
                            noItalic(Component.translatable("steamwork.gui.steam_science_interface.claim_hint"))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (clickType.isLeftClick()) {
                claimPoints(player);
            }
        }
    }

    private static @NotNull Component barComponent(int pct, int width) {
        Component bar = Component.empty();
        int filled = (int) Math.round(width * pct / 100.0);
        for (int i = 0; i < width; i++) {
            TextColor color = i < filled
                    ? (pct >= 85 ? NamedTextColor.GREEN : pct >= 50 ? NamedTextColor.YELLOW : pct >= 20 ? NamedTextColor.GOLD : NamedTextColor.RED)
                    : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text("|", color));
        }
        return bar;
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
