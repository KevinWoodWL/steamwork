package io.github.steamwork.content.line;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Crafter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wraps vanilla crafters as production-line members.
 */
class VanillaCrafterMember implements ManualInteractMember {

    private static final int GRID_SIZE = 9;
    private static final int RETRY_INTERVAL_TICKS = 5;
    private static final long BUFFER_EXPIRY_MS = 60_000L;
    // 自动合成节流：每 20 tick（1 秒）触发一次
    private static final int AUTO_CRAFT_INTERVAL_TICKS = 20;
    private static final Map<BlockKey, BufferedOutput> BUFFERED_OUTPUTS = new HashMap<>();
    // 每个合成器独立的轮询游标：记录上次写入的槽位，下次从下一个开始
    private static final Map<BlockKey, Integer> ROUND_ROBIN_CURSOR = new HashMap<>();
    // 每个合成器独立的自动合成节流计数器
    private static final Map<BlockKey, Integer> AUTO_CRAFT_COOLDOWN = new HashMap<>();
    // 每个合成器缓存的匹配配方（避免每 tick 全量遍历 Bukkit.recipeIterator）
    // key = 合成器位置，value = 上次成功匹配的配方；格子内容变化时由 acceptFromLine 清除
    private static final Map<BlockKey, CraftingRecipe> CACHED_RECIPE = new HashMap<>();

    private final @NotNull Block block;

    VanillaCrafterMember(@NotNull Block block) {
        this.block = block;
    }

    public static boolean isVanillaCrafter(@NotNull Block block) {
        return block.getType() == Material.CRAFTER;
    }

    static void scheduleBufferDrain() {
        Bukkit.getScheduler().runTaskTimer(
                io.github.steamwork.Steamwork.getInstance(),
                VanillaCrafterMember::drainBufferedOutputs,
                RETRY_INTERVAL_TICKS,
                RETRY_INTERVAL_TICKS
        );
    }

    private @NotNull PersistentDataContainer readPdc() {
        return ((Container) block.getState()).getPersistentDataContainer();
    }

    @Override
    public @Nullable UUID getLineId() {
        String s = readPdc().get(LINE_ID_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public int getLinePosition() {
        return readPdc().getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public @NotNull BlockFace getLineDirection() {
        String s = readPdc().get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
        if (s == null) return BlockFace.SELF;
        try { return BlockFace.valueOf(s); } catch (IllegalArgumentException ignored) { return BlockFace.SELF; }
    }

    @Override
    public @Nullable String getLineCreator() {
        return readPdc().get(LINE_CREATOR_KEY, PersistentDataType.STRING);
    }

    @Override
    public int getLineNumber() {
        return readPdc().getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void joinLine(@NotNull UUID lineId, int position, @NotNull BlockFace direction) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        pdc.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
        pdc.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, position);
        pdc.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, direction.name());
        state.update(true, false);
    }

    @Override
    public void setLineCreator(@Nullable String creator) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        if (creator != null) pdc.set(LINE_CREATOR_KEY, PersistentDataType.STRING, creator);
        else pdc.remove(LINE_CREATOR_KEY);
        state.update(true, false);
    }

