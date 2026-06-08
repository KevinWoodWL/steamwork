package io.github.steamwork;

import io.github.steamwork.content.machines.BronzeBoiler;
import io.github.steamwork.content.machines.InvarBoiler;
import io.github.steamwork.content.machines.ManganeseSteelBoiler;
import io.github.steamwork.content.machines.PrecisionCatalyticReactor;
import io.github.steamwork.content.machines.PrecisionFoundry;
import io.github.steamwork.content.machines.HeavyImpactCrusher;
import io.github.steamwork.content.machines.HydraulicForge;
import io.github.steamwork.content.machines.PrecisionCrystallizer;
import io.github.steamwork.content.machines.PrecisionCentrifuge;
import io.github.steamwork.content.machines.SteamArm;
import io.github.steamwork.content.machines.SteamAssemblyBench;
import io.github.steamwork.content.machines.PneumaticCargoHub;
import io.github.steamwork.content.machines.PneumaticDistributor;
import io.github.steamwork.content.machines.PneumaticInput;
import io.github.steamwork.content.machines.PneumaticOutput;
import io.github.steamwork.content.machines.SteamCatapult;
import io.github.steamwork.content.machines.SteamCompressor;
import io.github.steamwork.content.machines.SteamDistillationTower;
import io.github.steamwork.content.machines.PneumaticDuct;
import io.github.steamwork.content.machines.PneumaticGateValve;
import io.github.steamwork.content.machines.SteamSorter;
import io.github.steamwork.content.machines.SteamGrinder;
import io.github.steamwork.content.machines.SteamCanisterBench;
import io.github.steamwork.content.machines.SteamChargingChamber;
import io.github.steamwork.content.machines.SteamHeatingChamber;
import io.github.steamwork.content.machines.SteamOscillator;
import io.github.steamwork.content.machines.SteamPressureTransducer;
import io.github.steamwork.content.machines.SteamDifferenceEngine;
import io.github.steamwork.content.machines.PneumaticPressureModule;
import io.github.steamwork.content.machines.SteamPrecisionMill;
import io.github.steamwork.content.machines.SteamPress;
import io.github.steamwork.content.machines.SteamPressurizedFurnace;
import io.github.steamwork.content.machines.SteamScienceInterface;
import io.github.steamwork.content.machines.SteamSteepingVat;
import io.github.steamwork.content.machines.SteamSterilizer;
import io.github.steamwork.content.machines.SimpleSteamTurbine;
import io.github.steamwork.content.machines.BasicProcessingTurbine;
import io.github.steamwork.content.machines.HydraulicTurbine;
import io.github.steamwork.content.machines.PrecisionSteamTurbine;
import io.github.steamwork.content.machines.PrecisionProcessingTurbine;
import io.github.steamwork.content.machines.DieselTurbine;
import io.github.steamwork.content.machines.PylonUniversalTurbine;
import io.github.steamwork.content.machines.SteamWashingTrough;
import io.github.steamwork.content.machines.TungstenBoiler;
import io.github.steamwork.content.line.ProductionLineInlet;
import io.github.steamwork.content.line.ProductionLineBufferChest;
import io.github.steamwork.content.line.ProductionLineOutlet;
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
        RebarBlock.register(SteamworkKeys.SIMPLE_STEAM_TURBINE,         Material.FURNACE,       SimpleSteamTurbine.class);
        RebarBlock.register(SteamworkKeys.BASIC_PROCESSING_TURBINE,    Material.BLAST_FURNACE, BasicProcessingTurbine.class);
        RebarBlock.register(SteamworkKeys.HYDRAULIC_TURBINE,           Material.BLAST_FURNACE, HydraulicTurbine.class);
        RebarBlock.register(SteamworkKeys.PRECISION_STEAM_TURBINE,     Material.BLAST_FURNACE, PrecisionSteamTurbine.class);
        RebarBlock.register(SteamworkKeys.PRECISION_PROCESSING_TURBINE, Material.BLAST_FURNACE, PrecisionProcessingTurbine.class);
        RebarBlock.register(SteamworkKeys.DIESEL_TURBINE,              Material.BLAST_FURNACE, DieselTurbine.class);
        RebarBlock.register(SteamworkKeys.PYLON_UNIVERSAL_TURBINE,     Material.BLAST_FURNACE, PylonUniversalTurbine.class);
        RebarBlock.register(SteamworkKeys.STEAM_STERILIZER, Material.BARREL, SteamSterilizer.class);
        RebarBlock.register(SteamworkKeys.STEAM_STEEPING_VAT, Material.CAULDRON, SteamSteepingVat.class);
        RebarBlock.register(SteamworkKeys.STEAM_WASHING_TROUGH, Material.CAULDRON, SteamWashingTrough.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRESS, Material.IRON_BLOCK, SteamPress.class);
        RebarBlock.register(SteamworkKeys.STEAM_GRINDER, Material.GRINDSTONE, SteamGrinder.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRECISION_MILL, Material.GRINDSTONE, SteamPrecisionMill.class);
        RebarBlock.register(SteamworkKeys.PRECISION_FOUNDRY, Material.BLAST_FURNACE, PrecisionFoundry.class);
        RebarBlock.register(SteamworkKeys.PRECISION_CATALYTIC_REACTOR, Material.BREWING_STAND, PrecisionCatalyticReactor.class);
        RebarBlock.register(SteamworkKeys.HEAVY_IMPACT_CRUSHER, Material.ANVIL, HeavyImpactCrusher.class);
        RebarBlock.register(SteamworkKeys.HYDRAULIC_FORGE, Material.SMITHING_TABLE, HydraulicForge.class);
        RebarBlock.register(SteamworkKeys.PRECISION_CRYSTALLIZER, Material.AMETHYST_BLOCK, PrecisionCrystallizer.class);
        RebarBlock.register(SteamworkKeys.PRECISION_CENTRIFUGE, Material.REINFORCED_DEEPSLATE, PrecisionCentrifuge.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRESSURIZED_FURNACE, Material.BLAST_FURNACE, SteamPressurizedFurnace.class);
        RebarBlock.register(SteamworkKeys.STEAM_ASSEMBLY_BENCH, Material.SMITHING_TABLE, SteamAssemblyBench.class);
        RebarBlock.register(SteamworkKeys.STEAM_SCIENCE_INTERFACE, Material.LECTERN, SteamScienceInterface.class);
        RebarBlock.register(SteamworkKeys.STEAM_HEATING_CHAMBER, Material.BLAST_FURNACE, SteamHeatingChamber.class);
        RebarBlock.register(SteamworkKeys.STEAM_CANISTER_BENCH, Material.GRINDSTONE, SteamCanisterBench.class);
        RebarBlock.register(SteamworkKeys.STEAM_CHARGING_CHAMBER, Material.LODESTONE, SteamChargingChamber.class);
        RebarBlock.register(SteamworkKeys.STEAM_COMPRESSOR, Material.BLAST_FURNACE, SteamCompressor.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_CARGO_HUB, Material.DISPENSER, PneumaticCargoHub.class);
        RebarBlock.register(SteamworkKeys.STEAM_CATAPULT, Material.CUT_COPPER_SLAB, SteamCatapult.class);
        RebarBlock.register(SteamworkKeys.STEAM_SORTER, Material.DROPPER, SteamSorter.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_DUCT, Material.STRUCTURE_VOID, PneumaticDuct.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_INPUT, Material.STRUCTURE_VOID, PneumaticInput.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_OUTPUT, Material.STRUCTURE_VOID, PneumaticOutput.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_DISTRIBUTOR, Material.DISPENSER, PneumaticDistributor.class);
        RebarBlock.register(SteamworkKeys.PRODUCTION_LINE_INLET, Material.DISPENSER, ProductionLineInlet.class);
        RebarBlock.register(SteamworkKeys.PRODUCTION_LINE_BUFFER_CHEST, Material.CHEST, ProductionLineBufferChest.class);
        RebarBlock.register(SteamworkKeys.PRODUCTION_LINE_OUTLET, Material.DROPPER, ProductionLineOutlet.class);
        RebarBlock.register(SteamworkKeys.STEAM_DISTILLATION_TOWER, Material.CAULDRON, SteamDistillationTower.class);
        RebarBlock.register(SteamworkKeys.DISTILLATION_TOWER_SECTION, Material.LIGHT_GRAY_STAINED_GLASS, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.DISTILLATION_CONDENSER, Material.CUT_COPPER, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.INVAR_BLOCK, Material.IRON_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.DURALUMIN_BLOCK, Material.COPPER_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.TUNGSTEN_BLOCK, Material.NETHERITE_BLOCK, RebarBlock.class);
        RebarBlock.register(SteamworkKeys.MANGANESE_STEEL_BLOCK, Material.IRON_BLOCK,
                io.github.steamwork.content.machines.AssemblyPedestal.class);
        RebarBlock.register(SteamworkKeys.MANGANESE_BRONZE_BLOCK, Material.COPPER_BLOCK, RebarBlock.class);
        // 汽动逻辑（PneumaticCraft 灵感）
        RebarBlock.register(SteamworkKeys.STEAM_VORTEX_TUBE, Material.COPPER_BLOCK,
                io.github.steamwork.content.machines.SteamVortexTube.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_LOGIC_GATE, Material.WAXED_CUT_COPPER_SLAB,
                io.github.steamwork.content.machines.PneumaticLogicGate.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_DIFFERENTIAL_GATE, Material.WAXED_CHISELED_COPPER,
                io.github.steamwork.content.machines.PneumaticDifferentialGate.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_PULSER, Material.WAXED_COPPER_BULB,
                io.github.steamwork.content.machines.PneumaticPulser.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_LATCH, Material.POLISHED_BLACKSTONE,
                io.github.steamwork.content.machines.PneumaticLatch.class);
        RebarBlock.register(SteamworkKeys.STEAM_OSCILLATOR, Material.COPPER_BLOCK, SteamOscillator.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_GATE_VALVE, Material.STRUCTURE_VOID, PneumaticGateValve.class);
        RebarBlock.register(SteamworkKeys.STEAM_PRESSURE_TRANSDUCER, Material.BARREL, SteamPressureTransducer.class);
        RebarBlock.register(SteamworkKeys.STEAM_DIFFERENCE_ENGINE, Material.BARREL, SteamDifferenceEngine.class);
        RebarBlock.register(SteamworkKeys.PNEUMATIC_PRESSURE_MODULE, Material.BARREL, PneumaticPressureModule.class);
    }
}
