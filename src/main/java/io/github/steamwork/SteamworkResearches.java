package io.github.steamwork;

import io.github.pylonmc.rebar.item.research.Research;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

// Research nodes are keyed as <discipline>_<topic> to match the four disciplines
// used by SteamScienceInterface (material / biology / precision / chemistry).
// Costs follow a flat curve across four tiers:
//   T0 entry   :  20- 40
//   T1 early   :  60-110
//   T2 mid     : 140-220
//   T3 advanced: 280-380
//   T4 endgame : 450-600
public final class SteamworkResearches {

    private SteamworkResearches() {
        throw new AssertionError("Utility class");
    }

    // ---- Tier 0: entry-level handcraft ----------------------------------

    public static final Research MATERIAL_BASIC_METALS = new Research(
            steamworkKey("material_basic_metals"), SteamworkItems.BRASS_INGOT, 15L,
            SteamworkKeys.ZINC_INGOT, SteamworkKeys.BRASS_INGOT,
            SteamworkKeys.RUBBER_GASKET, SteamworkKeys.PRESSURE_GAUGE,
            SteamworkKeys.ZINC_CONCENTRATE, SteamworkKeys.SILICA_GRIT,
            SteamworkKeys.MINERAL_FLUX, SteamworkKeys.BRONZE_BOILER,
            SteamworkKeys.BRASS_GEAR,
            SteamworkKeys.BRASS_DISTILLATION_TUBE, SteamworkKeys.BRASS_FILTER,
            SteamworkKeys.BRASS_SIEVE, SteamworkKeys.BRASS_FLOW_VALVE,
            SteamworkKeys.BRASS_FAN_BLADE, SteamworkKeys.BRASS_VALVE_CORE,
            SteamworkKeys.BRASS_SEAL_RING,
            SteamworkKeys.NICHROME_DUST, SteamworkKeys.NICHROME_INGOT);

    public static final Research BIOLOGY_PLANT_PROCESSING = new Research(
            steamworkKey("biology_plant_processing"), SteamworkItems.PLANT_FIBER, 12L,
            SteamworkKeys.PLANT_FIBER, SteamworkKeys.STEAM_PULP, SteamworkKeys.RAW_RESIN,
            SteamworkKeys.VULCANIZED_RUBBER, SteamworkKeys.STERILE_BIOMASS);

    public static final Research MATERIAL_BASIC_CONSTRUCTION = new Research(
            steamworkKey("material_basic_construction"), SteamworkItems.FIBERBOARD, 15L,
            SteamworkKeys.TREATED_WOOD, SteamworkKeys.FIBERBOARD,
            SteamworkKeys.RUBBERIZED_FABRIC, SteamworkKeys.STERILE_CULTURE);

    // ---- Tier 1: early machinery & alloys -------------------------------

    public static final Research MATERIAL_BASIC_MACHINES = new Research(
            steamworkKey("material_basic_machines"), SteamworkItems.STEAM_STERILIZER, 25L,
            SteamworkKeys.STEAM_STERILIZER, SteamworkKeys.STEAM_STEEPING_VAT,
            SteamworkKeys.STEAM_WASHING_TROUGH,
            SteamworkKeys.STEAM_PRESS, SteamworkKeys.STEAM_GRINDER,
            SteamworkKeys.STEAM_PRESSURIZED_FURNACE,
            SteamworkKeys.HEAT_TREATED_METAL, SteamworkKeys.MACHINE_SCRAP);

    // ---- Tier 2: mid-tier processing ------------------------------------

    public static final Research PRECISION_STEAM_AUTOMATION = new Research(
            steamworkKey("precision_steam_automation"), SteamworkItems.STEAM_ARM, 170L,
            SteamworkKeys.STEAM_ARM, SteamworkKeys.STEAM_ASSEMBLY_BENCH,
            SteamworkKeys.STEAM_MOTOR,
            SteamworkKeys.STEAM_CANISTER_BRASS, SteamworkKeys.STEAM_CANISTER_INVAR,
            SteamworkKeys.STEAM_CANISTER_TUNGSTEN,
            SteamworkKeys.PRODUCTION_LINE_INLET, SteamworkKeys.PRODUCTION_LINE_OUTLET,
            SteamworkKeys.PRODUCTION_LINE_BLUEPRINT);

    public static final Research CHEMISTRY_BASIC_RESEARCH = new Research(
            steamworkKey("chemistry_basic_research"), SteamworkItems.STEAM_SCIENCE_INTERFACE, 18L,
            SteamworkKeys.MINERAL_ANALYSIS_SAMPLE, SteamworkKeys.ORGANIC_ANALYSIS_SAMPLE,
            SteamworkKeys.METALLURGICAL_ANALYSIS_SAMPLE, SteamworkKeys.FLUID_ANALYSIS_SAMPLE,
            SteamworkKeys.STEAM_SCIENCE_INTERFACE, SteamworkKeys.ANALYSIS_RESIDUE);

    // ---- Tier 3: advanced alloys & fluids -------------------------------

    public static final Research MATERIAL_ADVANCED_INGOTS = new Research(
            steamworkKey("material_advanced_ingots"), SteamworkItems.INVAR_INGOT, 45L,
            SteamworkKeys.INVAR_DUST, SteamworkKeys.DURALUMIN_DUST, SteamworkKeys.TUNGSTEN_DUST,
            SteamworkKeys.MANGANESE_STEEL_DUST, SteamworkKeys.MANGANESE_BRONZE_DUST,
            SteamworkKeys.INVAR_INGOT, SteamworkKeys.DURALUMIN_INGOT, SteamworkKeys.TUNGSTEN_INGOT,
            SteamworkKeys.MANGANESE_STEEL_INGOT, SteamworkKeys.MANGANESE_BRONZE_INGOT,
            SteamworkKeys.INVAR_BLOCK, SteamworkKeys.DURALUMIN_BLOCK, SteamworkKeys.TUNGSTEN_BLOCK,
            SteamworkKeys.MANGANESE_STEEL_BLOCK, SteamworkKeys.MANGANESE_BRONZE_BLOCK,
            SteamworkKeys.HEATING_COIL);

