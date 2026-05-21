package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.base.RebarDirectionalBlock;
import io.github.pylonmc.rebar.block.base.RebarGuiBlock;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.base.RebarVirtualInventoryBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蒸汽装配台：把若干部件（底座 + 合金件 + 黄铜组件）组装成蒸汽装备。
 * <p>
 * - 3×1×3 多方块结构：主方块在中心，4 角青铜块。
 * - GUI：5 个输入槽 + 1 个装配按钮 + 1 个输出槽。
 * - 不耗蒸汽、不 tick；玩家点击按钮即时完成。
 * - 输入顺序不计较；按 {@link SteamAssemblyRecipe} 的逐项匹配 + 占位算法消耗。
 * <p>
 * 现阶段所有"未充气"蒸汽装备（5 工具、4 护甲、3 罐子）的合成都在这里完成，
 * 原版工作台配方已撤掉。让"做出蒸汽装备 → 必须先建一个工作车间"成为玩法门槛。
 */
public class SteamAssemblyBench extends RebarBlock implements
        RebarDirectionalBlock,
        RebarGuiBlock,
        RebarSimpleMultiblock,
        RebarVirtualInventoryBlock {

    private static final int INPUT_SIZE = 5;

    public static class Item extends RebarItem {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }
    }

    protected final VirtualInventory inputInventory = new VirtualInventory(INPUT_SIZE);
    protected final VirtualInventory outputInventory = new VirtualInventory(1);
    private final AssembleButton assembleButton = new AssembleButton();

    @SuppressWarnings("unused")
    public SteamAssemblyBench(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setMultiblockDirection(context.getFacing());
    }

    @SuppressWarnings("unused")
    public SteamAssemblyBench(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    public void postInitialise() {
        // 输出槽只能被装配按钮写入；玩家不能塞东西进去。
        outputInventory.addPreUpdateHandler(RebarUtils.DISALLOW_PLAYERS_FROM_ADDING_ITEMS_HANDLER);
    }

    @Override
    public @NotNull Map<@NotNull Vector3i, @NotNull MultiblockComponent> getComponents() {
        // 4 角青铜块支撑工作平台。
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
                "input", inputInventory,
                "output", outputInventory
        );
    }

    @Override
    public void onBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        RebarVirtualInventoryBlock.super.onBreak(drops, context);
    }

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# i i i i i # o #",
                        "# # # # # # # # #",
                        "# # # # a # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('i', inputInventory)
                .addIngredient('o', outputInventory)
                .addIngredient('a', assembleButton)
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
                        + (isFormedAndFullyLoaded() ? "formed" : "missing")))
        ));
    }

    // ===== 配方解析 =====

    /**
     * 检查输入槽是否匹配某个配方。返回匹配到的配方；找不到返回 null。
     * 注意：本方法不消耗输入，仅查询。
     */
    private @Nullable SteamAssemblyRecipe findMatchingRecipe() {
        for (SteamAssemblyRecipe recipe : SteamAssemblyRecipe.RECIPE_TYPE) {
            if (canFulfill(recipe)) {
                return recipe;
            }
        }
        return null;
    }

    /** 不修改库存的"能否凑齐"检查。 */
    private boolean canFulfill(@NotNull SteamAssemblyRecipe recipe) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeInput.Item need : recipe.ingredients()) {
            int stillNeeded = need.getAmount();
            for (int slot = 0; slot < inputInventory.getSize(); slot++) {
                ItemStack stack = inputInventory.getItem(slot);
                int already = reserved.getOrDefault(slot, 0);
                if (stack == null || stack.isEmpty() || stack.getAmount() <= already) continue;
                if (!need.matchesIgnoringAmount(stack)) continue;
                int available = stack.getAmount() - already;
                int take = Math.min(stillNeeded, available);
                reserved.merge(slot, take, Integer::sum);
                stillNeeded -= take;
                if (stillNeeded <= 0) break;
            }
            if (stillNeeded > 0) return false;
        }
        return true;
    }

    /** 实际消耗输入槽的配方原料。前提：canFulfill 已返回 true。 */
    private void consumeIngredients(@NotNull SteamAssemblyRecipe recipe) {
        Map<Integer, Integer> reserved = new LinkedHashMap<>();
        for (RecipeInput.Item need : recipe.ingredients()) {
            int stillNeeded = need.getAmount();
            for (int slot = 0; slot < inputInventory.getSize(); slot++) {
                ItemStack stack = inputInventory.getItem(slot);
                int already = reserved.getOrDefault(slot, 0);
                if (stack == null || stack.isEmpty() || stack.getAmount() <= already) continue;
                if (!need.matchesIgnoringAmount(stack)) continue;
                int available = stack.getAmount() - already;
                int take = Math.min(stillNeeded, available);
                reserved.merge(slot, take, Integer::sum);
                stillNeeded -= take;
                if (stillNeeded <= 0) break;
            }
        }
        for (Map.Entry<Integer, Integer> entry : reserved.entrySet()) {
            ItemStack stack = inputInventory.getItem(entry.getKey());
            if (stack != null) {
                inputInventory.setItem(new MachineUpdateReason(), entry.getKey(), stack.subtract(entry.getValue()));
            }
        }
    }

    private void spawnAssembleFx() {
        Block b = getBlock();
        b.getWorld().spawnParticle(
                Particle.CRIT,
                b.getLocation().add(0.5, 1.1, 0.5),
                16, 0.3, 0.2, 0.3, 0.05);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.5f);
    }

    // ===== GUI button =====

    private final class AssembleButton extends AbstractItem {

        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean formed = isFormedAndFullyLoaded();
            SteamAssemblyRecipe match = formed ? findMatchingRecipe() : null;
            boolean ready = match != null && outputInventory.canHold(match.producedStack());

            Material mat = ready ? Material.LIME_STAINED_GLASS_PANE
                    : formed ? Material.GRAY_STAINED_GLASS_PANE
                    : Material.RED_STAINED_GLASS_PANE;
            String statusKey = ready ? "ready" : formed ? "no_recipe" : "structure_missing";

            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.button")))
                    .lore(List.of(
                            noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.status." + statusKey))
                    ));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            if (!isFormedAndFullyLoaded()) return;

            SteamAssemblyRecipe match = findMatchingRecipe();
            if (match == null) return;

            ItemStack out = match.producedStack();
            if (!outputInventory.canHold(out)) return;

            consumeIngredients(match);
            outputInventory.addItem(new MachineUpdateReason(), out);
            spawnAssembleFx();
            notifyWindows();
        }
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
