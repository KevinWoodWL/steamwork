package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkKeys;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

public class InvarBoiler extends AbstractSteamBoiler {

    public static class Item extends AbstractSteamBoiler.Item {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }
    }

    @SuppressWarnings("unused")
    public InvarBoiler(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public InvarBoiler(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull MultiblockComponent casingComponent() {
        return MultiblockComponent.of(SteamworkKeys.INVAR_BLOCK);
    }

    @Override
    protected @NotNull MultiblockComponent capComponent() {
        return MultiblockComponent.of(PylonKeys.IRON_SUPPORT_BEAM);
    }

    @Override
    protected @NotNull RebarFluid producedSteam() {
        return SteamworkFluids.STEAM;
    }

    @Override
    protected void spawnRunningFx() {
        Block b = getBlock();
        // 细密白烟：比青铜锅炉的泄漏烟更小更稳定，体现高效密封
        b.getWorld().spawnParticle(Particle.CLOUD,
                b.getLocation().add(0.5, 1.05, 0.5),
                4, 0.15, 0.05, 0.15, 0.02);
        if (Math.random() < 0.25) {
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.2f, 1.8f);
        }
    }
}