    public static final Research MATERIAL_ADVANCED_BOILERS = new Research(
            steamworkKey("material_advanced_boilers"), SteamworkItems.INVAR_BOILER, 100L,
            SteamworkKeys.INVAR_BOILER, SteamworkKeys.MANGANESE_STEEL_BOILER,
            SteamworkKeys.TUNGSTEN_BOILER);

    public static final Research CHEMISTRY_HEATING_CHAMBER = new Research(
            steamworkKey("chemistry_heating_chamber"), SteamworkItems.STEAM_HEATING_CHAMBER, 20L,
            SteamworkKeys.STEAM_HEATING_CHAMBER);

    public static final Research MATERIAL_STEAM_LOGISTICS = new Research(
            steamworkKey("material_steam_logistics"), SteamworkItems.STEAM_COMPRESSOR, 35L,
            SteamworkKeys.STEAM_COMPRESSOR,
            SteamworkKeys.PNEUMATIC_DUCT, SteamworkKeys.PNEUMATIC_OUTPUT, SteamworkKeys.PNEUMATIC_INPUT,
            SteamworkKeys.PNEUMATIC_CARGO_HUB,
            SteamworkKeys.STEAM_CATAPULT, SteamworkKeys.STEAM_SORTER,
            SteamworkKeys.PNEUMATIC_DISTRIBUTOR);

    public static final Research CHEMISTRY_DISTILLATION = new Research(
            steamworkKey("chemistry_distillation"), SteamworkItems.STEAM_DISTILLATION_TOWER, 40L,
            SteamworkKeys.STEAM_DISTILLATION_TOWER, SteamworkKeys.DISTILLATION_TOWER_SECTION,
            SteamworkKeys.DISTILLATION_CONDENSER,
            SteamworkKeys.REFINED_RESIN, SteamworkKeys.PLANT_ESSENCE,
            SteamworkKeys.DISTILLED_WATER_VIAL, SteamworkKeys.MINERAL_LEACHATE_VIAL,
            SteamworkKeys.WASTE_ACID_VIAL, SteamworkKeys.MINERAL_CONCENTRATE,
            SteamworkKeys.FIBER_RESIDUE);

    public static final Research PRECISION_MILLING = new Research(
            steamworkKey("precision_milling"), SteamworkItems.STEAM_PRECISION_MILL, 160L,
            SteamworkKeys.STEAM_PRECISION_MILL,
            SteamworkKeys.PRECISION_GEAR, SteamworkKeys.PRECISION_SCREW,
            SteamworkKeys.PRECISION_VALVE, SteamworkKeys.WEAR_PLATE,
            SteamworkKeys.HEAT_SINK, SteamworkKeys.MILLING_BLADE,
            SteamworkKeys.CATALYST_CORE, SteamworkKeys.PRECISION_BEARING);

    // ---- Tier 4: endgame ------------------------------------------------

    public static final Research PRECISION_TURBINES_1 = new Research(
            steamworkKey("precision_turbines_1"), SteamworkItems.SIMPLE_STEAM_TURBINE, 15L,
            SteamworkKeys.SIMPLE_STEAM_TURBINE);

    public static final Research PRECISION_TURBINES_2 = new Research(
            steamworkKey("precision_turbines_2"), SteamworkItems.ADVANCED_STEAM_TURBINE, 100L,
            SteamworkKeys.ADVANCED_STEAM_TURBINE);

    public static final Research PRECISION_STEAM_EQUIPMENT = new Research(
            steamworkKey("precision_steam_equipment"), SteamworkItems.STEAM_SWORD, 550L,
            SteamworkKeys.STEAM_SWORD, SteamworkKeys.STEAM_PICKAXE, SteamworkKeys.STEAM_AXE,
            SteamworkKeys.STEAM_SHOVEL, SteamworkKeys.STEAM_HOE);

    public static final Research MATERIAL_STEAM_ARMOR = new Research(
            steamworkKey("material_steam_armor"), SteamworkItems.STEAM_CHESTPLATE, 600L,
            SteamworkKeys.STEAM_HELMET, SteamworkKeys.STEAM_CHESTPLATE,
            SteamworkKeys.STEAM_LEGGINGS, SteamworkKeys.STEAM_BOOTS);

    public static void initialize() {
        MATERIAL_BASIC_METALS.register();
        BIOLOGY_PLANT_PROCESSING.register();
        MATERIAL_BASIC_CONSTRUCTION.register();

        MATERIAL_BASIC_MACHINES.register();

        PRECISION_STEAM_AUTOMATION.register();
        CHEMISTRY_BASIC_RESEARCH.register();

        MATERIAL_ADVANCED_INGOTS.register();
        MATERIAL_ADVANCED_BOILERS.register();
        CHEMISTRY_HEATING_CHAMBER.register();
        MATERIAL_STEAM_LOGISTICS.register();
        CHEMISTRY_DISTILLATION.register();
        PRECISION_MILLING.register();

        PRECISION_TURBINES_1.register();
        PRECISION_TURBINES_2.register();
        PRECISION_STEAM_EQUIPMENT.register();
        MATERIAL_STEAM_ARMOR.register();
    }
}
