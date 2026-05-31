package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

import static io.github.steamwork.recipes.registration.RecipeHelpers.rebarChoice;
import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

public final class CraftingRecipes {

    private CraftingRecipes() {
        throw new AssertionError("Utility class");
    }

    public static void register() {
        // 橡胶垫圈基础路径：单产，需要硫化橡胶。
        ShapelessRecipe rubberGasket = new ShapelessRecipe(
                SteamworkKeys.RUBBER_GASKET, SteamworkItems.RUBBER_GASKET);
        rubberGasket.addIngredient(rebarChoice(SteamworkItems.VULCANIZED_RUBBER));
        rubberGasket.addIngredient(rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        rubberGasket.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(rubberGasket);

        // 橡胶垫圈高产路径：覆胶织物含完整橡胶层，能切出 2 个垫圈，给孤儿材料 RUBBERIZED_FABRIC 找用途。
        ShapelessRecipe rubberGasketFromFabric = new ShapelessRecipe(
                steamworkKey("rubber_gasket_from_fabric"), SteamworkItems.RUBBER_GASKET.clone().asQuantity(2));
        rubberGasketFromFabric.addIngredient(rebarChoice(SteamworkItems.RUBBERIZED_FABRIC));
        rubberGasketFromFabric.addIngredient(rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        rubberGasketFromFabric.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(rubberGasketFromFabric);

        ShapelessRecipe stringFromPlantFiber = new ShapelessRecipe(
                steamworkKey("string_from_plant_fiber"), new ItemStack(Material.STRING));
        stringFromPlantFiber.addIngredient(rebarChoice(SteamworkItems.PLANT_FIBER));
        stringFromPlantFiber.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(stringFromPlantFiber);

        ShapelessRecipe paperFromSteamPulp = new ShapelessRecipe(
                steamworkKey("paper_from_steam_pulp"), new ItemStack(Material.PAPER, 3));
        paperFromSteamPulp.addIngredient(rebarChoice(SteamworkItems.STEAM_PULP));
        paperFromSteamPulp.addIngredient(rebarChoice(SteamworkItems.STEAM_PULP));
        paperFromSteamPulp.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(paperFromSteamPulp);

        ShapedRecipe pressureGauge = new ShapedRecipe(
                SteamworkKeys.PRESSURE_GAUGE, SteamworkItems.PRESSURE_GAUGE);
        pressureGauge.shape(" B ", " C ", " R ");
        pressureGauge.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        pressureGauge.setIngredient('C', Material.CLOCK);
        pressureGauge.setIngredient('R', Material.REDSTONE);
        pressureGauge.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pressureGauge);

        ShapedRecipe brassGear = new ShapedRecipe(
                SteamworkKeys.BRASS_GEAR, SteamworkItems.BRASS_GEAR);
        brassGear.shape("   ", "B B", "BSB");
        brassGear.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassGear.setIngredient('S', Material.STICK);
        brassGear.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassGear);

        ShapedRecipe heatingCoil = new ShapedRecipe(
                SteamworkKeys.HEATING_COIL, SteamworkItems.HEATING_COIL);
        heatingCoil.shape("   ", "NSN", "   ");
        heatingCoil.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        heatingCoil.setIngredient('S', rebarChoice(SteamworkItems.PLANT_FIBER));
        heatingCoil.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(heatingCoil);

        // 粗制 nichrome 粉（前置入门路径）：手工预混耐高温因子 + 铁锌基底，产量低但解锁加压熔炉。
        // 玩家有了加压熔炉之后会切到 registerPressurizingRecipes() 里的高效版本（每次产 2 个）。
        // 这是为了破除"做加压熔炉 → 要 nichrome → 要加压熔炉"的死循环。
        ShapelessRecipe nichromeDustCrude = new ShapelessRecipe(
                steamworkKey("nichrome_dust_crude"), SteamworkItems.NICHROME_DUST);
        nichromeDustCrude.addIngredient(3, Material.BLAZE_POWDER);
        nichromeDustCrude.addIngredient(new RecipeChoice.ExactChoice(new ItemStack(Material.IRON_INGOT)));
        nichromeDustCrude.addIngredient(rebarChoice(SteamworkItems.ZINC_INGOT));
        nichromeDustCrude.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(nichromeDustCrude);

        // Brass machine components
        // 黄铜蒸馏管: 黄铜锭 x 2 + Pylon锡片 x 2
        ShapedRecipe brassDistillationTube = new ShapedRecipe(
                SteamworkKeys.BRASS_DISTILLATION_TUBE, SteamworkItems.BRASS_DISTILLATION_TUBE);
        brassDistillationTube.shape("T T", "B B", "T T");
        brassDistillationTube.setIngredient('T', rebarChoice(PylonItems.TIN_SHEET));
        brassDistillationTube.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassDistillationTube.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassDistillationTube);

