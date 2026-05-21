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
        steamPress.setIngredient('A', Material.ANVIL);
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
