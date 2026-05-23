package io.github.steamwork.recipes.registration;

import io.github.pylonmc.pylon.PylonItems;
import io.github.pylonmc.rebar.recipe.RecipeType;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
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
        nichromeDustCrude.addIngredient(4, Material.BLAZE_POWDER);
        nichromeDustCrude.addIngredient(Material.IRON_INGOT);
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

        ShapedRecipe steamArm = new ShapedRecipe(
                SteamworkKeys.STEAM_ARM, SteamworkItems.STEAM_ARM);
        steamArm.shape("BPB", "VGV", "FIF");
        steamArm.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamArm.setIngredient('P', Material.PISTON);
        steamArm.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamArm.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        steamArm.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FLOW_VALVE));
        steamArm.setIngredient('I', rebarChoice(SteamworkItems.TREATED_WOOD));
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

        // Advanced Steam Turbine - more expensive recipe with premium materials
        // Uses INVAR_INGOT, BRASS_DISTILLATION_TUBE, and STEEL_INGOT for enhanced performance
        ShapedRecipe advancedSteamTurbine = new ShapedRecipe(
                SteamworkKeys.ADVANCED_STEAM_TURBINE, SteamworkItems.ADVANCED_STEAM_TURBINE);
        advancedSteamTurbine.shape("FDF", "CPC", "IGI");
        advancedSteamTurbine.setIngredient('F', rebarChoice(SteamworkItems.BRASS_FAN_BLADE));
        advancedSteamTurbine.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
        advancedSteamTurbine.setIngredient('C', rebarChoice(SteamworkItems.HEATING_COIL));
        advancedSteamTurbine.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        advancedSteamTurbine.setIngredient('I', rebarChoice(SteamworkItems.INVAR_INGOT));
        advancedSteamTurbine.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        advancedSteamTurbine.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(advancedSteamTurbine);

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
        steamPress.shape("VPV", "GAG", "BRB");
        steamPress.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamPress.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamPress.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
        steamPress.setIngredient('A', Material.PISTON);
        steamPress.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
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
        assemblyBench.shape("GGG", "BAB", "III");
        assemblyBench.setIngredient('G', rebarChoice(SteamworkItems.BRASS_GEAR));
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

        // 蒸汽科研接口：黄铜分析架 + 滤网（用于样品定位）+ 蒸汽气路 + 压力表读数。
        ShapedRecipe steamScienceInterface = new ShapedRecipe(
                SteamworkKeys.STEAM_SCIENCE_INTERFACE, SteamworkItems.STEAM_SCIENCE_INTERFACE);
        steamScienceInterface.shape("DPD", "FLF", "BRB");
        steamScienceInterface.setIngredient('D', rebarChoice(SteamworkItems.BRASS_DISTILLATION_TUBE));
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

        // 蒸汽加压机：铁砧活塞腔 + 黄铜密封 + 阀芯 + 压力表，将普通蒸汽压缩为加压蒸汽。
        ShapedRecipe steamCompressor = new ShapedRecipe(
                SteamworkKeys.STEAM_COMPRESSOR, SteamworkItems.STEAM_COMPRESSOR);
        steamCompressor.shape("VPV", "GAG", "BRB");
        steamCompressor.setIngredient('V', rebarChoice(SteamworkItems.BRASS_VALVE_CORE));
        steamCompressor.setIngredient('P', rebarChoice(SteamworkItems.PRESSURE_GAUGE));
        steamCompressor.setIngredient('G', rebarChoice(SteamworkItems.RUBBER_GASKET));
        steamCompressor.setIngredient('A', Material.PISTON);
        steamCompressor.setIngredient('B', rebarChoice(SteamworkItems.BRASS_INGOT));
        steamCompressor.setIngredient('R', rebarChoice(SteamworkItems.BRASS_SEAL_RING));
        steamCompressor.setCategory(CraftingBookCategory.MISC);
        RecipeType.VANILLA_SHAPED.addRecipe(steamCompressor);

        // ===== 分析样本 =====
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
