package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.SimpleRebarMultiblock;
import io.github.pylonmc.rebar.block.interfaces.SimpleRebarMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.guide.button.ItemButton;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.MachineUpdateReason;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.inventory.VirtualInventory;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;

/**
 * 蒸汽装配祭坛。竖直 3×3 多方块：中心是装配台，四周 8 个锰钢块作为底座。
 *
 * <p>玩家右键底座放入/取出物品，物品像展示框那样悬浮显示；右键中央装配台时，
 * 若 8 个底座上的物品凑成一条 {@link SteamAssemblyRecipe}，便播放白色粒子动画，
 * 数秒后消耗原料并产出成品。</p>
 */
public class SteamAssemblyBench extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        SimpleRebarMultiblock,
        TickingRebarBlock,
        VirtualInventoryRebarBlock,
        GuiRebarBlock {

    /** 8 个底座相对中心（装配台）的位置：竖直平面（北向默认），物品朝 facing 方向展示。
     *  X 顺序按"玩家视角从左到右"排列（+1→-1），使底座物理顺序与 GUI 槽位顺序一致。 */
    private static final List<Vector3i> PEDESTALS = List.of(
            new Vector3i(1, 1, 0), new Vector3i(0, 1, 0), new Vector3i(-1, 1, 0),
            new Vector3i(1, 0, 0), new Vector3i(-1, 0, 0),
            new Vector3i(1, -1, 0), new Vector3i(0, -1, 0), new Vector3i(-1, -1, 0)
    );

    private static final int CRAFT_TICKS = 50; // 合成动画时长（真实 tick，2.5s）

    private final int tickInterval = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);
    private final double steamPerCraft = getSettings().getOrThrow("steam-per-craft", ConfigAdapter.DOUBLE);

    /** 8 个底座上的物品（slot 序号对应 {@link #PEDESTALS} 序号），由 Rebar 持久化。 */
    private final VirtualInventory pedestals = new VirtualInventory(PEDESTALS.size());

    /** 当前每个底座的悬浮展示实体（瞬时，跨重载由 tick 依据库存重建）。 */
    private final Map<Integer, UUID> displays = new HashMap<>();
    /** 每个底座的数量 TextDisplay（有物品时显示，无物品时不显示）。 */
    private final Map<Integer, UUID> countDisplays = new HashMap<>();
    /** 装配台中心方块前方的配方成品预览 ItemDisplay（无匹配配方时移除）。 */
    private @Nullable UUID centerDisplayUuid = null;

    private final CraftButton craftButton = new CraftButton();
    private final ResultItem resultItem = new ResultItem();
    private final StatusItem statusItem = new StatusItem();
    private final SteamGaugeItem steamGaugeItem = new SteamGaugeItem();

    private volatile boolean crafting = false;
    /** 当多条配方匹配同一组底座物品时，玩家选中的配方序号（点击成品预览循环切换）。 */
    private int selectedRecipe = 0;

    @SuppressWarnings("unused")
    public SteamAssemblyBench(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setMultiblockDirection(context.getFacing());
        setTickInterval(tickInterval);
        // 管道接口放在装配台背后（SOUTH = facing 的反面），既不挡正面物品展示又更隐蔽美观
        createFluidPoint(FluidPointType.INPUT, BlockFace.SOUTH, context, false);
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
        setMultiblockDirection(getFacing());
        setTickInterval(tickInterval);
    }

    @Override
    public @NotNull Map<@NotNull Vector3i, @NotNull MultiblockComponent> getComponents() {
        MultiblockComponent pedestal = MultiblockComponent.of(SteamworkKeys.MANGANESE_STEEL_BLOCK);
        Map<Vector3i, MultiblockComponent> map = new HashMap<>();
        for (Vector3i pos : PEDESTALS) {
            map.put(pos, pedestal);
        }
        return map;
    }

    @Override
    public @NotNull Map<String, VirtualInventory> getVirtualInventories() {
        return Map.of("pedestals", pedestals);
    }

    // ── 底座归属 / 交互 ────────────────────────────────────────────────────────

    /** 该装配祭坛（已成型）是否拥有给定底座方块。 */
    public boolean ownsPedestal(@NotNull Block block) {
        if (!isFormedAndFullyLoaded()) return false;
        return pedestalIndex(block) >= 0;
    }

    private int pedestalIndex(@NotNull Block block) {
        for (int i = 0; i < PEDESTALS.size(); i++) {
            if (getMultiblockBlock(PEDESTALS.get(i)).equals(block)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 由底座方块（AssemblyPedestal）转发的交互：
     * <ul>
     *   <li>空手右键 → 取回该底座整组物品；</li>
     *   <li>手持物品右键 → 放入 1 个；</li>
     *   <li>手持物品潜行+右键 → 全放（同类型可叠加，最多一组）。</li>
     * </ul>
     */
    public void handlePedestalInteract(@NotNull Block pedestal, @NotNull Player player, boolean sneaking) {
        int index = pedestalIndex(pedestal);
        if (index < 0) return;

        ItemStack onPedestal = pedestals.getItem(index);
        ItemStack held = player.getInventory().getItemInMainHand();
        MachineUpdateReason reason = new MachineUpdateReason();

        // 空手 → 取回整组
        if (held == null || held.getType().isAir()) {
            if (onPedestal != null && !onPedestal.getType().isAir()) {
                pedestals.setItem(reason, index, null);
                returnToPlayer(player, onPedestal);
                player.playSound(pedestal.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.6f, 1.2f);
                refreshDisplays();
            }
            return;
        }

        // 手持物品 → 放入。底座为空或与手上同类型才可放
        boolean empty = onPedestal == null || onPedestal.getType().isAir();
        if (!empty && !onPedestal.isSimilar(held)) return;

        int current = empty ? 0 : onPedestal.getAmount();
        int space = held.getMaxStackSize() - current;
        if (space <= 0) return; // 已满一组

        int toPlace = Math.min(sneaking ? held.getAmount() : 1, space);
        if (toPlace <= 0) return;

        ItemStack placed = held.clone();
        placed.setAmount(current + toPlace);
        pedestals.setItem(reason, index, placed);

        held.setAmount(held.getAmount() - toPlace);
        player.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);

        player.playSound(pedestal.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6f, 1.4f);
        refreshDisplays();
    }

    private void returnToPlayer(@NotNull Player player, @NotNull ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // ── 右键装配台：尝试合成 ──────────────────────────────────────────────────

    @Override
    public @NotNull Gui createGui() {
        // 8 个底座槽镜像祭坛 3×3 布局（中心是装配台），右侧蒸汽/状态/合成按钮/成品预览
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# p p p # g s # #",
                        "# p . p # # # # #",
                        "# p p p # c o # #",
                        "# # # # # # # # #")
                .addIngredient('#', GuiItems.background())
                .addIngredient('.', ItemButton.of(SteamworkItems.STEAM_ASSEMBLY_BENCH))
                .addIngredient('p', pedestals)
                .addIngredient('g', steamGaugeItem)
                .addIngredient('s', statusItem)
                .addIngredient('c', craftButton)
                .addIngredient('o', resultItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.title"));
    }

    private void tryStartCraft(@NotNull Player player) {
        if (crafting) {
            player.sendActionBar(Component.translatable("steamwork.gui.steam_assembly_bench.actionbar.crafting"));
            return;
        }
        if (!isFormedAndFullyLoaded()) {
            player.sendActionBar(Component.translatable("steamwork.gui.steam_assembly_bench.actionbar.structure_missing"));
            return;
        }
        SteamAssemblyRecipe recipe = findMatch();
        if (recipe == null) {
            player.sendActionBar(Component.translatable("steamwork.gui.steam_assembly_bench.actionbar.no_recipe"));
            return;
        }
        if (fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) < steamPerCraft) {
            player.sendActionBar(Component.translatable("steamwork.gui.steam_assembly_bench.actionbar.no_steam"));
            return;
        }
        startCraftAnimation(recipe, player);
    }

    private void startCraftAnimation(@NotNull SteamAssemblyRecipe recipe, @NotNull Player player) {
        crafting = true;
        Location center = centerFront();
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
        player.sendActionBar(Component.translatable("steamwork.gui.steam_assembly_bench.actionbar.started"));

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // 祭坛被破坏 / 卸载 → 中止
                if (!isFormedAndFullyLoaded()) {
                    crafting = false;
                    cancel();
                    return;
                }
                tick++;
                Location c = centerFront();
                c.getWorld().spawnParticle(Particle.CLOUD, c, 3, 0.35, 0.35, 0.35, 0.01);
                c.getWorld().spawnParticle(Particle.WHITE_ASH, c, 6, 0.45, 0.45, 0.45, 0.02);
                if (tick % 10 == 0) {
                    c.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.0f + tick / 100f);
                }

                if (tick >= CRAFT_TICKS) {
                    cancel();
                    try {
                        completeCraft(recipe);
                    } catch (Exception ex) {
                        io.github.steamwork.Steamwork.getInstance().getLogger()
                                .warning("装配台合成失败: " + ex);
                    } finally {
                        crafting = false; // 无论成败都复位，避免卡在"装配中"
                    }
                }
            }
        }.runTaskTimer(io.github.steamwork.Steamwork.getInstance(), 1L, 1L);
    }

    private void completeCraft(@NotNull SteamAssemblyRecipe recipe) {
        // 动画期间物品/蒸汽可能被取走，完成时重新校验并消耗
        List<List<SlotPlan>> plan = assign(recipe);
        if (plan == null) return;
        if (fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) < steamPerCraft) return;
        removeFluid(SteamworkFluids.SUPERHEATED_STEAM, steamPerCraft);

        MachineUpdateReason reason = new MachineUpdateReason();
        for (List<SlotPlan> ingPlan : plan) {
            for (SlotPlan sp : ingPlan) {
                ItemStack stack = pedestals.getItem(sp.slot());
                if (stack == null) continue;
                int remaining = stack.getAmount() - sp.amount();
                pedestals.setItem(reason, sp.slot(), remaining <= 0 ? null : stack.asQuantity(remaining));
            }
        }
        refreshDisplays();

        Location out = centerFront();
        out.getWorld().dropItem(out, recipe.producedStack());
        out.getWorld().spawnParticle(Particle.CLOUD, out, 12, 0.25, 0.25, 0.25, 0.06);
        out.getWorld().spawnParticle(Particle.WHITE_ASH, out, 16, 0.30, 0.30, 0.30, 0.05);
        out.getWorld().playSound(out, Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
    }

    // ── 配方匹配 ──────────────────────────────────────────────────────────────

    /**
     * 单条原料的底座消耗计划：从 slot 消耗 amount 个。
     * 一条原料可能对应多个 SlotPlan（跨底座合并凑够数量时）。
     */
    private record SlotPlan(int slot, int amount) {}

    /** 当前底座物品能匹配的所有配方（多条工序产物可能用同一组原料，如钨头盔/靴子）。 */
    private @NotNull List<SteamAssemblyRecipe> findAllMatches() {
        List<SteamAssemblyRecipe> matches = new ArrayList<>();
        for (SteamAssemblyRecipe recipe : SteamAssemblyRecipe.RECIPE_TYPE) {
            if (assign(recipe) != null) matches.add(recipe);
        }
        return matches;
    }

    /** 当前选中的匹配配方（多条匹配时由玩家点击成品预览切换）。 */
    private @Nullable SteamAssemblyRecipe findMatch() {
        List<SteamAssemblyRecipe> matches = findAllMatches();
        if (matches.isEmpty()) return null;
        return matches.get(Math.floorMod(selectedRecipe, matches.size()));
    }

    /**
     * 把配方各原料分配到底座 slots，支持同一原料从多个底座合并凑够数量。
     * 允许底座上有多余的无关物品（不参与、也不消耗）。
     * 返回每条原料的消耗计划列表，null 表示无法完全匹配。
     */
    private @Nullable List<List<SlotPlan>> assign(@NotNull SteamAssemblyRecipe recipe) {
        List<RecipeInput.Item> ingredients = recipe.ingredients();

        // 逐步扣减剩余可用量，确保同一底座不被多条原料重复分配
        int[] available = new int[PEDESTALS.size()];
        for (int slot = 0; slot < PEDESTALS.size(); slot++) {
            ItemStack s = pedestals.getItem(slot);
            available[slot] = (s != null && !s.getType().isAir()) ? s.getAmount() : 0;
        }

        List<List<SlotPlan>> result = new ArrayList<>();
        for (RecipeInput.Item ing : ingredients) {
            int needed = ing.getAmount();
            List<SlotPlan> plan = new ArrayList<>();

            for (int slot = 0; slot < PEDESTALS.size() && needed > 0; slot++) {
                if (available[slot] <= 0) continue;
                ItemStack s = pedestals.getItem(slot);
                if (!typeMatchesIngredient(ing, s)) continue;
                int take = Math.min(available[slot], needed);
                plan.add(new SlotPlan(slot, take));
                available[slot] -= take;
                needed -= take;
            }

            if (needed > 0) return null; // 该原料数量不足
            result.add(plan);
        }
        return result;
    }

    /**
     * 检查底座物品类型是否与配方原料匹配（忽略数量，仅检查材质/NBT/自定义模型等）。
     * 通过将 stack 数量临时设为原料要求量，令 matches() 的数量校验始终通过。
     */
    private static boolean typeMatchesIngredient(@NotNull RecipeInput.Item ing, @Nullable ItemStack s) {
        if (s == null || s.getType().isAir()) return false;
        return ing.matches(s.asQuantity(ing.getAmount()));
    }

    // ── 悬浮展示实体 ──────────────────────────────────────────────────────────

    @Override
    public void tick() {
        refreshDisplays();
        craftButton.notifyWindows();
        resultItem.notifyWindows();
        statusItem.notifyWindows();
        steamGaugeItem.notifyWindows();
    }

    /** 依据底座库存，确保每个底座前方悬浮一个展示实体及数量标签；空底座则全部移除。
     *  同时在中心方块前方展示当前匹配配方的成品预览。 */
    private void refreshDisplays() {
        if (!isFormedAndFullyLoaded()) {
            clearDisplays();
            return;
        }

        // ── 中心配方成品预览 ────────────────────────────────────────────────────
        SteamAssemblyRecipe match = findMatch();
        ItemDisplay centerDisplay = currentCenterDisplay();
        if (match == null) {
            if (centerDisplay != null) { centerDisplay.remove(); }
            centerDisplayUuid = null;
        } else {
            ItemStack preview = match.producedStack().asQuantity(1);
            if (centerDisplay == null) {
                centerDisplay = spawnCenterDisplay(preview);
                if (centerDisplay != null) centerDisplayUuid = centerDisplay.getUniqueId();
            } else {
                centerDisplay.setItemStack(preview);
            }
        }

        for (int i = 0; i < PEDESTALS.size(); i++) {
            ItemStack item = pedestals.getItem(i);
            ItemDisplay display = currentDisplay(i);
            TextDisplay countDisplay = currentCountDisplay(i);

            if (item == null || item.getType().isAir()) {
                if (display != null) display.remove();
                displays.remove(i);
                if (countDisplay != null) countDisplay.remove();
                countDisplays.remove(i);
                continue;
            }

            // ItemDisplay
            if (display == null) {
                display = spawnDisplay(i, item);
                if (display != null) displays.put(i, display.getUniqueId());
            } else {
                display.setItemStack(item.asQuantity(1));
            }

            // 数量 TextDisplay
            Component countText = noItalic(Component.text(item.getAmount(), NamedTextColor.YELLOW));
            if (countDisplay == null) {
                countDisplay = spawnCountDisplay(i, countText);
                if (countDisplay != null) countDisplays.put(i, countDisplay.getUniqueId());
            } else {
                countDisplay.text(countText);
            }
        }
    }

    private @Nullable ItemDisplay currentDisplay(int index) {
        UUID id = displays.get(index);
        if (id == null) return null;
        if (org.bukkit.Bukkit.getEntity(id) instanceof ItemDisplay d && d.isValid()) return d;
        return null;
    }

    private @Nullable TextDisplay currentCountDisplay(int index) {
        UUID id = countDisplays.get(index);
        if (id == null) return null;
        if (org.bukkit.Bukkit.getEntity(id) instanceof TextDisplay d && d.isValid()) return d;
        return null;
    }

    private @Nullable ItemDisplay spawnDisplay(int index, @NotNull ItemStack item) {
        Block pedestal = getMultiblockBlock(PEDESTALS.get(index));
        Vector face = getFacing().getDirection();
        Location loc = pedestal.getLocation().add(0.5, 0.5, 0.5).add(face.clone().multiply(0.52));
        loc.setYaw(yawFor(getFacing()));
        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item.asQuantity(1));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // 缩到展示框大小（约半格）
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setGlowing(true); // 发光描边，便于看清
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
    }

    /** 在 ItemDisplay 正下方生成物品数量 TextDisplay，固定朝向（不跟随玩家转向）。 */
    private @Nullable TextDisplay spawnCountDisplay(int index, @NotNull Component text) {
        Block pedestal = getMultiblockBlock(PEDESTALS.get(index));
        Vector face = getFacing().getDirection();
        // 紧贴底座方块朝向面（0.501），高度偏下
        Location loc = pedestal.getLocation().add(0.5, 0.15, 0.5).add(face.clone().multiply(0.501));
        loc.setYaw(yawFor(getFacing()));
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED); // 固定朝向，不随玩家旋转
            d.setDefaultBackground(false);
            d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // 完全透明背景
            d.setShadowed(true);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
        });
    }

    private @Nullable ItemDisplay currentCenterDisplay() {
        if (centerDisplayUuid == null) return null;
        if (org.bukkit.Bukkit.getEntity(centerDisplayUuid) instanceof ItemDisplay d && d.isValid()) return d;
        return null;
    }

    /** 在装配台中心方块朝向面生成配方成品预览 ItemDisplay，位置与底座物品展示一致。 */
    private @Nullable ItemDisplay spawnCenterDisplay(@NotNull ItemStack item) {
        Vector face = getFacing().getDirection();
        Location loc = getBlock().getLocation().add(0.5, 0.5, 0.5).add(face.clone().multiply(0.52));
        loc.setYaw(yawFor(getFacing()));
        return loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()));
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
    }

    private static float yawFor(@NotNull BlockFace facing) {
        return switch (facing) {
            case NORTH -> 180f;
            case EAST -> 270f;
            case WEST -> 90f;
            default -> 0f; // SOUTH
        };
    }

    private void clearDisplays() {
        for (UUID id : displays.values()) {
            if (org.bukkit.Bukkit.getEntity(id) instanceof ItemDisplay d) d.remove();
        }
        displays.clear();
        for (UUID id : countDisplays.values()) {
            if (org.bukkit.Bukkit.getEntity(id) instanceof TextDisplay d) d.remove();
        }
        countDisplays.clear();
        ItemDisplay cd = currentCenterDisplay();
        if (cd != null) cd.remove();
        centerDisplayUuid = null;
    }

    private @NotNull Location centerFront() {
        Vector face = getFacing().getDirection();
        return getBlock().getLocation().add(0.5, 0.5, 0.5).add(face.clone().multiply(0.6));
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        // 归还底座上的物品
        for (int i = 0; i < PEDESTALS.size(); i++) {
            ItemStack s = pedestals.getItem(i);
            if (s != null && !s.getType().isAir()) drops.add(s.clone());
        }
        clearDisplays();
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
        VirtualInventoryRebarBlock.super.onBlockBreak(drops, context);
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        SteamAssemblyRecipe match = isFormedAndFullyLoaded() ? findMatch() : null;
        Component state;
        if (crafting) {
            state = Component.translatable("steamwork.gui.steam_assembly_bench.waila.assembling");
        } else if (match != null) {
            state = Component.translatable("steamwork.gui.steam_assembly_bench.waila.can_craft")
                    .append(match.producedStack().effectiveName());
        } else {
            state = Component.translatable(isFormedAndFullyLoaded()
                    ? "steamwork.gui.steam_assembly_bench.waila.open_gui"
                    : "steamwork.gui.steam_assembly_bench.waila.structure_missing");
        }
        return WailaDisplay.of(getDefaultWailaTranslationKey().arguments(
                RebarArgument.of("structure", Component.translatable("steamwork.structure."
                        + (isFormedAndFullyLoaded() ? "formed" : "missing"))),
                RebarArgument.of("steam-bar", io.github.steamwork.util.SteamworkUtils.createFluidAmountBar(
                        fluidAmount(SteamworkFluids.SUPERHEATED_STEAM),
                        fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM),
                        12,
                        TextColor.fromHexString("#ff8c00"))),
                RebarArgument.of("state", state)
        ));
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

    // ── GUI 元素 ──────────────────────────────────────────────────────────────

    /** 合成按钮：点击尝试装配。 */
    private final class CraftButton extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean ready = !crafting && isFormedAndFullyLoaded() && findMatch() != null
                    && fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) >= steamPerCraft;
            Material mat = crafting ? Material.CLOCK
                    : ready ? Material.LIME_DYE : Material.GRAY_DYE;
            Component title = Component.translatable(crafting
                    ? "steamwork.gui.steam_assembly_bench.status.assembling"
                    : "steamwork.gui.steam_assembly_bench.craft.start");
            if (!crafting) title = title.color(ready ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            Component costLore = Component.translatable("steamwork.gui.steam_assembly_bench.craft.steam_cost",
                    RebarArgument.of("steam", String.format("%.0f", steamPerCraft)));
            return ItemStackBuilder.of(mat)
                    .name(noItalic(title))
                    .lore(List.of(noItalic(costLore)));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            tryStartCraft(player);
        }
    }

    /** 成品预览：显示当前底座物品匹配出的成品；多条匹配时点击可循环切换。 */
    private final class ResultItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            List<SteamAssemblyRecipe> matches = isFormedAndFullyLoaded() ? findAllMatches() : List.of();
            if (matches.isEmpty()) {
                return ItemStackBuilder.of(Material.BARRIER)
                        .name(noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.result.no_match")));
            }
            int idx = Math.floorMod(selectedRecipe, matches.size());
            ItemStack preview = matches.get(idx).producedStack();
            if (matches.size() > 1) {
                var meta = preview.getItemMeta();
                List<Component> lore = meta.lore() != null
                        ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.result.switch_hint",
                        RebarArgument.of("index", String.valueOf(idx + 1)),
                        RebarArgument.of("total", String.valueOf(matches.size())))));
                meta.lore(lore);
                preview.setItemMeta(meta);
            }
            return new xyz.xenondevs.invui.item.ItemWrapper(preview);
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
            int count = findAllMatches().size();
            if (count > 1) {
                selectedRecipe = Math.floorMod(selectedRecipe + 1, count);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                resultItem.notifyWindows();
                craftButton.notifyWindows();
                statusItem.notifyWindows();
            }
        }
    }

    /** 状态指示。 */
    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            boolean formed = isFormedAndFullyLoaded();
            boolean hasSteam = fluidAmount(SteamworkFluids.SUPERHEATED_STEAM) >= steamPerCraft;
            Material mat;
            Component text;
            if (crafting) {
                mat = Material.GREEN_STAINED_GLASS_PANE;
                text = Component.translatable("steamwork.gui.steam_assembly_bench.status.assembling");
            } else if (!formed) {
                mat = Material.RED_STAINED_GLASS_PANE;
                text = Component.translatable("steamwork.gui.steam_assembly_bench.status.structure_missing");
            } else if (!hasSteam) {
                mat = Material.ORANGE_STAINED_GLASS_PANE;
                text = Component.translatable("steamwork.gui.steam_assembly_bench.status.no_steam");
            } else if (findMatch() == null) {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                text = Component.translatable("steamwork.gui.steam_assembly_bench.status.no_ingredients");
            } else {
                mat = Material.GREEN_STAINED_GLASS_PANE;
                text = Component.translatable("steamwork.gui.steam_assembly_bench.status.ready");
            }
            return ItemStackBuilder.of(mat).name(noItalic(text));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    /** 过热蒸汽量表。 */
    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.SUPERHEATED_STEAM);
            double capacity = fluidCapacity(SteamworkFluids.SUPERHEATED_STEAM);
            return ItemStackBuilder.of(Material.ORANGE_STAINED_GLASS)
                    .name(noItalic(Component.translatable("steamwork.fluid.superheated_steam")))
                    .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_assembly_bench.fluid_amount",
                            RebarArgument.of("steam", String.format("%.0f", steam)),
                            RebarArgument.of("capacity", String.format("%.0f", capacity))))));
        }

        @Override
        public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {}
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
