package io.github.steamwork;

import io.github.steamwork.content.equipment.SteamCanister;
import io.github.steamwork.content.equipment.SteamEquipment;
import io.github.steamwork.content.machines.SimpleSteamTurbine;
import io.github.steamwork.content.machines.SteamAssemblyBench;
import io.github.steamwork.content.machines.AdvancedSteamTurbine;
import io.github.steamwork.content.machines.BronzeBoiler;
import io.github.steamwork.content.machines.InvarBoiler;
import io.github.steamwork.content.machines.ManganeseSteelBoiler;
import io.github.steamwork.content.machines.SteamArm;
import io.github.steamwork.content.machines.SteamGrinder;
import io.github.steamwork.content.machines.SteamHeatingChamber;
import io.github.steamwork.content.machines.SteamPress;
import io.github.steamwork.content.machines.SteamPressurizedFurnace;
import io.github.steamwork.content.machines.SteamScienceInterface;
import io.github.steamwork.content.machines.SteamPrecisionMill;
import io.github.steamwork.content.machines.SteamSteepingVat;
import io.github.steamwork.content.machines.SteamSterilizer;
import io.github.steamwork.content.machines.SteamDistillationTower;
import io.github.steamwork.content.machines.SteamWashingTrough;
import io.github.steamwork.content.machines.TungstenBoiler;
import io.github.steamwork.recipes.SteamDistillationRecipe;
import io.github.steamwork.recipes.SteamGrindingRecipe;
import io.github.steamwork.recipes.SteamPressingRecipe;
import io.github.steamwork.recipes.SteamPressurizingRecipe;
import io.github.steamwork.recipes.SteamResearchRecipe;
import io.github.steamwork.recipes.SteamSteepingRecipe;
import io.github.steamwork.recipes.SteamSterilizingRecipe;
import io.github.steamwork.recipes.SteamWashingRecipe;
import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.content.guide.RebarGuide;
import io.github.pylonmc.rebar.guide.button.MachineRecipesButton;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.RebarUtils;
import io.github.steamwork.util.SteamCharge;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Field;

public final class SteamworkItems {

    private SteamworkItems() {
        throw new AssertionError("Utility class");
    }

