package io.github.steamwork;

import io.github.pylonmc.rebar.item.research.Research;
import io.github.pylonmc.rebar.config.RebarConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

// Research nodes are keyed as <discipline>_<topic> to match the four disciplines
// used by SteamScienceInterface (material / biology / precision / chemistry).
//
// ── 全局研究点曲线（通过 Rebar 指南解锁）────────────────────────────────────────
//   T0 entry   :  15- 30  （入门手工，无科研门槛）
//   T1 early   :  50- 70  （初期机械）
//   T2 mid     : 100-160  （中期自动化）
//   T3 advanced: 100-200  （高级，部分需配合学科点数）
//   T4 endgame : 300-500  （终局）
//
// ── 学科点数门控研究（★ 标注，需通过蒸汽科研接口解锁）───────────────────────────
//   这些研究的 cost = null，Rebar 指南无法解锁，
//   必须在科研接口 GUI 消耗对应学科点数。详见 SteamworkDisciplineResearch。
public final class SteamworkResearches {

    private SteamworkResearches() {
        throw new AssertionError("Utility class");
    }

    // ---- Tier 0: entry-level handcraft  (15-30 pt) ---------------------

    public static final Research MATERIAL_BASIC_METALS = new Research(
            steamworkKey("material_basic_metals"), SteamworkItems.BRASS_INGOT, 25L,
            SteamworkKeys.ZINC_INGOT, SteamworkKeys.BRASS_INGOT,
            SteamworkKeys.RUBBER_GASKET, SteamworkKeys.PRESSURE_GAUGE,
            SteamworkKeys.ZINC_CONCENTRATE, SteamworkKeys.SILICA_GRIT,
            SteamworkKeys.MINERAL_FLUX, SteamworkKeys.BRONZE_BOILER,
            SteamworkKeys.BRASS_GEAR,
            SteamworkKeys.BRASS_DISTILLATION_TUBE, SteamworkKeys.BRASS_FILTER,
            SteamworkKeys.BRASS_SIEVE, SteamworkKeys.BRASS_FLOW_VALVE,
            SteamworkKeys.BRASS_FAN_BLADE, SteamworkKeys.BRASS_VALVE_CORE,
            SteamworkKeys.BRASS_SEAL_RING,
            SteamworkKeys.NICHROME_DUST, SteamworkKeys.NICHROME_INGOT,
            SteamworkKeys.HEATING_COIL);

    public static final Research BIOLOGY_PLANT_PROCESSING = new Research(
            steamworkKey("biology_plant_processing"), SteamworkItems.PLANT_FIBER, 20L,
            SteamworkKeys.PLANT_FIBER, SteamworkKeys.STEAM_PULP, SteamworkKeys.RAW_RESIN,
            SteamworkKeys.VULCANIZED_RUBBER, SteamworkKeys.STERILE_BIOMASS);

    public static final Research MATERIAL_BASIC_CONSTRUCTION = new Research(
            steamworkKey("material_basic_construction"), SteamworkItems.FIBERBOARD, 20L,
            SteamworkKeys.TREATED_WOOD, SteamworkKeys.FIBERBOARD,
            SteamworkKeys.RUBBERIZED_FABRIC, SteamworkKeys.STERILE_CULTURE);

    // ---- Tier 1: early machinery  (50-70 pt) ---------------------------

    public static final Research MATERIAL_BASIC_MACHINES = new Research(
            steamworkKey("material_basic_machines"), SteamworkItems.STEAM_STERILIZER, 60L,
            SteamworkKeys.STEAM_STERILIZER, SteamworkKeys.STEAM_STEEPING_VAT,
            SteamworkKeys.STEAM_WASHING_TROUGH,
            SteamworkKeys.STEAM_PRESS, SteamworkKeys.STEAM_GRINDER,
            SteamworkKeys.STEAM_PRESSURIZED_FURNACE,
            SteamworkKeys.STEAM_HEATING_CHAMBER,
            SteamworkKeys.HEAT_TREATED_METAL, SteamworkKeys.MACHINE_SCRAP,
            SteamworkKeys.GRANITE_DUST, SteamworkKeys.DIORITE_DUST, SteamworkKeys.ANDESITE_DUST);

    // ---- Tier 2: mid-tier processing  (100-160 pt) ---------------------

