package io.github.steamwork;

import io.github.pylonmc.rebar.content.guide.RebarGuide;
import io.github.pylonmc.rebar.guide.button.AddonPageButton;
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;
import io.github.steamwork.guide.SequencedChainButton;
import io.github.steamwork.guide.SequencedChainPage;
import io.github.steamwork.recipes.SteamAssemblyRecipe;
import io.github.steamwork.recipes.SteamCatalyticReactionRecipe;
import io.github.steamwork.recipes.SteamFoundryRecipe;
import io.github.steamwork.recipes.SteamMillingRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteamworkPages {

    private SteamworkPages() {
        throw new AssertionError("Utility class");
    }

    public static final SimpleStaticGuidePage ROOT = new SimpleStaticGuidePage(steamworkKey("root"));
    public static final SimpleStaticGuidePage METALS = new SimpleStaticGuidePage(steamworkKey("metals"));
    public static final SimpleStaticGuidePage BASIC_MATERIALS = new SimpleStaticGuidePage(steamworkKey("basic_materials"));
    public static final SimpleStaticGuidePage COMPONENTS = new SimpleStaticGuidePage(steamworkKey("components"));
    public static final SimpleStaticGuidePage HEAT_SOURCES = new SimpleStaticGuidePage(steamworkKey("heat_sources"));
    public static final SimpleStaticGuidePage STEAM_MACHINES = new SimpleStaticGuidePage(steamworkKey("steam_machines"));
    public static final SimpleStaticGuidePage EQUIPMENT = new SimpleStaticGuidePage(steamworkKey("equipment"));
    public static final SimpleStaticGuidePage ARMOR = new SimpleStaticGuidePage(steamworkKey("armor"));
    public static final SimpleStaticGuidePage STEAM_LOGISTICS = new SimpleStaticGuidePage(steamworkKey("steam_logistics"));
    public static final SimpleStaticGuidePage STEAM_AUTOMATION = new SimpleStaticGuidePage(steamworkKey("steam_automation"));
    public static final SimpleStaticGuidePage STEAM_CALIBRATION = new SimpleStaticGuidePage(steamworkKey("steam_calibration"));
    public static final SimpleStaticGuidePage STEAM_DISTILLATION = new SimpleStaticGuidePage(steamworkKey("steam_distillation"));

    public static void initialize() {
        RebarGuide.getRootPage().addButton(new AddonPageButton(Steamwork.getInstance(), ROOT));

        ROOT.addPage(SteamworkItems.ZINC_INGOT, METALS);
        METALS.addItem(SteamworkItems.ZINC_INGOT);
        METALS.addItem(SteamworkItems.BRASS_INGOT);
        METALS.addItem(SteamworkItems.NICHROME_DUST);
        METALS.addItem(SteamworkItems.NICHROME_INGOT);
        // ★ MATERIAL_ADVANCED_INGOTS（学科门控）
        METALS.addItem(SteamworkItems.INVAR_DUST);
        METALS.addItem(SteamworkItems.INVAR_INGOT);
        METALS.addItem(SteamworkItems.INVAR_BLOCK);
        METALS.addItem(SteamworkItems.DURALUMIN_DUST);
        METALS.addItem(SteamworkItems.DURALUMIN_INGOT);
        METALS.addItem(SteamworkItems.DURALUMIN_BLOCK);
        METALS.addItem(SteamworkItems.TUNGSTEN_DUST);
        METALS.addItem(SteamworkItems.TUNGSTEN_INGOT);
        METALS.addItem(SteamworkItems.TUNGSTEN_BLOCK);
        METALS.addItem(SteamworkItems.MANGANESE_STEEL_DUST);
        METALS.addItem(SteamworkItems.MANGANESE_STEEL_INGOT);
        METALS.addItem(SteamworkItems.MANGANESE_STEEL_BLOCK);
        METALS.addItem(SteamworkItems.MANGANESE_BRONZE_DUST);
        METALS.addItem(SteamworkItems.MANGANESE_BRONZE_INGOT);
        METALS.addItem(SteamworkItems.MANGANESE_BRONZE_BLOCK);
        // 钯合金锭通过完整的四步精炼工序链制造，左键直接展示全流程
        METALS.addButton(new SequencedChainButton(SteamworkItems.PALLADIUM_ALLOY_INGOT, java.util.List.of(
                new SequencedChainPage.Step(SteamCatalyticReactionRecipe.RECIPE_TYPE,
                        steamworkKey("react_palladium_alloy")),
                new SequencedChainPage.Step(SteamFoundryRecipe.RECIPE_TYPE,
                        steamworkKey("foundry_palladium_alloy_matrix")),
                new SequencedChainPage.Step(SteamMillingRecipe.RECIPE_TYPE,
                        steamworkKey("mill_palladium_alloy_blank")),
                new SequencedChainPage.Step(SteamFoundryRecipe.RECIPE_TYPE,
                        steamworkKey("foundry_palladium_alloy_final"))
        )));

        ROOT.addPage(SteamworkItems.RUBBER_GASKET, BASIC_MATERIALS);
        BASIC_MATERIALS.addItem(SteamworkItems.ZINC_CONCENTRATE);
        BASIC_MATERIALS.addItem(SteamworkItems.SILICA_GRIT);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_FLUX);
        BASIC_MATERIALS.addItem(SteamworkItems.GRANITE_DUST);
        BASIC_MATERIALS.addItem(SteamworkItems.DIORITE_DUST);
        BASIC_MATERIALS.addItem(SteamworkItems.ANDESITE_DUST);
        BASIC_MATERIALS.addItem(SteamworkItems.RUBBER_GASKET);
        BASIC_MATERIALS.addItem(SteamworkItems.PRESSURE_GAUGE);
        BASIC_MATERIALS.addItem(SteamworkItems.HEAT_TREATED_METAL);
        BASIC_MATERIALS.addItem(SteamworkItems.MACHINE_SCRAP);
        BASIC_MATERIALS.addItem(SteamworkItems.PLANT_FIBER);
        BASIC_MATERIALS.addItem(SteamworkItems.STEAM_PULP);
        BASIC_MATERIALS.addItem(SteamworkItems.RAW_RESIN);
        BASIC_MATERIALS.addItem(SteamworkItems.VULCANIZED_RUBBER);
        BASIC_MATERIALS.addItem(SteamworkItems.STERILE_BIOMASS);
        BASIC_MATERIALS.addItem(SteamworkItems.TREATED_WOOD);
        BASIC_MATERIALS.addItem(SteamworkItems.FIBERBOARD);
        BASIC_MATERIALS.addItem(SteamworkItems.RUBBERIZED_FABRIC);
        BASIC_MATERIALS.addItem(SteamworkItems.STERILE_CULTURE);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_ANALYSIS_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.ORGANIC_ANALYSIS_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.FLUID_ANALYSIS_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.REFINED_MINERAL_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.CONCENTRATED_ORGANIC_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.REFINED_METALLURGICAL_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.PURIFIED_FLUID_SAMPLE);
        BASIC_MATERIALS.addItem(SteamworkItems.ANALYSIS_RESIDUE);
        // ★ CHEMISTRY_DISTILLATION / PRECISION_ADVANCED_AUTOMATION_1（学科门控）
        BASIC_MATERIALS.addItem(SteamworkItems.REFINED_RESIN);
        BASIC_MATERIALS.addItem(SteamworkItems.PLANT_ESSENCE);
        BASIC_MATERIALS.addItem(SteamworkItems.DISTILLED_WATER_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_LEACHATE_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.WASTE_ACID_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_CONCENTRATE);
        BASIC_MATERIALS.addItem(SteamworkItems.FIBER_RESIDUE);
        BASIC_MATERIALS.addItem(SteamworkItems.HIGH_POLYMER);

        ROOT.addPage(SteamworkItems.BRASS_GEAR, COMPONENTS);
        COMPONENTS.addItem(SteamworkItems.BRASS_GEAR);
        COMPONENTS.addItem(SteamworkItems.BRASS_DISTILLATION_TUBE);
        COMPONENTS.addItem(SteamworkItems.BRASS_FILTER);
        COMPONENTS.addItem(SteamworkItems.BRASS_SIEVE);
        COMPONENTS.addItem(SteamworkItems.BRASS_FLOW_VALVE);
        COMPONENTS.addItem(SteamworkItems.BRASS_FAN_BLADE);
        COMPONENTS.addItem(SteamworkItems.BRASS_VALVE_CORE);
        COMPONENTS.addItem(SteamworkItems.BRASS_SEAL_RING);
        COMPONENTS.addItem(SteamworkItems.STEAM_MOTOR);
        // ★ PRECISION_ADVANCED_AUTOMATION_1 / MATERIAL_ADVANCED_INGOTS（学科门控）
        COMPONENTS.addItem(SteamworkItems.PRECISION_GEAR);
        COMPONENTS.addItem(SteamworkItems.PRECISION_SCREW);
        COMPONENTS.addItem(SteamworkItems.PRECISION_VALVE);
        COMPONENTS.addItem(SteamworkItems.WEAR_PLATE);
        COMPONENTS.addItem(SteamworkItems.HEAT_SINK);
        COMPONENTS.addItem(SteamworkItems.MILLING_BLADE);
        COMPONENTS.addItem(SteamworkItems.CATALYST_CORE);
        COMPONENTS.addItem(SteamworkItems.PRECISION_BEARING);
        COMPONENTS.addItem(SteamworkItems.HEATING_COIL);
        COMPONENTS.addItem(SteamworkItems.HIGH_PRESSURE_PIPE);
        COMPONENTS.addItem(SteamworkItems.HIGH_PRESSURE_FLANGE);
        COMPONENTS.addItem(SteamworkItems.HYDRAULIC_PISTON);
        COMPONENTS.addItem(SteamworkItems.HYDRAULIC_SEAL);
        COMPONENTS.addItem(SteamworkItems.FORGED_PLATE);
        COMPONENTS.addItem(SteamworkItems.JET_NOZZLE);
        COMPONENTS.addItem(SteamworkItems.TURBINE_ROTOR);
        // 蒸汽飞行核心通过完整的四步工序链制造，左键直接展示全流程
        COMPONENTS.addButton(new SequencedChainButton(SteamworkItems.STEAM_FLIGHT_CORE, java.util.List.of(
                new SequencedChainPage.Step(SteamCatalyticReactionRecipe.RECIPE_TYPE,
                        steamworkKey("react_flight_core")),
                new SequencedChainPage.Step(SteamFoundryRecipe.RECIPE_TYPE,
                        steamworkKey("foundry_flight_core_matrix")),
                new SequencedChainPage.Step(SteamMillingRecipe.RECIPE_TYPE,
                        steamworkKey("mill_flight_core_blank")),
                new SequencedChainPage.Step(SteamAssemblyRecipe.RECIPE_TYPE,
                        steamworkKey("assemble_flight_core"))
        )));

        ROOT.addPage(new ItemStack(Material.CAMPFIRE), HEAT_SOURCES);
        HEAT_SOURCES.addItem(new ItemStack(Material.CAMPFIRE));
        HEAT_SOURCES.addItem(new ItemStack(Material.SOUL_CAMPFIRE));
        HEAT_SOURCES.addItem(new ItemStack(Material.LAVA_BUCKET));
        HEAT_SOURCES.addItem(new ItemStack(Material.MAGMA_BLOCK));

        ROOT.addPage(SteamworkItems.BRONZE_BOILER, STEAM_MACHINES);
        STEAM_MACHINES.addItem(SteamworkItems.BRONZE_BOILER);
        // ★ MATERIAL_ADVANCED_BOILERS（学科门控）
        STEAM_MACHINES.addItem(SteamworkItems.INVAR_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.MANGANESE_STEEL_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.TUNGSTEN_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_SCIENCE_INTERFACE);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_STERILIZER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_STEEPING_VAT);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_WASHING_TROUGH);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_PRESS);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_GRINDER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_PRESSURIZED_FURNACE);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_ARM);
        STEAM_MACHINES.addItem(SteamworkItems.SIMPLE_STEAM_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.BASIC_PROCESSING_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.PYLON_UNIVERSAL_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.PRECISION_PROCESSING_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.HYDRAULIC_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.DIESEL_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.PRECISION_STEAM_TURBINE);
        // ★ CHEMISTRY_HEATING_CHAMBER（学科门控）
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_HEATING_CHAMBER);

        ROOT.addPage(SteamworkItems.STEAM_SWORD, EQUIPMENT);
        EQUIPMENT.addItem(SteamworkItems.STEAM_ASSEMBLY_BENCH);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_BENCH);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CHARGING_CHAMBER);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_BRASS);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_INVAR);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_TUNGSTEN);
        EQUIPMENT.addItem(SteamworkItems.STEAM_SWORD);
        EQUIPMENT.addItem(SteamworkItems.STEAM_PICKAXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_AXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_SHOVEL);
        EQUIPMENT.addItem(SteamworkItems.STEAM_HOE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_BRONZE_SWORD);
        EQUIPMENT.addItem(SteamworkItems.STEAM_BRONZE_PICKAXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_BRONZE_AXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_BRONZE_SHOVEL);
        EQUIPMENT.addItem(SteamworkItems.STEAM_BRONZE_HOE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_INVAR_SWORD);
        EQUIPMENT.addItem(SteamworkItems.STEAM_INVAR_PICKAXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_INVAR_AXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_INVAR_SHOVEL);
        EQUIPMENT.addItem(SteamworkItems.STEAM_INVAR_HOE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_TUNGSTEN_SWORD);
        EQUIPMENT.addItem(SteamworkItems.STEAM_TUNGSTEN_PICKAXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_TUNGSTEN_AXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_TUNGSTEN_SHOVEL);
        EQUIPMENT.addItem(SteamworkItems.STEAM_TUNGSTEN_HOE);

        ROOT.addPage(SteamworkItems.STEAM_CHESTPLATE, ARMOR);
        ARMOR.addItem(SteamworkItems.STEAM_HELMET);
        ARMOR.addItem(SteamworkItems.STEAM_CHESTPLATE);
        ARMOR.addItem(SteamworkItems.STEAM_LEGGINGS);
        ARMOR.addItem(SteamworkItems.STEAM_BOOTS);
        ARMOR.addItem(SteamworkItems.STEAM_BRONZE_HELMET);
        ARMOR.addItem(SteamworkItems.STEAM_BRONZE_CHESTPLATE);
        ARMOR.addItem(SteamworkItems.STEAM_BRONZE_LEGGINGS);
        ARMOR.addItem(SteamworkItems.STEAM_BRONZE_BOOTS);
        ARMOR.addItem(SteamworkItems.STEAM_INVAR_HELMET);
        ARMOR.addItem(SteamworkItems.STEAM_INVAR_CHESTPLATE);
        ARMOR.addItem(SteamworkItems.STEAM_INVAR_LEGGINGS);
        ARMOR.addItem(SteamworkItems.STEAM_INVAR_BOOTS);
        ARMOR.addItem(SteamworkItems.STEAM_TUNGSTEN_HELMET);
        ARMOR.addItem(SteamworkItems.STEAM_TUNGSTEN_CHESTPLATE);
        ARMOR.addItem(SteamworkItems.STEAM_TUNGSTEN_LEGGINGS);
        ARMOR.addItem(SteamworkItems.STEAM_TUNGSTEN_BOOTS);

        ROOT.addPage(SteamworkItems.BRASS_FLOW_VALVE, STEAM_LOGISTICS);
        STEAM_LOGISTICS.addItem(SteamworkItems.STEAM_COMPRESSOR);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_CARGO_HUB);
        STEAM_LOGISTICS.addItem(SteamworkItems.STEAM_CATAPULT);
        STEAM_LOGISTICS.addItem(SteamworkItems.STEAM_SORTER);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_DUCT);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_INPUT);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_OUTPUT);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_GATE_VALVE);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_PRESSURE_MODULE);
        STEAM_LOGISTICS.addItem(SteamworkItems.PNEUMATIC_DISTRIBUTOR);

        ROOT.addPage(SteamworkItems.STEAM_ARM, STEAM_AUTOMATION);
        // ★ PRECISION_ADVANCED_AUTOMATION_1（学科门控）
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_PRECISION_MILL);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRECISION_FOUNDRY);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRECISION_CATALYTIC_REACTOR);
        STEAM_AUTOMATION.addItem(SteamworkItems.HEAVY_IMPACT_CRUSHER);
        STEAM_AUTOMATION.addItem(SteamworkItems.HYDRAULIC_FORGE);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRECISION_CRYSTALLIZER);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRECISION_CENTRIFUGE);
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_ARM);
        STEAM_AUTOMATION.addItem(SteamworkItems.MACHINE_CALIBRATOR);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRODUCTION_LINE_INLET);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRODUCTION_LINE_BUFFER_CHEST);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRODUCTION_LINE_OUTLET);
        STEAM_AUTOMATION.addItem(SteamworkItems.PRODUCTION_LINE_BLUEPRINT);
        // 汽动逻辑（Stage 2）
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_VORTEX_TUBE);
        STEAM_AUTOMATION.addItem(SteamworkItems.PNEUMATIC_LOGIC_GATE);
        STEAM_AUTOMATION.addItem(SteamworkItems.PNEUMATIC_DIFFERENTIAL_GATE);
        STEAM_AUTOMATION.addItem(SteamworkItems.PNEUMATIC_PULSER);
        STEAM_AUTOMATION.addItem(SteamworkItems.PNEUMATIC_LATCH);
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_OSCILLATOR);
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_PRESSURE_TRANSDUCER);
        STEAM_AUTOMATION.addItem(SteamworkItems.STEAM_DIFFERENCE_ENGINE);

        ROOT.addPage(SteamworkItems.MACHINE_CALIBRATOR, STEAM_CALIBRATION);
        STEAM_CALIBRATION.addItem(SteamworkItems.MACHINE_CALIBRATOR);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_ENERGY_SAVE);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_AUTO_INPUT);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_AUTO_OUTPUT);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_BULK);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_RANGE);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_BOOST);
        STEAM_CALIBRATION.addItem(SteamworkItems.UPGRADE_MODULE_PYLON_COMPAT);
        STEAM_CALIBRATION.addItem(SteamworkItems.AUTO_PRODUCTION_MODULE);

        ROOT.addPage(SteamworkItems.STEAM_DISTILLATION_TOWER, STEAM_DISTILLATION);
        // ★ CHEMISTRY_DISTILLATION（学科门控）
        STEAM_DISTILLATION.addItem(SteamworkItems.STEAM_DISTILLATION_TOWER);
        STEAM_DISTILLATION.addItem(SteamworkItems.DISTILLATION_TOWER_SECTION);
        STEAM_DISTILLATION.addItem(SteamworkItems.DISTILLATION_CONDENSER);

        ROOT.addFluid(SteamworkFluids.STEAM);
        ROOT.addFluid(SteamworkFluids.SUPERHEATED_STEAM);
        ROOT.addFluid(SteamworkFluids.PRESSURIZED_STEAM);
    }
}
