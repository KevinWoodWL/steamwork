package io.github.steamwork;

import io.github.pylonmc.rebar.content.guide.RebarGuide;
import io.github.pylonmc.rebar.guide.button.AddonPageButton;
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage;
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
    public static final SimpleStaticGuidePage STEAM_DISTILLATION = new SimpleStaticGuidePage(steamworkKey("steam_distillation"));

    public static void initialize() {
        RebarGuide.getRootPage().addButton(new AddonPageButton(Steamwork.getInstance(), ROOT));

        ROOT.addPage(SteamworkItems.ZINC_INGOT, METALS);
        METALS.addItem(SteamworkItems.ZINC_INGOT);
        METALS.addItem(SteamworkItems.BRASS_INGOT);
        METALS.addItem(SteamworkItems.NICHROME_DUST);
        METALS.addItem(SteamworkItems.NICHROME_INGOT);
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

        ROOT.addPage(SteamworkItems.RUBBER_GASKET, BASIC_MATERIALS);
        BASIC_MATERIALS.addItem(SteamworkItems.ZINC_CONCENTRATE);
        BASIC_MATERIALS.addItem(SteamworkItems.SILICA_GRIT);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_FLUX);
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
        BASIC_MATERIALS.addItem(SteamworkItems.ANALYSIS_RESIDUE);
        BASIC_MATERIALS.addItem(SteamworkItems.REFINED_RESIN);
        BASIC_MATERIALS.addItem(SteamworkItems.PLANT_ESSENCE);
        BASIC_MATERIALS.addItem(SteamworkItems.DISTILLED_WATER_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_LEACHATE_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.WASTE_ACID_VIAL);
        BASIC_MATERIALS.addItem(SteamworkItems.MINERAL_CONCENTRATE);
        BASIC_MATERIALS.addItem(SteamworkItems.FIBER_RESIDUE);

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
        COMPONENTS.addItem(SteamworkItems.PRECISION_GEAR);
        COMPONENTS.addItem(SteamworkItems.PRECISION_SCREW);
        COMPONENTS.addItem(SteamworkItems.PRECISION_VALVE);
        COMPONENTS.addItem(SteamworkItems.WEAR_PLATE);
        COMPONENTS.addItem(SteamworkItems.HEAT_SINK);
        COMPONENTS.addItem(SteamworkItems.MILLING_BLADE);
        COMPONENTS.addItem(SteamworkItems.CATALYST_CORE);
        COMPONENTS.addItem(SteamworkItems.PRECISION_BEARING);
        COMPONENTS.addItem(SteamworkItems.DISTILLATION_TOWER_SECTION);
        COMPONENTS.addItem(SteamworkItems.DISTILLATION_CONDENSER);
        COMPONENTS.addItem(SteamworkItems.HEATING_COIL);

        ROOT.addPage(new ItemStack(Material.CAMPFIRE), HEAT_SOURCES);
        HEAT_SOURCES.addItem(new ItemStack(Material.CAMPFIRE));
        HEAT_SOURCES.addItem(new ItemStack(Material.SOUL_CAMPFIRE));
        HEAT_SOURCES.addItem(new ItemStack(Material.LAVA_BUCKET));
        HEAT_SOURCES.addItem(new ItemStack(Material.MAGMA_BLOCK));

        ROOT.addPage(SteamworkItems.BRONZE_BOILER, STEAM_MACHINES);
        STEAM_MACHINES.addItem(SteamworkItems.BRONZE_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.INVAR_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.MANGANESE_STEEL_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.TUNGSTEN_BOILER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_SCIENCE_INTERFACE);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_STERILIZER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_STEEPING_VAT);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_WASHING_TROUGH);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_PRESS);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_GRINDER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_PRECISION_MILL);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_PRESSURIZED_FURNACE);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_ARM);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_ASSEMBLY_BENCH);
        STEAM_MACHINES.addItem(SteamworkItems.SIMPLE_STEAM_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.ADVANCED_STEAM_TURBINE);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_HEATING_CHAMBER);
        STEAM_MACHINES.addItem(SteamworkItems.STEAM_DISTILLATION_TOWER);

        ROOT.addPage(SteamworkItems.STEAM_SWORD, EQUIPMENT);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_BRASS);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_INVAR);
        EQUIPMENT.addItem(SteamworkItems.STEAM_CANISTER_TUNGSTEN);
        EQUIPMENT.addItem(SteamworkItems.STEAM_SWORD);
        EQUIPMENT.addItem(SteamworkItems.STEAM_PICKAXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_AXE);
        EQUIPMENT.addItem(SteamworkItems.STEAM_SHOVEL);
        EQUIPMENT.addItem(SteamworkItems.STEAM_HOE);

        ROOT.addPage(SteamworkItems.STEAM_CHESTPLATE, ARMOR);
        ARMOR.addItem(SteamworkItems.STEAM_HELMET);
        ARMOR.addItem(SteamworkItems.STEAM_CHESTPLATE);
        ARMOR.addItem(SteamworkItems.STEAM_LEGGINGS);
        ARMOR.addItem(SteamworkItems.STEAM_BOOTS);

        ROOT.addPage(SteamworkItems.BRASS_FLOW_VALVE, STEAM_LOGISTICS);

        ROOT.addPage(SteamworkItems.STEAM_ARM, STEAM_AUTOMATION);

        ROOT.addPage(SteamworkItems.STEAM_DISTILLATION_TOWER, STEAM_DISTILLATION);

        ROOT.addFluid(SteamworkFluids.STEAM);
        ROOT.addFluid(SteamworkFluids.SUPERHEATED_STEAM);
    }
}
