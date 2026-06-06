package io.github.steamwork.content.machines;

import io.github.pylonmc.pylon.PylonKeys;
import io.github.pylonmc.rebar.block.interfaces.SimpleRebarMultiblock.MultiblockComponent;
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

public class ManganeseSteelBoiler extends AbstractSteamBoiler {

    public static class Item extends AbstractSteamBoiler.Item {
        public Item(@NotNull ItemStack stack) {
            super(stack);
        }
    }

    @SuppressWarnings("unused")
    public ManganeseSteelBoiler(@NotNull Block block, @NotNull BlockCreateContext context) {
        super(block, context);
    }

    @SuppressWarnings("unused")
    public ManganeseSteelBoiler(@NotNull Block block, @NotNull PersistentDataContainer pdc) {
        super(block, pdc);
    }

    @Override
    protected @NotNull MultiblockComponent casingComponent() {
        return MultiblockComponent.of(SteamworkKeys.MANGANESE_STEEL_BLOCK);
    }

    @Override
    protected @NotNull MultiblockComponent capComponent() {
        return MultiblockComponent.of(PylonKeys.STEEL_SUPPORT_BEAM);
    }

    @Override
    protected @NotNull RebarFluid producedSteam() {
        return SteamworkFluids.SUPERHEATED_STEAM;
    }

    @Override
    protected @NotNull TextColor steamBarColor() {
        return TextColor.fromHexString("#ff8c00");
    }

    @Override
    protected void spawnRunningFx() {
        Block b = getBlock();
        // 橙色火焰粒子 + 少量烟雾，体现过热蒸汽的高温
        b.getWorld().spawnParticle(Particle.FLAME,
                b.getLocation().add(0.5, 1.1, 0.5),
                6, 0.2, 0.1, 0.2, 0.03);
        b.getWorld().spawnParticle(Particle.SMOKE,
                b.getLocation().add(0.5, 1.2, 0.5),
                3, 0.15, 0.05, 0.15, 0.02);
        if (Math.random() < 0.3) {
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 0.7f);
        }
    }
}