    // Base materials
    public static final ItemStack BRASS_INGOT = ItemStackBuilder.rebar(Material.COPPER_INGOT, SteamworkKeys.BRASS_INGOT).build();
    public static final ItemStack RUBBER_GASKET = ItemStackBuilder.rebar(Material.BLACK_DYE, SteamworkKeys.RUBBER_GASKET).build();
    public static final ItemStack PRESSURE_GAUGE = ItemStackBuilder.rebar(Material.CLOCK, SteamworkKeys.PRESSURE_GAUGE).build();
    public static final ItemStack ZINC_CONCENTRATE = ItemStackBuilder.rebar(Material.GLOWSTONE_DUST, SteamworkKeys.ZINC_CONCENTRATE).build();
    public static final ItemStack PLANT_FIBER = ItemStackBuilder.rebar(Material.STRING, SteamworkKeys.PLANT_FIBER).build();
    public static final ItemStack STEAM_PULP = ItemStackBuilder.rebar(Material.PAPER, SteamworkKeys.STEAM_PULP).build();
    public static final ItemStack RAW_RESIN = ItemStackBuilder.rebar(Material.HONEYCOMB, SteamworkKeys.RAW_RESIN).build();
    public static final ItemStack VULCANIZED_RUBBER = ItemStackBuilder.rebar(Material.BLACK_DYE, SteamworkKeys.VULCANIZED_RUBBER).build();
    public static final ItemStack STERILE_BIOMASS = ItemStackBuilder.rebar(Material.BONE_MEAL, SteamworkKeys.STERILE_BIOMASS).build();
    public static final ItemStack SILICA_GRIT = ItemStackBuilder.rebar(Material.QUARTZ, SteamworkKeys.SILICA_GRIT).build();
    public static final ItemStack MINERAL_FLUX = ItemStackBuilder.rebar(Material.SUGAR, SteamworkKeys.MINERAL_FLUX).build();
    public static final ItemStack TREATED_WOOD = ItemStackBuilder.rebar(Material.STICK, SteamworkKeys.TREATED_WOOD).build();
    public static final ItemStack FIBERBOARD = ItemStackBuilder.rebar(Material.BROWN_DYE, SteamworkKeys.FIBERBOARD).build();
    public static final ItemStack RUBBERIZED_FABRIC = ItemStackBuilder.rebar(Material.GRAY_DYE, SteamworkKeys.RUBBERIZED_FABRIC).build();
    public static final ItemStack STERILE_CULTURE = ItemStackBuilder.rebar(Material.GLOWSTONE_DUST, SteamworkKeys.STERILE_CULTURE).build();
    public static final ItemStack MACHINE_SCRAP = ItemStackBuilder.rebar(Material.IRON_NUGGET, SteamworkKeys.MACHINE_SCRAP).build();
    public static final ItemStack ANALYSIS_RESIDUE = ItemStackBuilder.rebar(Material.GRAY_DYE, SteamworkKeys.ANALYSIS_RESIDUE).build();
    public static final ItemStack REFINED_RESIN = ItemStackBuilder.rebar(Material.HONEYCOMB, SteamworkKeys.REFINED_RESIN).build();
    public static final ItemStack PLANT_ESSENCE = ItemStackBuilder.rebar(Material.LIME_DYE, SteamworkKeys.PLANT_ESSENCE).build();
    public static final ItemStack DISTILLED_WATER_VIAL = ItemStackBuilder.rebar(Material.POTION, SteamworkKeys.DISTILLED_WATER_VIAL).build();
    public static final ItemStack MINERAL_LEACHATE_VIAL = ItemStackBuilder.rebar(Material.LIME_DYE, SteamworkKeys.MINERAL_LEACHATE_VIAL).build();
    public static final ItemStack WASTE_ACID_VIAL = ItemStackBuilder.rebar(Material.GREEN_DYE, SteamworkKeys.WASTE_ACID_VIAL).build();
    public static final ItemStack MINERAL_CONCENTRATE = ItemStackBuilder.rebar(Material.PRISMARINE_CRYSTALS, SteamworkKeys.MINERAL_CONCENTRATE).build();
    public static final ItemStack FIBER_RESIDUE = ItemStackBuilder.rebar(Material.BROWN_DYE, SteamworkKeys.FIBER_RESIDUE).build();
    public static final ItemStack HEAT_TREATED_METAL = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.HEAT_TREATED_METAL).build();
    public static final ItemStack MINERAL_ANALYSIS_SAMPLE = ItemStackBuilder.rebar(Material.AMETHYST_SHARD, SteamworkKeys.MINERAL_ANALYSIS_SAMPLE).build();
    public static final ItemStack ORGANIC_ANALYSIS_SAMPLE = ItemStackBuilder.rebar(Material.FERMENTED_SPIDER_EYE, SteamworkKeys.ORGANIC_ANALYSIS_SAMPLE).build();
    public static final ItemStack METALLURGICAL_ANALYSIS_SAMPLE = ItemStackBuilder.rebar(Material.NETHERITE_SCRAP, SteamworkKeys.METALLURGICAL_ANALYSIS_SAMPLE).build();
    public static final ItemStack FLUID_ANALYSIS_SAMPLE = ItemStackBuilder.rebar(Material.EXPERIENCE_BOTTLE, SteamworkKeys.FLUID_ANALYSIS_SAMPLE).build();

    // Materials
    public static final ItemStack ZINC_INGOT = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.ZINC_INGOT).build();
    public static final ItemStack NICHROME_DUST = ItemStackBuilder.rebar(Material.REDSTONE, SteamworkKeys.NICHROME_DUST).build();
    public static final ItemStack NICHROME_INGOT = ItemStackBuilder.rebar(Material.NETHERITE_SCRAP, SteamworkKeys.NICHROME_INGOT).build();

    // Pressurized furnace alloys
    public static final ItemStack INVAR_DUST = ItemStackBuilder.rebar(Material.GUNPOWDER, SteamworkKeys.INVAR_DUST).build();
    public static final ItemStack DURALUMIN_DUST = ItemStackBuilder.rebar(Material.SUGAR, SteamworkKeys.DURALUMIN_DUST).build();
    public static final ItemStack TUNGSTEN_DUST = ItemStackBuilder.rebar(Material.GLOWSTONE_DUST, SteamworkKeys.TUNGSTEN_DUST).build();
    public static final ItemStack MANGANESE_STEEL_DUST = ItemStackBuilder.rebar(Material.GUNPOWDER, SteamworkKeys.MANGANESE_STEEL_DUST).build();
    public static final ItemStack MANGANESE_BRONZE_DUST = ItemStackBuilder.rebar(Material.GLOWSTONE_DUST, SteamworkKeys.MANGANESE_BRONZE_DUST).build();
    public static final ItemStack INVAR_INGOT = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.INVAR_INGOT).build();
    public static final ItemStack DURALUMIN_INGOT = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.DURALUMIN_INGOT).build();
    public static final ItemStack TUNGSTEN_INGOT = ItemStackBuilder.rebar(Material.NETHERITE_SCRAP, SteamworkKeys.TUNGSTEN_INGOT).build();
    public static final ItemStack MANGANESE_STEEL_INGOT = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.MANGANESE_STEEL_INGOT).build();
    public static final ItemStack MANGANESE_BRONZE_INGOT = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.MANGANESE_BRONZE_INGOT).build();

    // Components
    public static final ItemStack BRASS_GEAR = ItemStackBuilder.rebar(Material.GOLD_NUGGET, SteamworkKeys.BRASS_GEAR).build();
    public static final ItemStack HEATING_COIL = ItemStackBuilder.rebar(Material.REDSTONE, SteamworkKeys.HEATING_COIL).build();

    // Brass machine components
    public static final ItemStack BRASS_DISTILLATION_TUBE = ItemStackBuilder.rebar(Material.BLAZE_ROD, SteamworkKeys.BRASS_DISTILLATION_TUBE).build();
    public static final ItemStack BRASS_FILTER = ItemStackBuilder.rebar(Material.PAPER, SteamworkKeys.BRASS_FILTER).build();
    public static final ItemStack BRASS_SIEVE = ItemStackBuilder.rebar(Material.STRING, SteamworkKeys.BRASS_SIEVE).build();
    public static final ItemStack BRASS_FLOW_VALVE = ItemStackBuilder.rebar(Material.HONEYCOMB, SteamworkKeys.BRASS_FLOW_VALVE).build();
    public static final ItemStack BRASS_FAN_BLADE = ItemStackBuilder.rebar(Material.FEATHER, SteamworkKeys.BRASS_FAN_BLADE).build();
    public static final ItemStack BRASS_VALVE_CORE = ItemStackBuilder.rebar(Material.PRISMARINE_SHARD, SteamworkKeys.BRASS_VALVE_CORE).build();
    public static final ItemStack BRASS_SEAL_RING = ItemStackBuilder.rebar(Material.RABBIT_HIDE, SteamworkKeys.BRASS_SEAL_RING).build();

    // Block items
    public static final ItemStack BRONZE_BOILER = ItemStackBuilder.rebar(Material.COPPER_BLOCK, SteamworkKeys.BRONZE_BOILER).build();
    public static final ItemStack INVAR_BOILER = ItemStackBuilder.rebar(Material.IRON_BLOCK, SteamworkKeys.INVAR_BOILER).build();
    public static final ItemStack MANGANESE_STEEL_BOILER = ItemStackBuilder.rebar(Material.IRON_BLOCK, SteamworkKeys.MANGANESE_STEEL_BOILER).build();
    public static final ItemStack TUNGSTEN_BOILER = ItemStackBuilder.rebar(Material.NETHERITE_BLOCK, SteamworkKeys.TUNGSTEN_BOILER).build();
    public static final ItemStack STEAM_ARM = ItemStackBuilder.rebar(Material.COPPER_BLOCK, SteamworkKeys.STEAM_ARM).build();
    public static final ItemStack SIMPLE_STEAM_TURBINE = ItemStackBuilder.rebar(Material.FURNACE, SteamworkKeys.SIMPLE_STEAM_TURBINE).build();
    public static final ItemStack ADVANCED_STEAM_TURBINE = ItemStackBuilder.rebar(Material.BLAST_FURNACE, SteamworkKeys.ADVANCED_STEAM_TURBINE).build();
    public static final ItemStack STEAM_STERILIZER = ItemStackBuilder.rebar(Material.BARREL, SteamworkKeys.STEAM_STERILIZER).build();
    public static final ItemStack STEAM_STEEPING_VAT = ItemStackBuilder.rebar(Material.CAULDRON, SteamworkKeys.STEAM_STEEPING_VAT).build();
    public static final ItemStack STEAM_WASHING_TROUGH = ItemStackBuilder.rebar(Material.CAULDRON, SteamworkKeys.STEAM_WASHING_TROUGH).build();
    public static final ItemStack STEAM_PRESS = ItemStackBuilder.rebar(Material.IRON_BLOCK, SteamworkKeys.STEAM_PRESS).build();
    public static final ItemStack STEAM_GRINDER = ItemStackBuilder.rebar(Material.GRINDSTONE, SteamworkKeys.STEAM_GRINDER).build();
    public static final ItemStack STEAM_PRESSURIZED_FURNACE = ItemStackBuilder.rebar(Material.CUT_COPPER_SLAB, SteamworkKeys.STEAM_PRESSURIZED_FURNACE).build();
    public static final ItemStack STEAM_ASSEMBLY_BENCH = ItemStackBuilder.rebar(Material.SMITHING_TABLE, SteamworkKeys.STEAM_ASSEMBLY_BENCH).build();
    public static final ItemStack STEAM_SCIENCE_INTERFACE = ItemStackBuilder.rebar(Material.LECTERN, SteamworkKeys.STEAM_SCIENCE_INTERFACE).build();
    public static final ItemStack STEAM_HEATING_CHAMBER = ItemStackBuilder.rebar(Material.BLAST_FURNACE, SteamworkKeys.STEAM_HEATING_CHAMBER).build();
    public static final ItemStack STEAM_DISTILLATION_TOWER = ItemStackBuilder.rebar(Material.CAULDRON, SteamworkKeys.STEAM_DISTILLATION_TOWER).build();
    public static final ItemStack DISTILLATION_TOWER_SECTION = ItemStackBuilder.rebar(Material.LIGHT_GRAY_STAINED_GLASS, SteamworkKeys.DISTILLATION_TOWER_SECTION).build();
    public static final ItemStack DISTILLATION_CONDENSER = ItemStackBuilder.rebar(Material.CUT_COPPER, SteamworkKeys.DISTILLATION_CONDENSER).build();

    // Alloy blocks
    public static final ItemStack INVAR_BLOCK = ItemStackBuilder.rebar(Material.IRON_BLOCK, SteamworkKeys.INVAR_BLOCK).build();
    public static final ItemStack DURALUMIN_BLOCK = ItemStackBuilder.rebar(Material.COPPER_BLOCK, SteamworkKeys.DURALUMIN_BLOCK).build();
    public static final ItemStack TUNGSTEN_BLOCK = ItemStackBuilder.rebar(Material.NETHERITE_BLOCK, SteamworkKeys.TUNGSTEN_BLOCK).build();
    public static final ItemStack MANGANESE_STEEL_BLOCK = ItemStackBuilder.rebar(Material.IRON_BLOCK, SteamworkKeys.MANGANESE_STEEL_BLOCK).build();
    public static final ItemStack MANGANESE_BRONZE_BLOCK = ItemStackBuilder.rebar(Material.COPPER_BLOCK, SteamworkKeys.MANGANESE_BRONZE_BLOCK).build();

    // Machine components
    public static final ItemStack STEAM_MOTOR = ItemStackBuilder.rebar(Material.CLOCK, SteamworkKeys.STEAM_MOTOR).build();

    // Precision mill machine block
    public static final ItemStack STEAM_PRECISION_MILL = ItemStackBuilder.rebar(Material.GRINDSTONE, SteamworkKeys.STEAM_PRECISION_MILL).build();

    // Precision mill products
    public static final ItemStack PRECISION_GEAR = ItemStackBuilder.rebar(Material.IRON_NUGGET, SteamworkKeys.PRECISION_GEAR).build();
    public static final ItemStack PRECISION_SCREW = ItemStackBuilder.rebar(Material.IRON_NUGGET, SteamworkKeys.PRECISION_SCREW).build();
    public static final ItemStack PRECISION_VALVE = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.PRECISION_VALVE).build();
    public static final ItemStack WEAR_PLATE = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.WEAR_PLATE).build();
    public static final ItemStack HEAT_SINK = ItemStackBuilder.rebar(Material.IRON_INGOT, SteamworkKeys.HEAT_SINK).build();
    public static final ItemStack MILLING_BLADE = ItemStackBuilder.rebar(Material.IRON_SWORD, SteamworkKeys.MILLING_BLADE).build();
    public static final ItemStack CATALYST_CORE = ItemStackBuilder.rebar(Material.NETHER_STAR, SteamworkKeys.CATALYST_CORE).build();
    public static final ItemStack PRECISION_BEARING = ItemStackBuilder.rebar(Material.IRON_NUGGET, SteamworkKeys.PRECISION_BEARING).build();

    // Steam weapons / tools / armor — each carries a steam buffer in PDC (see SteamCharge).
    // Capacity baked in template so crafting copies it; main-thread runtime can read amount/capacity uniformly.
    public static final ItemStack STEAM_SWORD = withSteamCapacity(ItemStackBuilder
            .rebarWeapon(Material.IRON_SWORD, SteamworkKeys.STEAM_SWORD, true, false, false).build(), 2000.0);

    public static final ItemStack STEAM_PICKAXE = withSteamCapacity(ItemStackBuilder
            .rebarToolWeapon(Material.IRON_PICKAXE, SteamworkKeys.STEAM_PICKAXE, RebarUtils.pickaxeMineable(), true, false, false).build(), 2500.0);
    public static final ItemStack STEAM_AXE = withSteamCapacity(ItemStackBuilder
            .rebarToolWeapon(Material.IRON_AXE, SteamworkKeys.STEAM_AXE, RebarUtils.axeMineable(), true, false, false).build(), 2500.0);
    public static final ItemStack STEAM_SHOVEL = withSteamCapacity(ItemStackBuilder
            .rebarToolWeapon(Material.IRON_SHOVEL, SteamworkKeys.STEAM_SHOVEL, RebarUtils.shovelMineable(), true, false, false).build(), 1800.0);
    public static final ItemStack STEAM_HOE = withSteamCapacity(ItemStackBuilder
            .rebarToolWeapon(Material.IRON_HOE, SteamworkKeys.STEAM_HOE, RebarUtils.hoeMineable(), true, false, false).build(), 1500.0);

    public static final ItemStack STEAM_HELMET = withSteamCapacity(
            ItemStackBuilder.rebarHelmet(Material.IRON_HELMET, SteamworkKeys.STEAM_HELMET, true).build(), 1500.0);
    public static final ItemStack STEAM_CHESTPLATE = withSteamCapacity(
            ItemStackBuilder.rebarChestplate(Material.IRON_CHESTPLATE, SteamworkKeys.STEAM_CHESTPLATE, true).build(), 2500.0);
    public static final ItemStack STEAM_LEGGINGS = withSteamCapacity(
            ItemStackBuilder.rebarLeggings(Material.IRON_LEGGINGS, SteamworkKeys.STEAM_LEGGINGS, true).build(), 1500.0);
    public static final ItemStack STEAM_BOOTS = withSteamCapacity(
            ItemStackBuilder.rebarBoots(Material.IRON_BOOTS, SteamworkKeys.STEAM_BOOTS, true).build(), 2000.0);

    // Portable steam energy. Each canister's capacity is baked into the template PDC via
    // SteamCharge.initIfMissing — crafting copies the PDC, so each crafted stack starts at 0/capacity.
    public static final ItemStack STEAM_CANISTER_BRASS = withSteamCapacity(
            ItemStackBuilder.rebar(Material.HONEY_BOTTLE, SteamworkKeys.STEAM_CANISTER_BRASS).build(), 2000.0);
    public static final ItemStack STEAM_CANISTER_INVAR = withSteamCapacity(
            ItemStackBuilder.rebar(Material.GLASS_BOTTLE, SteamworkKeys.STEAM_CANISTER_INVAR).build(), 5000.0);
    public static final ItemStack STEAM_CANISTER_TUNGSTEN = withSteamCapacity(
            ItemStackBuilder.rebar(Material.EXPERIENCE_BOTTLE, SteamworkKeys.STEAM_CANISTER_TUNGSTEN).build(), 12000.0);

    private static ItemStack withSteamCapacity(ItemStack stack, double capacity) {
        SteamCharge.initIfMissing(stack, capacity);
        return stack;
    }

    private static void addOptionalPylonGuideItem(io.github.pylonmc.rebar.guide.pages.base.SimpleInfoPage page, String fieldName) {
        ItemStack stack = getOptionalPylonItem(fieldName);
        if (stack != null) {
            page.addItem(stack);
        }
    }

    private static ItemStack getOptionalPylonItem(String fieldName) {
        try {
            Field field = PylonItems.class.getField(fieldName);
            Object value = field.get(null);
            return value instanceof ItemStack itemStack ? itemStack : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public static void initialize() {
        RebarItem.register(RebarItem.class, BRASS_INGOT);
        RebarItem.register(RebarItem.class, RUBBER_GASKET);
        RebarItem.register(RebarItem.class, PRESSURE_GAUGE);
        RebarItem.register(RebarItem.class, ZINC_CONCENTRATE);
        RebarItem.register(RebarItem.class, PLANT_FIBER);
        RebarItem.register(RebarItem.class, STEAM_PULP);
        RebarItem.register(RebarItem.class, RAW_RESIN);
        RebarItem.register(RebarItem.class, VULCANIZED_RUBBER);
        RebarItem.register(RebarItem.class, STERILE_BIOMASS);
        RebarItem.register(RebarItem.class, SILICA_GRIT);
        RebarItem.register(RebarItem.class, MINERAL_FLUX);
        RebarItem.register(RebarItem.class, TREATED_WOOD);
        RebarItem.register(RebarItem.class, FIBERBOARD);
        RebarItem.register(RebarItem.class, RUBBERIZED_FABRIC);
        RebarItem.register(RebarItem.class, STERILE_CULTURE);
        RebarItem.register(RebarItem.class, MACHINE_SCRAP);
        RebarItem.register(RebarItem.class, ANALYSIS_RESIDUE);
        RebarItem.register(RebarItem.class, REFINED_RESIN);
        RebarItem.register(RebarItem.class, PLANT_ESSENCE);
        RebarItem.register(RebarItem.class, DISTILLED_WATER_VIAL);
        RebarItem.register(RebarItem.class, MINERAL_LEACHATE_VIAL);
        RebarItem.register(RebarItem.class, WASTE_ACID_VIAL);
        RebarItem.register(RebarItem.class, MINERAL_CONCENTRATE);
        RebarItem.register(RebarItem.class, FIBER_RESIDUE);
        RebarItem.register(RebarItem.class, HEAT_TREATED_METAL);
        RebarItem.register(RebarItem.class, MINERAL_ANALYSIS_SAMPLE);
        RebarItem.register(RebarItem.class, ORGANIC_ANALYSIS_SAMPLE);
        RebarItem.register(RebarItem.class, METALLURGICAL_ANALYSIS_SAMPLE);
        RebarItem.register(RebarItem.class, FLUID_ANALYSIS_SAMPLE);
        RebarItem.register(RebarItem.class, ZINC_INGOT);
        RebarItem.register(RebarItem.class, NICHROME_DUST);
        RebarItem.register(RebarItem.class, NICHROME_INGOT);

        // Pressurized furnace alloys
        RebarItem.register(RebarItem.class, INVAR_DUST);
        RebarItem.register(RebarItem.class, DURALUMIN_DUST);
        RebarItem.register(RebarItem.class, TUNGSTEN_DUST);
        RebarItem.register(RebarItem.class, MANGANESE_STEEL_DUST);
        RebarItem.register(RebarItem.class, MANGANESE_BRONZE_DUST);
        RebarItem.register(RebarItem.class, INVAR_INGOT);
        RebarItem.register(RebarItem.class, DURALUMIN_INGOT);
        RebarItem.register(RebarItem.class, TUNGSTEN_INGOT);
        RebarItem.register(RebarItem.class, MANGANESE_STEEL_INGOT);
        RebarItem.register(RebarItem.class, MANGANESE_BRONZE_INGOT);

        RebarItem.register(RebarItem.class, BRASS_GEAR);
        RebarItem.register(RebarItem.class, HEATING_COIL);

        // Brass machine components
        RebarItem.register(RebarItem.class, BRASS_DISTILLATION_TUBE);
        RebarItem.register(RebarItem.class, BRASS_FILTER);
        RebarItem.register(RebarItem.class, BRASS_SIEVE);
        RebarItem.register(RebarItem.class, BRASS_FLOW_VALVE);
        RebarItem.register(RebarItem.class, BRASS_FAN_BLADE);
        RebarItem.register(RebarItem.class, BRASS_VALVE_CORE);
        RebarItem.register(RebarItem.class, BRASS_SEAL_RING);

        RebarItem.register(BronzeBoiler.Item.class, BRONZE_BOILER, SteamworkKeys.BRONZE_BOILER);
        RebarItem.register(InvarBoiler.Item.class, INVAR_BOILER, SteamworkKeys.INVAR_BOILER);
        RebarItem.register(ManganeseSteelBoiler.Item.class, MANGANESE_STEEL_BOILER, SteamworkKeys.MANGANESE_STEEL_BOILER);
        RebarItem.register(TungstenBoiler.Item.class, TUNGSTEN_BOILER, SteamworkKeys.TUNGSTEN_BOILER);
        RebarItem.register(SteamArm.Item.class, STEAM_ARM, SteamworkKeys.STEAM_ARM);
        RebarItem.register(SimpleSteamTurbine.Item.class, SIMPLE_STEAM_TURBINE, SteamworkKeys.SIMPLE_STEAM_TURBINE);
        RebarItem.register(AdvancedSteamTurbine.Item.class, ADVANCED_STEAM_TURBINE, SteamworkKeys.ADVANCED_STEAM_TURBINE);
        RebarItem.register(SteamSterilizer.Item.class, STEAM_STERILIZER, SteamworkKeys.STEAM_STERILIZER);
        RebarItem.register(SteamSteepingVat.Item.class, STEAM_STEEPING_VAT, SteamworkKeys.STEAM_STEEPING_VAT);
        RebarItem.register(SteamWashingTrough.Item.class, STEAM_WASHING_TROUGH, SteamworkKeys.STEAM_WASHING_TROUGH);
        RebarItem.register(SteamPress.Item.class, STEAM_PRESS, SteamworkKeys.STEAM_PRESS);
        RebarItem.register(SteamGrinder.Item.class, STEAM_GRINDER, SteamworkKeys.STEAM_GRINDER);
        RebarItem.register(SteamPressurizedFurnace.Item.class, STEAM_PRESSURIZED_FURNACE, SteamworkKeys.STEAM_PRESSURIZED_FURNACE);
        RebarItem.register(SteamAssemblyBench.Item.class, STEAM_ASSEMBLY_BENCH, SteamworkKeys.STEAM_ASSEMBLY_BENCH);
        RebarItem.register(SteamScienceInterface.Item.class, STEAM_SCIENCE_INTERFACE, SteamworkKeys.STEAM_SCIENCE_INTERFACE);
        RebarItem.register(SteamHeatingChamber.Item.class, STEAM_HEATING_CHAMBER, SteamworkKeys.STEAM_HEATING_CHAMBER);
        RebarItem.register(SteamDistillationTower.Item.class, STEAM_DISTILLATION_TOWER, SteamworkKeys.STEAM_DISTILLATION_TOWER);
        RebarItem.register(RebarItem.class, DISTILLATION_TOWER_SECTION, SteamworkKeys.DISTILLATION_TOWER_SECTION);
        RebarItem.register(RebarItem.class, DISTILLATION_CONDENSER, SteamworkKeys.DISTILLATION_CONDENSER);
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_STERILIZER)
                .addButton(new MachineRecipesButton(STEAM_STERILIZER, SteamSterilizingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_STEEPING_VAT)
                .addButton(new MachineRecipesButton(STEAM_STEEPING_VAT, SteamSteepingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_WASHING_TROUGH)
                .addButton(new MachineRecipesButton(STEAM_WASHING_TROUGH, SteamWashingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_PRESS)
                .addButton(new MachineRecipesButton(STEAM_PRESS, SteamPressingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_GRINDER)
                .addButton(new MachineRecipesButton(STEAM_GRINDER, SteamGrindingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_PRESSURIZED_FURNACE)
                .addButton(new MachineRecipesButton(STEAM_PRESSURIZED_FURNACE, SteamPressurizingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_SCIENCE_INTERFACE)
                .addButton(new MachineRecipesButton(STEAM_SCIENCE_INTERFACE, SteamResearchRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_PRECISION_MILL)
                .addButton(new MachineRecipesButton(STEAM_PRECISION_MILL, io.github.steamwork.recipes.SteamMillingRecipe.RECIPE_TYPE));
        RebarGuide.getOrCreateInfoPage(SteamworkKeys.STEAM_DISTILLATION_TOWER)
                .addButton(new MachineRecipesButton(STEAM_DISTILLATION_TOWER, SteamDistillationRecipe.RECIPE_TYPE));

        // ===== 涡轮支持机器清单 =====
        // 简易蒸汽涡轮：仅原版熔炉系列。
        io.github.pylonmc.rebar.guide.pages.base.SimpleInfoPage simpleTurbinePage =
                RebarGuide.getOrCreateInfoPage(SteamworkKeys.SIMPLE_STEAM_TURBINE);
        simpleTurbinePage.addItem(new ItemStack(Material.FURNACE));
        simpleTurbinePage.addItem(new ItemStack(Material.BLAST_FURNACE));
        simpleTurbinePage.addItem(new ItemStack(Material.SMOKER));

        // 高级蒸汽涡轮：原版熔炉 + Steamwork 加工机器 + 所有实现 RebarProcessor 的 Pylon 机器。
        io.github.pylonmc.rebar.guide.pages.base.SimpleInfoPage advancedTurbinePage =
                RebarGuide.getOrCreateInfoPage(SteamworkKeys.ADVANCED_STEAM_TURBINE);
        // 原版熔炉
        advancedTurbinePage.addItem(new ItemStack(Material.FURNACE));
        advancedTurbinePage.addItem(new ItemStack(Material.BLAST_FURNACE));
        advancedTurbinePage.addItem(new ItemStack(Material.SMOKER));
        // Steamwork 自家加工机器
        advancedTurbinePage.addItem(STEAM_STERILIZER);
        advancedTurbinePage.addItem(STEAM_STEEPING_VAT);
        advancedTurbinePage.addItem(STEAM_WASHING_TROUGH);
        advancedTurbinePage.addItem(STEAM_PRESS);
        advancedTurbinePage.addItem(STEAM_GRINDER);
        advancedTurbinePage.addItem(STEAM_PRECISION_MILL);
        // Pylon 液压系列
        addOptionalPylonGuideItem(advancedTurbinePage, "HYDRAULIC_BREAKER");
        addOptionalPylonGuideItem(advancedTurbinePage, "HYDRAULIC_MINER");
        addOptionalPylonGuideItem(advancedTurbinePage, "HYDRAULIC_HAMMER_HEAD");
        addOptionalPylonGuideItem(advancedTurbinePage, "HYDRAULIC_MIXING_ATTACHMENT");
        // Pylon 柴油系列
        addOptionalPylonGuideItem(advancedTurbinePage, "DIESEL_BREAKER");
        addOptionalPylonGuideItem(advancedTurbinePage, "DIESEL_HAMMER_HEAD");
        addOptionalPylonGuideItem(advancedTurbinePage, "DIESEL_MIXING_ATTACHMENT");
        addOptionalPylonGuideItem(advancedTurbinePage, "PALLADIUM_CONDENSER");
        addOptionalPylonGuideItem(advancedTurbinePage, "BIOREFINERY");
        // Pylon 简单机器
        addOptionalPylonGuideItem(advancedTurbinePage, "MANUAL_CORE_DRILL");
        addOptionalPylonGuideItem(advancedTurbinePage, "COLLIMATOR");
        // Pylon 冶炼 / 纯化
        addOptionalPylonGuideItem(advancedTurbinePage, "COAL_FIRED_PURIFICATION_TOWER");
        addOptionalPylonGuideItem(advancedTurbinePage, "SMELTERY_BURNER");

        // Alloy blocks
        RebarItem.register(RebarItem.class, INVAR_BLOCK, SteamworkKeys.INVAR_BLOCK);
        RebarItem.register(RebarItem.class, DURALUMIN_BLOCK, SteamworkKeys.DURALUMIN_BLOCK);
        RebarItem.register(RebarItem.class, TUNGSTEN_BLOCK, SteamworkKeys.TUNGSTEN_BLOCK);
        RebarItem.register(RebarItem.class, MANGANESE_STEEL_BLOCK, SteamworkKeys.MANGANESE_STEEL_BLOCK);
        RebarItem.register(RebarItem.class, MANGANESE_BRONZE_BLOCK, SteamworkKeys.MANGANESE_BRONZE_BLOCK);

        // Machine components
        RebarItem.register(RebarItem.class, STEAM_MOTOR, SteamworkKeys.STEAM_MOTOR);

        RebarItem.register(SteamPrecisionMill.Item.class, STEAM_PRECISION_MILL, SteamworkKeys.STEAM_PRECISION_MILL);

        // Precision mill products
        RebarItem.register(RebarItem.class, PRECISION_GEAR);
        RebarItem.register(RebarItem.class, PRECISION_SCREW);
        RebarItem.register(RebarItem.class, PRECISION_VALVE);
        RebarItem.register(RebarItem.class, WEAR_PLATE);
        RebarItem.register(RebarItem.class, HEAT_SINK);
        RebarItem.register(RebarItem.class, MILLING_BLADE);
        RebarItem.register(RebarItem.class, CATALYST_CORE);
        RebarItem.register(RebarItem.class, PRECISION_BEARING);

        RebarItem.register(SteamEquipment.class, STEAM_SWORD, SteamworkKeys.STEAM_SWORD);
        RebarItem.register(SteamEquipment.class, STEAM_PICKAXE, SteamworkKeys.STEAM_PICKAXE);
        RebarItem.register(SteamEquipment.class, STEAM_AXE, SteamworkKeys.STEAM_AXE);
        RebarItem.register(SteamEquipment.class, STEAM_SHOVEL, SteamworkKeys.STEAM_SHOVEL);
        RebarItem.register(SteamEquipment.class, STEAM_HOE, SteamworkKeys.STEAM_HOE);
        RebarItem.register(SteamEquipment.class, STEAM_HELMET, SteamworkKeys.STEAM_HELMET);
        RebarItem.register(SteamEquipment.class, STEAM_CHESTPLATE, SteamworkKeys.STEAM_CHESTPLATE);
        RebarItem.register(SteamEquipment.class, STEAM_LEGGINGS, SteamworkKeys.STEAM_LEGGINGS);
        RebarItem.register(SteamEquipment.class, STEAM_BOOTS, SteamworkKeys.STEAM_BOOTS);

        RebarItem.register(SteamCanister.class, STEAM_CANISTER_BRASS, SteamworkKeys.STEAM_CANISTER_BRASS);
        RebarItem.register(SteamCanister.class, STEAM_CANISTER_INVAR, SteamworkKeys.STEAM_CANISTER_INVAR);
        RebarItem.register(SteamCanister.class, STEAM_CANISTER_TUNGSTEN, SteamworkKeys.STEAM_CANISTER_TUNGSTEN);
    }
}
