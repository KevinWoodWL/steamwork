package io.github.steamwork.content.line;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.pylon.content.machines.simple.MixingPot;
import io.github.pylonmc.pylon.recipes.MixingPotRecipe;
import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.recipe.FluidOrItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Wraps Pylon's Mixing Pot as a production-line member.
 *
 * <p>The pot itself has no inventory; Pylon recipes consume item entities sitting in the pot.
 * The line therefore inserts items as centered drops and captures item results through
 * {@link PylonLineOutputBridge}. Fluid results remain inside the pot and are not part of the
 * item production line.</p>
 */
class PylonMixingPotMember implements ProductionLineMember, ManualInteractMember {

    private static final int MAX_INPUT_STACKS = 7;
    private static final double INPUT_RADIUS_XZ = 0.5;
    private static final double INPUT_RADIUS_Y = 0.8;

    private final @NotNull Block block;
    private final @NotNull MixingPot mixingPot;

    PylonMixingPotMember(@NotNull Block block, @NotNull MixingPot mixingPot) {
        this.block = block;
        this.mixingPot = mixingPot;
    }

    static boolean isPylonMixingPot(@NotNull RebarBlock rb) {
        return rb instanceof MixingPot;
    }

    private @NotNull NamespacedKey posKey() {
        return new NamespacedKey("steamwork",
                "plm_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    private @NotNull PersistentDataContainer readContainer() {
        PersistentDataContainer chunkPdc = block.getChunk().getPersistentDataContainer();
        PersistentDataContainer existing = chunkPdc.get(posKey(), PersistentDataType.TAG_CONTAINER);
        return existing != null ? existing : chunkPdc.getAdapterContext().newPersistentDataContainer();
    }

    private void writeContainer(@NotNull PersistentDataContainer container) {
        block.getChunk().getPersistentDataContainer().set(posKey(), PersistentDataType.TAG_CONTAINER, container);
    }

    private void removeContainer() {
        block.getChunk().getPersistentDataContainer().remove(posKey());
    }

    @Override
    public @Nullable UUID getLineId() {
        String s = readContainer().get(LINE_ID_KEY, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public int getLinePosition() {
        return readContainer().getOrDefault(LINE_POSITION_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public @NotNull BlockFace getLineDirection() {
        String s = readContainer().get(LINE_DIRECTION_KEY, PersistentDataType.STRING);
        if (s == null) return BlockFace.SELF;
        try { return BlockFace.valueOf(s); } catch (IllegalArgumentException ignored) { return BlockFace.SELF; }
    }

    @Override
    public @Nullable String getLineCreator() {
        return readContainer().get(LINE_CREATOR_KEY, PersistentDataType.STRING);
    }

    @Override
    public int getLineNumber() {
        return readContainer().getOrDefault(LINE_NUMBER_KEY, PersistentDataType.INTEGER, 0);
    }

    @Override
    public void joinLine(@NotNull UUID lineId, int position, @NotNull BlockFace direction) {
        PersistentDataContainer c = readContainer();
        c.set(LINE_ID_KEY, PersistentDataType.STRING, lineId.toString());
        c.set(LINE_POSITION_KEY, PersistentDataType.INTEGER, position);
        c.set(LINE_DIRECTION_KEY, PersistentDataType.STRING, direction.name());
        writeContainer(c);
    }

    @Override
    public void setLineCreator(@Nullable String creator) {
        PersistentDataContainer c = readContainer();
        if (creator != null) c.set(LINE_CREATOR_KEY, PersistentDataType.STRING, creator);
        else c.remove(LINE_CREATOR_KEY);
        writeContainer(c);
    }

    @Override
    public void setLineNumber(int number) {
        PersistentDataContainer c = readContainer();
        if (number > 0) c.set(LINE_NUMBER_KEY, PersistentDataType.INTEGER, number);
        else c.remove(LINE_NUMBER_KEY);
        writeContainer(c);
    }

    @Override
    public void leaveLine() {
        removeContainer();
        PylonLineOutputBridge.clearSource(block);
    }

    @Override
    public boolean acceptFromLine(@NotNull ItemStack item) {
        if (!isInLine() || PylonLineOutputBridge.hasPendingOrBufferedOutput(block)) return false;

        ItemStack single = item.asQuantity(1);
        List<Item> nearby = nearbyItems();

        for (Item entity : nearby) {
            ItemStack stack = entity.getItemStack();
            if (stack.isEmpty() || !stack.isSimilar(single) || stack.getAmount() >= stack.getMaxStackSize()) {
                continue;
            }
            stack.setAmount(stack.getAmount() + 1);
            entity.setItemStack(stack);
            entity.setPickupDelay(40);
            entity.setVelocity(new Vector(0.0, 0.0, 0.0));
            return true;
        }

        if (nearby.size() >= MAX_INPUT_STACKS) return false;

        Location dropLocation = block.getLocation().toCenterLocation().add(0.0, 0.35, 0.0);
        Item entity = block.getWorld().dropItem(dropLocation, single);
        entity.setVelocity(new Vector(0.0, 0.0, 0.0));
        entity.setPickupDelay(40);
        return true;
    }

    @Override
    public void performAutoInteract() {
        if (!isInLine() || getLineDirection() == BlockFace.SELF) return;
        if (PylonLineOutputBridge.hasPendingOrBufferedOutput(block)) return;

        MixingPotRecipe recipe = findNextRecipe();
        if (recipe == null) return;

        if (recipe.output() instanceof FluidOrItem.Item) {
            PylonLineOutputBridge.expectOutput(block, recipe);
            if (!mixingPot.tryDoRecipe()) {
                PylonLineOutputBridge.cancelExpectedOutput(block);
            }
            return;
        }

        // Fluid outputs stay in the pot; the item line has no downstream product to capture.
        mixingPot.tryDoRecipe();
    }

    private @Nullable MixingPotRecipe findNextRecipe() {
        if (mixingPot.getFluidType() == null) return null;

        Material fireType = mixingPot.getFire().getType();
        boolean isFire = fireType == Material.FIRE || fireType == Material.SOUL_FIRE;
        if (!isFire) return null;

        RebarBlock ignitedBlock = BlockStorage.get(mixingPot.getIgnitedBlock());
        boolean isEnrichedFire = ignitedBlock != null
                && ignitedBlock.getSchema().getKey().equals(PylonKeys.ENRICHED_SOUL_SOIL);

        List<ItemStack> stacks = nearbyItems().stream()
                .map(Item::getItemStack)
                .toList();

        for (MixingPotRecipe recipe : MixingPotRecipe.RECIPE_TYPE.getRecipes()) {
            if (recipe.matches(stacks, isEnrichedFire, mixingPot.getFluidType(), mixingPot.getFluidAmount())) {
                return recipe;
            }
        }
        return null;
    }

    private @NotNull List<Item> nearbyItems() {
        return block.getLocation()
                .toCenterLocation()
                .getNearbyEntitiesByType(Item.class, INPUT_RADIUS_XZ, INPUT_RADIUS_Y, INPUT_RADIUS_XZ)
                .stream()
                .filter(Item::isValid)
                .toList();
    }
}
