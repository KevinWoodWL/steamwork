package io.github.steamwork;

import io.github.pylonmc.pylon.api.MeltingPoint;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.fluid.tags.FluidTemperature;
import io.github.pylonmc.rebar.recipe.IngredientCalculator;
import org.bukkit.Material;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteamworkFluids {

    private SteamworkFluids() {
        throw new AssertionError("Utility class");
    }

    public static final RebarFluid STEAM = new RebarFluid(
            steamworkKey("steam"),
            Material.WHITE_CONCRETE_POWDER
    ).addTag(FluidTemperature.HOT);

    public static final RebarFluid SUPERHEATED_STEAM = new RebarFluid(
            steamworkKey("superheated_steam"),
            Material.WHITE_CONCRETE
    ).addTag(FluidTemperature.HOT);

    public static final RebarFluid MOLTEN_ZINC = new RebarFluid(
            steamworkKey("molten_zinc"),
            Material.LIGHT_BLUE_TERRACOTTA
    ).addTag(FluidTemperature.HOT).addTag(new MeltingPoint(419.5));

    public static final RebarFluid MOLTEN_BRASS = new RebarFluid(
            steamworkKey("molten_brass"),
            Material.YELLOW_CONCRETE
    ).addTag(FluidTemperature.HOT).addTag(new MeltingPoint(920.0));

    public static final RebarFluid DISTILLED_WATER = new RebarFluid(
            steamworkKey("distilled_water"),
            Material.LIGHT_BLUE_CONCRETE
    ).addTag(FluidTemperature.NORMAL);

    public static final RebarFluid MINERAL_LEACHATE = new RebarFluid(
            steamworkKey("mineral_leachate"),
            Material.LIME_CONCRETE_POWDER
    ).addTag(FluidTemperature.NORMAL);

    public static final RebarFluid WASTE_ACID = new RebarFluid(
            steamworkKey("waste_acid"),
            Material.GREEN_CONCRETE
    ).addTag(FluidTemperature.NORMAL);

    public static final RebarFluid LIGHT_FRACTION = new RebarFluid(
            steamworkKey("light_fraction"),
            Material.WHITE_TERRACOTTA
    ).addTag(FluidTemperature.NORMAL);

    public static final RebarFluid MEDIUM_FRACTION = new RebarFluid(
            steamworkKey("medium_fraction"),
            Material.YELLOW_TERRACOTTA
    ).addTag(FluidTemperature.NORMAL);

    public static final RebarFluid HEAVY_FRACTION = new RebarFluid(
            steamworkKey("heavy_fraction"),
            Material.BROWN_TERRACOTTA
    ).addTag(FluidTemperature.NORMAL);

    static {
        STEAM.register();
        IngredientCalculator.addBaseIngredient(STEAM);

        SUPERHEATED_STEAM.register();
        IngredientCalculator.addBaseIngredient(SUPERHEATED_STEAM);

        MOLTEN_ZINC.register();
        IngredientCalculator.addBaseIngredient(MOLTEN_ZINC);

        MOLTEN_BRASS.register();
        IngredientCalculator.addBaseIngredient(MOLTEN_BRASS);

        DISTILLED_WATER.register();
        IngredientCalculator.addBaseIngredient(DISTILLED_WATER);

        MINERAL_LEACHATE.register();
        IngredientCalculator.addBaseIngredient(MINERAL_LEACHATE);

        WASTE_ACID.register();
        IngredientCalculator.addBaseIngredient(WASTE_ACID);

        LIGHT_FRACTION.register();
        IngredientCalculator.addBaseIngredient(LIGHT_FRACTION);

        MEDIUM_FRACTION.register();
        IngredientCalculator.addBaseIngredient(MEDIUM_FRACTION);

        HEAVY_FRACTION.register();
        IngredientCalculator.addBaseIngredient(HEAVY_FRACTION);
    }

    public static void initialize() {
    }
}