    public static final Research PRECISION_STEAM_CALIBRATION = new Research(
            steamworkKey("precision_steam_calibration"), SteamworkItems.MACHINE_CALIBRATOR, 100L,
            SteamworkKeys.MACHINE_CALIBRATOR,
            SteamworkKeys.UPGRADE_MODULE_ENERGY_SAVE,
            SteamworkKeys.UPGRADE_MODULE_AUTO_INPUT,
            SteamworkKeys.UPGRADE_MODULE_AUTO_OUTPUT,
            SteamworkKeys.UPGRADE_MODULE_BULK,
            SteamworkKeys.UPGRADE_MODULE_RANGE,
            SteamworkKeys.UPGRADE_MODULE_BOOST,
            SteamworkKeys.UPGRADE_MODULE_PYLON_COMPAT,
            SteamworkKeys.AUTO_PRODUCTION_MODULE,
            SteamworkKeys.TERMINAL_CAPACITY_MODULE);

    public static final Research PRECISION_STEAM_ASSEMBLY = new Research(
            steamworkKey("precision_steam_assembly"), SteamworkItems.STEAM_ASSEMBLY_BENCH, 120L,
            SteamworkKeys.STEAM_ASSEMBLY_BENCH,
            SteamworkKeys.STEAM_CANISTER_BRASS, SteamworkKeys.STEAM_CANISTER_INVAR,
            SteamworkKeys.STEAM_CANISTER_TUNGSTEN, SteamworkKeys.STEAM_CANISTER_BENCH,
            SteamworkKeys.STEAM_CHARGING_CHAMBER);

    public static final Research PRECISION_PRODUCTION_LINE = new Research(
            steamworkKey("precision_production_line"), SteamworkItems.PRODUCTION_LINE_INLET, 130L,
            SteamworkKeys.PRODUCTION_LINE_INLET, SteamworkKeys.PRODUCTION_LINE_OUTLET,
            SteamworkKeys.PRODUCTION_LINE_BLUEPRINT, SteamworkKeys.PRODUCTION_LINE_BUFFER_CHEST);

    public static final Research PRECISION_PNEUMATIC_LOGIC = new Research(
            steamworkKey("precision_pneumatic_logic"), SteamworkItems.PNEUMATIC_LOGIC_GATE, 120L,
            SteamworkKeys.STEAM_VORTEX_TUBE,
            SteamworkKeys.PNEUMATIC_LOGIC_GATE,
            SteamworkKeys.STEAM_OSCILLATOR,
            SteamworkKeys.STEAM_PRESSURE_TRANSDUCER,
            SteamworkKeys.STEAM_DIFFERENCE_ENGINE,
            SteamworkKeys.PNEUMATIC_DIFFERENTIAL_GATE,
            SteamworkKeys.PNEUMATIC_PULSER,
            SteamworkKeys.PNEUMATIC_LATCH);

    /** 解锁蒸汽科研接口 —— 必须保持全局研究点可解锁，否则玩家无法建造接口来获取学科点数。 */
    public static final Research CHEMISTRY_BASIC_RESEARCH = new Research(
            steamworkKey("chemistry_basic_research"), SteamworkItems.STEAM_SCIENCE_INTERFACE, 50L,
            SteamworkKeys.MINERAL_ANALYSIS_SAMPLE, SteamworkKeys.ORGANIC_ANALYSIS_SAMPLE,
            SteamworkKeys.METALLURGICAL_ANALYSIS_SAMPLE, SteamworkKeys.FLUID_ANALYSIS_SAMPLE,
            SteamworkKeys.REFINED_MINERAL_SAMPLE, SteamworkKeys.CONCENTRATED_ORGANIC_SAMPLE,
            SteamworkKeys.REFINED_METALLURGICAL_SAMPLE, SteamworkKeys.PURIFIED_FLUID_SAMPLE,
            SteamworkKeys.STEAM_SCIENCE_INTERFACE, SteamworkKeys.ANALYSIS_RESIDUE);

    // ---- Tier 3: advanced alloys & fluids  (120-200 pt / ★ discipline) --