    @Override
    public void setLineNumber(int number) {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        if (number > 0) pdc.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, number);
        else pdc.remove(LINE_NUMBER_KEY);
        state.update(true, false);
    }

    @Override
    public void leaveLine() {
        var state = block.getState();
        PersistentDataContainer pdc = ((Container) state).getPersistentDataContainer();
        pdc.remove(LINE_ID_KEY);
        pdc.remove(LINE_POSITION_KEY);
        pdc.remove(LINE_DIRECTION_KEY);
        pdc.remove(LINE_CREATOR_KEY);
        pdc.remove(LINE_NUMBER_KEY);
        state.update(true, false);
        BUFFERED_OUTPUTS.remove(BlockKey.of(block));
        ROUND_ROBIN_CURSOR.remove(BlockKey.of(block));
        AUTO_CRAFT_COOLDOWN.remove(BlockKey.of(block));
        CACHED_RECIPE.remove(BlockKey.of(block));
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (!(block.getState() instanceof Crafter crafter)) return false;
        Inventory inventory = crafter.getInventory();
        int size = Math.min(GRID_SIZE, inventory.getSize());

        // 收集"可接受此物品"的候选槽位：已有相同物品且未满、非禁用
        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (crafter.isSlotDisabled(slot)) continue;
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.isEmpty()) continue;
            if (!current.isSimilar(item)) continue;
            if (current.getAmount() >= current.getMaxStackSize()) continue;
            candidates.add(slot);
        }

        if (candidates.isEmpty()) return false;

        // 轮询：从上次游标的下一个候选开始
        BlockKey key = BlockKey.of(block);
        int lastSlot = ROUND_ROBIN_CURSOR.getOrDefault(key, -1);
        int startIdx = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i) > lastSlot) { startIdx = i; break; }
            if (i == candidates.size() - 1) startIdx = 0; // 所有候选都 <= lastSlot，回绕
        }

        int targetSlot = candidates.get(startIdx);
        ItemStack current = inventory.getItem(targetSlot);
        // current 非 null、非空已在上方过滤保证
        assert current != null;
        current.setAmount(current.getAmount() + 1);
        inventory.setItem(targetSlot, current);
        ROUND_ROBIN_CURSOR.put(key, targetSlot);
        // 格子内容变化，清除配方缓存
        CACHED_RECIPE.remove(key);
        return true;
    }

    boolean tryCraftIntoLine(@NotNull CraftingRecipe recipe, @NotNull ItemStack result) {
        UUID lineId = getLineId();
        BlockFace direction = getLineDirection();
        if (lineId == null || direction == BlockFace.SELF || result.isEmpty()) return false;
        if (!(block.getState() instanceof Crafter crafter)) return false;

        Inventory inventory = crafter.getInventory();
        List<Integer> consumedSlots = planConsumedSlots(crafter, inventory, recipe);
        if (consumedSlots == null || consumedSlots.isEmpty()) return false;

        ItemStack[] nextGrid = copyGrid(inventory);
        List<ItemStack> remainders = new ArrayList<>();
        for (int slot : consumedSlots) {
            ItemStack stack = nextGrid[slot];
            if (stack == null || stack.isEmpty()) return false;
            ItemStack remainder = craftingRemainder(stack);
            if (remainder != null) remainders.add(remainder);

            if (stack.getAmount() <= 1) nextGrid[slot] = null;
            else nextGrid[slot] = stack.asQuantity(stack.getAmount() - 1);
        }
        if (!placeRemainders(crafter, nextGrid, remainders)) return false;

        for (int slot = 0; slot < nextGrid.length; slot++) {
            inventory.setItem(slot, nextGrid[slot]);
        }
        deliverOrBuffer(lineId, direction, result);
        return true;
    }

    /**
     * 自动合成：由自动生产模组每 tick 调用。
     * 条件：产线内所有参与配方的格子物品数量 > 1（即消耗后至少还剩 1 个，配方可持续执行）。
     * 节流：每 20 tick（1 秒）最多触发一次。
     */
    @Override
    public void performAutoInteract() {
        if (!isInLine()) return;
        BlockKey key = BlockKey.of(block);
        int cooldown = AUTO_CRAFT_COOLDOWN.getOrDefault(key, 0);
        if (cooldown > 0) {
            AUTO_CRAFT_COOLDOWN.put(key, cooldown - 1);
            return;
        }
        if (!(block.getState() instanceof Crafter crafter)) return;
        Inventory inventory = crafter.getInventory();

        // 优先尝试缓存的配方，避免每 tick 全量遍历 Bukkit.recipeIterator()
        CraftingRecipe cached = CACHED_RECIPE.get(key);
        if (cached != null && tryWithRecipe(crafter, inventory, cached, key)) return;

        // 缓存未命中或失效，全量扫描一次并缓存结果
        for (java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
            org.bukkit.inventory.Recipe r = it.next();
            if (!(r instanceof CraftingRecipe recipe)) continue;
            if (tryWithRecipe(crafter, inventory, recipe, key)) {
                CACHED_RECIPE.put(key, recipe);
                return;
            }
        }
        // 没有匹配配方，清除缓存
        CACHED_RECIPE.remove(key);
    }

    private boolean tryWithRecipe(@NotNull Crafter crafter, @NotNull Inventory inventory,
                                   @NotNull CraftingRecipe recipe, @NotNull BlockKey key) {
        List<Integer> consumedSlots = planConsumedSlots(crafter, inventory, recipe);
        if (consumedSlots == null || consumedSlots.isEmpty()) return false;

        for (int slot : consumedSlots) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getAmount() <= 1) return false;
        }

        ItemStack result = recipe.getResult();
        if (result.isEmpty()) return false;

        if (tryCraftIntoLine(recipe, result)) {
            AUTO_CRAFT_COOLDOWN.put(key, AUTO_CRAFT_INTERVAL_TICKS);
            org.bukkit.Location center = block.getLocation().toCenterLocation().add(0, 0.5, 0);
            block.getWorld().playSound(center, org.bukkit.Sound.UI_BUTTON_CLICK,
                    org.bukkit.SoundCategory.BLOCKS, 0.5f, 1.0f);
            block.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, center,
                    8, 0.2, 0.15, 0.2, 0.02);
            return true;
        }
        return false;
    }

    private @Nullable List<Integer> planConsumedSlots(
            @NotNull Crafter crafter,
            @NotNull Inventory inventory,
            @NotNull CraftingRecipe recipe
    ) {
        if (recipe instanceof ShapedRecipe shaped) {
            return planShaped(crafter, inventory, shaped);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return planChoices(crafter, inventory, shapeless.getChoiceList());
        }
        if (recipe instanceof TransmuteRecipe transmute) {
            return planChoices(crafter, inventory, List.of(transmute.getInput(), transmute.getMaterial()));
        }
        return null;
    }

    private @Nullable List<Integer> planShaped(
            @NotNull Crafter crafter,
            @NotNull Inventory inventory,
            @NotNull ShapedRecipe recipe
    ) {
        String[] shape = recipe.getShape();
        Map<Character, RecipeChoice> choiceMap = recipe.getChoiceMap();
        int width = shapeWidth(shape);

        for (int yOffset = 0; yOffset <= 3 - shape.length; yOffset++) {
            for (int xOffset = 0; xOffset <= 3 - width; xOffset++) {
                List<Integer> consumed = new ArrayList<>();
                boolean matches = true;
                for (int y = 0; y < 3 && matches; y++) {
                    for (int x = 0; x < 3; x++) {
                        int slot = y * 3 + x;
                        RecipeChoice choice = choiceAt(shape, choiceMap, x - xOffset, y - yOffset);
                        ItemStack stack = usableSlot(crafter, inventory, slot) ? inventory.getItem(slot) : null;
                        boolean empty = stack == null || stack.isEmpty();
                        if (choice == null) {
                            if (!empty) matches = false;
                        } else if (empty || !choice.test(stack)) {
                            matches = false;
                        } else {
                            consumed.add(slot);
                        }
                    }
                }
                if (matches) return consumed;
            }
        }
        return null;
    }

    private @Nullable List<Integer> planChoices(
            @NotNull Crafter crafter,
            @NotNull Inventory inventory,
            @NotNull List<RecipeChoice> choices
    ) {
        List<RecipeChoice> remaining = new ArrayList<>();
        for (RecipeChoice choice : choices) remaining.add(choice.clone());
        List<Integer> consumed = new ArrayList<>();

        for (int slot = 0; slot < Math.min(GRID_SIZE, inventory.getSize()); slot++) {
            if (!usableSlot(crafter, inventory, slot)) continue;
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) continue;

            boolean matched = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).test(stack)) {
                    remaining.remove(i);
                    consumed.add(slot);
                    matched = true;
                    break;
                }
            }
            if (!matched) return null;
        }
        return remaining.isEmpty() ? consumed : null;
    }

    private boolean usableSlot(@NotNull Crafter crafter, @NotNull Inventory inventory, int slot) {
        return slot >= 0 && slot < Math.min(GRID_SIZE, inventory.getSize()) && !crafter.isSlotDisabled(slot);
    }

    private int shapeWidth(@NotNull String[] shape) {
        int width = 0;
        for (String row : shape) width = Math.max(width, row.length());
        return width;
    }

    private @Nullable RecipeChoice choiceAt(
            @NotNull String[] shape,
            @NotNull Map<Character, RecipeChoice> choiceMap,
            int x,
            int y
    ) {
        if (y < 0 || y >= shape.length) return null;
        String row = shape[y];
        if (x < 0 || x >= row.length()) return null;
        char symbol = row.charAt(x);
        if (symbol == ' ') return null;
        return choiceMap.get(symbol);
    }

    private @NotNull ItemStack[] copyGrid(@NotNull Inventory inventory) {
        ItemStack[] copy = new ItemStack[Math.min(GRID_SIZE, inventory.getSize())];
        for (int slot = 0; slot < copy.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            copy[slot] = stack == null || stack.isEmpty() ? null : stack.clone();
        }
        return copy;
    }

    private @Nullable ItemStack craftingRemainder(@NotNull ItemStack stack) {
        Material remainder = stack.getType().getCraftingRemainingItem();
        return remainder == null || remainder.isAir() ? null : ItemStack.of(remainder);
    }

    private boolean placeRemainders(
            @NotNull Crafter crafter,
            @NotNull ItemStack[] grid,
            @NotNull List<ItemStack> remainders
    ) {
        for (ItemStack remainder : remainders) {
            if (!placeOneRemainder(crafter, grid, remainder)) return false;
        }
        return true;
    }

    private boolean placeOneRemainder(
            @NotNull Crafter crafter,
            @NotNull ItemStack[] grid,
            @NotNull ItemStack remainder
    ) {
        for (int slot = 0; slot < grid.length; slot++) {
            if (crafter.isSlotDisabled(slot)) continue;
            ItemStack current = grid[slot];
            if (current != null && !current.isEmpty()
                    && current.isSimilar(remainder)
                    && current.getAmount() < current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + 1);
                return true;
            }
        }
        for (int slot = 0; slot < grid.length; slot++) {
            if (crafter.isSlotDisabled(slot)) continue;
            ItemStack current = grid[slot];
            if (current == null || current.isEmpty()) {
                grid[slot] = remainder.asQuantity(1);
                return true;
            }
        }
        return false;
    }

    private void deliverOrBuffer(@NotNull UUID lineId, @NotNull BlockFace direction, @NotNull ItemStack result) {
        ItemStack remaining = result.clone();
        ProductionLineMember downstream = findNextMember(block, direction, lineId);
        if (downstream != null) {
            int delivered = 0;
            while (delivered < remaining.getAmount()) {
                if (!ProductionLineMember.acceptIntoLine(downstream, remaining.asQuantity(1))) break;
                delivered++;
            }
            remaining.setAmount(remaining.getAmount() - delivered);
        }
        if (remaining.getAmount() > 0) {
            enqueueOutput(BlockKey.of(block), lineId, direction, remaining);
        }
    }

    private static void enqueueOutput(
            @NotNull BlockKey key,
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull ItemStack item
    ) {
        long expiresAt = System.currentTimeMillis() + BUFFER_EXPIRY_MS;
        BufferedOutput existing = BUFFERED_OUTPUTS.get(key);
        if (existing == null) {
            Deque<ItemStack> queue = new ArrayDeque<>();
            queue.add(item.clone());
            BUFFERED_OUTPUTS.put(key, new BufferedOutput(lineId, direction, queue, expiresAt));
        } else {
            existing.items().add(item.clone());
            BUFFERED_OUTPUTS.put(key, new BufferedOutput(existing.lineId(), existing.direction(), existing.items(), expiresAt));
        }
    }

    static void drainBufferedOutputs() {
        if (BUFFERED_OUTPUTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        var iterator = BUFFERED_OUTPUTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, BufferedOutput> entry = iterator.next();
            BufferedOutput output = entry.getValue();
            if (output.expiresAt() < now) {
                iterator.remove();
                continue;
            }

            Block source = entry.getKey().toBlock();
            if (source == null) { iterator.remove(); continue; }
            ProductionLineMember member = ProductionLineMember.of(source);
            if (member == null || !output.lineId().equals(member.getLineId())) {
                iterator.remove();
                continue;
            }

            ProductionLineMember downstream = findNextMember(source, output.direction(), output.lineId());
            if (downstream == null) continue;

            Deque<ItemStack> queue = output.items();
            while (!queue.isEmpty()) {
                ItemStack head = queue.peek();
                int delivered = 0;
                int total = head.getAmount();
                while (delivered < total) {
                    if (!ProductionLineMember.acceptIntoLine(downstream, head.asQuantity(1))) break;
                    delivered++;
                }
                head.setAmount(head.getAmount() - delivered);
                if (head.getAmount() <= 0) queue.poll();
                else break;
            }
            if (queue.isEmpty()) iterator.remove();
        }
    }

    private static @Nullable ProductionLineMember findNextMember(
            @NotNull Block source,
            @NotNull BlockFace direction,
            @NotNull UUID lineId
    ) {
        for (int i = 1; i <= DISBAND_MAX_GAP + 1; i++) {
            ProductionLineMember member = ProductionLineMember.of(source.getRelative(direction, i));
            if (member == null) continue;
            return lineId.equals(member.getLineId()) ? member : null;
        }
        return null;
    }

    private record BufferedOutput(
            @NotNull UUID lineId,
            @NotNull BlockFace direction,
            @NotNull Deque<ItemStack> items,
            long expiresAt
    ) {}

    private record BlockKey(@NotNull UUID worldId, int x, int y, int z) {
        static @NotNull BlockKey of(@NotNull Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        @Nullable Block toBlock() {
            World world = Bukkit.getWorld(worldId);
            return world != null ? world.getBlockAt(x, y, z) : null;
        }
    }
}
