package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.base.RebarSimpleMultiblock.MultiblockComponent;
import io.github.pylonmc.rebar.block.context.BlockCreateContext;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkKeys;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

public class TungstenBoiler extends AbstractSteamBoiler {

    public static class Item extends AbstractSteamBoiler.Item {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }
    }

    @SuppressWarnings("unused")
    public TungstenBoiler(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public TungstenBoiler(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull MultiblockComponent casingComponent() {
        return MultiblockComponent.of(SteamworkKeys.TUNGSTEN_BLOCK);
    }

    @Override
    protected @NotNull MultiblockComponent capComponent() {
        return MultiblockComponent.of(PylonKeys.REFRACTORY_BRICKS);
    }

    @Override
    protected @NotNull RebarFluid producedSteam() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    @Override
    protected @NotNull TextColor steamBarColor() {
        return TextColor.fromHexString("#ff4500");
    }

    @Override
    protected void spawnRunningFx() {
        Block b = getBlock();
        // 岩浆粒子 + 浓烟，体现极高温过热蒸汽
        b.getWorld().spawnParticle(Particle.LAVA,
                b.getLocation().add(0.5, 1.05, 0.5),
                3, 0.2, 0.05, 0.2, 0.0);
        b.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                b.getLocation().add(0.5, 1.15, 0.5),
                4, 0.2, 0.08, 0.2, 0.02);
        if (Math.random() < 0.4) {
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.4f, 0.5f);
        }
    }
}
