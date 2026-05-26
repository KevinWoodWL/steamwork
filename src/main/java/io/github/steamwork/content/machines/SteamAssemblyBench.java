package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarFluidBufferBlock;
import io.github.pylonmc.rebar.block.base.RebarInventoryBlock;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.base.RebarTickingBlock;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatic steam crafting table. The template inventory defines a vanilla crafting recipe,
 * while the exposed input/output inventories let the pneumatic network feed and extract items.
 */
public class SteamAssemblyBench extends RebarBlock implements
        RebarDirectionalBlock,
        RebarFluidBufferBlock,
        RebarInventoryBlock,
        RebarSimpleMultiblock,
        RebarTickingBlock,
        RebarVirtualInventoryBlock {

    private static final int TEMPLATE_SIZE = 9;
    private static final int STORAGE_SIZE = 9;

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerCraft = getSettings().getOrThrow("steam-per-craft", ConfigAdapter.DOUBLE);

    private final VirtualInventory templateInventory = new VirtualInventory(TEMPLATE_SIZE);
    private final VirtualInventory inputInventory = new VirtualInventory(STORAGE_SIZE);
    private final VirtualInventory outputInventory = new VirtualInventory(1);

    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();

    private boolean lastActive = false;
    private StopReason currentReason = StopReason.READY;

    public enum StopReason {
        READY("ready"),
        STRUCTURE_MISSING("structure_missing"),
        NO_TEMPLATE("no_template"),
        NO_RECIPE("no_recipe"),
        NO_INGREDIENTS("no_ingredients"),
        NO_STEAM("no_steam"),
        OUTPUT_FULL("output_full"),
        PROCESSING("processing");

        private final String key;

        StopReason(String key) {
            this.key = key;
        }

        public @NotNull String key() {
            return key;
        }
    }

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
        private final double steamPerCraft = getSettings().getOrThrow("steam-per-craft", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) {
            super(stack);
        }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(
                    RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)),
                    RebarArgument.of("steam-per-craft", UnitFormat.MILLIBUCKETS.format(steamPerCraft))
            );
        }
    }

    @SuppressWarnings("unused")
    public SteamAssemblyBench(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setMultiblockDirection(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.SUPERHEATED_STEAM, steamBuffer, true, false);
    }

    @SuppressWarnings("unused")
    public SteamAssemblyBench(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        try {
            getFacing();
        } catch (IllegalStateException e) {
            setFacing(BlockFace.SOUTH);
        }
        setTickInterval(tickInterval);
    }

    @Override
    public void postInitialise() {
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
    }

    @Override
    public @NotNull Map<@NotNull Vector3i, @NotNull MultiblockComponent> getComponents() {
        MultiblockComponent corner = MultiblockComponent.of(PylonKeys.BRONZE_BLOCK);
        return Map.of(
                new Vector3i(-1, 0, -1), corner,
                new Vector3i(-1, 0, 1), corner,
                new Vector3i(1, 0, -1), corner,
                new Vector3i(1, 0, 1), corner
        );
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of(
                "template", templateInventory,
                "input", inputInventory,
                "output", outputInventory
        );
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarFluidBufferBlock.super.onBreak(drops, context);
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    @Override
    public void tick() {
        StopReason nextReason = tryCraftOneCycle();
        setActive(nextReason == StopReason.PROCESSING);
        currentReason = nextReason == StopReason.PROCESSING ? StopReason.READY : nextReason;
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
    }

    private @NotNull StopReason tryCraftOneCycle() {
        if (!isFormedAndFullyLoaded()) return StopReason.STRUCTURE_MISSING;
        if (isTemplateEmpty()) return StopReason.NO_TEMPLATE;
        if (fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) < steamPerCraft) return StopReason.NO_STEAM;

        RecipeMatch match = findMatchingRecipe();
        if (match == null) return StopReason.NO_RECIPE;
        if (!outputInventory.canHold(match.result())) return StopReason.OUTPUT_FULL;

        Map<Integer, Integer> plan = planIngredientConsumption(match.choices());
        if (plan == null) return StopReason.NO_INGREDIENTS;

        consumePlannedIngredients(plan);
        outputInventory.addItem(new MachineUpdateReason(), match.result().clone());
        removeFluid(SteamworkFluids.SUPERHEATED_STEAM, steamPerCraft);
        spawnAssembleFx();
        return StopReason.PROCESSING;
    }

    private boolean isTemplateEmpty() {
        for (ItemStack stack : templateInventory.getItems()) {
            if (stack != null && !stack.getType().isAir()) return false;
        }
        return true;
    }

    private @Nullable RecipeMatch findMatchingRecipe() {
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            RecipeMatch match = matchRecipe(recipe);
            if (match != null) return match;
        }
        return null;
    }

    private @Nullable RecipeMatch matchRecipe(@NotNull Recipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return matchShapedRecipe(shaped);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return matchShapelessRecipe(shapeless);
        }
        return null;
    }

    private @Nullable RecipeMatch matchShapedRecipe(@NotNull ShapedRecipe recipe) {
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();

        for (int yOffset = 0; yOffset <= 3 - shape.length; yOffset++) {
            int width = shapeWidth(shape);
            for (int xOffset = 0; xOffset <= 3 - width; xOffset++) {
                List<RecipeChoice> choices = new ArrayList<>();
                boolean matches = true;
                for (int y = 0; y < 3 && matches; y++) {
                    for (int x = 0; x < 3; x++) {
                        RecipeChoice choice = choiceAt(shape, choiceMap, x - xOffset, y - yOffset);
                        ItemStack template = templateInventory.getItem(y * 3 + x);
                        if (!templateMatchesChoice(template, choice)) {
                            matches = false;
                            break;
                        }
                        if (choice != null) choices.add(choice.clone());
                    }
                }
                if (matches) return new RecipeMatch(recipe.getResult(), choices);
            }
        }
        return null;
    }

    private int shapeWidth(@NotNull String[] shape) {
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row.length());
        }
        return width;
    }

    private @Nullable RecipeChoice choiceAt(
            @NotNull String[] shape,
            @NotNull Map<Character, RecipeChoice> choiceMap,
            int x,
            int y) {
        if (y < 0 || y >= shape.length) return null;
        String row = shape[y];
        if (x < 0 || x >= row.length()) return null;
        char symbol = row.charAt(x);
        if (symbol == ' ') return null;
        return choiceMap.get(symbol);
    }

    private @Nullable RecipeMatch matchShapelessRecipe(@NotNull ShapelessRecipe recipe) {
        List<RecipeChoice> remaining = new ArrayList<>();
        for (RecipeChoice choice : recipe.getChoiceList()) {
            remaining.add(choice.clone());
        }

        for (ItemStack template : templateInventory.getItems()) {
            if (template == null || template.getType().isAir()) continue;
            boolean matched = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).test(template)) {
                    remaining.remove(i);
                    matched = true;
                    break;
                }
            }
            if (!matched) return null;
        }

        if (!remaining.isEmpty()) return null;
        List<RecipeChoice> choices = new ArrayList<>();
        for (RecipeChoice choice : recipe.getChoiceList()) {
            choices.add(choice.clone());
        }
        return new RecipeMatch(recipe.getResult(), choices);
    }

    private boolean templateMatchesChoice(@Nullable ItemStack template, @Nullable RecipeChoice choice) {
        boolean empty = template == null || template.getType().isAir();
        if (choice == null) return empty;
        return !empty && choice.test(template);
    }

    private @Nullable Map<Integer, Integer> planIngredientConsumption(@NotNull List<RecipeChoice> choices) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeChoice choice : choices) {
            boolean found = false;
            for (int slot = 0; slot < inputInventory.getSize(); slot++) {
                ItemStack stack = inputInventory.getItem(slot);
                int alreadyReserved = reserved.getOrDefault(slot, 0);
                if (stack == null || stack.getType().isAir() || stack.getAmount() <= alreadyReserved) continue;
                if (!choice.test(stack)) continue;
                reserved.merge(slot, 1, Integer::sum);
                found = true;
                break;
            }
            if (!found) return null;
        }
        return reserved;
    }

    private void consumePlannedIngredients(@NotNull Map<Integer, Integer> plan) {
        MachineUpdateReason reason = new MachineUpdateReason();
        for (Map.Entry<Integer, Integer> entry : plan.entrySet()) {
            ItemStack stack = inputInventory.getItem(entry.getKey());
            if (stack == null || stack.getType().isAir()) continue;
            inputInventory.setItem(reason, entry.getKey(), stack.subtract(entry.getValue()));
        }
    }

    private void spawnAssembleFx() {
        Block block = getBlock();
        block.getWorld().spawnParticle(
                Particle.CRIT,
                block.getLocation().add(0.5, 1.1, 0.5),
                12, 0.3, 0.2, 0.3, 0.05);
        if (Math.random() < 0.35) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_ANVIL_USE, 0.45f, 1.6f);
        }
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
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# t t t # g # o #",
                        "# t t t # s # o #",
                        "# t t t # # # # #",
                        "# i i i i i i i #",
                        "# # i i i i # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('t', templateInventory)
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('g', steamGaugeItem)
                .addIngredient('s', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.title"));
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return new WailaDisplay(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("structure", Component.translatable("steamwork.structure."
                        + (isFormedAndFullyLoaded() ? "formed" : "missing"))),
                RebarArgument.of("steam-bar", PylonUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.SUPERHEATED_STEAM),
                        fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM),
                        12,
                        TextColor.fromHexString("#ff8c00")
                )),
                RebarArgument.of("state", Component.translatable("steamwork.state." + currentReason.key()))
        ));
    }

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material material = switch (currentReason) {
                case READY, PROCESSING -> Material.GREEN_STAINED_GLASS_PANE;
                case NO_STEAM, STRUCTURE_MISSING -> Material.RED_STAINED_GLASS_PANE;
                case OUTPUT_FULL -> Material.YELLOW_STAINED_GLASS_PANE;
                case NO_TEMPLATE, NO_RECIPE, NO_INGREDIENTS -> Material.GRAY_STAINED_GLASS_PANE;
            };

            return ItemStackBuilder.of(material)
                    .name(noItalic(Component.translatable("steamwork.state." + (lastActive ? "active" : "idle"))))
                    .lore(List.of(
                            noItalic(reasonComponent(currentReason)),
                            noItalic(Component.text(String.format("%.0f mB/craft", steamPerCraft)))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private @NotNull Component reasonComponent(@NotNull StopReason reason) {
        return switch (reason) {
            case NO_TEMPLATE -> Component.translatable("steamwork.state.ready");
            case NO_RECIPE, NO_INGREDIENTS -> Component.translatable("steamwork.state.no_ingredients");
            default -> Component.translatable("steamwork.state." + reason.key());
        };
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.SUPERHEATED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM);
            return ItemStackBuilder.of(Material.ORANGE_STAINED_GLASS)
                    .name(noItalic(Component.translatable("steamwork.fluid.superheated_steam")))
                    .lore(List.of(noItalic(Component.text(
                            UnitFormat.MILLIBUCKETS.format(steam).decimalPlaces(0)
                                    + " / "
                                    + UnitFormat.MILLIBUCKETS.format(capacity).decimalPlaces(0)
                    ))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private record RecipeMatch(@NotNull ItemStack result, @NotNull List<RecipeChoice> choices) {}

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