    /**
     * ★ 材料学门控：需在蒸汽科研接口消耗 80 材料学点数解锁。
     * 包含全部高阶合金，是中后期最重要的研究之一。
     */
    public static final Research MATERIAL_ADVANCED_INGOTS = new Research(
            steamworkKey("material_advanced_ingots"), SteamworkItems.INVAR_INGOT, (Long) null,
            SteamworkKeys.INVAR_DUST, SteamworkKeys.DURALUMIN_DUST, SteamworkKeys.TUNGSTEN_DUST,
            SteamworkKeys.MANGANESE_STEEL_DUST, SteamworkKeys.MANGANESE_BRONZE_DUST,
            SteamworkKeys.INVAR_INGOT, SteamworkKeys.DURALUMIN_INGOT, SteamworkKeys.TUNGSTEN_INGOT,
            SteamworkKeys.MANGANESE_STEEL_INGOT, SteamworkKeys.MANGANESE_BRONZE_INGOT,
            SteamworkKeys.INVAR_BLOCK, SteamworkKeys.DURALUMIN_BLOCK, SteamworkKeys.TUNGSTEN_BLOCK,
            SteamworkKeys.MANGANESE_STEEL_BLOCK, SteamworkKeys.MANGANESE_BRONZE_BLOCK);

    /**
     * ★ 材料学门控：需在蒸汽科研接口消耗 60 材料学点数解锁。
     */
    public static final Research MATERIAL_ADVANCED_BOILERS = new Research(
            steamworkKey("material_advanced_boilers"), SteamworkItems.INVAR_BOILER, (Long) null,
            SteamworkKeys.INVAR_BOILER, SteamworkKeys.MANGANESE_STEEL_BOILER,
            SteamworkKeys.TUNGSTEN_BOILER);

    public static final Research MATERIAL_STEAM_LOGISTICS = new Research(
            steamworkKey("material_steam_logistics"), SteamworkItems.STEAM_COMPRESSOR, 130L,
            SteamworkKeys.STEAM_COMPRESSOR,
            SteamworkKeys.PNEUMATIC_DUCT, SteamworkKeys.PNEUMATIC_OUTPUT, SteamworkKeys.PNEUMATIC_INPUT,
            SteamworkKeys.PNEUMATIC_CARGO_HUB,
            SteamworkKeys.STEAM_CATAPULT, SteamworkKeys.STEAM_SORTER,
            SteamworkKeys.PNEUMATIC_DISTRIBUTOR,
            SteamworkKeys.PNEUMATIC_GATE_VALVE,
            SteamworkKeys.PNEUMATIC_PRESSURE_MODULE,
            SteamworkKeys.PNEUMATIC_LINE_VALVE, SteamworkKeys.PNEUMATIC_LINE_SENSOR);

    /**
     * ★ 化学门控：需在蒸汽科研接口消耗 100 化学点数解锁。
     */
    public static final Research CHEMISTRY_DISTILLATION = new Research(
            steamworkKey("chemistry_distillation"), SteamworkItems.STEAM_DISTILLATION_TOWER, (Long) null,
            SteamworkKeys.STEAM_DISTILLATION_TOWER, SteamworkKeys.DISTILLATION_TOWER_SECTION,
            SteamworkKeys.DISTILLATION_CONDENSER,
            SteamworkKeys.REFINED_RESIN, SteamworkKeys.PLANT_ESSENCE,
            SteamworkKeys.DISTILLED_WATER_VIAL, SteamworkKeys.MINERAL_LEACHATE_VIAL,
            SteamworkKeys.WASTE_ACID_VIAL, SteamworkKeys.MINERAL_CONCENTRATE,
            SteamworkKeys.FIBER_RESIDUE);

