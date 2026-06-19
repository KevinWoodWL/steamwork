package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.steamwork.SteamworkFluids;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

public class BronzeBoiler extends AbstractSteamBoiler {

    public static class Item extends AbstractSteamBoiler.Item {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }
    }

    @SuppressWarnings("unused")
    public BronzeBoiler(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public BronzeBoiler(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull MultiblockComponent casingComponent() {
        return MultiblockComponent.of(PylonKeys.BRONZE_BLOCK);
    }

    @Override
    protected @NotNull MultiblockComponent capComponent() {
        return MultiblockComponent.of(PylonKeys.BRONZE_GRATING);
    }

    @Override
    protected @NotNull RebarFluid producedSteam() {
        return SteamworkFluids.STEAM;
    }
}
