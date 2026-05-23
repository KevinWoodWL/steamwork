package io.github.steamwork;

import org.bukkit.NamespacedKey;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteamworkKeys {

    private SteamworkKeys() {
        throw new AssertionError("Utility class");
    }

    // Base materials
    public static final NamespacedKey BRASS_INGOT = steamworkKey("brass_ingot");
    public static final NamespacedKey RUBBER_GASKET = steamworkKey("rubber_gasket");
    public static final NamespacedKey PRESSURE_GAUGE = steamworkKey("pressure_gauge");
    public static final NamespacedKey ZINC_CONCENTRATE = steamworkKey("zinc_concentrate");
    public static final NamespacedKey PLANT_FIBER = steamworkKey("plant_fiber");
    public static final NamespacedKey STEAM_PULP = steamworkKey("steam_pulp");
    public static final NamespacedKey RAW_RESIN = steamworkKey("raw_resin");
    public static final NamespacedKey VULCANIZED_RUBBER = steamworkKey("vulcanized_rubber");
    public static final NamespacedKey STERILE_BIOMASS = steamworkKey("sterile_biomass");
    public static final NamespacedKey SILICA_GRIT = steamworkKey("silica_grit");
    public static final NamespacedKey MINERAL_FLUX = steamworkKey("mineral_flux");
    public static final NamespacedKey TREATED_WOOD = steamworkKey("treated_wood");
    public static final NamespacedKey FIBERBOARD = steamworkKey("fiberboard");
    public static final NamespacedKey RUBBERIZED_FABRIC = steamworkKey("rubberized_fabric");
    public static final NamespacedKey STERILE_CULTURE = steamworkKey("sterile_culture");
    public static final NamespacedKey MACHINE_SCRAP = steamworkKey("machine_scrap");
    public static final NamespacedKey ANALYSIS_RESIDUE = steamworkKey("analysis_residue");
    public static final NamespacedKey REFINED_RESIN = steamworkKey("refined_resin");
    public static final NamespacedKey PLANT_ESSENCE = steamworkKey("plant_essence");
    public static final NamespacedKey DISTILLED_WATER_VIAL = steamworkKey("distilled_water_vial");
    public static final NamespacedKey MINERAL_LEACHATE_VIAL = steamworkKey("mineral_leachate_vial");
    public static final NamespacedKey WASTE_ACID_VIAL = steamworkKey("waste_acid_vial");
    public static final NamespacedKey MINERAL_CONCENTRATE = steamworkKey("mineral_concentrate");
    public static final NamespacedKey FIBER_RESIDUE = steamworkKey("fiber_residue");
    public static final NamespacedKey HEAT_TREATED_METAL = steamworkKey("heat_treated_metal");
    public static final NamespacedKey MINERAL_ANALYSIS_SAMPLE = steamworkKey("mineral_analysis_sample");
    public static final NamespacedKey ORGANIC_ANALYSIS_SAMPLE = steamworkKey("organic_analysis_sample");
    public static final NamespacedKey METALLURGICAL_ANALYSIS_SAMPLE = steamworkKey("metallurgical_analysis_sample");
    public static final NamespacedKey FLUID_ANALYSIS_SAMPLE = steamworkKey("fluid_analysis_sample");

    // Materials
    public static final NamespacedKey ZINC_INGOT = steamworkKey("zinc_ingot");
    public static final NamespacedKey NICHROME_DUST = steamworkKey("nichrome_dust");
    public static final NamespacedKey NICHROME_INGOT = steamworkKey("nichrome_ingot");

    // Components
    public static final NamespacedKey BRASS_GEAR = steamworkKey("brass_gear");
    public static final NamespacedKey HEATING_COIL = steamworkKey("heating_coil");

    public static final NamespacedKey BRONZE_BOILER = steamworkKey("bronze_boiler");
    public static final NamespacedKey INVAR_BOILER = steamworkKey("invar_boiler");
    public static final NamespacedKey MANGANESE_STEEL_BOILER = steamworkKey("manganese_steel_boiler");
    public static final NamespacedKey TUNGSTEN_BOILER = steamworkKey("tungsten_boiler");
    public static final NamespacedKey STEAM_ARM = steamworkKey("steam_arm");
    public static final NamespacedKey SIMPLE_STEAM_TURBINE = steamworkKey("simple_steam_turbine");
    public static final NamespacedKey ADVANCED_STEAM_TURBINE = steamworkKey("advanced_steam_turbine");
    public static final NamespacedKey STEAM_STERILIZER = steamworkKey("steam_sterilizer");
    public static final NamespacedKey STEAM_STEEPING_VAT = steamworkKey("steam_steeping_vat");
    public static final NamespacedKey STEAM_WASHING_TROUGH = steamworkKey("steam_washing_trough");
    public static final NamespacedKey STEAM_PRESS = steamworkKey("steam_press");
    public static final NamespacedKey STEAM_GRINDER = steamworkKey("steam_grinder");
    public static final NamespacedKey STEAM_SCIENCE_INTERFACE = steamworkKey("steam_science_interface");
    public static final NamespacedKey STEAM_HEATING_CHAMBER = steamworkKey("steam_heating_chamber");
    public static final NamespacedKey STEAM_DISTILLATION_TOWER = steamworkKey("steam_distillation_tower");
    public static final NamespacedKey DISTILLATION_TOWER_SECTION = steamworkKey("distillation_tower_section");
    public static final NamespacedKey DISTILLATION_CONDENSER = steamworkKey("distillation_condenser");

    // Steam tools and weapons
    public static final NamespacedKey STEAM_SWORD = steamworkKey("steam_sword");
    public static final NamespacedKey STEAM_PICKAXE = steamworkKey("steam_pickaxe");
    public static final NamespacedKey STEAM_AXE = steamworkKey("steam_axe");
    public static final NamespacedKey STEAM_SHOVEL = steamworkKey("steam_shovel");
    public static final NamespacedKey STEAM_HOE = steamworkKey("steam_hoe");

    // Steam armor
    public static final NamespacedKey STEAM_HELMET = steamworkKey("steam_helmet");
    public static final NamespacedKey STEAM_CHESTPLATE = steamworkKey("steam_chestplate");
    public static final NamespacedKey STEAM_LEGGINGS = steamworkKey("steam_leggings");
    public static final NamespacedKey STEAM_BOOTS = steamworkKey("steam_boots");

    // Portable steam energy (canisters): 3 tiers, capacity scales with alloy.
    public static final NamespacedKey STEAM_CANISTER_BRASS = steamworkKey("steam_canister_brass");
    public static final NamespacedKey STEAM_CANISTER_INVAR = steamworkKey("steam_canister_invar");
    public static final NamespacedKey STEAM_CANISTER_TUNGSTEN = steamworkKey("steam_canister_tungsten");

    // Pressurized furnace alloys
    public static final NamespacedKey INVAR_DUST = steamworkKey("invar_dust");
    public static final NamespacedKey DURALUMIN_DUST = steamworkKey("duralumin_dust");
    public static final NamespacedKey TUNGSTEN_DUST = steamworkKey("tungsten_dust");
    public static final NamespacedKey MANGANESE_STEEL_DUST = steamworkKey("manganese_steel_dust");
    public static final NamespacedKey MANGANESE_BRONZE_DUST = steamworkKey("manganese_bronze_dust");
    public static final NamespacedKey INVAR_INGOT = steamworkKey("invar_ingot");
    public static final NamespacedKey DURALUMIN_INGOT = steamworkKey("duralumin_ingot");
    public static final NamespacedKey TUNGSTEN_INGOT = steamworkKey("tungsten_ingot");
    public static final NamespacedKey MANGANESE_STEEL_INGOT = steamworkKey("manganese_steel_ingot");
    public static final NamespacedKey MANGANESE_BRONZE_INGOT = steamworkKey("manganese_bronze_ingot");

    // Pressurized furnace block
    public static final NamespacedKey STEAM_PRESSURIZED_FURNACE = steamworkKey("steam_pressurized_furnace");

    // Brass components for machines
    public static final NamespacedKey BRASS_DISTILLATION_TUBE = steamworkKey("brass_distillation_tube");
    public static final NamespacedKey BRASS_FILTER = steamworkKey("brass_filter");
    public static final NamespacedKey BRASS_SIEVE = steamworkKey("brass_sieve");
    public static final NamespacedKey BRASS_FLOW_VALVE = steamworkKey("brass_flow_valve");
    public static final NamespacedKey BRASS_FAN_BLADE = steamworkKey("brass_fan_blade");
    public static final NamespacedKey BRASS_VALVE_CORE = steamworkKey("brass_valve_core");
    public static final NamespacedKey BRASS_SEAL_RING = steamworkKey("brass_seal_ring");

    // Alloy blocks
    public static final NamespacedKey INVAR_BLOCK = steamworkKey("invar_block");
    public static final NamespacedKey DURALUMIN_BLOCK = steamworkKey("duralumin_block");
    public static final NamespacedKey TUNGSTEN_BLOCK = steamworkKey("tungsten_block");
    public static final NamespacedKey MANGANESE_STEEL_BLOCK = steamworkKey("manganese_steel_block");
    public static final NamespacedKey MANGANESE_BRONZE_BLOCK = steamworkKey("manganese_bronze_block");

    // Machine components
    public static final NamespacedKey STEAM_MOTOR = steamworkKey("steam_motor");

    // Equipment workshop
    public static final NamespacedKey STEAM_ASSEMBLY_BENCH = steamworkKey("steam_assembly_bench");

    // Precision mill machine
    public static final NamespacedKey STEAM_PRECISION_MILL = steamworkKey("steam_precision_mill");

    // Precision mill products
    public static final NamespacedKey PRECISION_GEAR = steamworkKey("precision_gear");
    public static final NamespacedKey PRECISION_SCREW = steamworkKey("precision_screw");
    public static final NamespacedKey PRECISION_VALVE = steamworkKey("precision_valve");
    public static final NamespacedKey WEAR_PLATE = steamworkKey("wear_plate");
    public static final NamespacedKey HEAT_SINK = steamworkKey("heat_sink");
    public static final NamespacedKey MILLING_BLADE = steamworkKey("milling_blade");
    public static final NamespacedKey CATALYST_CORE = steamworkKey("catalyst_core");
    public static final NamespacedKey PRECISION_BEARING = steamworkKey("precision_bearing");

    // Steam logistics
    public static final NamespacedKey STEAM_COMPRESSOR = steamworkKey("steam_compressor");
    public static final NamespacedKey PNEUMATIC_CARGO_HUB = steamworkKey("pneumatic_cargo_hub");
}