    /**
     * ★ 精密工程门控：需在蒸汽科研接口消耗 150 精密点数解锁。
     * 包含全部精密加工机器，是通往终局的核心研究。
     */
    public static final Research PRECISION_ADVANCED_AUTOMATION_1 = new Research(
            steamworkKey("precision_advanced_automation_1"), SteamworkItems.STEAM_PRECISION_MILL, (Long) null,
            SteamworkKeys.STEAM_ARM, SteamworkKeys.STEAM_MOTOR,
            SteamworkKeys.STEAM_PRECISION_MILL,
            SteamworkKeys.PRECISION_GEAR, SteamworkKeys.PRECISION_SCREW,
            SteamworkKeys.PRECISION_VALVE, SteamworkKeys.WEAR_PLATE,
            SteamworkKeys.HEAT_SINK, SteamworkKeys.MILLING_BLADE,
            SteamworkKeys.CATALYST_CORE, SteamworkKeys.PRECISION_BEARING,
            SteamworkKeys.PRECISION_CATALYTIC_REACTOR,
            SteamworkKeys.HEAVY_IMPACT_CRUSHER,
            SteamworkKeys.HYDRAULIC_FORGE,
            SteamworkKeys.HIGH_PRESSURE_PIPE, SteamworkKeys.HIGH_PRESSURE_FLANGE,
            SteamworkKeys.HYDRAULIC_PISTON, SteamworkKeys.HYDRAULIC_SEAL,
            SteamworkKeys.FORGED_PLATE,
            SteamworkKeys.HIGH_POLYMER,
            SteamworkKeys.PALLADIUM_WORKPIECE_1, SteamworkKeys.PALLADIUM_WORKPIECE_2,
            SteamworkKeys.PALLADIUM_WORKPIECE_3, SteamworkKeys.PALLADIUM_ALLOY_INGOT,
            SteamworkKeys.JET_NOZZLE, SteamworkKeys.STEAM_FLIGHT_CORE,
            SteamworkKeys.FLIGHT_CORE_WORKPIECE_1, SteamworkKeys.FLIGHT_CORE_WORKPIECE_2,
            SteamworkKeys.FLIGHT_CORE_WORKPIECE_3, SteamworkKeys.TURBINE_ROTOR,
            SteamworkKeys.PRECISION_CRYSTALLIZER,
            SteamworkKeys.PRECISION_CENTRIFUGE,
            SteamworkKeys.PRECISION_FOUNDRY);

    /**
     * ★ 精密工程门控：需在蒸汽科研接口消耗 200 精密点数解锁。
     * 包含机器人核心、四种机器人及控制终端——比精密自动化更深一层的终局内容。
     */
    public static final Research PRECISION_STEAM_ROBOTS = new Research(
            steamworkKey("precision_steam_robots"), SteamworkItems.MINING_ROBOT, (Long) null,
            SteamworkKeys.ROBOT_CORE,
            SteamworkKeys.ROBOT_CORE_WORKPIECE_1,
            SteamworkKeys.ROBOT_CORE_WORKPIECE_2,
            SteamworkKeys.ROBOT_CORE_WORKPIECE_3,
            SteamworkKeys.MINING_ROBOT, SteamworkKeys.LUMBER_ROBOT,
            SteamworkKeys.HAUL_ROBOT, SteamworkKeys.PATROL_ROBOT,
            SteamworkKeys.PICKER_ROBOT, SteamworkKeys.FARMER_ROBOT,
            SteamworkKeys.BUTCHER_ROBOT,
            SteamworkKeys.ROBOT_CONTROL_TERMINAL);

    // ---- Tier 4: endgame  (300-500 pt) ---------------------------------

    public static final Research PRECISION_TURBINES_1 = new Research(
            steamworkKey("precision_turbines_1"), SteamworkItems.SIMPLE_STEAM_TURBINE, 80L,
            SteamworkKeys.SIMPLE_STEAM_TURBINE);

    public static final Research PRECISION_TURBINES_2 = new Research(
            steamworkKey("precision_turbines_2"), SteamworkItems.BASIC_PROCESSING_TURBINE, 350L,
            SteamworkKeys.BASIC_PROCESSING_TURBINE,
            SteamworkKeys.PRECISION_PROCESSING_TURBINE,
            SteamworkKeys.HYDRAULIC_TURBINE,
            SteamworkKeys.DIESEL_TURBINE,
            SteamworkKeys.PYLON_UNIVERSAL_TURBINE,
            SteamworkKeys.PRECISION_STEAM_TURBINE);

    public static final Research PRECISION_STEAM_EQUIPMENT = new Research(
            steamworkKey("precision_steam_equipment"), SteamworkItems.STEAM_SWORD, 450L,
            SteamworkKeys.STEAM_SWORD, SteamworkKeys.STEAM_PICKAXE, SteamworkKeys.STEAM_AXE,
            SteamworkKeys.STEAM_SHOVEL, SteamworkKeys.STEAM_HOE,
            SteamworkKeys.STEAM_BRONZE_SWORD, SteamworkKeys.STEAM_BRONZE_PICKAXE,
            SteamworkKeys.STEAM_BRONZE_AXE, SteamworkKeys.STEAM_BRONZE_SHOVEL,
            SteamworkKeys.STEAM_BRONZE_HOE,
            SteamworkKeys.STEAM_INVAR_SWORD, SteamworkKeys.STEAM_INVAR_PICKAXE,
            SteamworkKeys.STEAM_INVAR_AXE, SteamworkKeys.STEAM_INVAR_SHOVEL,
            SteamworkKeys.STEAM_INVAR_HOE,
            SteamworkKeys.STEAM_TUNGSTEN_SWORD, SteamworkKeys.STEAM_TUNGSTEN_PICKAXE,
            SteamworkKeys.STEAM_TUNGSTEN_AXE, SteamworkKeys.STEAM_TUNGSTEN_SHOVEL,
            SteamworkKeys.STEAM_TUNGSTEN_HOE);

