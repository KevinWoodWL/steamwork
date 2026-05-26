package io.github.steamwork;

import io.github.pylonmc.pylon.api.MeltingPoint;
import io.github.pylonmc.rebar.fluid.RebarFluid;
import io.github.pylonmc.rebar.fluid.tags.FluidTemperature;
import io.github.pylonmc.rebar.recipe.IngredientCalculator;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class SteamworkFluids {

    private SteamworkFluids() {
        throw new AssertionError("Utility class");
    }

    public static final RebarFluid STEAM = new RebarFluid(
            steamworkKey("steam"),
            TextColor.fromHexString("#d8edf0"),
            Material.WHITE_CONCRETE_POWDER,
            FluidTemperature.HOT);

    public static final RebarFluid SUPERHEATED_STEAM = new RebarFluid(
            steamworkKey("superheated_steam"),
            TextColor.fromHexString("#ff8c00"),
            Material.WHITE_CONCRETE,
            FluidTemperature.HOT);

    public static final RebarFluid PRESSURIZED_STEAM = new RebarFluid(
            steamworkKey("pressurized_steam"),
            TextColor.fromHexString("#18c0d8"),
            Material.CYAN_CONCRETE_POWDER,
            FluidTemperature.HOT);

    public static final RebarFluid MOLTEN_ZINC = new RebarFluid(
            steamworkKey("molten_zinc"),
            TextColor.fromHexString("#b0bec5"),
            Material.LIGHT_BLUE_TERRACOTTA,
            FluidTemperature.HOT).addTag(new MeltingPoint(419.5));

    public static final RebarFluid MOLTEN_BRASS = new RebarFluid(
            steamworkKey("molten_brass"),
            TextColor.fromHexString("#d4a017"),
            Material.YELLOW_CONCRETE,
            FluidTemperature.HOT).addTag(new MeltingPoint(920.0));

    public static final RebarFluid DISTILLED_WATER = new RebarFluid(
            steamworkKey("distilled_water"),
            TextColor.fromHexString("#29b6d6"),
            Material.LIGHT_BLUE_CONCRETE,
            FluidTemperature.NORMAL);

    public static final RebarFluid MINERAL_LEACHATE = new RebarFluid(
            steamworkKey("mineral_leachate"),
            TextColor.fromHexString("#8bc34a"),
            Material.LIME_CONCRETE_POWDER,
            FluidTemperature.NORMAL);

    public static final RebarFluid WASTE_ACID = new RebarFluid(
            steamworkKey("waste_acid"),
            TextColor.fromHexString("#4caf50"),
            Material.GREEN_CONCRETE,
            FluidTemperature.NORMAL);

    public static final RebarFluid LIGHT_FRACTION = new RebarFluid(
            steamworkKey("light_fraction"),
            TextColor.fromHexString("#d9c9a8"),
            Material.WHITE_TERRACOTTA,
            FluidTemperature.NORMAL);

    public static final RebarFluid MEDIUM_FRACTION = new RebarFluid(
            steamworkKey("medium_fraction"),
            TextColor.fromHexString("#c8a060"),
            Material.YELLOW_TERRACOTTA,
            FluidTemperature.NORMAL);

    public static final RebarFluid HEAVY_FRACTION = new RebarFluid(
            steamworkKey("heavy_fraction"),
            TextColor.fromHexString("#6d4c41"),
            Material.BROWN_TERRACOTTA,
            FluidTemperature.NORMAL);

    static {
        STEAM.register();
        IngredientCalculator.addBaseIngredient(STEAM);

        SUPERHEATED_STEAM.register();
        IngredientCalculator.addBaseIngredient(SUPERHEATED_STEAM);

        PRESSURIZED_STEAM.register();
        IngredientCalculator.addBaseIngredient(PRESSURIZED_STEAM);

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
