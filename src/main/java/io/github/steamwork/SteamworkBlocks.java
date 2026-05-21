package io.github.steamwork;

import io.github.steamwork.content.machines.BronzeBoiler;
import io.github.steamwork.content.machines.InvarBoiler;
import io.github.steamwork.content.machines.ManganeseSteelBoiler;
import io.github.steamwork.content.machines.SteamArm;
import io.github.steamwork.content.machines.SteamAssemblyBench;
import io.github.steamwork.content.machines.SteamDistillationTower;
import io.github.steamwork.content.machines.SteamGrinder;
import io.github.steamwork.content.machines.SteamHeatingChamber;
import io.github.steamwork.content.machines.SteamPress;
import io.github.steamwork.content.machines.SteamPressurizedFurnace;
import io.github.steamwork.content.machines.SteamScienceInterface;
import io.github.steamwork.content.machines.SteamSteepingVat;
import io.github.steamwork.content.machines.SteamSterilizer;
import io.github.steamwork.content.machines.SimpleSteamTurbine;
import io.github.steamwork.content.machines.AdvancedSteamTurbine;
import io.github.steamwork.content.machines.SteamWashingTrough;
import io.github.steamwork.content.machines.TungstenBoiler;
import io.github.pylonmc.rebar.block.RebarBlock;
import org.bukkit.Material;

public final class SteamworkBlocks {

    private SteamworkBlocks() {
        throw new AssertionError("Utility class");
    }

    public static void initialize() {
        RebarBlock.register(SteamworkKeys.BRONZE_BOILER, Material.COPPER_BLOCK, BronzeBoiler.class);
        RebarBlock.register(SteamworkKeys.INVAR_BOILER, Material.IRON_BLOCK, InvarBoiler.class);
        RebarBlock.register(SteamworkKeys.MANGANESE_STEEL_BOILER, Material.IRON_BLOCK, ManganeseSteelBoiler.class);
        RebarBlock.register(SteamworkKeys.TUNGSTEN_BOILER, Material.NETHERITE_BLOCK, TungstenBoiler.class);
        RebarBlock.register(SteamworkKeys.STEAM_ARM, Material.COPPER_BLOCK, SteamArm.class);
        RebarBlock.register(SteamworkKeys.SIMPLE_STEAM_TURBINE, Material.FURNACE, SimpleSteamTurbine.class);
        RebarBlock.register(SteamworkKeys.ADVANCED_STEAM_TURBINE, Material.BLAST_FURNACE, AdvancedSteamTurbine.class);
        RebarBlock.register(SteamworkKeys.STEAM_STERILIZER, Material.BARREL, SteamSterilizer.class);
        RebarBlock.register(SteamworkKeys.STEAM_STEEPING_VAT, Material.CAULDRON, SteamSteepingVat.class);
        RebarBlock.register(SteamworkKeys.STEAM_WASHING_TROUGH, Material.CAULDRON, SteamWashingTrough.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRESS, Material.IRON_BLOCK, SteamPress.class);
        RebarBlock.register(SteamworkKeys.STEAM_GRINDER, Material.GRINDSTONE, SteamGrinder.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRESSURIZED_FURNACE, Material.CUT_COPPER_SLAB, SteamPressurizedFurnace.class);
        RebarBlock.register(SteamworkKeys.STEAM_ASSEMBLY_BENCH, Material.SMITHING_TABLE, SteamAssemblyBench.class);
        RebarBlock.register(SteamworkKeys.STEAM_SCIENCE_INTERFACE, Material.LECTERN, SteamScienceInterface.class);
        RebarBlock.register(SteamworkKeys.STEAM_HEATING_CHAMBER, Material.BLAST_FURNACE, SteamHeatingChamber.class);
        RebarBlock.register(SteamworkKeys.STEAM_DISTILLATION_TOWER, Material.CAULDRON, SteamDistillationTower.class);
        RebarBlock.register(SteamworkKeys.DISTILLATION_TOWER_SECTION, Material.LIGHT_GRAY_STAINED_GLASS, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.DISTILLATION_CONDENSER, Material.CUT_COPPER, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.INVAR_BLOCK, Material.IRON_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.DURALUMIN_BLOCK, Material.COPPER_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.TUNGSTEN_BLOCK, Material.NETHERITE_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.MANGANESE_STEEL_BLOCK, Material.IRON_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.MANGANESE_BRONZE_BLOCK, Material.COPPER_BLOCK, RebarBlock.class);
    }
}