    public static final Research MATERIAL_STEAM_ARMOR = new Research(
            steamworkKey("material_steam_armor"), SteamworkItems.STEAM_CHESTPLATE, 600L,
            SteamworkKeys.STEAM_HELMET, SteamworkKeys.STEAM_CHESTPLATE,
            SteamworkKeys.STEAM_LEGGINGS, SteamworkKeys.STEAM_BOOTS,
            SteamworkKeys.STEAM_BRONZE_HELMET, SteamworkKeys.STEAM_BRONZE_CHESTPLATE,
            SteamworkKeys.STEAM_BRONZE_LEGGINGS, SteamworkKeys.STEAM_BRONZE_BOOTS,
            SteamworkKeys.STEAM_INVAR_HELMET, SteamworkKeys.STEAM_INVAR_CHESTPLATE,
            SteamworkKeys.STEAM_INVAR_LEGGINGS, SteamworkKeys.STEAM_INVAR_BOOTS,
            SteamworkKeys.STEAM_TUNGSTEN_HELMET, SteamworkKeys.STEAM_TUNGSTEN_CHESTPLATE,
            SteamworkKeys.STEAM_TUNGSTEN_LEGGINGS, SteamworkKeys.STEAM_TUNGSTEN_BOOTS);

    public static void initialize() {
        MATERIAL_BASIC_METALS.register();
        BIOLOGY_PLANT_PROCESSING.register();
        MATERIAL_BASIC_CONSTRUCTION.register();

        MATERIAL_BASIC_MACHINES.register();

        PRECISION_STEAM_CALIBRATION.register();
        PRECISION_STEAM_ASSEMBLY.register();
        PRECISION_PRODUCTION_LINE.register();
        PRECISION_PNEUMATIC_LOGIC.register();
        CHEMISTRY_BASIC_RESEARCH.register();

        MATERIAL_ADVANCED_INGOTS.register();
        MATERIAL_ADVANCED_BOILERS.register();
        MATERIAL_STEAM_LOGISTICS.register();
        CHEMISTRY_DISTILLATION.register();
        PRECISION_ADVANCED_AUTOMATION_1.register();
        PRECISION_STEAM_ROBOTS.register();

        PRECISION_TURBINES_1.register();
        PRECISION_TURBINES_2.register();
        PRECISION_STEAM_EQUIPMENT.register();
        MATERIAL_STEAM_ARMOR.register();
    }

    /**
     * 桥接机器（汽动产线阀 / 传感器）的附加放置门控。
     *
     * <p>这两台是「货运 × 逻辑」桥：物品本身由框架按所属研究（蒸汽货运 {@link #MATERIAL_STEAM_LOGISTICS}）
     * 门控领取/合成；此方法额外<b>硬性</b>要求放置者已解锁 {@link #PRECISION_PNEUMATIC_LOGIC 汽动逻辑} 研究——
     * 两个研究齐备才能放置使用。这里<b>不</b>看 {@code rebar.item.*} 绕过权限：该权限对 OP 默认为真，
     * 会让管理员/测试环境直接跳过门控（这正是之前「没解锁也能用」的原因）。仅在研究系统整体关闭、
     * 或非玩家放置（插件/世界生成/区块加载）时放行。</p>
     *
     * @return {@code true} 表示应拦截本次放置（缺汽动逻辑研究），并已向玩家发送提示。
     */
    public static boolean denyLineBridgePlacement(@Nullable Player player) {
        if (!RebarConfig.ResearchConfig.ENABLED || player == null) return false;
        if (PRECISION_PNEUMATIC_LOGIC.isResearchedBy(player)) return false;
        player.sendMessage(Component.translatable("steamwork.message.line_bridge.requires_logic"));
        return true;
    }
}
