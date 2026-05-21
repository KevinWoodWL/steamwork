package io.github.steamwork;

import io.github.pylonmc.rebar.item.research.Research;
import io.github.pylonmc.rebar.registry.RebarRegistry;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteamworkResearches {

    private SteamworkResearches() {
        throw new AssertionError("Utility class");
    }

    public static final Research MINERAL_REFINING = new Research(
            steamworkKey("mineral_refining"), SteamworkItems.ZINC_CONCENTRATE, 25L,
            SteamworkKeys.ZINC_CONCENTRATE, SteamworkKeys.SILICA_GRIT, SteamworkKeys.MINERAL_FLUX,
            SteamworkKeys.ZINC_INGOT);
    public static final Research BASIC_STEAM_SCIENCE = new Research(
            steamworkKey("basic_steam_science"), SteamworkItems.BRASS_INGOT, 50L,
            SteamworkKeys.BRASS_INGOT, SteamworkKeys.PLANT_FIBER, SteamworkKeys.STEAM_PULP,
            SteamworkKeys.RAW_RESIN, SteamworkKeys.VULCANIZED_RUBBER, SteamworkKeys.STERILE_BIOMASS,
            SteamworkKeys.TREATED_WOOD, SteamworkKeys.FIBERBOARD, SteamworkKeys.RUBBERIZED_FABRIC,
            SteamworkKeys.STERILE_CULTURE,
            SteamworkKeys.RUBBER_GASKET);
    public static final Research PRESSURE_MEASUREMENT = new Research(
            steamworkKey("pressure_measurement"), SteamworkItems.PRESSURE_GAUGE, 75L, SteamworkKeys.PRESSURE_GAUGE);
    public static final Research MECHANICAL_TRANSMISSION = new Research(
            steamworkKey("mechanical_transmission"), SteamworkItems.BRASS_GEAR, 100L, SteamworkKeys.BRASS_GEAR);
    public static final Research BOILER_TECHNOLOGY = new Research(
            steamworkKey("boiler_technology"), SteamworkItems.BRONZE_BOILER, 120L,
            SteamworkKeys.BRONZE_BOILER);
    public static final Research STEAM_AUTOMATION = new Research(
            steamworkKey("steam_automation"), SteamworkItems.STEAM_ARM, 150L,
            SteamworkKeys.STEAM_ARM, SteamworkKeys.STEAM_STERILIZER,
            SteamworkKeys.STEAM_STEEPING_VAT, SteamworkKeys.STEAM_WASHING_TROUGH);
    public static final Research STEAM_EQUIPMENT = new Research(
            steamworkKey("steam_equipment"), SteamworkItems.STEAM_SWORD, 200L,
            SteamworkKeys.STEAM_SWORD, SteamworkKeys.STEAM_PICKAXE, SteamworkKeys.STEAM_AXE,
            SteamworkKeys.STEAM_SHOVEL, SteamworkKeys.STEAM_HOE);
    public static final Research STEAM_ARMOR = new Research(
            steamworkKey("steam_armor"), SteamworkItems.STEAM_CHESTPLATE, 250L,
            SteamworkKeys.STEAM_HELMET, SteamworkKeys.STEAM_CHESTPLATE,
            SteamworkKeys.STEAM_LEGGINGS, SteamworkKeys.STEAM_BOOTS);

    // Unlocks the machine itself.
    public static final Research PRESSURIZED_FURNACE = new Research(
            steamworkKey("pressurized_furnace"), SteamworkItems.STEAM_PRESSURIZED_FURNACE, 280L,
            SteamworkKeys.STEAM_PRESSURIZED_FURNACE);

    // Unlocks advanced alloys separately from the machine.
    public static final Research ADVANCED_INGOTS = new Research(
            steamworkKey("advanced_ingots"), SteamworkItems.INVAR_INGOT, 320L,
            SteamworkKeys.INVAR_DUST, SteamworkKeys.DURALUMIN_DUST, SteamworkKeys.TUNGSTEN_DUST,
            SteamworkKeys.MANGANESE_STEEL_DUST, SteamworkKeys.MANGANESE_BRONZE_DUST,
            SteamworkKeys.INVAR_INGOT, SteamworkKeys.DURALUMIN_INGOT, SteamworkKeys.TUNGSTEN_INGOT,
            SteamworkKeys.MANGANESE_STEEL_INGOT, SteamworkKeys.MANGANESE_BRONZE_INGOT,
            SteamworkKeys.INVAR_BLOCK, SteamworkKeys.DURALUMIN_BLOCK, SteamworkKeys.TUNGSTEN_BLOCK,
            SteamworkKeys.MANGANESE_STEEL_BLOCK, SteamworkKeys.MANGANESE_BRONZE_BLOCK);
    public static final Research ADVANCED_BOILERS = new Research(
            steamworkKey("advanced_boilers"), SteamworkItems.INVAR_BOILER, 360L,
            SteamworkKeys.INVAR_BOILER, SteamworkKeys.MANGANESE_STEEL_BOILER, SteamworkKeys.TUNGSTEN_BOILER);

    public static final Research ADVANCED_ALLOYS = new Research(
            steamworkKey("advanced_alloys"), SteamworkItems.NICHROME_INGOT, 380L,
            SteamworkKeys.NICHROME_DUST, SteamworkKeys.NICHROME_INGOT, SteamworkKeys.HEATING_COIL);
    public static final Research STEAM_TURBINE_TECH = new Research(
            steamworkKey("steam_turbine_tech"), SteamworkItems.SIMPLE_STEAM_TURBINE, 420L, 
            SteamworkKeys.SIMPLE_STEAM_TURBINE, SteamworkKeys.ADVANCED_STEAM_TURBINE);

    public static void initialize() {
        MINERAL_REFINING.register();
        BASIC_STEAM_SCIENCE.register();
        PRESSURE_MEASUREMENT.register();
        MECHANICAL_TRANSMISSION.register();
        BOILER_TECHNOLOGY.register();
        STEAM_AUTOMATION.register();
        STEAM_EQUIPMENT.register();
        STEAM_ARMOR.register();
        PRESSURIZED_FURNACE.register();
        ADVANCED_INGOTS.register();
        ADVANCED_BOILERS.register();
        ADVANCED_ALLOYS.register();
        STEAM_TURBINE_TECH.register();

        RebarRegistry.RESEARCHES.mapKey(steamworkKey("simple_steam_turbine"), STEAM_TURBINE_TECH.getKey());
        RebarRegistry.RESEARCHES.mapKey(steamworkKey("advanced_steam_turbine"), STEAM_TURBINE_TECH.getKey());
        RebarRegistry.RESEARCHES.mapKey(steamworkKey("pressure_alloys"), PRESSURIZED_FURNACE.getKey());
    }
}
