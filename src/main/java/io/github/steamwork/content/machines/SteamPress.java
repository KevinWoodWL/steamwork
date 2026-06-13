package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.util.PylonUtils;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.FluidBufferRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GhostBlockHolderRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.GuiRebarBlock;
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler;
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.fluid.FluidPointType;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.recipe.RecipeInput;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.pylonmc.pylon.recipes.FormingRecipe;
import io.github.pylonmc.pylon.recipes.PipeBendingRecipe;
import io.github.pylonmc.pylon.recipes.TableSawRecipe;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamPressingRecipe;
import io.github.steamwork.recipes.SteamProcessRecipe;
import io.github.steamwork.recipes.pylon.FormingRecipeWrapper;
import io.github.steamwork.recipes.pylon.PipeBendingRecipeWrapper;
import io.github.steamwork.recipes.pylon.TableSawRecipeWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3i;

/**
 * 蒸汽冲压机 —— 多方块结构：
 * <pre>
 *  [冲压机]  ← 机器方块（Y+2），右键打开状态 GUI
 *  [空气]    ← Y+1
 *  [铁块]    ← 右键放/取物品，物品以 ItemDisplay 展示在铁块上方（Y+0）
 * </pre>
 */
public class SteamPress extends RebarBlock implements
        DirectionalRebarBlock,
        FluidBufferRebarBlock,
        GhostBlockHolderRebarBlock,
        GuiRebarBlock,
        InteractRebarBlockHandler,
        TickingRebarBlock,
        SteamBoostable {

    private static final NamespacedKey RECIPE_KEY     = new NamespacedKey("steamwork", "pressing_recipe");
    private static final NamespacedKey TICKS_KEY      = new NamespacedKey("steamwork", "pressing_ticks");
    private static final NamespacedKey ENTITY_KEY     = new NamespacedKey("steamwork", "pressing_entity");
    private static final NamespacedKey PISTON_KEY     = new NamespacedKey("steamwork", "pressing_piston");
    private static final NamespacedKey PRESS_MARKER   = new NamespacedKey("steamwork", "press_display");

    /** 活塞静止（缩回）位的 Y 偏移——放置时与复位后必须一致，否则复位后会偏离、材质冲突。 */
    private static final float PISTON_IDLE_Y    = 0.2375f;
    /** 活塞下压到底（铁块顶面）的 Y 偏移。 */
    private static final float PISTON_PRESSED_Y = -0.5f;

    private final int tickInterval   = getSettings().getOrThrow("tick-interval", ConfigAdapter.INTEGER);
    private final double steamBuffer = getSettings().getOrThrow("steam-buffer",  ConfigAdapter.DOUBLE);
    private final double scrapChance = getSettings().get("scrap-chance", ConfigAdapter.DOUBLE, 0.1);
    private final int graceTicks     = getSettings().get("interruption-grace-ticks", ConfigAdapter.INTEGER, 60);

    @Nullable private NamespacedKey currentRecipeKey = null;
    private int recipeTicksRemaining = 0;
    private int interruptionTicks    = 0;
    private boolean lastActive       = false;
    private boolean pistonPressed    = false;

    @Nullable private UUID displayEntityUuid = null;
    @Nullable private UUID pistonEntityUuid  = null;

    public enum State {
        READY("ready"),
        NO_STRUCTURE("structure_missing"),
        NO_INGREDIENTS("no_ingredients"),
        NO_STEAM("no_steam"),
        PROCESSING("processing");

        private final String key;
        State(String key) { this.key = key; }
        public @NotNull String key() { return key; }
    }

    @NotNull private State currentState = State.READY;

    private final StatusItem   statusItem   = new StatusItem();
    private final SteamGaugeItem steamItem  = new SteamGaugeItem();
    private final ProgressItem progressItem = new ProgressItem();

    // ===== Item class =====

    public static class Item extends RebarItem {
        private final double steamBuffer = getSettings().getOrThrow("steam-buffer", ConfigAdapter.DOUBLE);

        public Item(@NotNull ItemStack stack) { super(stack); }

        @Override
        public @NotNull List<RebarArgument> getPlaceholders() {
            return List.of(RebarArgument.of("steam-buffer", UnitFormat.MILLIBUCKETS.format(steamBuffer)));
        }
    }

    // ===== Constructors =====

    @SuppressWarnings("unused")
    public SteamPress(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
        setFacing(context.getFacing());
        setTickInterval(tickInterval);
        createFluidPoint(FluidPointType.INPUT, BlockFace.NORTH, context, false);
        createFluidBuffer(SteamworkFluids.STEAM, steamBuffer, true, false);
        spawnPistonDisplay();
    }

    @SuppressWarnings("unused")
    public SteamPress(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
        currentRecipeKey     = pdc.get(RECIPE_KEY, RebarSerializers.NAMESPACED_KEY);
        recipeTicksRemaining = pdc.getOrDefault(TICKS_KEY, PersistentDataType.INTEGER, 0);
        String uuidStr = pdc.get(ENTITY_KEY, PersistentDataType.STRING);
        if (uuidStr != null) {
            try { displayEntityUuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException ignored) {}
        }
        String pistonStr = pdc.get(PISTON_KEY, PersistentDataType.STRING);
        if (pistonStr != null) {
            try { pistonEntityUuid = UUID.fromString(pistonStr); } catch (IllegalArgumentException ignored) {}
        }
    }

    @Override
    public void postInitialise() {
        if (displayEntityUuid != null) {
            Entity e = getBlock().getWorld().getEntity(displayEntityUuid);
            if (!(e instanceof ItemDisplay)) displayEntityUuid = null;
        }
        if (pistonEntityUuid != null) {
            Entity e = getBlock().getWorld().getEntity(pistonEntityUuid);
            if (!(e instanceof BlockDisplay)) pistonEntityUuid = null;
        }
        // Piston display is always present; respawn if entity was lost
        if (pistonEntityUuid == null) {
            spawnPistonDisplay();
        }
        // Sync ghost block with current structure state
        syncGhostBlock();
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        if (currentRecipeKey != null && recipeTicksRemaining > 0) {
            pdc.set(RECIPE_KEY, RebarSerializers.NAMESPACED_KEY, currentRecipeKey);
            pdc.set(TICKS_KEY,  PersistentDataType.INTEGER, recipeTicksRemaining);
        } else {
            pdc.remove(RECIPE_KEY);
            pdc.remove(TICKS_KEY);
        }
        if (displayEntityUuid != null) {
            pdc.set(ENTITY_KEY, PersistentDataType.STRING, displayEntityUuid.toString());
        } else {
            pdc.remove(ENTITY_KEY);
        }
        if (pistonEntityUuid != null) {
            pdc.set(PISTON_KEY, PersistentDataType.STRING, pistonEntityUuid.toString());
        } else {
            pdc.remove(PISTON_KEY);
        }
    }

    @Override
    public void onBlockBreak(@NotNull List<@NotNull ItemStack> drops, @NotNull BlockBreakContext context) {
        FluidBufferRebarBlock.super.onBlockBreak(drops, context);
        ItemDisplay d = getDisplayEntity();
        if (d != null) {
            ItemStack item = d.getItemStack();
            d.remove();
            displayEntityUuid = null;
            if (item != null && !item.isEmpty()) {
                getBlock().getWorld().dropItemNaturally(getIronBlockPos().getLocation().add(0.5, 0.5, 0.5), item);
            }
        }
        removePistonDisplay();
        if (hasGhostBlockAt(new Vector3i(0, -2, 0))) {
            removeGhostBlock(new Vector3i(0, -2, 0));
        }
    }

    // ===== Structure helpers =====

    private static final Vector3i IRON_BLOCK_OFFSET = new Vector3i(0, -2, 0);

    public @NotNull Block getIronBlockPos() {
        return getBlock().getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN);
    }

    public boolean hasValidStructure() {
        Block below1 = getBlock().getRelative(BlockFace.DOWN);
        Block below2 = below1.getRelative(BlockFace.DOWN);
        return below1.getType() == Material.AIR && below2.getType() == Material.IRON_BLOCK;
    }

    /**
     * 供产线调用：将物品放入冲压机铁块上的展示实体（相当于玩家手持右键放入）。
     * 如果机器正在加工或展示物品已存在且与 item 不同，则拒绝。
     *
     * @return true 表示成功接受
     */
    public boolean insertInputItem(@NotNull ItemStack item) {
        if (!hasValidStructure()) return false;
        if (isProcessing()) return false;
        ItemDisplay existing = getDisplayEntity();
        if (existing == null) {
            spawnDisplayEntity(item.asQuantity(1));
            return true;
        }
        ItemStack current = existing.getItemStack();
        if (current == null || current.isEmpty()) {
            existing.setItemStack(item.asQuantity(1));
            return true;
        }
        if (!current.isSimilar(item)) return false;
        if (current.getAmount() >= current.getMaxStackSize()) return false;
        current.setAmount(current.getAmount() + 1);
        existing.setItemStack(current);
        return true;
    }

    private void syncGhostBlock() {
        boolean hasSteelBelow = getBlock().getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN)
                .getType() == Material.IRON_BLOCK;
        boolean ghostPresent = hasGhostBlockAt(IRON_BLOCK_OFFSET);
        if (!hasSteelBelow && !ghostPresent) {
            addGhostBlock(IRON_BLOCK_OFFSET,
                    List.of(org.bukkit.Bukkit.createBlockData(Material.IRON_BLOCK)),
                    List.of());
        } else if (hasSteelBelow && ghostPresent) {
            removeGhostBlock(IRON_BLOCK_OFFSET);
        }
    }

    // ===== Display entity =====

    @Nullable
    private ItemDisplay getDisplayEntity() {
        if (displayEntityUuid == null) return null;
        Entity e = getBlock().getWorld().getEntity(displayEntityUuid);
        if (e instanceof ItemDisplay id) return id;
        displayEntityUuid = null;
        return null;
    }

    @Nullable
    private ItemStack getDisplayItem() {
        ItemDisplay d = getDisplayEntity();
        if (d == null) return null;
        ItemStack s = d.getItemStack();
        return (s == null || s.isEmpty()) ? null : s;
    }

    private void spawnDisplayEntity(@NotNull ItemStack item) {
        Block ironBlock = getIronBlockPos();
        ItemDisplay display = ironBlock.getWorld().spawn(
                ironBlock.getLocation().add(0.5, 1.01, 0.5),
                ItemDisplay.class,
                d -> {
                    d.setItemStack(item.clone());
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    // Rotate 90° around X to lay flat
                    d.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new Quaternionf(new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0)),
                            new Vector3f(0.5f, 0.5f, 0.5f),
                            new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
                    ));
                    d.setGravity(false);
                    d.setInvulnerable(true);
                    d.setPersistent(true);
                    d.setGlowing(true);
                    d.getPersistentDataContainer().set(PRESS_MARKER, PersistentDataType.BYTE, (byte) 1);
                });
        displayEntityUuid = display.getUniqueId();
    }

    private void removeDisplayEntity() {
        ItemDisplay d = getDisplayEntity();
        if (d != null) d.remove();
        displayEntityUuid = null;
    }

    // ===== Piston BlockDisplay =====

    @Nullable
    private BlockDisplay getPistonDisplay() {
        if (pistonEntityUuid == null) return null;
        Entity e = getBlock().getWorld().getEntity(pistonEntityUuid);
        if (e instanceof BlockDisplay bd) return bd;
        pistonEntityUuid = null;
        return null;
    }

    private void spawnPistonDisplay() {
        if (getPistonDisplay() != null) return;
        Block airBlock = getBlock().getRelative(BlockFace.DOWN);
        BlockData pistonHead = org.bukkit.Bukkit.createBlockData(
                "minecraft:piston_head[facing=down,type=normal,short=false]");
        BlockDisplay bd = airBlock.getWorld().spawn(
                airBlock.getLocation().add(0.5, 0.5, 0.5),
                BlockDisplay.class,
                d -> {
                    d.setBlock(pistonHead);
                    d.setGravity(false);
                    d.setInvulnerable(true);
                    d.setPersistent(true);
                    // Retracted (idle) position: piston head sits at top of air gap
                    d.setTransformation(new Transformation(
                            new Vector3f(-0.5f, PISTON_IDLE_Y, -0.5f),
                            new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                            new Vector3f(1, 1, 1),
                            new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
                    ));
                    d.getPersistentDataContainer().set(PRESS_MARKER, PersistentDataType.BYTE, (byte) 1);
                });
        pistonEntityUuid = bd.getUniqueId();
    }

    private void removePistonDisplay() {
        BlockDisplay bd = getPistonDisplay();
        if (bd != null) bd.remove();
        pistonEntityUuid = null;
    }

    /** 开始加工时下压活塞头到铁块顶面（{@link #PISTON_PRESSED_Y}），并播放音效+粒子。 */
    private void pressPistonDown() {
        BlockDisplay bd = getPistonDisplay();
        if (bd == null || pistonPressed) return;
        pistonPressed = true;
        bd.setInterpolationDelay(0);
        bd.setInterpolationDuration(6);
        bd.setTransformation(new Transformation(
                new Vector3f(-0.5f, PISTON_PRESSED_Y, -0.5f),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                new Vector3f(1, 1, 1),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
        ));
        getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.8f);
        getBlock().getWorld().spawnParticle(Particle.SMOKE,
                getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.1, 0.2, 0.02);
    }

    /** 加工结束后将活塞头升回静止位（{@link #PISTON_IDLE_Y}，与放置时一致），并产出蒸汽效果。 */
    private void liftPiston() {
        BlockDisplay bd = getPistonDisplay();
        if (bd == null) return;
        pistonPressed = false;
        bd.setInterpolationDelay(0);
        bd.setInterpolationDuration(6);
        bd.setTransformation(new Transformation(
                new Vector3f(-0.5f, PISTON_IDLE_Y, -0.5f),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0)),
                new Vector3f(1, 1, 1),
                new Quaternionf(new AxisAngle4f(0, 0, 1, 0))
        ));
        getBlock().getWorld().spawnParticle(Particle.CLOUD,
                getBlock().getLocation().add(0.5, 0.8, 0.5), 24, 0.3, 0.2, 0.3, 0.05);
        getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.2f);
    }

    // ===== Iron block right-click (global listener) =====

    public static void registerGlobalListeners() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(
                new IronBlockListener(), io.github.steamwork.Steamwork.getInstance());
    }

    public static class IronBlockListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOW)
        public void onIronBlockClick(@NotNull PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            if (event.getHand() != EquipmentSlot.HAND) return;
            Block clicked = event.getClickedBlock();
            if (clicked == null || clicked.getType() != Material.IRON_BLOCK) return;
            Block above2 = clicked.getRelative(BlockFace.UP).getRelative(BlockFace.UP);
            if (!(BlockStorage.get(above2) instanceof SteamPress press)) return;
            if (!press.hasValidStructure()) return;
            event.setCancelled(true);
            press.handleDepotInteract(event.getPlayer());
        }
    }

    private void handleDepotInteract(@NotNull Player player) {
        ItemDisplay existing = getDisplayEntity();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        if (isProcessing()) {
            // During processing: allow empty-hand right-click to retrieve excess items
            if (!inHand.isEmpty() || existing == null) return;
            ItemStack displayed = existing.getItemStack();
            // getDisplayItem() returns null when item is cleared (last one being processed)
            if (displayed == null || displayed.isEmpty()) return;
            // Take out all remaining items (the one currently being processed is already consumed)
            existing.setItemStack(null);
            player.getInventory().addItem(displayed).forEach((k, leftover) ->
                    getIronBlockPos().getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
            return;
        }

        if (existing != null) {
            // Take the displayed item back into player's inventory
            ItemStack displayed = existing.getItemStack();
            removeDisplayEntity();
            if (displayed != null && !displayed.isEmpty()) {
                player.getInventory().addItem(displayed).forEach((k, leftover) ->
                        getIronBlockPos().getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
        } else if (!inHand.isEmpty()) {
            // Shift+right-click: place the whole stack; normal right-click: place one item
            ItemStack toPlace;
            if (player.isSneaking()) {
                toPlace = inHand.clone();
                inHand.setAmount(0);
            } else {
                toPlace = inHand.asQuantity(1);
                inHand.subtract(1);
            }
            spawnDisplayEntity(toPlace);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 0.8f);
        }
    }

    // ===== Machine block right-click → open status GUI =====

    @Override
    public void onInteractedWith(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.LOWEST) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        // Machine block GUI is opened automatically by GuiRebarBlock - nothing extra needed here
    }

    // ===== Tick =====

    @Override
    public void tick() {
        if (!hasValidStructure()) {
            currentState = State.NO_STRUCTURE;
            resetRecipe();
            syncGhostBlock();
            notifyGui();
            return;
        }

        syncGhostBlock();

        if (isProcessing()) {
            tickProcessing();
        } else {
            currentState = tryStartRecipe();
            interruptionTicks = 0;
        }
        notifyGui();
    }

    private void tickProcessing() {
        SteamProcessRecipe recipe = getCurrentRecipe();
        if (recipe == null) {
            resetRecipe();
            currentState = State.READY;
            interruptionTicks = 0;
            return;
        }

        double steamPerTick = recipe.steamCost() / recipe.timeTicks();
        int progressTicks = Math.min(tickInterval,
                (int) Math.floor(fluidAmount(SteamworkFluids.STEAM) / steamPerTick));

        if (progressTicks <= 0) {
            currentState = State.NO_STEAM;
            handleInterruption(recipe);
            return;
        }

        removeFluid(SteamworkFluids.STEAM, steamPerTick * progressTicks);
        recipeTicksRemaining -= progressTicks;
        interruptionTicks = 0;
        currentState = State.PROCESSING;
        pressPistonDown();
        spawnFx(4);

        if (recipeTicksRemaining <= 0) {
            completeRecipe(recipe);
        }
    }

    // tryStartRecipe 从原生配方列表和 Pylon 联动缓存中匹配输入，原生优先。
    private @NotNull State tryStartRecipe() {
        ItemStack input = getDisplayItem();
        if (input == null) return State.READY;

        for (SteamPressingRecipe recipe : SteamPressingRecipe.RECIPE_TYPE) {
            RecipeInput.Item need = recipe.ingredient();
            if (!need.matchesIgnoringAmount(input)) continue;
            if (input.getAmount() < need.getAmount()) continue;
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) return State.NO_STEAM;

            // Consume one recipe's worth from the display stack, but keep entity visible
            consumeOneFromDisplay(need.getAmount());
            currentRecipeKey     = recipe.getKey();
            recipeTicksRemaining = recipe.timeTicks();
            setActive(true);
            spawnFx(8);
            return State.PROCESSING;
        }

        // Pylon 联动：成形 / 弯管 / 锯切配方
        for (SteamProcessRecipe recipe : getPylonRecipeCache()) {
            RecipeInput.Item need = recipe.ingredient();
            if (!need.matchesIgnoringAmount(input)) continue;
            if (input.getAmount() < need.getAmount()) continue;
            if (fluidAmount(SteamworkFluids.STEAM) < recipe.steamCost()) return State.NO_STEAM;
            recipe.onRecipeStart();
            consumeOneFromDisplay(need.getAmount());
            currentRecipeKey     = recipe.getKey();
            recipeTicksRemaining = recipe.timeTicks();
            setActive(true);
            spawnFx(8);
            return State.PROCESSING;
        }

        return State.NO_INGREDIENTS;
    }

    private void consumeOneFromDisplay(int amount) {
        ItemDisplay d = getDisplayEntity();
        if (d == null) return;
        ItemStack stack = d.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        int remaining = stack.getAmount() - amount;
        if (remaining <= 0) {
            // Stack exhausted — clear it but keep entity visible until completeRecipe
            d.setItemStack(null);
        } else {
            ItemStack updated = stack.clone();
            updated.setAmount(remaining);
            d.setItemStack(updated);
        }
    }

    private void completeRecipe(@NotNull SteamProcessRecipe recipe) {
        Block ironBlock = getIronBlockPos();
        ItemStack produced = recipe.producedStack().clone();

        // 优先将产物推送给产线下游成员
        produced = io.github.steamwork.content.line.SteamPressMember.tryDeliverOutput(
                ironBlock, produced);

        if (!produced.isEmpty()) {
            ironBlock.getWorld().dropItemNaturally(
                    ironBlock.getLocation().add(0.5, 0.5, 0.5), produced);
        }

        if (scrapChance > 0 && Math.random() < scrapChance) {
            ItemStack scrap = SteamworkItems.MACHINE_SCRAP.clone();
            io.github.steamwork.content.line.ProductionLineMember pressMember =
                    io.github.steamwork.content.line.ProductionLineMember.of(ironBlock);
            if (pressMember != null && pressMember.isInLine()) {
                scrap = io.github.steamwork.content.line.ProductionLineMember
                        .deliverToNextMember(ironBlock, pressMember, scrap);
            }
            if (!scrap.isEmpty()) {
                ironBlock.getWorld().dropItemNaturally(
                        ironBlock.getLocation().add(0.5, 0.5, 0.5), scrap);
            }
        }

        liftPiston();
        // Remove display entity only if the stack is now empty
        if (getDisplayItem() == null) {
            removeDisplayEntity();
        }
        resetRecipe();
    }

    private void handleInterruption(@NotNull SteamProcessRecipe recipe) {
        if (graceTicks <= 0) return;
        interruptionTicks += tickInterval;
        if (interruptionTicks <= graceTicks) return;

        recipeTicksRemaining += tickInterval;
        if (recipeTicksRemaining >= recipe.timeTicks()) {
            getBlock().getWorld().spawnParticle(Particle.SMOKE,
                    getBlock().getLocation().add(0.5, 1.0, 0.5), 10, 0.3, 0.2, 0.3, 0.05);
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.6f);
            dropScrap();
            resetRecipe();
            currentState = State.READY;
            interruptionTicks = 0;
        }
    }

    private void dropScrap() {
        Block ironBlock = getIronBlockPos();
        ItemStack scrap = SteamworkItems.MACHINE_SCRAP.clone();
        io.github.steamwork.content.line.ProductionLineMember pressMember =
                io.github.steamwork.content.line.ProductionLineMember.of(ironBlock);
        if (pressMember != null && pressMember.isInLine()) {
            scrap = io.github.steamwork.content.line.ProductionLineMember
                    .deliverToNextMember(ironBlock, pressMember, scrap);
        }
        if (!scrap.isEmpty()) {
            ironBlock.getWorld().dropItemNaturally(
                    ironBlock.getLocation().add(0.5, 1.0, 0.5), scrap);
        }
    }

    private void spawnFx(int count) {
        getBlock().getWorld().spawnParticle(
                Particle.SMOKE,
                getBlock().getLocation().add(0.5, 0.9, 0.5),
                count, 0.15, 0.05, 0.15, 0.01);
        if (Math.random() < 0.15) {
            getBlock().getWorld().playSound(getBlock().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.4f, 1.4f);
        }
    }

    @Override
    public void boostProcess(int ticks) {
        if (!isProcessing()) return;
        recipeTicksRemaining = Math.max(0, recipeTicksRemaining - ticks);
        if (recipeTicksRemaining <= 0) {
            SteamProcessRecipe recipe = getCurrentRecipe();
            if (recipe != null) {
                completeRecipe(recipe);
            }
        }
    }

    private void resetRecipe() {
        currentRecipeKey     = null;
        recipeTicksRemaining = 0;
        setActive(false);
    }

    // ===== Pylon 联动配方 =====

    @Nullable private List<SteamProcessRecipe> pylonRecipeCache = null;

    /**
     * 懒加载 Pylon 联动配方缓存。
     * 包含 FormingRecipe、PipeBendingRecipe、TableSawRecipe，
     * 分别对应蒸汽冲压机的三种 Pylon 工序：成形、弯管、锯切。
     */
    private @NotNull List<SteamProcessRecipe> getPylonRecipeCache() {
        if (pylonRecipeCache == null) {
            List<SteamProcessRecipe> list = new ArrayList<>();
            FormingRecipe.RECIPE_TYPE.getRecipes()
                    .forEach(r -> list.add(new FormingRecipeWrapper(r, 50.0, 160)));
            PipeBendingRecipe.RECIPE_TYPE.getRecipes()
                    .forEach(r -> list.add(new PipeBendingRecipeWrapper(r, 40.0, 140)));
            TableSawRecipe.RECIPE_TYPE.getRecipes()
                    .forEach(r -> list.add(new TableSawRecipeWrapper(r, 35.0, 120)));
            pylonRecipeCache = list;
        }
        return pylonRecipeCache;
    }

    private boolean isProcessing() {
        return currentRecipeKey != null && recipeTicksRemaining > 0;
    }

    @Nullable
    private SteamProcessRecipe getCurrentRecipe() {
        if (currentRecipeKey == null) return null;
        // 先查原生冲压配方
        SteamPressingRecipe native_ = SteamPressingRecipe.RECIPE_TYPE.getRecipe(currentRecipeKey);
        if (native_ != null) return native_;
        // 再查 Pylon 联动缓存（key 由 Pylon 方配方注册，命名空间不同，不会与原生配方冲突）
        return getPylonRecipeCache().stream()
                .filter(r -> currentRecipeKey.equals(r.getKey()))
                .findFirst().orElse(null);
    }

    private void setActive(boolean active) {
        if (lastActive != active) {
            lastActive = active;
            refreshBlockTextureItem();
            if (!active) {
                pistonPressed = false;
            }
        }
    }

    private void notifyGui() {
        statusItem.notifyWindows();
        steamItem.notifyWindows();
        progressItem.notifyWindows();
    }

    // ===== GUI (machine block right-click → status view) =====

    @Override
    public @NotNull Gui createGui() {
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# # # s # p # # #",
                        "# # # # a # # # #",
                        "# # # # # # # # #"
                )
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', steamItem)
                .addIngredient('p', progressItem)
                .addIngredient('a', statusItem)
                .build();
    }

    @Override
    public @NotNull Component getGuiTitle() {
        return noItalic(Component.translatable("steamwork.gui.steam_press.title"));
    }

    // ===== WAILA =====

    @Override
    public @NotNull Map<String, kotlin.Pair<String, Integer>> getBlockTextureProperties() {
        var props = super.getBlockTextureProperties();
        props.put("active", new kotlin.Pair<>(Boolean.toString(lastActive), 2));
        return props;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(this, player)
                .add(ProgressBar.fluidContentsWithName(SteamworkFluids.STEAM, fluidCapacity(SteamworkFluids.STEAM), fluidAmount(SteamworkFluids.STEAM)))
                .add(Component.translatable("steamwork.state." + currentState.key()));
    }

    // ===== GUI inner items =====

    private final class StatusItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            Material mat = switch (currentState) {
                case PROCESSING   -> Material.GREEN_STAINED_GLASS_PANE;
                case NO_STEAM, NO_STRUCTURE -> Material.RED_STAINED_GLASS_PANE;
                case NO_INGREDIENTS, READY  -> Material.GRAY_STAINED_GLASS_PANE;
            };
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_press.status."
                            + (lastActive ? "active" : "idle"))))
                    .lore(List.of(noItalic(Component.translatable(
                            "steamwork.gui.steam_press.reason." + currentState.key()))));
        }

        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private final class SteamGaugeItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            double steam = fluidAmount(SteamworkFluids.STEAM);
            double cap   = fluidCapacity(SteamworkFluids.STEAM);
            int pct = (int) Math.round(100.0 * steam / Math.max(1.0, cap));
            Material mat = pct >= 75 ? Material.LIGHT_BLUE_STAINED_GLASS
                    : pct >= 50 ? Material.CYAN_STAINED_GLASS
                    : pct >= 25 ? Material.BLUE_STAINED_GLASS
                    : Material.GRAY_STAINED_GLASS;
            return ItemStackBuilder.of(mat)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_press.steam_gauge")))
                    .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_press.steam",
                            RebarArgument.of("steam",    Component.text(String.format("%.0f", steam))),
                            RebarArgument.of("capacity", Component.text(String.format("%.0f", cap)))))));
        }

        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private final class ProgressItem extends AbstractItem {
        @Override
        public @NotNull ItemProvider getItemProvider(@NotNull Player viewer) {
            SteamProcessRecipe recipe = getCurrentRecipe();
            boolean processing = recipe != null && recipeTicksRemaining > 0;

            if (!processing) {
                return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(noItalic(Component.translatable("steamwork.gui.steam_press.progress")))
                        .lore(List.of(noItalic(Component.translatable("steamwork.gui.steam_processor.progress_idle"))));
            }

            int total     = Math.max(1, recipe.timeTicks());
            int remaining = Math.max(0, recipeTicksRemaining);
            int pct       = (int) Math.round(100.0 * (total - remaining) / total);
            boolean warn  = graceTicks > 0 && interruptionTicks > graceTicks;

            List<Component> lore = new ArrayList<>();
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_processor.progress_percent",
                    RebarArgument.of("percent", Component.text(pct + "%")))));
            lore.add(noItalic(Component.translatable("steamwork.gui.steam_processor.time_remaining",
                    RebarArgument.of("time", UnitFormat.formatDuration(
                            java.time.Duration.ofMillis(remaining * 50L), true, false)))));
            if (warn) {
                lore.add(noItalic(Component.translatable("steamwork.gui.steam_processor.interruption_warning")));
            }

            return ItemStackBuilder.of(warn ? Material.RED_STAINED_GLASS_PANE : Material.CLOCK)
                    .name(noItalic(Component.translatable("steamwork.gui.steam_press.progress")))
                    .lore(lore);
        }

        @Override
        public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {}
    }

    private static @NotNull Component noItalic(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    public static void refreshRecipeCache() {}
}