        // 黄铜滤网: 黄铜锭 x 2 + Pylon锡片 x 2
        ShapedRecipe brassFilter = new ShapedRecipe(
                SteamworkKeys.BRASS_FILTER, SteamworkItems.BRASS_FILTER);
        brassFilter.shape("TBT", "B B", "TBT");
        brassFilter.setIngredient('T', rebarChoice(PylonItems.TIN_SHEET));
        brassFilter.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassFilter.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassFilter);

        // 黄铜筛网: 黄铜锭 x 2 + Pylon锡片
        ShapedRecipe brassSieve = new ShapedRecipe(
                SteamworkKeys.BRASS_SIEVE, SteamworkItems.BRASS_SIEVE);
        brassSieve.shape("TBT", "   ", "B B");
        brassSieve.setIngredient('T', rebarChoice(PylonItems.TIN_SHEET));
        brassSieve.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassSieve.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassSieve);

        // 黄铜分流阀: 黄铜锭 x 2 + 红石
        ShapedRecipe brassFlowValve = new ShapedRecipe(
                SteamworkKeys.BRASS_FLOW_VALVE, SteamworkItems.BRASS_FLOW_VALVE);
        brassFlowValve.shape(" B ", "BRB", " B ");
        brassFlowValve.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassFlowValve.setIngredient('R', Material.REDSTONE);
        brassFlowValve.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassFlowValve);

        // 黄铜扇叶: 黄铜锭 x 2 + Pylon锡片
        ShapedRecipe brassFanBlade = new ShapedRecipe(
                SteamworkKeys.BRASS_FAN_BLADE, SteamworkItems.BRASS_FAN_BLADE);
        brassFanBlade.shape(" T ", "B B", " T ");
        brassFanBlade.setIngredient('T', rebarChoice(PylonItems.TIN_SHEET));
        brassFanBlade.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassFanBlade.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassFanBlade);

        // 黄铜阀芯: 锌锭 x 2 + 黄铜锭
        ShapedRecipe brassValveCore = new ShapedRecipe(
                SteamworkKeys.BRASS_VALVE_CORE, SteamworkItems.BRASS_VALVE_CORE);
        brassValveCore.shape(" Z ", "ZBZ", " Z ");
        brassValveCore.setIngredient('Z', rebarChoice(SteamworkItems.ZINC_INGOT));
        brassValveCore.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        brassValveCore.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(brassValveCore);

        // 黄铜密封环: 黄铜锭 x 2 + Pylon锡片 x 2
        ShapelessRecipe brassSealRing = new ShapelessRecipe(
                SteamworkKeys.BRASS_SEAL_RING, SteamworkItems.BRASS_SEAL_RING);
        brassSealRing.addIngredient(rebarChoice(SteamworkItems.BRASS_INGOT));
        brassSealRing.addIngredient(rebarChoice(SteamworkItems.BRASS_INGOT));
        brassSealRing.addIngredient(rebarChoice(PylonItems.TIN_SHEET));
        brassSealRing.addIngredient(rebarChoice(PylonItems.TIN_SHEET));
        brassSealRing.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(brassSealRing);

        ShapedRecipe bronzeBoiler = new ShapedRecipe(
                SteamworkKeys.BRONZE_BOILER, SteamworkItems.BRONZE_BOILER);
        bronzeBoiler.shape("SPS", "BWB", "GRG");
        bronzeBoiler.setIngredient('S', rebarChoice(PylonItems.BRONZE_SHEET));
        bronzeBoiler.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        bronzeBoiler.setIngredient('B', rebarChoice(PylonItems.BRONZE_INGOT));
        bronzeBoiler.setIngredient('W', Material.WATER_BUCKET);
        bronzeBoiler.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        bronzeBoiler.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        bronzeBoiler.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(bronzeBoiler);

        ShapedRecipe invarBoiler = new ShapedRecipe(
                SteamworkKeys.INVAR_BOILER, SteamworkItems.INVAR_BOILER);
        invarBoiler.shape("IPI", "SBS", "HRH");
        invarBoiler.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        invarBoiler.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        invarBoiler.setIngredient('S', Material.IRON_INGOT);
        invarBoiler.setIngredient('B', rebarChoice(SteamworkItems.BRONZE_BOILER));
        invarBoiler.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        invarBoiler.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        invarBoiler.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(invarBoiler);

        ShapedRecipe manganeseSteelBoiler = new ShapedRecipe(
                SteamworkKeys.MANGANESE_STEEL_BOILER, SteamworkItems.MANGANESE_STEEL_BOILER);
        manganeseSteelBoiler.shape("MPM", "SBS", "HRH");
        manganeseSteelBoiler.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_STEEL_INGOT));
        manganeseSteelBoiler.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        manganeseSteelBoiler.setIngredient('S', rebarChoice(PylonItems.STEEL_INGOT));
        manganeseSteelBoiler.setIngredient('B', rebarChoice(SteamworkItems.INVAR_BOILER));
        manganeseSteelBoiler.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        manganeseSteelBoiler.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        manganeseSteelBoiler.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(manganeseSteelBoiler);

        ShapedRecipe tungstenBoiler = new ShapedRecipe(
                SteamworkKeys.TUNGSTEN_BOILER, SteamworkItems.TUNGSTEN_BOILER);
        tungstenBoiler.shape("TPT", "RBR", "HNH");
        tungstenBoiler.setIngredient('T', rebarChoice(SteamworkItems.TUNGSTEN_INGOT));
        tungstenBoiler.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        tungstenBoiler.setIngredient('R', rebarChoice(PylonItems.REFRACTORY_BRICK));
        tungstenBoiler.setIngredient('B', rebarChoice(SteamworkItems.MANGANESE_STEEL_BOILER));
        tungstenBoiler.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        tungstenBoiler.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        tungstenBoiler.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(tungstenBoiler);

        // 蒸汽动力臂：硬铝合金轻质臂体 + 精密阀门精确控位 + 蒸汽马达驱动 + 耐磨板接触头 + 黄铜密封环。
        // 定位为精密自动化入门，材料档次与精密铣床同级。
        ShapedRecipe steamArm = new ShapedRecipe(
                SteamworkKeys.STEAM_ARM, SteamworkItems.STEAM_ARM);
        steamArm.shape("DPD", "VMV", "WSW");
        steamArm.setIngredient('D', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        steamArm.setIngredient('P', rebarChoice(SteamworkItems.PRECISION_VALVE));
        steamArm.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamArm.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        steamArm.setIngredient('W', rebarChoice(SteamworkItems.WEAR_PLATE));
        steamArm.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamArm.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamArm);

        // Simple Steam Turbine - cheaper recipe with basic materials
        ShapedRecipe simpleSteamTurbine = new ShapedRecipe(
                SteamworkKeys.SIMPLE_STEAM_TURBINE, SteamworkItems.SIMPLE_STEAM_TURBINE);
        simpleSteamTurbine.shape("FBF", "CPC", "IGI");
        simpleSteamTurbine.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FAN_BLADE));
        simpleSteamTurbine.setIngredient('B', rebarChoice(SteamworkItems.BRASS_GEAR));
        simpleSteamTurbine.setIngredient('C', rebarChoice(SteamworkItems.NICHROME_INGOT));
        simpleSteamTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        simpleSteamTurbine.setIngredient('I', Material.IRON_INGOT);
        simpleSteamTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(simpleSteamTurbine);



        // Precision Steam Turbine - accelerates all machine types (max 2 targets)
        // Requires basic + precision processing turbine + pylon universal turbine + palladium alloy
        ShapedRecipe precisionSteamTurbine = new ShapedRecipe(
                SteamworkKeys.PRECISION_STEAM_TURBINE, SteamworkItems.PRECISION_STEAM_TURBINE);
        precisionSteamTurbine.shape("BPB", "UAU", "BPB");
        precisionSteamTurbine.setIngredient('B', rebarChoice(SteamworkItems.BASIC_PROCESSING_TURBINE));
        precisionSteamTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRECISION_PROCESSING_TURBINE));
        precisionSteamTurbine.setIngredient('U', rebarChoice(SteamworkItems.PYLON_UNIVERSAL_TURBINE));
        precisionSteamTurbine.setIngredient('A', rebarChoice(SteamworkItems.PALLADIUM_ALLOY_INGOT));
        precisionSteamTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionSteamTurbine);

        // Basic Processing Turbine - accelerates basic Steamwork machines (max 5 targets)
        // Brass fan blade + heating coil + pressure gauge + forged plate
        ShapedRecipe basicProcessingTurbine = new ShapedRecipe(
                SteamworkKeys.BASIC_PROCESSING_TURBINE, SteamworkItems.BASIC_PROCESSING_TURBINE);
        basicProcessingTurbine.shape("FBF", "CPC", "FGF");
        basicProcessingTurbine.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        basicProcessingTurbine.setIngredient('B', rebarChoice(SteamworkItems.BRASS_FAN_BLADE));
        basicProcessingTurbine.setIngredient('C', rebarChoice(SteamworkItems.HEATING_COIL));
        basicProcessingTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        basicProcessingTurbine.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        basicProcessingTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(basicProcessingTurbine);

        // Precision Processing Turbine - accelerates precision Steamwork machines (max 3 targets)
        // Precision gear + precision bearing + heating coil + forged plate
        ShapedRecipe precisionProcessingTurbine = new ShapedRecipe(
                SteamworkKeys.PRECISION_PROCESSING_TURBINE, SteamworkItems.PRECISION_PROCESSING_TURBINE);
        precisionProcessingTurbine.shape("GPG", "CBC", "FDF");
        precisionProcessingTurbine.setIngredient('G', rebarChoice(SteamworkItems.PRECISION_GEAR));
        precisionProcessingTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        precisionProcessingTurbine.setIngredient('C', rebarChoice(SteamworkItems.HEATING_COIL));
        precisionProcessingTurbine.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        precisionProcessingTurbine.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        precisionProcessingTurbine.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        precisionProcessingTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionProcessingTurbine);

        // Hydraulic Turbine - accelerates Pylon hydraulic machines (max 4 targets)
        ShapedRecipe hydraulicTurbine = new ShapedRecipe(
                SteamworkKeys.HYDRAULIC_TURBINE, SteamworkItems.HYDRAULIC_TURBINE);
        hydraulicTurbine.shape("VBV", "PGP", "VFV");
        hydraulicTurbine.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        hydraulicTurbine.setIngredient('B', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        hydraulicTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        hydraulicTurbine.setIngredient('G', rebarChoice(SteamworkItems.PRECISION_GEAR));
        hydraulicTurbine.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        hydraulicTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(hydraulicTurbine);

        // Diesel Turbine - accelerates Pylon diesel machines (max 4 targets)
        ShapedRecipe dieselTurbine = new ShapedRecipe(
                SteamworkKeys.DIESEL_TURBINE, SteamworkItems.DIESEL_TURBINE);
        dieselTurbine.shape("HPH", "PGP", "HFH");
        dieselTurbine.setIngredient('H', rebarChoice(SteamworkItems.HYDRAULIC_SEAL));
        dieselTurbine.setIngredient('P', rebarChoice(SteamworkItems.HIGH_PRESSURE_PIPE));
        dieselTurbine.setIngredient('G', rebarChoice(SteamworkItems.PRECISION_GEAR));
        dieselTurbine.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        dieselTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(dieselTurbine);

        // Pylon Universal Turbine - 前期通用 Pylon 涡轮，使用黄铜系基础材料
        // 黄铜密封环（密封）+ 黄铜扇叶（转子）+ 锻造板（结构）+ 压力表（控制）+ 黄铜流量阀（流量控制）
        ShapedRecipe pylonUniversalTurbine = new ShapedRecipe(
                SteamworkKeys.PYLON_UNIVERSAL_TURBINE, SteamworkItems.PYLON_UNIVERSAL_TURBINE);
        pylonUniversalTurbine.shape("SGS", "BPB", "SVS");
        pylonUniversalTurbine.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        pylonUniversalTurbine.setIngredient('G', rebarChoice(SteamworkItems.BRASS_FAN_BLADE));
        pylonUniversalTurbine.setIngredient('B', rebarChoice(SteamworkItems.FORGED_PLATE));
        pylonUniversalTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        pylonUniversalTurbine.setIngredient('V', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        pylonUniversalTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pylonUniversalTurbine);

        ShapedRecipe steamSterilizer = new ShapedRecipe(
                SteamworkKeys.STEAM_STERILIZER, SteamworkItems.STEAM_STERILIZER);
        steamSterilizer.shape("DGD", "RBR", "SPS");
        steamSterilizer.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        steamSterilizer.setIngredient('G', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamSterilizer.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamSterilizer.setIngredient('B', Material.BARREL);
        steamSterilizer.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SIEVE));
        steamSterilizer.setIngredient('P', rebarChoice(SteamworkItems.FIBERBOARD));
        steamSterilizer.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamSterilizer);

        ShapedRecipe steamSteepingVat = new ShapedRecipe(
                SteamworkKeys.STEAM_STEEPING_VAT, SteamworkItems.STEAM_STEEPING_VAT);
        steamSteepingVat.shape("F F", "RVR", "SGS");
        steamSteepingVat.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FILTER));
        steamSteepingVat.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamSteepingVat.setIngredient('V', Material.CAULDRON);
        steamSteepingVat.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        steamSteepingVat.setIngredient('S', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        steamSteepingVat.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamSteepingVat);

        ShapedRecipe steamWashingTrough = new ShapedRecipe(
                SteamworkKeys.STEAM_WASHING_TROUGH, SteamworkItems.STEAM_WASHING_TROUGH);
        steamWashingTrough.shape("SPS", "RVR", "DGD");
        steamWashingTrough.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SIEVE));
        steamWashingTrough.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamWashingTrough.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamWashingTrough.setIngredient('V', Material.CAULDRON);
        steamWashingTrough.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        steamWashingTrough.setIngredient('G', rebarChoice(SteamworkItems.TREATED_WOOD));
        steamWashingTrough.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamWashingTrough);

        // 蒸汽冲压机：黄铜活塞 + 阀芯 + 铁砧式核心，靠蒸汽压力推动柱塞反复冲压。
        ShapedRecipe steamPress = new ShapedRecipe(
                SteamworkKeys.STEAM_PRESS, SteamworkItems.STEAM_PRESS);
        steamPress.shape("VPV", "GAG", "MRM");
        steamPress.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamPress.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamPress.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        steamPress.setIngredient('A', Material.PISTON);
        steamPress.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_BRONZE_INGOT));
        steamPress.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamPress.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamPress);

        // 蒸汽研磨机：黄铜筛网外壳 + 原版砂轮核心，靠蒸汽推动磨盘高速旋转粉碎物料。
        ShapedRecipe steamGrinder = new ShapedRecipe(
                SteamworkKeys.STEAM_GRINDER, SteamworkItems.STEAM_GRINDER);
        steamGrinder.shape("GPG", "SHS", "BRB");
        steamGrinder.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        steamGrinder.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamGrinder.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SIEVE));
        steamGrinder.setIngredient('H', Material.GRINDSTONE);
        steamGrinder.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamGrinder.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamGrinder.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamGrinder);

        // Steam equipment（5 工具 + 4 护甲）已迁移到蒸汽装配台。见 AssemblyRecipes。

        // 蒸汽精密铣床：因瓦框架 + 锰钢刀头 + 精密传动 + 蒸汽气路。
        // 需要因瓦锭（低热膨胀保精度）+ 锰钢锭（高硬度刀盘）+ 黄铜传动件 + 阀芯。
        ShapedRecipe steamPrecisionMill = new ShapedRecipe(
                SteamworkKeys.STEAM_PRECISION_MILL, SteamworkItems.STEAM_PRECISION_MILL);
        steamPrecisionMill.shape("IMI", "VGV", "BPB");
        steamPrecisionMill.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        steamPrecisionMill.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_STEEL_INGOT));
        steamPrecisionMill.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamPrecisionMill.setIngredient('G', Material.GRINDSTONE);
        steamPrecisionMill.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamPrecisionMill.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamPrecisionMill.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamPrecisionMill);

        ShapedRecipe precisionFoundry = new ShapedRecipe(
                SteamworkKeys.PRECISION_FOUNDRY, SteamworkItems.PRECISION_FOUNDRY);
        precisionFoundry.shape("WHW", "CMC", "WGW");
        precisionFoundry.setIngredient('W', rebarChoice(SteamworkItems.WEAR_PLATE));
        precisionFoundry.setIngredient('H', rebarChoice(SteamworkItems.HEAT_SINK));
        precisionFoundry.setIngredient('C', rebarChoice(SteamworkItems.HEATING_COIL));
        precisionFoundry.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_BRONZE_INGOT));
        precisionFoundry.setIngredient('G', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        precisionFoundry.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionFoundry);

        ShapedRecipe precisionCatalyticReactor = new ShapedRecipe(
                SteamworkKeys.PRECISION_CATALYTIC_REACTOR, SteamworkItems.PRECISION_CATALYTIC_REACTOR);
        precisionCatalyticReactor.shape("VHV", "DCD", "VPV");
        precisionCatalyticReactor.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        precisionCatalyticReactor.setIngredient('H', rebarChoice(SteamworkItems.HEAT_SINK));
        precisionCatalyticReactor.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        precisionCatalyticReactor.setIngredient('C', rebarChoice(SteamworkItems.CATALYST_CORE));
        precisionCatalyticReactor.setIngredient('P', rebarChoice(SteamworkItems.WEAR_PLATE));
        precisionCatalyticReactor.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionCatalyticReactor);

        // Alloy blocks: 9 ingots <-> 1 block.
        addBlockRecipe(SteamworkKeys.INVAR_BLOCK, SteamworkItems.INVAR_BLOCK, SteamworkItems.INVAR_INGOT);
        addBlockRecipe(SteamworkKeys.DURALUMIN_BLOCK, SteamworkItems.DURALUMIN_BLOCK, SteamworkItems.DURALUMIN_INGOT);
        addBlockRecipe(SteamworkKeys.TUNGSTEN_BLOCK, SteamworkItems.TUNGSTEN_BLOCK, SteamworkItems.TUNGSTEN_INGOT);
        addBlockRecipe(SteamworkKeys.MANGANESE_STEEL_BLOCK, SteamworkItems.MANGANESE_STEEL_BLOCK, SteamworkItems.MANGANESE_STEEL_INGOT);
        addBlockRecipe(SteamworkKeys.MANGANESE_BRONZE_BLOCK, SteamworkItems.MANGANESE_BRONZE_BLOCK, SteamworkItems.MANGANESE_BRONZE_INGOT);

        // 蒸汽罐（3 级）已迁移到蒸汽装配台。见 AssemblyRecipes。

        // 蒸汽装配台本体：原版工作台合成，作为整个蒸汽装备生态的入口。
        ShapedRecipe assemblyBench = new ShapedRecipe(
                SteamworkKeys.STEAM_ASSEMBLY_BENCH, SteamworkItems.STEAM_ASSEMBLY_BENCH);
        assemblyBench.shape("GPG", "BAB", "III");
        assemblyBench.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        assemblyBench.setIngredient('P', rebarChoice(SteamworkItems.PALLADIUM_ALLOY_INGOT));
        assemblyBench.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        assemblyBench.setIngredient('A', Material.ANVIL);
        assemblyBench.setIngredient('I', Material.IRON_INGOT);
        assemblyBench.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(assemblyBench);

        // Steam pressurized furnace
        ShapedRecipe pressurizedFurnace = new ShapedRecipe(
                SteamworkKeys.STEAM_PRESSURIZED_FURNACE, SteamworkItems.STEAM_PRESSURIZED_FURNACE);
        pressurizedFurnace.shape("VNV", "HPH", "SGS");
        pressurizedFurnace.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        pressurizedFurnace.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        pressurizedFurnace.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        pressurizedFurnace.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        pressurizedFurnace.setIngredient('S', rebarChoice(SteamworkItems.BRASS_INGOT));
        pressurizedFurnace.setIngredient('G', rebarChoice(SteamworkItems.RUBBER_GASKET));
        pressurizedFurnace.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pressurizedFurnace);

        // 蒸汽科研接口：镍铬合金侧壁（耐热分析仓）+ 滤网（样品定位）+ 蒸汽气路 + 压力表读数。
        // 注意：不能使用钯合金，否则与 PRECISION_ADVANCED_AUTOMATION_1（需科研接口才能解锁）形成循环依赖。
        ShapedRecipe steamScienceInterface = new ShapedRecipe(
                SteamworkKeys.STEAM_SCIENCE_INTERFACE, SteamworkItems.STEAM_SCIENCE_INTERFACE);
        steamScienceInterface.shape("APA", "FLF", "BRB");
        steamScienceInterface.setIngredient('A', rebarChoice(SteamworkItems.NICHROME_INGOT));
        steamScienceInterface.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamScienceInterface.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FILTER));
        steamScienceInterface.setIngredient('L', Material.LECTERN);
        steamScienceInterface.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamScienceInterface.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        steamScienceInterface.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamScienceInterface);

        // 蒸汽加热室：殷钢外壳 + 加热线圈核心 + 阀芯调流 + 压力表，把普通蒸汽再热为过热蒸汽。
        ShapedRecipe steamHeatingChamber = new ShapedRecipe(
                SteamworkKeys.STEAM_HEATING_CHAMBER, SteamworkItems.STEAM_HEATING_CHAMBER);
        steamHeatingChamber.shape("IPI", "HCH", "VRV");
        steamHeatingChamber.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        steamHeatingChamber.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamHeatingChamber.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        steamHeatingChamber.setIngredient('C', rebarChoice(SteamworkItems.NICHROME_INGOT));
        steamHeatingChamber.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamHeatingChamber.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        steamHeatingChamber.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamHeatingChamber);

        // 精馏塔节：玻璃壁 + 镍铬合金框 + 蒸馏管，每段塔由 2 玻璃 + 4 镍铬锭 + 2 蒸馏管 + 1 黄铜密封环组成。
        ShapedRecipe distillationTowerSection = new ShapedRecipe(
                SteamworkKeys.DISTILLATION_TOWER_SECTION, SteamworkItems.DISTILLATION_TOWER_SECTION);
        distillationTowerSection.shape("NGN", "DRD", "NGN");
        distillationTowerSection.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        distillationTowerSection.setIngredient('G', Material.GLASS);
        distillationTowerSection.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        distillationTowerSection.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        distillationTowerSection.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(distillationTowerSection);

        // 精馏冷凝顶盖：紫铜板 + 加热线圈反向用作冷凝盘 + 阀芯。
        ShapedRecipe distillationCondenser = new ShapedRecipe(
                SteamworkKeys.DISTILLATION_CONDENSER, SteamworkItems.DISTILLATION_CONDENSER);
        distillationCondenser.shape("CVC", "HRH", "CCC");
        distillationCondenser.setIngredient('C', Material.CUT_COPPER);
        distillationCondenser.setIngredient('V', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        distillationCondenser.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        distillationCondenser.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        distillationCondenser.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(distillationCondenser);

        // 蒸汽精馏塔（控制器底座）：镍铬合金底盘 + 蒸馏管气路 + 加热线圈 + 阀芯 + 压力表。
        // 玩家在此基础上再向上堆 1-4 节塔节 + 1 个冷凝顶盖即可工作。
        ShapedRecipe steamDistillationTower = new ShapedRecipe(
                SteamworkKeys.STEAM_DISTILLATION_TOWER, SteamworkItems.STEAM_DISTILLATION_TOWER);
        steamDistillationTower.shape("DPD", "HVH", "NRN");
        steamDistillationTower.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        steamDistillationTower.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamDistillationTower.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        steamDistillationTower.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamDistillationTower.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        steamDistillationTower.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        steamDistillationTower.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamDistillationTower);

        // 蒸汽压缩机：镍铬合金（耐热压缩腔）+ 高压管件（高压气路）+ 阀芯 + 压力表 + 密封环
        // 形状：VPV / TAT / HRH  （T=钨锭，A=活塞，V=阀芯，H=高压管件）
        ShapedRecipe steamCompressor = new ShapedRecipe(
                SteamworkKeys.STEAM_COMPRESSOR, SteamworkItems.STEAM_COMPRESSOR);
        steamCompressor.shape("VPV", "TAT", "HRH");
        steamCompressor.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamCompressor.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamCompressor.setIngredient('T', rebarChoice(SteamworkItems.TUNGSTEN_INGOT));
        steamCompressor.setIngredient('A', Material.PISTON);
        steamCompressor.setIngredient('H', rebarChoice(SteamworkItems.HIGH_PRESSURE_PIPE));
        steamCompressor.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamCompressor.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamCompressor);

        // 气动货运站：精密阀门 + 因瓦合金（低膨胀管路）+ 黄铜过滤器 + 压力表 + 高压法兰（管路接头）+ 密封环
        // 形状：VFV / IPB / FRF  （I=因瓦锭，F=黄铜过滤器，P=高压法兰）
        ShapedRecipe pneumaticCargoHub = new ShapedRecipe(
                SteamworkKeys.PNEUMATIC_CARGO_HUB, SteamworkItems.PNEUMATIC_CARGO_HUB);
        pneumaticCargoHub.shape("VFV", "IPB", "FRF");
        pneumaticCargoHub.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        pneumaticCargoHub.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FILTER));
        pneumaticCargoHub.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        pneumaticCargoHub.setIngredient('P', rebarChoice(SteamworkItems.HIGH_PRESSURE_FLANGE));
        pneumaticCargoHub.setIngredient('B', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        pneumaticCargoHub.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        pneumaticCargoHub.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pneumaticCargoHub);

        // 蒸汽弹射器：精密阀门 + 镍铬合金（弹射腔耐热壁）+ 发射器 + 液压活塞（高压推进）+ 液压密封件 + 密封环
        // 形状：VNV / DAD / SRS  （N=镍铬锭，D=发射器，A=液压活塞，S=液压密封件，R=密封环）
        ShapedRecipe steamCatapult = new ShapedRecipe(
                SteamworkKeys.STEAM_CATAPULT, SteamworkItems.STEAM_CATAPULT);
        steamCatapult.shape("VNV", "DAD", "SRS");
        steamCatapult.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        steamCatapult.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        steamCatapult.setIngredient('D', Material.DISPENSER);
        steamCatapult.setIngredient('A', rebarChoice(SteamworkItems.HYDRAULIC_PISTON));
        steamCatapult.setIngredient('S', rebarChoice(SteamworkItems.HYDRAULIC_SEAL));
        steamCatapult.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamCatapult.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamCatapult);

        // 蒸汽分拣机：因瓦合金（低膨胀分拣轨道）+ 黄铜过滤器 + 蒸汽马达 + 流量阀 + 黄铜 + 垫圈
        // 形状：FIF / BMB / GBG  （I=因瓦锭，F=黄铜过滤器，M=蒸汽马达）
        ShapedRecipe steamSorter = new ShapedRecipe(
                SteamworkKeys.STEAM_SORTER, SteamworkItems.STEAM_SORTER);
        steamSorter.shape("FIF", "BMB", "GBG");
        steamSorter.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FILTER));
        steamSorter.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        steamSorter.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamSorter.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        steamSorter.setIngredient('G', rebarChoice(SteamworkItems.RUBBER_GASKET));
        steamSorter.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamSorter);

        // 气动导管：因瓦合金（低膨胀管壁）+ 黄铜密封环，8个产量（管段量大，不能太贵）
        // 形状：RIR / I I / RIR  （I=因瓦锭，R=密封环）
        ShapedRecipe pneumaticDuct = new ShapedRecipe(
                SteamworkKeys.PNEUMATIC_DUCT, SteamworkItems.PNEUMATIC_DUCT.clone().asQuantity(8));
        pneumaticDuct.shape("RIR", "I I", "RIR");
        pneumaticDuct.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        pneumaticDuct.setIngredient('I', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        pneumaticDuct.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pneumaticDuct);

        // 气动分发器：精密阀门 + 因瓦合金 + 黄铜过滤器 + 蒸汽马达 + 垫圈 + 镍铬合金
        // 形状：VFV / NMN / GIG  （V=精密阀门，F=黄铜过滤器，N=镍铬锭，M=蒸汽马达，G=垫圈，I=因瓦锭）
        ShapedRecipe pneumaticDistributor = new ShapedRecipe(
                SteamworkKeys.PNEUMATIC_DISTRIBUTOR, SteamworkItems.PNEUMATIC_DISTRIBUTOR);
        pneumaticDistributor.shape("VFV", "NMN", "GIG");
        pneumaticDistributor.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        pneumaticDistributor.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FILTER));
        pneumaticDistributor.setIngredient('N', rebarChoice(SteamworkItems.NICHROME_INGOT));
        pneumaticDistributor.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        pneumaticDistributor.setIngredient('G', rebarChoice(SteamworkItems.RUBBER_GASKET));
        pneumaticDistributor.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        pneumaticDistributor.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pneumaticDistributor);

        ShapedRecipe productionLineBufferChest = new ShapedRecipe(
                SteamworkKeys.PRODUCTION_LINE_BUFFER_CHEST, SteamworkItems.PRODUCTION_LINE_BUFFER_CHEST);
        productionLineBufferChest.shape("RIR", "HCH", "RIR");
        productionLineBufferChest.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        productionLineBufferChest.setIngredient('I', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        productionLineBufferChest.setIngredient('H', Material.HOPPER);
        productionLineBufferChest.setIngredient('C', Material.CHEST);
        productionLineBufferChest.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(productionLineBufferChest);

        // 产线入口：精密控制分发器。精密轴承（传动）+ 压力表（物料感测）居中上，
        // 精密阀门（通断控制）夹住发射器核心，蒸汽马达（动力）+ 精密螺钉（精密安装）在底部。
        ShapedRecipe productionLineInlet = new ShapedRecipe(
                SteamworkKeys.PRODUCTION_LINE_INLET, SteamworkItems.PRODUCTION_LINE_INLET);
        productionLineInlet.shape("BGB", "VDV", "MSM");
        productionLineInlet.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        productionLineInlet.setIngredient('G', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        productionLineInlet.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        productionLineInlet.setIngredient('D', Material.DISPENSER);
        productionLineInlet.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        productionLineInlet.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        productionLineInlet.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(productionLineInlet);

        // 产线出口：精密排料机构。锻造板（坚固底座）+ 黄铜流量阀（排料控制）在外围，
        // 精密轴承（滚轮传送）居上，投掷器为核心，橡皮垫圈（密封）+ 精密螺钉（安装）在底部。
        ShapedRecipe productionLineOutlet = new ShapedRecipe(
                SteamworkKeys.PRODUCTION_LINE_OUTLET, SteamworkItems.PRODUCTION_LINE_OUTLET);
        productionLineOutlet.shape("BFB", "POP", "SRS");
        productionLineOutlet.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        productionLineOutlet.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        productionLineOutlet.setIngredient('P', rebarChoice(SteamworkItems.FORGED_PLATE));
        productionLineOutlet.setIngredient('O', Material.DROPPER);
        productionLineOutlet.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        productionLineOutlet.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        productionLineOutlet.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(productionLineOutlet);

        // 产线蓝图：精密技术文件。十字型布局：因瓦锭（稳定基架）居上，
        // 精密螺钉（左右，固定文件框）夹住纸（文件本体），压力表（技术参数印制）居下。
        ShapedRecipe productionLineBlueprint = new ShapedRecipe(
                SteamworkKeys.PRODUCTION_LINE_BLUEPRINT, SteamworkItems.PRODUCTION_LINE_BLUEPRINT);
        productionLineBlueprint.shape(" I ", "SPS", " G ");
        productionLineBlueprint.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        productionLineBlueprint.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        productionLineBlueprint.setIngredient('P', Material.PAPER);
        productionLineBlueprint.setIngredient('G', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        productionLineBlueprint.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(productionLineBlueprint);

        // 气动输入端（插入器）：无动力中继，接收网络推送后插入目标容器。
        // 因瓦合金（低膨胀管壁）+ 分流阀（单向止回）+ 原版漏斗（插入头）+ 黄铜密封环
        // 形状：RIR / FHF / RIR  （R=密封环 x4，I=因瓦锭 x2，F=分流阀 x2，H=漏斗）
        ShapedRecipe pneumaticInput = new ShapedRecipe(
                SteamworkKeys.PNEUMATIC_INPUT, SteamworkItems.PNEUMATIC_INPUT);
        pneumaticInput.shape("RIR", "FHF", "RIR");
        pneumaticInput.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        pneumaticInput.setIngredient('I', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        pneumaticInput.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        pneumaticInput.setIngredient('H', Material.HOPPER);
        pneumaticInput.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pneumaticInput);

        // 气动输出端（提取器）：消耗蒸汽，从相邻容器抽取物品推入导管网络。
        // 因瓦合金（低膨胀管壁）+ 压力表（监控抽取气压）+ 阀芯（精密流量控制）
        // + 蒸汽马达（驱动抽吸机构）+ 橡胶垫圈（密封）+ 黄铜密封环（管接头）
        // 形状：IPI / VMV / GRG  （I=因瓦锭，P=压力表，V=阀芯，M=蒸汽马达，G=垫圈，R=密封环）
        ShapedRecipe pneumaticOutput = new ShapedRecipe(
                SteamworkKeys.PNEUMATIC_OUTPUT, SteamworkItems.PNEUMATIC_OUTPUT);
        pneumaticOutput.shape("IPI", "VMV", "GRG");
        pneumaticOutput.setIngredient('I', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        pneumaticOutput.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        pneumaticOutput.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        pneumaticOutput.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        pneumaticOutput.setIngredient('G', rebarChoice(SteamworkItems.RUBBER_GASKET));
        pneumaticOutput.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        pneumaticOutput.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pneumaticOutput);
        // 每种样本提供三条合成路径：Steamwork 自家材料（产量低、最便宜），
        // 原版材料（产量中等），Pylon 材料（产量最高）。覆盖玩家现有资源。
        // 产量按研究耗时缩放：矿物/有机 400t 是基准，流体 440t 略高，冶金 500t 最高。

        // ---- 矿物分析样本（研究耗时 400t） ----
        // Steamwork 路径：锌精矿 + 二氧化硅砂 + 玻璃瓶 → 2 个
        ShapelessRecipe mineralSampleSteamwork = new ShapelessRecipe(
                SteamworkKeys.MINERAL_ANALYSIS_SAMPLE,
                SteamworkItems.MINERAL_ANALYSIS_SAMPLE.clone().asQuantity(2));
        mineralSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.ZINC_CONCENTRATE));
        mineralSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.SILICA_GRIT));
        mineralSampleSteamwork.addIngredient(Material.GLASS_BOTTLE);
        mineralSampleSteamwork.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(mineralSampleSteamwork);

        // 原版路径：紫水晶碎片 + 红石 + 石英 + 玻璃瓶 → 4 个
        ShapelessRecipe mineralSampleVanilla = new ShapelessRecipe(
                steamworkKey("mineral_analysis_sample_vanilla"),
                SteamworkItems.MINERAL_ANALYSIS_SAMPLE.clone().asQuantity(4));
        mineralSampleVanilla.addIngredient(Material.AMETHYST_SHARD);
        mineralSampleVanilla.addIngredient(Material.REDSTONE);
        mineralSampleVanilla.addIngredient(Material.QUARTZ);
        mineralSampleVanilla.addIngredient(Material.GLASS_BOTTLE);
        mineralSampleVanilla.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(mineralSampleVanilla);

        // Pylon 路径：铜粉 + 铁粉 + 锡粉 + 石英粉 + 玻璃瓶 → 6 个
        ShapelessRecipe mineralSamplePylon = new ShapelessRecipe(
                steamworkKey("mineral_analysis_sample_pylon"),
                SteamworkItems.MINERAL_ANALYSIS_SAMPLE.clone().asQuantity(6));
        mineralSamplePylon.addIngredient(rebarChoice(PylonItems.COPPER_DUST));
        mineralSamplePylon.addIngredient(rebarChoice(PylonItems.IRON_DUST));
        mineralSamplePylon.addIngredient(rebarChoice(PylonItems.TIN_DUST));
        mineralSamplePylon.addIngredient(rebarChoice(PylonItems.QUARTZ_DUST));
        mineralSamplePylon.addIngredient(Material.GLASS_BOTTLE);
        mineralSamplePylon.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(mineralSamplePylon);

        // ---- 有机分析样本（研究耗时 400t） ----
        // Steamwork 路径：无菌生质 + 植物纤维 + 玻璃瓶 → 2 个
        ShapelessRecipe organicSampleSteamwork = new ShapelessRecipe(
                SteamworkKeys.ORGANIC_ANALYSIS_SAMPLE,
                SteamworkItems.ORGANIC_ANALYSIS_SAMPLE.clone().asQuantity(2));
        organicSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.STERILE_BIOMASS));
        organicSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.PLANT_FIBER));
        organicSampleSteamwork.addIngredient(Material.GLASS_BOTTLE);
        organicSampleSteamwork.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(organicSampleSteamwork);

        // 原版路径：发酵蛛眼 + 骨粉 + 小麦 + 玻璃瓶 → 4 个
        ShapelessRecipe organicSampleVanilla = new ShapelessRecipe(
                steamworkKey("organic_analysis_sample_vanilla"),
                SteamworkItems.ORGANIC_ANALYSIS_SAMPLE.clone().asQuantity(4));
        organicSampleVanilla.addIngredient(Material.FERMENTED_SPIDER_EYE);
        organicSampleVanilla.addIngredient(Material.BONE_MEAL);
        organicSampleVanilla.addIngredient(Material.WHEAT);
        organicSampleVanilla.addIngredient(Material.GLASS_BOTTLE);
        organicSampleVanilla.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(organicSampleVanilla);

        // Pylon 路径：纤维 x 2 + 硫磺 + 煤粉 + 玻璃瓶 → 6 个
        ShapelessRecipe organicSamplePylon = new ShapelessRecipe(
                steamworkKey("organic_analysis_sample_pylon"),
                SteamworkItems.ORGANIC_ANALYSIS_SAMPLE.clone().asQuantity(6));
        organicSamplePylon.addIngredient(rebarChoice(PylonItems.FIBER));
        organicSamplePylon.addIngredient(rebarChoice(PylonItems.FIBER));
        organicSamplePylon.addIngredient(rebarChoice(PylonItems.SULFUR));
        organicSamplePylon.addIngredient(rebarChoice(PylonItems.COAL_DUST));
        organicSamplePylon.addIngredient(Material.GLASS_BOTTLE);
        organicSamplePylon.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(organicSamplePylon);

        // ---- 冶金分析样本（研究耗时 500t，最贵） ----
        // Steamwork 路径：热处理金属 + 机器废屑 + 玻璃瓶 → 3 个
        ShapelessRecipe metallurgicalSampleSteamwork = new ShapelessRecipe(
                SteamworkKeys.METALLURGICAL_ANALYSIS_SAMPLE,
                SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE.clone().asQuantity(3));
        metallurgicalSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.HEAT_TREATED_METAL));
        metallurgicalSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.MACHINE_SCRAP));
        metallurgicalSampleSteamwork.addIngredient(Material.GLASS_BOTTLE);
        metallurgicalSampleSteamwork.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(metallurgicalSampleSteamwork);

        // 原版路径：下界合金碎片 + 铁锭 + 金粒 + 玻璃瓶 → 6 个
        ShapelessRecipe metallurgicalSampleVanilla = new ShapelessRecipe(
                steamworkKey("metallurgical_analysis_sample_vanilla"),
                SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE.clone().asQuantity(6));
        metallurgicalSampleVanilla.addIngredient(Material.NETHERITE_SCRAP);
        metallurgicalSampleVanilla.addIngredient(Material.IRON_INGOT);
        metallurgicalSampleVanilla.addIngredient(Material.GOLD_NUGGET);
        metallurgicalSampleVanilla.addIngredient(Material.GLASS_BOTTLE);
        metallurgicalSampleVanilla.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(metallurgicalSampleVanilla);

        // Pylon 路径：青铜锭 + 钢锭 + 锻铁 + 渣 + 玻璃瓶 → 9 个
        ShapelessRecipe metallurgicalSamplePylon = new ShapelessRecipe(
                steamworkKey("metallurgical_analysis_sample_pylon"),
                SteamworkItems.METALLURGICAL_ANALYSIS_SAMPLE.clone().asQuantity(9));
        metallurgicalSamplePylon.addIngredient(rebarChoice(PylonItems.BRONZE_INGOT));
        metallurgicalSamplePylon.addIngredient(rebarChoice(PylonItems.STEEL_INGOT));
        metallurgicalSamplePylon.addIngredient(rebarChoice(PylonItems.WROUGHT_IRON));
        metallurgicalSamplePylon.addIngredient(rebarChoice(PylonItems.SLAG));
        metallurgicalSamplePylon.addIngredient(Material.GLASS_BOTTLE);
        metallurgicalSamplePylon.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(metallurgicalSamplePylon);

        // ---- 流体分析样本（研究耗时 440t） ----
        // Steamwork 路径：蒸馏水瓶 + 矿物浸出液瓶 + 矿物助熔剂 → 2 个
        ShapelessRecipe fluidSampleSteamwork = new ShapelessRecipe(
                SteamworkKeys.FLUID_ANALYSIS_SAMPLE,
                SteamworkItems.FLUID_ANALYSIS_SAMPLE.clone().asQuantity(2));
        fluidSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.DISTILLED_WATER_VIAL));
        fluidSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.MINERAL_LEACHATE_VIAL));
        fluidSampleSteamwork.addIngredient(rebarChoice(SteamworkItems.MINERAL_FLUX));
        fluidSampleSteamwork.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(fluidSampleSteamwork);

        // 原版路径：水瓶 + 凝魂沙 + 紫颂果 + 糖 + 玻璃瓶 → 5 个
        ShapelessRecipe fluidSampleVanilla = new ShapelessRecipe(
                steamworkKey("fluid_analysis_sample_vanilla"),
                SteamworkItems.FLUID_ANALYSIS_SAMPLE.clone().asQuantity(5));
        fluidSampleVanilla.addIngredient(Material.POTION);
        fluidSampleVanilla.addIngredient(Material.SOUL_SAND);
        fluidSampleVanilla.addIngredient(Material.CHORUS_FRUIT);
        fluidSampleVanilla.addIngredient(Material.SUGAR);
        fluidSampleVanilla.addIngredient(Material.GLASS_BOTTLE);
        fluidSampleVanilla.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(fluidSampleVanilla);

        // Pylon 路径：石膏粉 + 硫磺 + 黑曜石碎片 + 细密沉积物 + 玻璃瓶 → 7 个
        ShapelessRecipe fluidSamplePylon = new ShapelessRecipe(
                steamworkKey("fluid_analysis_sample_pylon"),
                SteamworkItems.FLUID_ANALYSIS_SAMPLE.clone().asQuantity(7));
        fluidSamplePylon.addIngredient(rebarChoice(PylonItems.GYPSUM_DUST));
        fluidSamplePylon.addIngredient(rebarChoice(PylonItems.SULFUR));
        fluidSamplePylon.addIngredient(rebarChoice(PylonItems.OBSIDIAN_CHIP));
        fluidSamplePylon.addIngredient(rebarChoice(PylonItems.FINE_SEDIMENT));
        fluidSamplePylon.addIngredient(Material.GLASS_BOTTLE);
        fluidSamplePylon.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(fluidSamplePylon);

        // 精密调校仪：精密轴承（高精度转动支撑）+ 精密螺杆（微调紧固）+ 热处理金属（耐热手柄）
        //           + 精密齿轮（传动核心）+ 锻造钢板（结构底座）+ 钨锭（高硬度探针）
        // 前置：精密铣床（轴承/螺杆/齿轮）+ 液压锻造机（锻造钢板）+ 钨合金冶炼
        // 形状：PBP / SGS / FTF
        ShapedRecipe machineCalibrator = new ShapedRecipe(
                SteamworkKeys.MACHINE_CALIBRATOR, SteamworkItems.MACHINE_CALIBRATOR);
        machineCalibrator.shape("PBP", "SGS", "FTF");
        machineCalibrator.setIngredient('P', rebarChoice(SteamworkItems.PRECISION_BEARING));
        machineCalibrator.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_SCREW));
        machineCalibrator.setIngredient('S', rebarChoice(SteamworkItems.HEAT_TREATED_METAL));
        machineCalibrator.setIngredient('G', rebarChoice(SteamworkItems.PRECISION_GEAR));
        machineCalibrator.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        machineCalibrator.setIngredient('T', rebarChoice(SteamworkItems.TUNGSTEN_INGOT));
        machineCalibrator.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(machineCalibrator);

        // 节能模组：加热线圈（热能回收）夹住调校仪，两侧橡皮垫圈（隔热密封），十字形。
        ShapedRecipe energySaveModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_ENERGY_SAVE, SteamworkItems.UPGRADE_MODULE_ENERGY_SAVE);
        energySaveModule.shape(" H ", "RCR", " H ");
        energySaveModule.setIngredient('H', rebarChoice(SteamworkItems.HEATING_COIL));
        energySaveModule.setIngredient('R', rebarChoice(SteamworkItems.RUBBER_GASKET));
        energySaveModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        energySaveModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(energySaveModule);

        // 范围模组（涡轮专用）：精密轴承（旋转感测）+ 精密齿轮（传动扩展）+ 调校仪，十字形。
        ShapedRecipe rangeModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_RANGE, SteamworkItems.UPGRADE_MODULE_RANGE);
        rangeModule.shape(" B ", "GCG", " B ");
        rangeModule.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        rangeModule.setIngredient('G', rebarChoice(SteamworkItems.PRECISION_GEAR));
        rangeModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        rangeModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(rangeModule);

        // 增幅模组（涡轮专用，不可叠加）：蒸汽马达（动力源）+ 锻造板（承压结构）+ 调校仪，十字形。
        ShapedRecipe boostModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_BOOST, SteamworkItems.UPGRADE_MODULE_BOOST);
        boostModule.shape(" M ", "FCF", " M ");
        boostModule.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        boostModule.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        boostModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        boostModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(boostModule);

        // 自动进料模组（与自动出料互斥）：精密轴承（传送机构）+ 漏斗（进料口）+ 黄铜流量阀（流量控制）+ 调校仪 + 精密螺钉
        ShapedRecipe autoInputModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_AUTO_INPUT, SteamworkItems.UPGRADE_MODULE_AUTO_INPUT);
        autoInputModule.shape("BHB", "FCF", " S ");
        autoInputModule.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        autoInputModule.setIngredient('H', Material.HOPPER);
        autoInputModule.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        autoInputModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        autoInputModule.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        autoInputModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(autoInputModule);

        // 自动出料模组（与自动进料互斥）：精密轴承（传送机构）+ 投掷器（出料口）+ 精密阀门（排料控制）+ 调校仪 + 精密螺钉
        ShapedRecipe autoOutputModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_AUTO_OUTPUT, SteamworkItems.UPGRADE_MODULE_AUTO_OUTPUT);
        autoOutputModule.shape("BDB", "VCV", " S ");
        autoOutputModule.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        autoOutputModule.setIngredient('D', Material.DROPPER);
        autoOutputModule.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        autoOutputModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        autoOutputModule.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        autoOutputModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(autoOutputModule);

        // 批量加工模组：磨损板（耐久高吞吐）+ 桶（容量扩展）+ 漏斗（物料吞吐）+ 调校仪，满格。
        ShapedRecipe bulkModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_BULK, SteamworkItems.UPGRADE_MODULE_BULK);
        bulkModule.shape("WBW", "HCH", "WBW");
        bulkModule.setIngredient('W', rebarChoice(SteamworkItems.WEAR_PLATE));
        bulkModule.setIngredient('B', Material.BARREL);
        bulkModule.setIngredient('H', Material.HOPPER);
        bulkModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        bulkModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(bulkModule);

        // 自动生产模组（产线入口专用，后期配方）：
        // 高压管（蒸汽信号导管）+ 精密轴承（精密传动机构），上排；
        // 压力表×2（双路感应监测）夹住调校仪核心，中排；
        // 钨锭×2（高强度承压结构）夹住蒸汽马达（自动触发动力），下排。
        ShapedRecipe autoProductionModule = new ShapedRecipe(
                SteamworkKeys.AUTO_PRODUCTION_MODULE, SteamworkItems.AUTO_PRODUCTION_MODULE);
        autoProductionModule.shape("SBS", "PCP", "TMT");
        autoProductionModule.setIngredient('S', rebarChoice(SteamworkItems.HIGH_PRESSURE_PIPE));
        autoProductionModule.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        autoProductionModule.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        autoProductionModule.setIngredient('C', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        autoProductionModule.setIngredient('T', rebarChoice(SteamworkItems.TUNGSTEN_INGOT));
        autoProductionModule.setIngredient('M', rebarChoice(SteamworkItems.STEAM_MOTOR));
        autoProductionModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(autoProductionModule);

        // Pylon 联动模组：精密调校仪（中心）+ 催化剂核心 + 钯合金锭 + 精密阀门
        ShapedRecipe pylonCompatModule = new ShapedRecipe(
                SteamworkKeys.UPGRADE_MODULE_PYLON_COMPAT, SteamworkItems.UPGRADE_MODULE_PYLON_COMPAT);
        pylonCompatModule.shape("PVP", "CAC", "PVP");
        pylonCompatModule.setIngredient('P', rebarChoice(SteamworkItems.PALLADIUM_ALLOY_INGOT));
        pylonCompatModule.setIngredient('V', rebarChoice(SteamworkItems.PRECISION_VALVE));
        pylonCompatModule.setIngredient('C', rebarChoice(SteamworkItems.CATALYST_CORE));
        pylonCompatModule.setIngredient('A', rebarChoice(SteamworkItems.MACHINE_CALIBRATOR));
        pylonCompatModule.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(pylonCompatModule);

        // 重型冲击破碎机：锰钢锭主体 + 铣削刀片（高硬度冲击头）+ 黄铜齿轮（传动）+ 黄铜阀芯（控制）
        // 形状：MBM / GAG / MVM
        ShapedRecipe heavyImpactCrusher = new ShapedRecipe(
                SteamworkKeys.HEAVY_IMPACT_CRUSHER, SteamworkItems.HEAVY_IMPACT_CRUSHER);
        heavyImpactCrusher.shape("MBM", "GAG", "MVM");
        heavyImpactCrusher.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_STEEL_INGOT));
        heavyImpactCrusher.setIngredient('B', rebarChoice(SteamworkItems.MILLING_BLADE));
        heavyImpactCrusher.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        heavyImpactCrusher.setIngredient('A', Material.ANVIL);
        heavyImpactCrusher.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        heavyImpactCrusher.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(heavyImpactCrusher);

        // 液压锻造机：锰钢锭（高强度结构框架）+ 因瓦合金（耐压结构）+ 黄铜密封环（密封）+ 铁砧（锻造基座）
        // 形状：MIM / SAS / MIM
        ShapedRecipe hydraulicForge = new ShapedRecipe(
                SteamworkKeys.HYDRAULIC_FORGE, SteamworkItems.HYDRAULIC_FORGE);
        hydraulicForge.shape("MIM", "SAS", "MIM");
        hydraulicForge.setIngredient('M', rebarChoice(SteamworkItems.MANGANESE_STEEL_INGOT));
        hydraulicForge.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        hydraulicForge.setIngredient('S', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        hydraulicForge.setIngredient('A', Material.ANVIL);
        hydraulicForge.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(hydraulicForge);

        // 精密结晶炉：锻造钢板结构底座 + 紫水晶簇（晶格） + 玻璃（观察窗） + 黄铜阀门核心（流量控制）
        // 形状：FGF / VAV / FVF（F=锻造钢板，V=黄铜阀芯，A=紫水晶块，G=玻璃）
        ShapedRecipe precisionCrystallizer = new ShapedRecipe(
                SteamworkKeys.PRECISION_CRYSTALLIZER, SteamworkItems.PRECISION_CRYSTALLIZER);
        precisionCrystallizer.shape("FGF", "VAV", "FVF");
        precisionCrystallizer.setIngredient('F', rebarChoice(SteamworkItems.FORGED_PLATE));
        precisionCrystallizer.setIngredient('G', Material.GLASS);
        precisionCrystallizer.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        precisionCrystallizer.setIngredient('A', Material.AMETHYST_BLOCK);
        precisionCrystallizer.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionCrystallizer);


        // 精密离心机：锻造钢板底座 + 黄铜扇叶（旋转动力）+ 精密轴承（高速旋转支撑）+ 精密螺杆（精密紧固）
        // 形状：DFD / SBS / PBP（D=硬铝锭, F=黄铜扇叶, S=精密螺杆, B=精密轴承, P=锻造钢板）
        ShapedRecipe precisionCentrifuge = new ShapedRecipe(
                SteamworkKeys.PRECISION_CENTRIFUGE, SteamworkItems.PRECISION_CENTRIFUGE);
        precisionCentrifuge.shape("DFD", "SBS", "PBP");
        precisionCentrifuge.setIngredient('D', rebarChoice(SteamworkItems.DURALUMIN_INGOT));
        precisionCentrifuge.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FAN_BLADE));
        precisionCentrifuge.setIngredient('S', rebarChoice(SteamworkItems.PRECISION_SCREW));
        precisionCentrifuge.setIngredient('B', rebarChoice(SteamworkItems.PRECISION_BEARING));
        precisionCentrifuge.setIngredient('P', rebarChoice(SteamworkItems.FORGED_PLATE));
        precisionCentrifuge.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(precisionCentrifuge);
    }

    /** Adds reversible 9 ingots <-> 1 block recipes. */
    private static void addBlockRecipe(NamespacedKey blockKey, ItemStack blockItem, ItemStack ingotItem) {
        // Ingots to block.
        ShapedRecipe toBlock = new ShapedRecipe(blockKey, blockItem);
        toBlock.shape("III", "III", "III");
        toBlock.setIngredient('I', rebarChoice(ingotItem));
        toBlock.setCategory(CraftingBookCategory.BUILDING);
        RecipeType.VANILLA_SHAPED.addRecipe(toBlock);

        // Block to ingots.
        ShapelessRecipe fromBlock = new ShapelessRecipe(
                steamworkKey(blockKey.getKey() + "_from_block"), ingotItem.clone().asQuantity(9));
        fromBlock.addIngredient(rebarChoice(blockItem));
        fromBlock.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPELESS.addRecipe(fromBlock);
    }
}
