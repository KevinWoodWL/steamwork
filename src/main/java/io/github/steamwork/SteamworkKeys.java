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
    public static final NamespacedKey GRANITE_DUST  = steamworkKey("granite_dust");
    public static final NamespacedKey DIORITE_DUST  = steamworkKey("diorite_dust");
    public static final NamespacedKey ANDESITE_DUST = steamworkKey("andesite_dust");
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
    // 高级分析样本（压机 4× 基础 → 1 个高级，产出约 5× 学科点数）
    public static final NamespacedKey REFINED_MINERAL_SAMPLE = steamworkKey("refined_mineral_sample");
    public static final NamespacedKey CONCENTRATED_ORGANIC_SAMPLE = steamworkKey("concentrated_organic_sample");
    public static final NamespacedKey REFINED_METALLURGICAL_SAMPLE = steamworkKey("refined_metallurgical_sample");
    public static final NamespacedKey PURIFIED_FLUID_SAMPLE = steamworkKey("purified_fluid_sample");

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
    public static final NamespacedKey SIMPLE_STEAM_TURBINE        = steamworkKey("simple_steam_turbine");
    public static final NamespacedKey BASIC_PROCESSING_TURBINE    = steamworkKey("basic_processing_turbine");
    public static final NamespacedKey HYDRAULIC_TURBINE           = steamworkKey("hydraulic_turbine");
    public static final NamespacedKey PRECISION_STEAM_TURBINE     = steamworkKey("precision_steam_turbine");
    public static final NamespacedKey PRECISION_PROCESSING_TURBINE = steamworkKey("precision_processing_turbine");
    public static final NamespacedKey DIESEL_TURBINE              = steamworkKey("diesel_turbine");
    public static final NamespacedKey PYLON_UNIVERSAL_TURBINE     = steamworkKey("pylon_universal_turbine");
    public static final NamespacedKey STEAM_STERILIZER = steamworkKey("steam_sterilizer");
    public static final NamespacedKey STEAM_STEEPING_VAT = steamworkKey("steam_steeping_vat");
    public static final NamespacedKey STEAM_WASHING_TROUGH = steamworkKey("steam_washing_trough");
    public static final NamespacedKey STEAM_PRESS = steamworkKey("steam_press");
    public static final NamespacedKey STEAM_GRINDER = steamworkKey("steam_grinder");
    public static final NamespacedKey STEAM_SCIENCE_INTERFACE = steamworkKey("steam_science_interface");
    public static final NamespacedKey STEAM_HEATING_CHAMBER = steamworkKey("steam_heating_chamber");
    public static final NamespacedKey STEAM_CANISTER_BENCH = steamworkKey("steam_canister_bench");
    public static final NamespacedKey STEAM_CHARGING_CHAMBER = steamworkKey("steam_charging_chamber");
    public static final NamespacedKey STEAM_DISTILLATION_TOWER = steamworkKey("steam_distillation_tower");
    public static final NamespacedKey DISTILLATION_TOWER_SECTION = steamworkKey("distillation_tower_section");
    public static final NamespacedKey DISTILLATION_CONDENSER = steamworkKey("distillation_condenser");

    // 汽动逻辑（PneumaticCraft 灵感）
    public static final NamespacedKey STEAM_VORTEX_TUBE = steamworkKey("steam_vortex_tube");
    public static final NamespacedKey PNEUMATIC_LOGIC_GATE = steamworkKey("pneumatic_logic_gate");
    public static final NamespacedKey STEAM_OSCILLATOR = steamworkKey("steam_oscillator");
    public static final NamespacedKey PNEUMATIC_GATE_VALVE = steamworkKey("pneumatic_gate_valve");
    public static final NamespacedKey STEAM_PRESSURE_TRANSDUCER = steamworkKey("steam_pressure_transducer");

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

    // Material-specialized steam tools and weapons
    public static final NamespacedKey STEAM_BRONZE_SWORD = steamworkKey("steam_bronze_sword");
    public static final NamespacedKey STEAM_BRONZE_PICKAXE = steamworkKey("steam_bronze_pickaxe");
    public static final NamespacedKey STEAM_BRONZE_AXE = steamworkKey("steam_bronze_axe");
    public static final NamespacedKey STEAM_BRONZE_SHOVEL = steamworkKey("steam_bronze_shovel");
    public static final NamespacedKey STEAM_BRONZE_HOE = steamworkKey("steam_bronze_hoe");
    public static final NamespacedKey STEAM_INVAR_SWORD = steamworkKey("steam_invar_sword");
    public static final NamespacedKey STEAM_INVAR_PICKAXE = steamworkKey("steam_invar_pickaxe");
    public static final NamespacedKey STEAM_INVAR_AXE = steamworkKey("steam_invar_axe");
    public static final NamespacedKey STEAM_INVAR_SHOVEL = steamworkKey("steam_invar_shovel");
    public static final NamespacedKey STEAM_INVAR_HOE = steamworkKey("steam_invar_hoe");
    public static final NamespacedKey STEAM_TUNGSTEN_SWORD = steamworkKey("steam_tungsten_sword");
    public static final NamespacedKey STEAM_TUNGSTEN_PICKAXE = steamworkKey("steam_tungsten_pickaxe");
    public static final NamespacedKey STEAM_TUNGSTEN_AXE = steamworkKey("steam_tungsten_axe");
    public static final NamespacedKey STEAM_TUNGSTEN_SHOVEL = steamworkKey("steam_tungsten_shovel");
    public static final NamespacedKey STEAM_TUNGSTEN_HOE = steamworkKey("steam_tungsten_hoe");

    // Material-specialized steam armor
    public static final NamespacedKey STEAM_BRONZE_HELMET = steamworkKey("steam_bronze_helmet");
    public static final NamespacedKey STEAM_BRONZE_CHESTPLATE = steamworkKey("steam_bronze_chestplate");
    public static final NamespacedKey STEAM_BRONZE_LEGGINGS = steamworkKey("steam_bronze_leggings");
    public static final NamespacedKey STEAM_BRONZE_BOOTS = steamworkKey("steam_bronze_boots");
    public static final NamespacedKey STEAM_INVAR_HELMET = steamworkKey("steam_invar_helmet");
    public static final NamespacedKey STEAM_INVAR_CHESTPLATE = steamworkKey("steam_invar_chestplate");
    public static final NamespacedKey STEAM_INVAR_LEGGINGS = steamworkKey("steam_invar_leggings");
    public static final NamespacedKey STEAM_INVAR_BOOTS = steamworkKey("steam_invar_boots");
    public static final NamespacedKey STEAM_TUNGSTEN_HELMET = steamworkKey("steam_tungsten_helmet");
    public static final NamespacedKey STEAM_TUNGSTEN_CHESTPLATE = steamworkKey("steam_tungsten_chestplate");
    public static final NamespacedKey STEAM_TUNGSTEN_LEGGINGS = steamworkKey("steam_tungsten_leggings");
    public static final NamespacedKey STEAM_TUNGSTEN_BOOTS = steamworkKey("steam_tungsten_boots");

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

    // Upgrade tool
    public static final NamespacedKey MACHINE_CALIBRATOR = steamworkKey("machine_calibrator");

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
    public static final NamespacedKey PALLADIUM_ALLOY_INGOT = steamworkKey("palladium_alloy_ingot");
    public static final NamespacedKey HIGH_POLYMER = steamworkKey("high_polymer");
    public static final NamespacedKey SEQUENCED_WORKPIECE = steamworkKey("sequenced_workpiece");
    // 钯合金工序链中间品：每步一个独立 key，让配方系统能按步骤区分（Rebar 配方匹配只看 item key）
    public static final NamespacedKey PALLADIUM_WORKPIECE_1 = steamworkKey("palladium_workpiece_1");
    public static final NamespacedKey PALLADIUM_WORKPIECE_2 = steamworkKey("palladium_workpiece_2");
    public static final NamespacedKey PALLADIUM_WORKPIECE_3 = steamworkKey("palladium_workpiece_3");

    // 蒸汽飞行核心：喷射喷嘴部件 + 多工序成品 + 三步工序链中间品
    public static final NamespacedKey JET_NOZZLE = steamworkKey("jet_nozzle");
    public static final NamespacedKey STEAM_FLIGHT_CORE = steamworkKey("steam_flight_core");
    public static final NamespacedKey FLIGHT_CORE_WORKPIECE_1 = steamworkKey("flight_core_workpiece_1");
    public static final NamespacedKey FLIGHT_CORE_WORKPIECE_2 = steamworkKey("flight_core_workpiece_2");
    public static final NamespacedKey FLIGHT_CORE_WORKPIECE_3 = steamworkKey("flight_core_workpiece_3");

    // 涡轮转子：精密蒸汽涡轮的核心多工序部件
    public static final NamespacedKey TURBINE_ROTOR = steamworkKey("turbine_rotor");

    // Steam automation machines
    public static final NamespacedKey PRECISION_FOUNDRY = steamworkKey("precision_foundry");
    public static final NamespacedKey PRECISION_CATALYTIC_REACTOR = steamworkKey("precision_catalytic_reactor");
    public static final NamespacedKey HEAVY_IMPACT_CRUSHER = steamworkKey("heavy_impact_crusher");
    public static final NamespacedKey HYDRAULIC_FORGE = steamworkKey("hydraulic_forge");
    public static final NamespacedKey PRECISION_CRYSTALLIZER = steamworkKey("precision_crystallizer");
    public static final NamespacedKey PRECISION_CENTRIFUGE = steamworkKey("precision_centrifuge");

    // Hydraulic forge products
    public static final NamespacedKey HIGH_PRESSURE_PIPE = steamworkKey("high_pressure_pipe");
    public static final NamespacedKey HIGH_PRESSURE_FLANGE = steamworkKey("high_pressure_flange");
    public static final NamespacedKey HYDRAULIC_PISTON = steamworkKey("hydraulic_piston");
    public static final NamespacedKey HYDRAULIC_SEAL = steamworkKey("hydraulic_seal");
    public static final NamespacedKey FORGED_PLATE = steamworkKey("forged_plate");

    // Upgrade modules
    public static final NamespacedKey UPGRADE_MODULE_ENERGY_SAVE = steamworkKey("upgrade_module_energy_save");
    public static final NamespacedKey UPGRADE_MODULE_AUTO_INPUT = steamworkKey("upgrade_module_auto_input");
    public static final NamespacedKey UPGRADE_MODULE_AUTO_OUTPUT = steamworkKey("upgrade_module_auto_output");
    public static final NamespacedKey UPGRADE_MODULE_BULK = steamworkKey("upgrade_module_bulk");
    public static final NamespacedKey UPGRADE_MODULE_RANGE = steamworkKey("upgrade_module_range");
    public static final NamespacedKey UPGRADE_MODULE_BOOST = steamworkKey("upgrade_module_boost");
    public static final NamespacedKey UPGRADE_MODULE_PYLON_COMPAT = steamworkKey("upgrade_module_pylon_compat");

    // Production line
    public static final NamespacedKey PRODUCTION_LINE_INLET = steamworkKey("production_line_inlet");
    public static final NamespacedKey PRODUCTION_LINE_BUFFER_CHEST = steamworkKey("production_line_buffer_chest");
    public static final NamespacedKey PRODUCTION_LINE_OUTLET = steamworkKey("production_line_outlet");
    public static final NamespacedKey PRODUCTION_LINE_BLUEPRINT = steamworkKey("production_line_blueprint");
    public static final NamespacedKey AUTO_PRODUCTION_MODULE = steamworkKey("auto_production_module");

    // Steam logistics
    public static final NamespacedKey STEAM_COMPRESSOR = steamworkKey("steam_compressor");
    public static final NamespacedKey PNEUMATIC_CARGO_HUB = steamworkKey("pneumatic_cargo_hub");
    public static final NamespacedKey STEAM_CATAPULT = steamworkKey("steam_catapult");
    public static final NamespacedKey STEAM_SORTER = steamworkKey("steam_sorter");
    public static final NamespacedKey PNEUMATIC_DUCT = steamworkKey("pneumatic_duct");
    public static final NamespacedKey PNEUMATIC_INPUT = steamworkKey("pneumatic_input");
    public static final NamespacedKey PNEUMATIC_OUTPUT = steamworkKey("pneumatic_output");
    public static final NamespacedKey PNEUMATIC_DISTRIBUTOR = steamworkKey("pneumatic_distributor");
}
