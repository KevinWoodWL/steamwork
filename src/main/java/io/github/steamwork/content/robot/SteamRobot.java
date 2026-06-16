package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.EntityStorage;
import io.github.pylonmc.rebar.entity.RebarEntity;
import io.github.pylonmc.rebar.entity.interfaces.TickingRebarEntity;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.content.line.ProductionLineInlet;
import io.github.steamwork.content.machines.SteamChargingChamber;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.github.steamwork.util.SteamworkBlockPrompt;
import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.window.Window;

import java.util.List;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.steamwork.util.SteamworkUtils.steamworkKey;

/**
 * 蒸汽机器人 —— P0 实体地基 + <b>P1a 蒸汽燃料</b>。
 *
 * <p>以铜傀儡（{@link CopperGolem}，已涂蜡、永不氧化）为底座，{@link RebarEntity} 包装、
 * {@link EntityStorage} 托管，{@link TickingRebarEntity} 周期决策；部署点（home）周围巡游，
 * 用原版寻路 {@code getPathfinder().moveTo} 行动；home 与蒸汽量持久化，重载恢复。</p>
 *
 * <p><b>P1a 燃料</b>：内置蒸汽储量（{@code steam}/{@code steamCapacity}），每 tick 行动耗汽
 * {@code steamPerTick}；耗尽即<b>缺汽停摆</b>，WAILA 显示蒸汽条与状态。</p>
 *
 * <p><b>P1b 充能</b>：首次被某台蒸汽充汽舱充能时<b>自动绑定</b>该舱（{@link #bindChamber}）；之后蒸汽
 * 低于 {@code recharge-at} 比例即进入回充模式，寻路回绑定充汽舱站定等充（途中不耗汽），充满恢复巡游。</p>
 *
 * <p><b>P1c 任务模式</b>：{@link RobotType}（巡逻/采矿/砍树），非潜行右键循环切换、持久化。</p>
 *
 * <p><b>P2 采矿/砍树</b>：在 {@code work-radius} 工作区内找最近的<b>原版</b>矿石/原木目标（绝不破坏
 * 任何 Rebar 方块），寻路过去用对应工具取正确掉落、移除方块、回收到 home 旁容器（放不下则散落），
 * 每次破坏额外耗汽 {@code steam-per-break}。</p>
 *
 * <p>后续阶段（见 {@code docs/design/steam-robot.md}）：补料/搬运、Pylon 自定义方块破坏、
 * 完整 GUI、铜傀儡放下动画等。</p>
 */
public class SteamRobot extends RebarEntity<CopperGolem> implements
        TickingRebarEntity {

    /** 机器人类型（固定职业，部署时确定，不可切换）。 */
    public enum RobotType { PATROL, MINE, CHOP, HAUL, PICK, FARM, BUTCHER }

    /** 细粒度活动状态（用于 WAILA / GUI 实时显示机器人正在做什么）。 */
    public enum Activity {
        IDLE, PATROLLING, SEARCHING, APPROACHING, CLEARING, WORKING, CHARGING, NO_STEAM,
        HAUL_UNBOUND, HAUL_PICKUP, HAUL_LOAD, HAUL_DELIVER, HAUL_UNLOAD,
        DELIVERING, DEPOSITING, RETURNING,
        PICKING, HARVESTING, PLANTING, HUNTING, ATTACKING
    }

    // 作业取掉落用的工具（无附魔，保证矿石/原木掉落正确、无 Fortune/精准采集）
    private static final ItemStack MINE_TOOL = new ItemStack(Material.NETHERITE_PICKAXE);
    private static final ItemStack CHOP_TOOL = new ItemStack(Material.NETHERITE_AXE);
    /** 单个目标方块寻路失败的放弃阈值（决策次数）：避免卡在不可达目标上。 */
    private static final int TARGET_GIVE_UP = 40;
    /** 单次锁定的连通簇（整棵树 / 整条矿脉）最大方块数。 */
    private static final int CLUSTER_CAP = 512;
    /** 锁定簇到起点的水平半径上限（格）：把一簇限制为"一棵树/一处矿脉"，避免并入旁边另一棵树。 */
    private static final int CLUSTER_HORIZONTAL_RADIUS = 6;
    /** 工作区扫不到目标后，待命多少次决策再重新扫描（避免空扫大立方体每 tick 跑）。 */
    private static final int SCAN_IDLE_COOLDOWN = 10;
    /** 接近目标连续多少次决策无明显靠近就判定为够不到、放弃（避免对不可达目标死寻路）。 */
    private static final int NO_PROGRESS_LIMIT = 6;

    // home（部署点）持久化键，存为三个 double
    private static final NamespacedKey HOME_X = steamworkKey("robot_home_x");
    private static final NamespacedKey HOME_Y = steamworkKey("robot_home_y");
    private static final NamespacedKey HOME_Z = steamworkKey("robot_home_z");
    // 蒸汽储量持久化键
    private static final NamespacedKey STEAM_KEY = steamworkKey("robot_steam");
    // 绑定充汽舱核心位置持久化键（与机器人同世界，存为三个 double）
    private static final NamespacedKey BOUND_X = steamworkKey("robot_bound_x");
    private static final NamespacedKey BOUND_Y = steamworkKey("robot_bound_y");
    private static final NamespacedKey BOUND_Z = steamworkKey("robot_bound_z");
    // 任务模式持久化键（存枚举名）
    private static final NamespacedKey JOB_KEY = steamworkKey("robot_job");
    // 归属终端 ID 持久化键
    private static final NamespacedKey TERMINAL_KEY = steamworkKey("robot_terminal");
    // 搬运取货/送货点 + 携带物品持久化键
    private static final NamespacedKey HAUL_SRC_X = steamworkKey("robot_haul_src_x");
    private static final NamespacedKey HAUL_SRC_Y = steamworkKey("robot_haul_src_y");
    private static final NamespacedKey HAUL_SRC_Z = steamworkKey("robot_haul_src_z");
    private static final NamespacedKey HAUL_TGT_X = steamworkKey("robot_haul_tgt_x");
    private static final NamespacedKey HAUL_TGT_Y = steamworkKey("robot_haul_tgt_y");
    private static final NamespacedKey HAUL_TGT_Z = steamworkKey("robot_haul_tgt_z");
    private static final NamespacedKey HAUL_CARRY = steamworkKey("robot_haul_carry");
    private static final NamespacedKey SAPLING_KEY = steamworkKey("robot_saplings");

    // ===== 设置（settings/steam_robot.yml，缺省时用默认值，避免缺配置崩溃）=====
    private final int    tickInterval  = getSetting("tick-interval",  ConfigAdapter.INTEGER, 10);
    private final double wanderRadius  = getSetting("wander-radius",  ConfigAdapter.DOUBLE,  6.0);
    private final double moveSpeed     = getSetting("move-speed",     ConfigAdapter.DOUBLE,  1.0);
    private final double steamCapacity = getSetting("steam-capacity", ConfigAdapter.DOUBLE,  2000.0);
    private final double steamPerTick  = getSetting("steam-per-tick", ConfigAdapter.DOUBLE,  10.0);
    /** 蒸汽低于容量该比例时回绑定充汽舱充能。 */
    private final double rechargeAt    = getSetting("recharge-at",    ConfigAdapter.DOUBLE,  0.25);
    /** 每破坏一个方块额外耗汽（mB）。 */
    private final double steamPerBreak = getSetting("steam-per-break", ConfigAdapter.DOUBLE, 30.0);
    /** 破坏一个方块需累积的决策次数（每次决策约 tick-interval 游戏刻），≥1，避免秒砍。 */
    private final int    breakTicks    = Math.max(1, getSetting("break-ticks", ConfigAdapter.INTEGER, 4));
    /** 搬运一次最多携带多少个物品堆（满载即去送货）。 */
    private final int    inventoryCapacity = Math.max(1, getSetting("inventory-capacity", ConfigAdapter.INTEGER, 7));
    private final int    haulCapacity  = Math.max(1, getSetting("haul-capacity", ConfigAdapter.INTEGER, 9));
    private final boolean replantEnabled = getSetting("replant-saplings", ConfigAdapter.BOOLEAN, true);
    private final int    saplingSlotCapacity = Math.max(1, getSetting("sapling-slot-capacity", ConfigAdapter.INTEGER, 1));

    // ===== 状态 =====
    private @NotNull Location home;
    /** 归属终端 UUID；null = 未绑定终端（待命不干活）。 */
    private @Nullable java.util.UUID terminalId = null;
    private @Nullable Location target = null;
    /** 距上次重新选点经过的决策次数，超过阈值即强制重选，避免目标不可达时永久卡住。 */
    private int ticksSinceRetarget = 0;
    /** 当前蒸汽储量（mB）。 */
    private double steam;
    /** 绑定的充汽舱核心位置（首次被某舱充能时自动绑定）；null = 未绑定。 */
    private @Nullable Location boundChamber = null;
    /** 是否正在回充汽舱充能途中（避免在路上耗汽、防抖）。 */
    private boolean seekingCharge = false;
    private boolean returningToWork = false;       // 充完汽后需要走回工作点
    /** 采矿/砍树：携带库存满载，需前往出货点卸货。 */
    private boolean needsDelivery = false;
    private boolean firstTickAfterLoad = true;
    /** 当前任务模式。 */
    private @NotNull RobotType robotType = RobotType.PATROL;
    /** 当前细粒度活动状态（每 tick 更新，供 WAILA / GUI 显示）。 */
    private @NotNull Activity activity = Activity.IDLE;
    /** 接近阶段选定的目标方块（走过去再锁定整簇）；避免每 tick 重复全扫。 */
    private @Nullable Block approachTarget = null;
    /** 接近目标时上次到目标的距离平方（用于"无进度"检测）。 */
    private double lastApproachDistSq = Double.MAX_VALUE;
    /** 连续多少次决策没有明显靠近目标（无进度计数）。 */
    private int noProgress = 0;
    /** 当前正在破坏的方块（来自锁定簇），瞬态；重载后重新扫描。 */
    private @Nullable Block targetBlock = null;
    /** 接近阶段寻路尝试计数（超过 {@link #TARGET_GIVE_UP} 放弃，避免卡死）。 */
    private int approachAge = 0;
    /** 当前方块已累积的破坏进度（决策次数），达到 {@link #breakTicks} 才真正破坏（非秒砍）。 */
    private int breakingProgress = 0;
    /** 锁定的连通簇（整棵树 / 整条矿脉）中尚未破坏的方块；瞬态。 */
    private final java.util.List<Block> lockedCluster = new java.util.ArrayList<>();
    /** 采伐时"上一块"的位置：下一块取离它最近的，以便沿一棵树连续砍完再跳到另一棵。 */
    private @Nullable Location lastFocus = null;
    /** 空扫冷却剩余决策次数（>0 时跳过扫描、原地待命）。 */
    private int scanCooldown = 0;
    /** 不可达 / 被保护插件拦截的方块 → 加入时的 tick（过期后重新可用）。 */
    private final java.util.Map<Block, Long> blockedTargets = new java.util.HashMap<>();
    private static final long BLACKLIST_EXPIRY_TICKS = 600; // 30 秒（20 tps × 30）
    /** 预扫描：砍当前树时提前锁定下一棵的树根，砍完无缝衔接不用重扫。 */
    private @Nullable Block nextApproachTarget = null;
    /** 当前簇是否已做过预扫描（每簇最多扫一次，避免末尾几块时每 tick 空扫大立方体）。 */
    private boolean preScanned = false;
    /** 砍树补种：当前簇的树根方块（整棵砍完后在此补种树苗）。 */
    private @Nullable Block clusterRoot = null;
    /** 砍树补种：树根处的原木材质（用于确定对应树苗）。 */
    private @Nullable Material clusterLogType = null;

    // ===== 搬运（HAUL）状态 =====
    /** 取货点（容器）位置；null = 未绑定。 */
    private @Nullable Location haulSource = null;
    /** 送货点（容器 / 产线入口）位置；null = 未绑定。 */
    private @Nullable Location haulTarget = null;
    /** 携带中的物品堆（取货装入、送货卸出）；持久化。 */
    private final java.util.List<ItemStack> carrying = new java.util.ArrayList<>();
    /** 树苗槽（仅 CHOP 使用，独立于 carrying，不会被送去出货点）；持久化。 */
    private final java.util.List<ItemStack> saplingSlot = new java.util.ArrayList<>();
    /** 搬运寻路尝试计数（周期性重发寻路）。 */
    private int haulNavAge = 0;
    /** 存/取货动画节拍计数（到达后先等再放，每 N tick 放一次，避免音效重叠）。 */
    private int depositTick = 0;
    private static final int DEPOSIT_DELAY = 2;   // 到达后等 2 个决策 tick 再开始放
    private static final int DEPOSIT_INTERVAL = 3; // 每 3 个决策 tick 放一次（约 1.5 秒）
    private int postDeliveryCooldown = 0;          // 交货结束后的短暂冷却（关箱 + 过渡）
    /** 重载后随机错开延迟（决策次数），分散多机器人同 tick 计算负载。 */
    private int staggerDelay = 0;

    /** 新生成：写入 rebar 实体 key 并记录部署点、固定职业、归属终端。 */
    public SteamRobot(@NotNull CopperGolem golem, @NotNull Location home,
                      @NotNull RobotType type, @Nullable java.util.UUID terminalId) {
        super(SteamworkKeys.STEAM_ROBOT, golem);
        this.home = home.clone();
        this.robotType = type;
        this.terminalId = terminalId;
        this.steam = steamCapacity;
        configure();
    }

    /** 重载恢复：单参构造器（{@link io.github.pylonmc.rebar.entity.RebarEntitySchema} 反射查找此签名）。 */
    @SuppressWarnings("unused")
    public SteamRobot(@NotNull CopperGolem golem) {
        super(golem);
        PersistentDataContainer pdc = golem.getPersistentDataContainer();
        Double hx = pdc.get(HOME_X, PersistentDataType.DOUBLE);
        Double hy = pdc.get(HOME_Y, PersistentDataType.DOUBLE);
        Double hz = pdc.get(HOME_Z, PersistentDataType.DOUBLE);
        this.home = (hx != null && hy != null && hz != null)
                ? new Location(golem.getWorld(), hx, hy, hz)
                : golem.getLocation();
        Double s = pdc.get(STEAM_KEY, PersistentDataType.DOUBLE);
        this.steam = (s != null) ? Math.min(s, steamCapacity) : steamCapacity;
        Double bx = pdc.get(BOUND_X, PersistentDataType.DOUBLE);
        Double by = pdc.get(BOUND_Y, PersistentDataType.DOUBLE);
        Double bz = pdc.get(BOUND_Z, PersistentDataType.DOUBLE);
        this.boundChamber = (bx != null && by != null && bz != null)
                ? new Location(golem.getWorld(), bx, by, bz)
                : null;
        String job = pdc.get(JOB_KEY, PersistentDataType.STRING);
        if (job != null) {
            try { this.robotType = RobotType.valueOf(job); } catch (IllegalArgumentException ignored) {}
        }
        String tid = pdc.get(TERMINAL_KEY, PersistentDataType.STRING);
        if (tid != null) {
            try { this.terminalId = java.util.UUID.fromString(tid); } catch (IllegalArgumentException ignored) {}
        }
        this.haulSource = readLoc(pdc, golem.getWorld(), HAUL_SRC_X, HAUL_SRC_Y, HAUL_SRC_Z);
        this.haulTarget = readLoc(pdc, golem.getWorld(), HAUL_TGT_X, HAUL_TGT_Y, HAUL_TGT_Z);
        byte[] carry = pdc.get(HAUL_CARRY, PersistentDataType.BYTE_ARRAY);
        if (carry != null) {
            for (ItemStack ci : ItemStack.deserializeItemsFromBytes(carry)) {
                if (ci != null && !ci.isEmpty()) carrying.add(ci);
            }
        }
        if ((robotType == RobotType.MINE || robotType == RobotType.CHOP
                || robotType == RobotType.PICK || robotType == RobotType.FARM
                || robotType == RobotType.BUTCHER || robotType == RobotType.PATROL)
                && !carrying.isEmpty()) {
            needsDelivery = true;
        }
        byte[] sap = pdc.get(SAPLING_KEY, PersistentDataType.BYTE_ARRAY);
        if (sap != null) {
            for (ItemStack si : ItemStack.deserializeItemsFromBytes(sap)) {
                if (si != null && !si.isEmpty()) saplingSlot.add(si);
            }
        }
        configure();
        this.staggerDelay = ThreadLocalRandom.current().nextInt(tickInterval);
    }

    private static @Nullable Location readLoc(@NotNull PersistentDataContainer pdc, @NotNull World world,
                                              @NotNull NamespacedKey kx, @NotNull NamespacedKey ky, @NotNull NamespacedKey kz) {
        Double x = pdc.get(kx, PersistentDataType.DOUBLE);
        Double y = pdc.get(ky, PersistentDataType.DOUBLE);
        Double z = pdc.get(kz, PersistentDataType.DOUBLE);
        return (x != null && y != null && z != null) ? new Location(world, x, y, z) : null;
    }

    /** 在指定位置生成一台机器人并登记到 {@link EntityStorage}。 */
    public static @NotNull SteamRobot spawn(@NotNull Location loc, @NotNull RobotType type,
                                            @Nullable java.util.UUID terminalId) {
        World world = loc.getWorld();
        CopperGolem golem = world.spawn(loc, CopperGolem.class, g -> {
            g.setPersistent(true);
            g.setRemoveWhenFarAway(false);
        });
        SteamRobot robot = new SteamRobot(golem, loc, type, terminalId);
        EntityStorage.add(robot);
        return robot;
    }

    private void configure() {
        setTickInterval(tickInterval);
        CopperGolem golem = getEntity();
        golem.setPersistent(true);
        golem.setRemoveWhenFarAway(false);
        // 关键：清空铜傀儡的自主 AI，否则会与我们的寻路抢控制（乱逛/捡物）。
        // 铜傀儡是新版 brain 制 AI：setAware(false) 会连导航一起冻住（不能用），
        // removeAllGoals 又只清旧 goal 制（对它无效）。
        // 正解：保持 aware=true 让导航可用，用反射清空 brain 的行为与记忆，使其无自主动作；
        // 再清一遍 goal 制双保险。导航此后完全由本类的 moveTo 驱动。
        Bukkit.getMobGoals().removeAllGoals(golem);
        neutralizeBrain(golem);
        // 已涂蜡铜傀儡：固定为未氧化的亮铜外观，且不再随时间氧化
        golem.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        golem.setOxidizing(CopperGolem.Oxidizing.waxed());
        golem.customName(buildNameTag());
        golem.setCustomNameVisible(true);
        updateHeldTool();
    }

    // neutralizeBrain 的反射方法缓存（运行时类稳定，首次解析后复用，避免每次 configure 重复查找）
    private static java.lang.reflect.Method mGetHandle, mGetBrain, mRemoveBehaviors, mClearMemories;

    /**
     * 反射清空铜傀儡的 brain（行为 + 记忆），使其无自主动作但导航仍可用。
     * 运行时为 mojang 映射（Paper/Purpur）：CraftMob.getHandle() → Mob.getBrain()
     * → Brain.removeAllBehaviors()/clearMemories()。失败仅告警、不致命。
     */
    private void neutralizeBrain(@NotNull CopperGolem golem) {
        try {
            if (mGetHandle == null) mGetHandle = golem.getClass().getMethod("getHandle");
            Object nmsMob = mGetHandle.invoke(golem);
            if (mGetBrain == null) mGetBrain = nmsMob.getClass().getMethod("getBrain");
            Object brain = mGetBrain.invoke(nmsMob);
            if (mRemoveBehaviors == null) mRemoveBehaviors = brain.getClass().getMethod("removeAllBehaviors");
            if (mClearMemories == null) mClearMemories = brain.getClass().getMethod("clearMemories");
            mRemoveBehaviors.invoke(brain);
            mClearMemories.invoke(brain);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Steamwork] 无法清空蒸汽机器人 brain（映射变动?）：" + t);
        }
    }

    /** 仅在姿态变化时设置铜傀儡动作状态，避免每 tick 重复发包。 */
    private void setState(@NotNull CopperGolem golem, @NotNull CopperGolem.State state) {
        if (golem.getGolemState() != state) golem.setGolemState(state);
    }

    /** 按当前任务模式让铜傀儡手持对应工具（采矿→镐 / 砍树→斧 / 巡逻→空手），死亡不掉落。 */
    private void updateHeldTool() {
        EntityEquipment eq = getEntity().getEquipment();
        if (eq == null) return;
        ItemStack held = switch (robotType) {
            case MINE -> new ItemStack(Material.IRON_PICKAXE);
            case CHOP -> new ItemStack(Material.IRON_AXE);
            case FARM -> new ItemStack(Material.IRON_HOE);
            case BUTCHER -> new ItemStack(Material.IRON_SWORD);
            // 搬运/拾取：手持正在携带的物品（空载则空手），直观显示在搬什么
            case HAUL, PICK -> carrying.isEmpty() ? null : carrying.get(0).clone();
            case PATROL -> new ItemStack(Material.IRON_SWORD);
        };
        // 先置空再设值：强制 Paper 检测到 state change 并广播装备更新包给客户端。
        // 若直接 set 相同物品，Paper 优化为 no-op 不发包，客户端预测状态得不到修正。
        eq.setItemInMainHand(null);
        eq.setItemInMainHand(held);
        eq.setItemInMainHandDropChance(0f);
    }

    /** 每 tick 强制刷新手持物品，确保无论何种路径移除都能立刻恢复。 */
    private void ensureHeldTool() {
        updateHeldTool();
    }

    /**
     * 强制重设装备（无论是否变化）以触发服务端向客户端发送装备修正包。
     * 用于事件取消后修正客户端预测状态。
     */
    void refreshEquipment() {
        updateHeldTool();
    }

    @Override
    public void tick() {
        CopperGolem golem = getEntity();
        if (!golem.isValid()) return;

        if (staggerDelay > 0) { staggerDelay--; return; }

        if (firstTickAfterLoad) {
            firstTickAfterLoad = false;
            setTickInterval(tickInterval);
        }

        ensureHeldTool();
        golem.customName(buildNameTag());

        // ── 未绑定终端 → 待命不干活 ──
        RobotControlTerminal term = terminal();
        if (term == null) {
            activity = Activity.IDLE;
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            setState(golem, CopperGolem.State.IDLE);
            return;
        }

        // 同步充汽点：跟随终端配置
        Location chargePoint = term.getChargePoint();

        // ── 交货后冷却（关箱过渡，避免立刻跑去充汽）──
        if (postDeliveryCooldown > 0) {
            postDeliveryCooldown--;
        }

        // ── 回充模式：低汽且终端有充汽点 → 走过去站定等充（途中不耗汽）──
        boolean patrolNeedsHeal = robotType == RobotType.PATROL
                && golem.getHealth() < golem.getMaxHealth();
        if (seekingCharge) {
            boolean healDone = !patrolNeedsHeal;
            if ((steam >= steamCapacity && healDone) || chargePoint == null || !chamberLoaded(chargePoint)) {
                seekingCharge = false;
                // 有未完成的砍伐/采矿簇 → 需走回工作点
                if (targetBlock != null || !lockedCluster.isEmpty()) {
                    returningToWork = true;
                }
            } else {
                activity = Activity.CHARGING;
                boundChamber = chargePoint;
                navigateToChamber(golem);
                return;
            }
        } else if (postDeliveryCooldown <= 0
                && (steam < steamCapacity * rechargeAt || patrolNeedsHeal)
                && chargePoint != null && chamberLoaded(chargePoint)) {
            if (!carrying.isEmpty() && (robotType == RobotType.MINE || robotType == RobotType.CHOP
                    || robotType == RobotType.PICK || robotType == RobotType.FARM
                    || robotType == RobotType.BUTCHER || robotType == RobotType.PATROL)) {
                needsDelivery = true;
            } else {
                seekingCharge = true;
                boundChamber = chargePoint;
                pauseWork();
                activity = Activity.CHARGING;
                navigateToChamber(golem);
                return;
            }
        }

        // ── 缺汽停摆（无可用充汽舱）──
        if (steam <= 0.0) {
            activity = Activity.NO_STEAM;
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            target = null;
            golem.setGolemState(CopperGolem.State.IDLE);
            return;
        }

        // ── 作业（按任务模式）──
        boolean active;
        switch (robotType) {
            case MINE, CHOP -> active = mineStep(golem, robotType);
            case HAUL       -> active = haulStep(golem);
            case PICK       -> active = pickStep(golem);
            case FARM       -> active = farmStep(golem);
            case BUTCHER    -> active = butcherStep(golem);
            case PATROL     -> active = patrolStep(golem);
            default         -> active = false;
        }

        // 行动耗汽（仅在实际行动/作业时扣；待命不耗汽）。破坏的额外耗汽在 breakTarget 内单独扣
        if (active) {
            steam = Math.max(0.0, steam - steamPerTick);
            golem.getWorld().spawnParticle(Particle.CLOUD, golem.getLocation().add(0, 1.0, 0),
                    6, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private static final ItemStack PATROL_TOOL = new ItemStack(Material.NETHERITE_SWORD);
    private static final int PATROL_Y_TOLERANCE = 5;

    private @Nullable org.bukkit.entity.Monster patrolChaseTarget = null;
    private int patrolChaseAge = 0;
    private int patrolNoProgress = 0;
    private double patrolLastDistSq = Double.MAX_VALUE;

    /**
     * 巡逻（骑士模式）：在工作区内游走，发现敌怪就锁定追踪并攻击；无敌怪时继续巡逻。
     * <ul>
     *   <li>Y 差超过 {@link #PATROL_Y_TOLERANCE} 的怪（地底矿洞）不追</li>
     *   <li>锁定目标后追踪，追不到（无进度超限 / 总时间超限）自动放弃</li>
     *   <li>掉落物自动收集，满载去出货点卸货</li>
     * </ul>
     */
    private boolean patrolStep(@NotNull CopperGolem golem) {
        if (needsDelivery) return deliverStep(golem);

        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) {
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }

        World world = golem.getWorld();
        Location loc = golem.getLocation();
        int minX = term.regionMinX(), maxX = term.regionMaxX();
        int minZ = term.regionMinZ(), maxZ = term.regionMaxZ();

        // ---- 维持锁定目标：失效/超出 Y 容差则释放 ----
        if (patrolChaseTarget != null
                && (!patrolChaseTarget.isValid() || patrolChaseTarget.isDead()
                    || Math.abs(patrolChaseTarget.getLocation().getY() - loc.getY()) > PATROL_Y_TOLERANCE)) {
            resetPatrolChase();
        }

        // ---- 无锁定目标：扫描区域内最近的敌怪（过滤 Y 差过大的） ----
        if (patrolChaseTarget == null) {
            var scanBox = new org.bukkit.util.BoundingBox(
                    minX, world.getMinHeight(), minZ, maxX + 1, world.getMaxHeight(), maxZ + 1);
            double bestSq = Double.MAX_VALUE;
            for (org.bukkit.entity.Entity ent : world.getNearbyEntities(scanBox)) {
                if (!(ent instanceof org.bukkit.entity.Monster mob)) continue;
                if (!mob.isValid() || mob.isDead()) continue;
                if (Math.abs(mob.getLocation().getY() - loc.getY()) > PATROL_Y_TOLERANCE) continue;
                double sq = loc.distanceSquared(mob.getLocation());
                if (sq < bestSq) { bestSq = sq; patrolChaseTarget = mob; }
            }
        }

        // ---- 有目标：追踪 → 攻击 ----
        if (patrolChaseTarget != null) {
            Location targetLoc = patrolChaseTarget.getLocation();
            double distSq = loc.distanceSquared(targetLoc);
            golem.lookAt(targetLoc);

            if (distSq > 4.0) {
                if (distSq < patrolLastDistSq - 0.25) {
                    patrolLastDistSq = distSq;
                    patrolNoProgress = 0;
                } else {
                    patrolNoProgress++;
                }
                patrolChaseAge++;
                if (patrolNoProgress > NO_PROGRESS_LIMIT || patrolChaseAge > TARGET_GIVE_UP) {
                    resetPatrolChase();
                    if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
                    return true;
                }
                activity = Activity.HUNTING;
                setState(golem, CopperGolem.State.IDLE);
                if (!golem.getPathfinder().hasPath() || patrolChaseAge % 8 == 1) {
                    golem.getPathfinder().moveTo(targetLoc, moveSpeed);
                }
                return true;
            }

            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            activity = Activity.ATTACKING;
            setState(golem, CopperGolem.State.GETTING_ITEM);
            golem.swingMainHand();
            patrolChaseTarget.damage(patrolChaseTarget.getHealth() + 1.0, golem);
            world.playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.7f, 0.9f);
            world.spawnParticle(Particle.SWEEP_ATTACK, targetLoc.clone().add(0, 0.5, 0), 1);

            org.bukkit.entity.Monster victim = patrolChaseTarget;
            resetPatrolChase();
            Bukkit.getScheduler().runTaskLater(io.github.steamwork.Steamwork.getInstance(), () -> {
                Location dl = victim.getLocation();
                for (org.bukkit.entity.Entity ent : world.getNearbyEntities(dl, 2, 2, 2)) {
                    if (ent instanceof org.bukkit.entity.Item item && item.isValid()) {
                        collectDrops(List.of(item.getItemStack()), item.getLocation());
                        item.remove();
                    }
                }
                updateHeldTool();
            }, 2L);

            steam = Math.max(0.0, steam - steamPerBreak);
            if (carrying.size() >= inventoryCapacity) needsDelivery = true;
            return true;
        }

        // ---- 无敌怪：巡逻游走 ----
        activity = Activity.PATROLLING;
        setState(golem, CopperGolem.State.IDLE);
        ticksSinceRetarget++;

        boolean reached = target != null
                && loc.getWorld() == target.getWorld()
                && loc.distanceSquared(target) < 2.25;

        if (target == null || reached || ticksSinceRetarget > 6) {
            target = pickWanderTarget(golem);
            golem.getPathfinder().moveTo(target, moveSpeed);
            ticksSinceRetarget = 0;
        }
        return true;
    }

    private void resetPatrolChase() {
        patrolChaseTarget = null;
        patrolChaseAge = 0;
        patrolNoProgress = 0;
        patrolLastDistSq = Double.MAX_VALUE;
    }

    /**
     * 搬运（HAUL）：空手→去取货点把容器里的物品装入携带缓存；满载/有货→去送货点投放
     * （容器或产线入口原料缓存）。取货/送货点未绑定、取货点空、送货点满 → 原地待命（不耗汽）。
     */
    private boolean haulStep(@NotNull CopperGolem golem) {
        if (haulSource == null || haulTarget == null) {
            closeOpenedLid();
            activity = Activity.HAUL_UNBOUND;
            setState(golem, CopperGolem.State.IDLE);        // 未绑定 → 待命，GUI 提示去绑定
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            return false;
        }

        boolean loaded = !carrying.isEmpty();
        Location dest = loaded ? haulTarget : haulSource;
        Block destBlock = dest.getBlock();
        if (!chunkLoaded(destBlock)) { closeOpenedLid(); setState(golem, CopperGolem.State.IDLE); return false; }

        Location loc = golem.getLocation();
        Location destCenter = dest.toCenterLocation();
        golem.lookAt(destCenter);
        double distSq = loc.distanceSquared(destCenter);

        // 还没到（>2.5 格）→ 走过去（途中关掉之前开着的箱盖）
        if (distSq > 6.25) {
            closeOpenedLid();
            depositTick = 0;
            activity = loaded ? Activity.HAUL_DELIVER : Activity.HAUL_PICKUP;
            setState(golem, CopperGolem.State.IDLE);
            clearLeavesAhead(golem, destCenter);
            haulNavAge++;
            if (haulNavAge % 8 == 1 || !golem.getPathfinder().hasPath()) {
                golem.getPathfinder().moveTo(destCenter, moveSpeed);
            }
            return true;
        }

        // 到了：站定、开箱盖、取/送（用铜傀儡原版取/放/检查动作）
        if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
        haulNavAge = 0;
        openLid(destBlock);

        // 节拍控制：到达后先等几 tick 再开始存/取，每 N tick 操作一次
        depositTick++;
        if (depositTick < DEPOSIT_DELAY) {
            setState(golem, CopperGolem.State.IDLE);
            return true;
        }
        if ((depositTick - DEPOSIT_DELAY) % DEPOSIT_INTERVAL != 0) {
            return true;
        }

        if (loaded) {
            int delivered = depositToTarget();
            if (delivered > 0) {
                activity = Activity.HAUL_UNLOAD;
                setState(golem, CopperGolem.State.DROPPING_ITEM);
                golem.swingMainHand();
                golem.getWorld().playSound(destCenter, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.5f, 1.2f);
                updateHeldTool();
                steam = Math.max(0.0, steam - steamPerBreak);
                return true;
            }
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.DROPPING_NO_ITEM);
            depositTick = 0;
            return false;
        } else {
            int picked = pullFromSource();
            if (picked > 0) {
                activity = Activity.HAUL_LOAD;
                setState(golem, CopperGolem.State.GETTING_ITEM);
                golem.swingMainHand();
                golem.getWorld().playSound(destCenter, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 1.0f);
                updateHeldTool();
                return true;
            }
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.GETTING_NO_ITEM);
            depositTick = 0;
            return false;
        }
    }

    /** 当前打开了箱盖的容器（搬运时开/关，离开即关）；瞬态。 */
    private @Nullable Block openedLid = null;

    /** 打开容器箱盖（仅箱子/木桶等 {@link org.bukkit.block.Lidded}）；同一容器只触发一次开盖。 */
    private void openLid(@NotNull Block block) {
        if (openedLid != null && openedLid.equals(block)) return;
        closeOpenedLid();
        if (block.getState() instanceof org.bukkit.block.Lidded lid) {
            lid.open();
            openedLid = block;
        }
    }

    /** 关掉当前开着的箱盖（若有）。 */
    private void closeOpenedLid() {
        if (openedLid == null) return;
        if (chunkLoaded(openedLid) && openedLid.getState() instanceof org.bukkit.block.Lidded lid) {
            lid.close();
        }
        openedLid = null;
    }

    /** 从取货点容器把物品装入携带缓存（至多 haulCapacity 堆），返回装入的物品总数。 */
    private int pullFromSource() {
        Inventory inv = containerInventoryAt(haulSource);
        if (inv == null) return 0;
        int picked = 0;
        for (int i = 0; i < inv.getSize() && carrying.size() < haulCapacity; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.isEmpty()) continue;
            carrying.add(it.clone());
            inv.setItem(i, null);
            picked += it.getAmount();
        }
        return picked;
    }

    /** 把携带缓存投放到送货点（产线入口原料缓存 / 容器），返回投放成功的物品总数。 */
    private int depositToTarget() {
        return depositToBlock(haulTarget.getBlock());
    }

    /** 把携带缓存投放到指定方块（容器 / 产线入口），返回投放成功的物品总数。 */
    private int depositToBlock(@NotNull Block target) {
        if (!chunkLoaded(target)) return 0;
        int delivered = 0;
        java.util.Iterator<ItemStack> it = carrying.iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            int before = stack.getAmount();
            int leftover = depositOne(target, stack);
            if (leftover <= 0) {
                it.remove();
                delivered += before;
            } else {
                delivered += (before - leftover);
                stack.setAmount(leftover);
                break;
            }
        }
        return delivered;
    }

    /** 投放一堆物品到送货点，返回放不下的余量。 */
    private int depositOne(@NotNull Block target, @NotNull ItemStack stack) {
        // 产线入口 → 原料缓存
        if (PneumaticEndpointSupport.loadedRebarBlock(target) instanceof ProductionLineInlet inlet) {
            return inlet.acceptHauledIngredient(stack.clone());
        }
        // 普通容器
        if (target.getState() instanceof Container c) {
            int left = 0;
            for (ItemStack o : c.getInventory().addItem(stack.clone()).values()) left += o.getAmount();
            return left;
        }
        return stack.getAmount();                          // 不可送 → 全剩
    }

    /** 取该位置方块的容器库存（非容器 / 未加载 → null）。 */
    private @Nullable Inventory containerInventoryAt(@Nullable Location locn) {
        if (locn == null) return null;
        Block b = locn.getBlock();
        if (!chunkLoaded(b)) return null;
        return (b.getState() instanceof Container c) ? c.getInventory() : null;
    }

    // ===== 拾取机器人（PICK）=====

    /** 不可达掉落物黑名单（实体 UUID → 拉黑时刻），避免反复追踪够不到的物品。 */
    private final java.util.Map<java.util.UUID, Long> blockedItems = new java.util.HashMap<>();
    private static final long ITEM_BLACKLIST_EXPIRY = 400; // 20 秒后重试
    /** 当前追踪的掉落物实体 UUID（用于无进度检测）。 */
    private @Nullable java.util.UUID pickTargetId = null;
    /** 拾取接近阶段的无进度计数。 */
    private int pickNoProgress = 0;
    private double pickLastDistSq = Double.MAX_VALUE;
    private int pickApproachAge = 0;

    /**
     * 拾取：扫描工作区地面掉落物 → 走过去 → 捡起 → 满载送出货点。
     * 够不到的物品（天上、围墙内等）自动拉黑跳过。
     * @return 是否处于行动状态（决定本 tick 是否耗汽）
     */
    private boolean pickStep(@NotNull CopperGolem golem) {
        if (needsDelivery) return deliverStep(golem);

        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) {
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }

        World world = golem.getWorld();
        Location loc = golem.getLocation();
        long now = world.getFullTime();

        // 清理过期黑名单
        blockedItems.entrySet().removeIf(e -> now - e.getValue() > ITEM_BLACKLIST_EXPIRY);

        // 扫描区域内掉落物实体（跳过黑名单）
        org.bukkit.entity.Item nearest = null;
        double bestSq = Double.MAX_VALUE;
        int minX = term.regionMinX(), maxX = term.regionMaxX();
        int minZ = term.regionMinZ(), maxZ = term.regionMaxZ();
        var pickScanBox = new org.bukkit.util.BoundingBox(
                minX, world.getMinHeight(), minZ, maxX + 1, world.getMaxHeight(), maxZ + 1);
        for (org.bukkit.entity.Entity ent : world.getNearbyEntities(pickScanBox)) {
            if (!(ent instanceof org.bukkit.entity.Item item)) continue;
            if (!item.isValid() || item.isDead()) continue;
            if (blockedItems.containsKey(item.getUniqueId())) continue;
            double sq = loc.distanceSquared(item.getLocation());
            if (sq < bestSq) { bestSq = sq; nearest = item; }
        }

        if (nearest == null) {
            pickTargetId = null;
            if (!carrying.isEmpty()) { needsDelivery = true; return true; }
            if (scanCooldown > 0) { scanCooldown--; }
            else { scanCooldown = SCAN_IDLE_COOLDOWN; }
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            returnToTerminal(golem);
            return false;
        }

        // 目标切换时重置进度追踪
        java.util.UUID nid = nearest.getUniqueId();
        if (!nid.equals(pickTargetId)) {
            pickTargetId = nid;
            pickNoProgress = 0;
            pickLastDistSq = Double.MAX_VALUE;
            pickApproachAge = 0;
        }

        // 走向目标掉落物
        Location itemLoc = nearest.getLocation();
        double distSq = loc.distanceSquared(itemLoc);
        if (distSq > 4.0) {
            // 无进度检测
            if (distSq < pickLastDistSq - 0.25) {
                pickLastDistSq = distSq;
                pickNoProgress = 0;
            } else {
                pickNoProgress++;
            }
            pickApproachAge++;
            if (pickNoProgress > NO_PROGRESS_LIMIT || pickApproachAge > TARGET_GIVE_UP) {
                blockedItems.put(nid, now);
                pickTargetId = null;
                pickNoProgress = 0;
                pickApproachAge = 0;
                pickLastDistSq = Double.MAX_VALUE;
                if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
                return true;
            }
            activity = Activity.APPROACHING;
            setState(golem, CopperGolem.State.IDLE);
            golem.lookAt(itemLoc);
            if (!golem.getPathfinder().hasPath() || pickApproachAge % 8 == 1) {
                golem.getPathfinder().moveTo(itemLoc, moveSpeed);
            }
            return true;
        }

        // 到了：捡起
        pickTargetId = null;
        pickNoProgress = 0;
        pickApproachAge = 0;
        pickLastDistSq = Double.MAX_VALUE;
        activity = Activity.PICKING;
        setState(golem, CopperGolem.State.GETTING_ITEM);
        golem.swingMainHand();
        ItemStack drop = nearest.getItemStack();
        nearest.remove();
        collectDrops(List.of(drop), loc);
        updateHeldTool();
        world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 1.0f);
        steam = Math.max(0.0, steam - steamPerBreak);
        if (carrying.size() >= inventoryCapacity) needsDelivery = true;
        return true;
    }

    // ===== 农耕机器人（FARM）=====

    private static final ItemStack FARM_TOOL = new ItemStack(Material.NETHERITE_HOE);
    private static final int FARM_WORK_BATCH = 8;

    /** 一阶段内有序的作业目标队列：扫描时一次性填入，逐个执行直至清空才重扫。 */
    private final java.util.ArrayDeque<FarmTask> farmQueue = new java.util.ArrayDeque<>();
    private int farmApproachAge = 0;
    private int farmNoProgress = 0;
    private double farmLastDistSq = Double.MAX_VALUE;

    private record FarmTask(@NotNull Block block, boolean harvest) {}

    /**
     * 农耕（批次路线模式）：
     * <ol>
     *   <li>队列为空 → 扫描整个区域，把所有可操作耕地一次性收入队列，按离机器人近→远排序</li>
     *   <li>逐个从队头取目标：接近→执行（收割/播种）→弹出→取下一个</li>
     *   <li>期间新成熟的作物不影响当前阶段；满载去出货点卸货后继续当前队列</li>
     *   <li>队列清空后重新扫描</li>
     * </ol>
     */
    private boolean farmStep(@NotNull CopperGolem golem) {
        if (needsDelivery) return deliverStep(golem);

        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) {
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }

        // ---- 推进队头：跳过已失效的目标 ----
        while (!farmQueue.isEmpty()) {
            FarmTask head = farmQueue.peek();
            Block b = head.block();
            if (!golem.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) { farmQueue.poll(); continue; }
            if (head.harvest() && !isMatureCrop(b))  { farmQueue.poll(); continue; }
            if (!head.harvest() && b.getType() != Material.AIR) { farmQueue.poll(); continue; }
            break;
        }

        // ---- 队列空 → 批次扫描 ----
        if (farmQueue.isEmpty()) {
            if (scanCooldown > 0) { scanCooldown--; }
            else {
                farmScan(golem);
                scanCooldown = farmQueue.isEmpty() ? SCAN_IDLE_COOLDOWN : 0;
            }
        }

        if (farmQueue.isEmpty()) {
            if (!carrying.isEmpty()) { needsDelivery = true; return true; }
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            returnToTerminal(golem);
            return false;
        }

        FarmTask current = farmQueue.peek();
        return farmApproachAndWork(golem, current.block(), current.harvest());
    }

    /**
     * 扫描区域内全部可操作耕地，按 Y 层分组 + 层内最近邻链排序后填入队列。
     * 同一 Y 层的目标全部完成后再移到下一层，层间按距离机器人近→远排列。
     */
    private void farmScan(@NotNull CopperGolem golem) {
        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) return;
        World world = golem.getWorld();
        Location loc = golem.getLocation();
        int minX = term.regionMinX(), maxX = term.regionMaxX();
        int minZ = term.regionMinZ(), maxZ = term.regionMaxZ();

        java.util.Map<Integer, java.util.List<FarmTask>> byY = new java.util.LinkedHashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                int surfaceY = world.getHighestBlockYAt(x, z);
                int yLow = Math.max(world.getMinHeight(), surfaceY - 3);
                int yHigh = Math.min(world.getMaxHeight(), surfaceY + 3);
                for (int y = yLow; y <= yHigh; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (isMatureCrop(b)) {
                        byY.computeIfAbsent(y, k -> new java.util.ArrayList<>()).add(new FarmTask(b, true));
                    } else if (b.getType() == Material.FARMLAND
                            && b.getRelative(BlockFace.UP).getType() == Material.AIR
                            && !saplingSlot.isEmpty()) {
                        int ty = y + 1;
                        byY.computeIfAbsent(ty, k -> new java.util.ArrayList<>())
                                .add(new FarmTask(b.getRelative(BlockFace.UP), false));
                    }
                }
            }
        }

        java.util.List<Integer> yLevels = new java.util.ArrayList<>(byY.keySet());
        int robotY = loc.getBlockY();
        yLevels.sort(java.util.Comparator.comparingInt(y -> Math.abs(y - robotY)));

        farmQueue.clear();
        Location cursor = loc;
        for (int y : yLevels) {
            java.util.List<FarmTask> layer = byY.get(y);
            while (!layer.isEmpty()) {
                int nearest = 0;
                double bestDist = Double.MAX_VALUE;
                for (int i = 0; i < layer.size(); i++) {
                    double d = layer.get(i).block().getLocation().toCenterLocation().distanceSquared(cursor);
                    if (d < bestDist) { bestDist = d; nearest = i; }
                }
                FarmTask next = layer.remove(nearest);
                farmQueue.add(next);
                cursor = next.block().getLocation().toCenterLocation();
            }
        }
    }

    private boolean farmApproachAndWork(@NotNull CopperGolem golem, @NotNull Block target, boolean harvest) {
        Location loc = golem.getLocation();
        Location center = target.getLocation().toCenterLocation();
        double distSq = loc.distanceSquared(center);
        golem.lookAt(center);

        if (distSq > 4.0) {
            if (distSq < farmLastDistSq - 0.25) {
                farmLastDistSq = distSq;
                farmNoProgress = 0;
            } else {
                farmNoProgress++;
            }
            farmApproachAge++;
            if (farmNoProgress > NO_PROGRESS_LIMIT || farmApproachAge > TARGET_GIVE_UP) {
                farmQueue.poll();
                resetFarmApproach();
                if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
                return true;
            }
            activity = Activity.APPROACHING;
            setState(golem, CopperGolem.State.IDLE);
            if (!golem.getPathfinder().hasPath() || farmApproachAge % 8 == 1) {
                golem.getPathfinder().moveTo(center, moveSpeed);
            }
            return true;
        }

        if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();

        activity = Activity.HARVESTING;
        setState(golem, CopperGolem.State.GETTING_ITEM);
        golem.swingMainHand();
        farmWorkReachable(golem);
        return true;
    }

    /** 批量处理当前位置 reach 范围内的所有农耕任务，上限 {@link #FARM_WORK_BATCH}。 */
    private void farmWorkReachable(@NotNull CopperGolem golem) {
        Location loc = golem.getLocation();
        int worked = 0;
        java.util.Iterator<FarmTask> it = farmQueue.iterator();
        while (it.hasNext() && worked < FARM_WORK_BATCH && !needsDelivery) {
            FarmTask task = it.next();
            Block b = task.block();
            if (loc.distanceSquared(b.getLocation().toCenterLocation()) > 4.0) continue;
            if (task.harvest()) {
                if (!isMatureCrop(b)) { it.remove(); continue; }
                farmDoHarvest(golem, b);
            } else {
                if (b.getType() != Material.AIR || saplingSlot.isEmpty()) { it.remove(); continue; }
                farmDoPlant(golem, b);
            }
            it.remove();
            worked++;
        }
        resetFarmApproach();
    }

    private void farmDoHarvest(@NotNull CopperGolem golem, @NotNull Block target) {
        Location center = target.getLocation().toCenterLocation();
        Collection<ItemStack> drops = target.getDrops(FARM_TOOL);
        World world = target.getWorld();
        world.playSound(center, target.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 0.9f);
        world.spawnParticle(Particle.BLOCK, center, 12, 0.3, 0.3, 0.3, target.getBlockData());
        target.setType(Material.AIR);
        collectDrops(drops, target.getLocation());
        autoCollectSeeds();
        Block below = target.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.FARMLAND && !saplingSlot.isEmpty()) {
            Material seed = saplingSlot.getFirst().getType();
            Material crop = cropForSeed(seed);
            if (crop != null) {
                target.setType(crop);
                world.playSound(center, crop.createBlockData().getSoundGroup().getPlaceSound(),
                        SoundCategory.BLOCKS, 1.0f, 0.8f);
                consumeSapling(seed);
            }
        }
        steam = Math.max(0.0, steam - steamPerBreak);
        if (carrying.size() >= inventoryCapacity) needsDelivery = true;
    }

    private void farmDoPlant(@NotNull CopperGolem golem, @NotNull Block target) {
        Location center = target.getLocation().toCenterLocation();
        Material seed = saplingSlot.getFirst().getType();
        Material crop = cropForSeed(seed);
        if (crop != null && target.getType() == Material.AIR) {
            target.setType(crop);
            target.getWorld().playSound(center, crop.createBlockData().getSoundGroup().getPlaceSound(),
                    SoundCategory.BLOCKS, 1.0f, 0.8f);
            consumeSapling(seed);
        }
        steam = Math.max(0.0, steam - steamPerBreak * 0.5);
    }

    private void resetFarmApproach() {
        farmApproachAge = 0;
        farmNoProgress = 0;
        farmLastDistSq = Double.MAX_VALUE;
    }

    private static boolean isMatureCrop(@NotNull Block b) {
        if (!(b.getBlockData() instanceof org.bukkit.block.data.Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private static @Nullable Material cropForSeed(@NotNull Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case MELON_SEEDS -> Material.MELON_STEM;
            case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
            case TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP;
            case PITCHER_POD -> Material.PITCHER_CROP;
            default -> null;
        };
    }

    // ===== 屠宰机器人（BUTCHER）=====

    private static final ItemStack BUTCHER_TOOL = new ItemStack(Material.NETHERITE_SWORD);

    /**
     * 屠宰：扫描工作区内的动物 → 走过去 → 攻击 → 掉落收集 → 满载送出货点。
     * 每种动物保留至少 2 只（避免灭绝）。
     * @return 是否处于行动状态
     */
    private boolean butcherStep(@NotNull CopperGolem golem) {
        if (needsDelivery) return deliverStep(golem);

        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) {
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }

        World world = golem.getWorld();
        Location loc = golem.getLocation();
        int minX = term.regionMinX(), maxX = term.regionMaxX();
        int minZ = term.regionMinZ(), maxZ = term.regionMaxZ();

        // 统计区域内各类动物数量，保留每种至少 2 只
        java.util.Map<org.bukkit.entity.EntityType, Integer> animalCounts = new java.util.HashMap<>();
        java.util.List<org.bukkit.entity.Animals> candidates = new java.util.ArrayList<>();
        var butcherScanBox = new org.bukkit.util.BoundingBox(
                minX, world.getMinHeight(), minZ, maxX + 1, world.getMaxHeight(), maxZ + 1);
        for (org.bukkit.entity.Entity ent : world.getNearbyEntities(butcherScanBox)) {
            if (!(ent instanceof org.bukkit.entity.Animals animal)) continue;
            if (!animal.isValid() || animal.isDead()) continue;
            if (animal instanceof org.bukkit.entity.Tameable t && t.isTamed()) continue;
            if (!animal.isAdult()) continue;
            animalCounts.merge(animal.getType(), 1, Integer::sum);
            candidates.add(animal);
        }

        // 找最近的可屠宰动物（该种类数量 > 2）
        org.bukkit.entity.Animals target = null;
        double bestSq = Double.MAX_VALUE;
        for (org.bukkit.entity.Animals a : candidates) {
            if (animalCounts.getOrDefault(a.getType(), 0) <= 2) continue;
            double sq = loc.distanceSquared(a.getLocation());
            if (sq < bestSq) { bestSq = sq; target = a; }
        }

        if (target == null) {
            if (!carrying.isEmpty()) { needsDelivery = true; return true; }
            if (scanCooldown > 0) { scanCooldown--; }
            else { scanCooldown = SCAN_IDLE_COOLDOWN; }
            activity = Activity.IDLE;
            setState(golem, CopperGolem.State.IDLE);
            returnToTerminal(golem);
            return false;
        }

        Location targetLoc = target.getLocation();
        double distSq = loc.distanceSquared(targetLoc);
        golem.lookAt(targetLoc);

        if (distSq > 4.0) {
            activity = Activity.HUNTING;
            setState(golem, CopperGolem.State.IDLE);
            if (!golem.getPathfinder().hasPath() || ticksSinceRetarget++ > 6) {
                golem.getPathfinder().moveTo(targetLoc, moveSpeed);
                ticksSinceRetarget = 0;
            }
            return true;
        }

        // 到了：攻击
        if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
        activity = Activity.ATTACKING;
        setState(golem, CopperGolem.State.GETTING_ITEM);
        golem.swingMainHand();
        target.damage(target.getHealth() + 1.0, golem);
        world.playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 0.7f, 0.9f);
        world.spawnParticle(Particle.SWEEP_ATTACK, targetLoc.clone().add(0, 0.5, 0), 1);

        // 等一 tick 让掉落物生成，下次 tick 由拾取逻辑或 collectDrops 处理
        // 屠宰后短暂扫描附近掉落物
        Bukkit.getScheduler().runTaskLater(io.github.steamwork.Steamwork.getInstance(), () -> {
            for (org.bukkit.entity.Entity ent : world.getNearbyEntities(targetLoc, 2, 2, 2)) {
                if (ent instanceof org.bukkit.entity.Item item && item.isValid()) {
                    collectDrops(List.of(item.getItemStack()), item.getLocation());
                    item.remove();
                }
            }
            updateHeldTool();
        }, 2L);

        steam = Math.max(0.0, steam - steamPerBreak);
        if (carrying.size() >= inventoryCapacity) needsDelivery = true;
        return true;
    }

    /** 回充寻路：未到充汽舱判定框就周期性寻路过去；到了就站定等充。 */
    private void navigateToChamber(@NotNull CopperGolem golem) {
        Location loc = golem.getLocation();
        if (boundChamber != null
                && boundChamber.getWorld() == loc.getWorld()
                && loc.distanceSquared(boundChamber) <= 2.6) {   // 已在核心 ±1.6 内（充汽舱判定框）
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            golem.setGolemState(CopperGolem.State.IDLE);
            target = null;
            ticksSinceRetarget = 0;
            return;
        }
        ticksSinceRetarget++;
        if (ticksSinceRetarget > 6 || !golem.getPathfinder().hasPath()) {
            if (boundChamber != null) golem.getPathfinder().moveTo(boundChamber, moveSpeed);
            ticksSinceRetarget = 0;
        }
    }

    /** 绑定充汽舱所在区块是否已加载、且该方块确为充汽舱。 */
    private boolean chamberLoaded(@NotNull Location chamberCore) {
        Block b = chamberCore.getBlock();
        return PneumaticEndpointSupport.isChunkLoaded(b)
                && PneumaticEndpointSupport.loadedRebarBlock(b) instanceof SteamChargingChamber;
    }

    /**
     * 采矿 / 砍树（两阶段）：
     * <ol>
     *   <li><b>接近</b>：工作区内找最近目标，寻路过去；够近即对其连通簇（整棵树 / 整条矿脉）
     *       flood-fill 锁定。</li>
     *   <li><b>采伐</b>：站定逐个破坏锁定簇里的方块，每个需累积 {@link #breakTicks} 次进度
     *       （挥臂 + 裂纹，非秒砍）；簇清空后回接近阶段重新扫描。</li>
     * </ol>
     * 工作区无目标时原地待命（不游荡、不耗汽）。
     *
     * @return 是否处于行动/作业状态（用于决定本 tick 是否耗汽）
     */
    private boolean mineStep(@NotNull CopperGolem golem, @NotNull RobotType mode) {
        // 满载 → 去出货点卸货
        if (needsDelivery) {
            return deliverStep(golem);
        }
        // 采伐阶段：已锁定一棵树 / 一条矿脉
        if (targetBlock != null || !lockedCluster.isEmpty()) {
            harvestStep(golem, mode);
            return true;
        }
        // 接近阶段：选定一个接近目标后<b>持续走向它</b>，不每 tick 重新全扫（重扫很费且可能触发区块加载）
        if (approachTarget != null && !isTarget(approachTarget, mode)) {
            approachTarget = null; // 选定目标已失效 → 重新扫描
        }
        if (approachTarget == null) {
            activity = Activity.SEARCHING;                // 在找目标
            // 优先使用预扫描结果（砍上一棵树时已提前找到的下一棵）
            if (nextApproachTarget != null && isTarget(nextApproachTarget, mode)
                    && !isBlocked(nextApproachTarget)) {
                Block found = nextApproachTarget;
                nextApproachTarget = null;
                approachTarget = (mode == RobotType.CHOP) ? treeBase(found) : found;
                if (isBlocked(approachTarget)) {
                    blacklist(found);
                    approachTarget = null;
                    return true;
                }
                beginApproach();
            } else {
                nextApproachTarget = null;
                // 空扫冷却：上次没扫到目标，等几次决策再扫，避免空扫大立方体每 tick 跑
                if (scanCooldown > 0) {
                    scanCooldown--;
                    setState(golem, CopperGolem.State.IDLE);
                    return false;
                }
                Block found = findNearestTarget(golem, mode);
                if (found == null) {
                    // 无目标但还有货物 → 先交货
                    if (!carrying.isEmpty()) {
                        needsDelivery = true;
                        return true;
                    }
                    // 工作区无目标：走回终端旁待命，等待目标出现（不耗汽）
                    scanCooldown = SCAN_IDLE_COOLDOWN;
                    activity = Activity.IDLE;
                    setState(golem, CopperGolem.State.IDLE);
                    target = null;
                    returnToTerminal(golem);
                    return false;
                }
                // 砍树：把目标降到这根树干的<b>最底部</b>，让机器人走到树根旁砍，而不是爬到树顶
                approachTarget = (mode == RobotType.CHOP) ? treeBase(found) : found;
                if (isBlocked(approachTarget)) {
                    blacklist(found);
                    approachTarget = null;
                    return true;
                }
                beginApproach();
            }
        }
        setState(golem, CopperGolem.State.IDLE);          // 接近途中：非作业姿态
        Location center = approachTarget.getLocation().toCenterLocation();
        golem.lookAt(center);                             // 朝向目标
        Location loc = golem.getLocation();
        double hDistSq = horizontalDistSq(loc, center);
        double dy = loc.getY() - approachTarget.getY();   // 有符号：+ 在目标上方，- 在下方

        // 锁定条件：水平就在树干/矿石旁（≤约2格），且站在底部附近——允许略低（浅坑）/略高（旁边台阶），
        // 但不能高出太多（防止站到树顶上）。满足即锁定整簇开砍。
        if (hDistSq <= 4.0 && dy >= -2.5 && dy <= 1.5) {
            lockCluster(approachTarget, mode);
            approachTarget = null;
            return true;
        }

        // 困在高处检测：如果机器人在目标上方过高（站到树顶了），直接寻路回 home 着陆再重试
        if (dy > 3.0 && noProgress >= 2) {
            golem.getPathfinder().moveTo(home, moveSpeed);
            blacklistTree(approachTarget, mode);
            approachTarget = null;
            noProgress = 0;
            return true;
        }

        // 无进度检测：靠近了就清零，否则累计；够不到太久 → 放弃这棵（悬空/被围 → 整棵拉黑，去下一棵）
        double distSq = loc.distanceSquared(center);
        if (distSq < lastApproachDistSq - 0.25) {         // 比上次近了至少 0.5 格
            lastApproachDistSq = distSq;
            noProgress = 0;
        } else {
            noProgress++;
        }
        if (noProgress > NO_PROGRESS_LIMIT || ++approachAge > TARGET_GIVE_UP) {
            blacklistTree(approachTarget, mode);          // 够不到 → 整棵树拉黑，避免反复卡在同一棵
            approachTarget = null;
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            return true;
        }
        // 清掉正前方（朝目标方向）挡路的树叶——仅树叶，绝不破坏原木/其它方块
        boolean clearing = clearLeavesAhead(golem, center);
        activity = clearing ? Activity.CLEARING : Activity.APPROACHING;
        // 仅在需要重新寻路时计算站立点（findStandingSpot 略有开销，不必每 tick 算）
        if (approachAge % 8 == 1 || !golem.getPathfinder().hasPath()) {
            // 砍树时寻路到树根旁的站立点，而非原木本身（避免爬上树）
            Location navTarget = (mode == RobotType.CHOP)
                    ? findStandingSpot(approachTarget) : center;
            golem.getPathfinder().moveTo(navTarget, moveSpeed);
        }
        return true;
    }

    /**
     * 清掉机器人正前方（朝 {@code toward} 方向）脚部与头部高度挡路的树叶。
     * @return 是否破坏了至少一块树叶
     */
    private boolean clearLeavesAhead(@NotNull CopperGolem golem, @NotNull Location toward) {
        Location loc = golem.getLocation();
        double dx = toward.getX() - loc.getX();
        double dz = toward.getZ() - loc.getZ();
        if (dx * dx + dz * dz < 0.04) return false;        // 已基本到位，无前方可言
        BlockFace dir = (Math.abs(dx) >= Math.abs(dz))
                ? (dx >= 0 ? BlockFace.EAST : BlockFace.WEST)
                : (dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH);
        Block feetAhead = loc.getBlock().getRelative(dir);
        boolean broke = breakLeaf(golem, feetAhead);                       // 脚部高度
        broke |= breakLeaf(golem, feetAhead.getRelative(BlockFace.UP));    // 头部高度
        return broke;
    }

    /**
     * 若该方块是<b>树叶</b>则破坏以清路（收集掉落、不耗汽、可被保护插件拦截）。
     * 仅破坏树叶，绝不破坏原木 / 其它方块 / 任何 Rebar 方块。
     *
     * @return 是否破坏了一块树叶
     */
    private boolean breakLeaf(@NotNull CopperGolem golem, @NotNull Block b) {
        if (!chunkLoaded(b)) return false;
        Material leaf = b.getType();
        if (!leaf.name().endsWith("_LEAVES")) return false;
        if (PneumaticEndpointSupport.loadedRebarBlock(b) != null) return false;
        // 保护插件可拦截
        if (!new EntityChangeBlockEvent(golem, b, Material.AIR.createBlockData()).callEvent()) return false;

        Collection<ItemStack> drops = b.getDrops(CHOP_TOOL);
        World w = b.getWorld();
        golem.swingMainHand();
        w.playSound(b.getLocation().toCenterLocation(),
                leaf.createBlockData().getSoundGroup().getBreakSound(), 0.5f, 1.0f);
        w.spawnParticle(Particle.BLOCK, b.getLocation().toCenterLocation(),
                8, 0.3, 0.3, 0.3, leaf.createBlockData());
        b.setType(Material.AIR);
        collectDrops(drops, b.getLocation());
        if (robotType == RobotType.CHOP) autoCollectSeeds();
        return true;
    }

    /**
     * 把够不到的目标拉黑：砍树时<b>整棵树</b>的原木一次性拉黑（flood-fill 连通原木），
     * 这样机器人不会反复选回同一棵够不到的树（如悬空树根、被严密包围的树），直接去下一棵。
     * 采矿则只拉黑该方块。
     */
    private void blacklistTree(@NotNull Block start, @NotNull RobotType mode) {
        if (mode != RobotType.CHOP) { blacklist(start); return; }
        if (blockedTargets.size() > 4096) blockedTargets.clear();
        final int sx = start.getX(), sz = start.getZ();
        final int rSq = CLUSTER_HORIZONTAL_RADIUS * CLUSTER_HORIZONTAL_RADIUS;
        java.util.Set<Block> visited = new java.util.HashSet<>();
        java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        int count = 0;
        long now = getEntity().getWorld().getFullTime();
        while (!queue.isEmpty() && count < CLUSTER_CAP) {
            Block b = queue.poll();
            blockedTargets.put(b, now);
            count++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        if (!visited.add(nb)) continue;
                        int hx = nb.getX() - sx, hz = nb.getZ() - sz;
                        if (hx * hx + hz * hz > rSq) continue;
                        if (isTarget(nb, mode)) queue.add(nb);
                    }
                }
            }
        }
    }

    /** 从一根原木向下找到这根树干最底部的原木（让机器人走到树根处砍伐）。 */
    private @NotNull Block treeBase(@NotNull Block log) {
        Block b = log;
        for (int i = 0; i < 32; i++) {                    // 上限保护
            Block below = b.getRelative(BlockFace.DOWN);
            if (!isTarget(below, RobotType.CHOP)) break;
            b = below;
        }
        return b;
    }

    /**
     * 找到树根旁可站立的地面空气格——机器人寻路到这里而非原木方块本身，
     * 避免 Minecraft 寻路把原木当台阶爬上树顶。
     * 搜索顺序：树根同层四面 → 树根下方一格四面（斜坡）；找不到则 fallback 原木正上方。
     */
    private @NotNull Location findStandingSpot(@NotNull Block treeBase) {
        // 在树根周围半径 2 格、高度 -1~+1 层搜索可站立位置
        // 优先选近的（距离排序：r=1 四邻 → r=1 对角 → r=2）
        Location best = null;
        double bestSq = Double.MAX_VALUE;
        Location center = treeBase.getLocation().toCenterLocation();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    Block feet = treeBase.getRelative(dx, dy, dz);
                    if (!isPassable(feet)) continue;
                    if (!isPassable(feet.getRelative(BlockFace.UP))) continue;
                    if (!isSolid(feet.getRelative(BlockFace.DOWN))) continue;
                    double sq = feet.getLocation().toCenterLocation().distanceSquared(center);
                    if (sq < bestSq) { bestSq = sq; best = feet.getLocation().toCenterLocation(); }
                }
            }
        }
        if (best != null) return best;
        // fallback：树根正上方
        return treeBase.getRelative(BlockFace.UP).getLocation().toCenterLocation();
    }

    /** 区块是否已加载——访问方块前必须判断，否则 getType() 会在主线程同步加载区块导致卡死。 */
    private static boolean chunkLoaded(@NotNull Block b) {
        return b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4);
    }

    private static boolean isPassable(@NotNull Block b) {
        return chunkLoaded(b) && !b.getType().isSolid();
    }

    private static boolean isSolid(@NotNull Block b) {
        return chunkLoaded(b) && b.getType().isSolid();
    }

    /** 两点的水平（忽略 Y）距离平方。 */
    private static double horizontalDistSq(@NotNull Location a, @NotNull Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** 选定新接近目标时重置接近进度/计数。 */
    private void beginApproach() {
        approachAge = 0;
        noProgress = 0;
        lastApproachDistSq = Double.MAX_VALUE;
    }

    /** 采伐：逐个破坏锁定簇里的方块，每个需累积破坏进度（非秒砍）。 */
    private void harvestStep(@NotNull CopperGolem golem, @NotNull RobotType mode) {
        activity = Activity.WORKING;
        World world = golem.getWorld();

        // 开始作业即站定：停掉接近阶段残留的寻路路径，采伐期间不再走动
        // （否则会出现「一边砍树一边往树顶爬」的情况）
        if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
        target = null;

        // 当前目标失效 → 清裂纹换下一个
        if (targetBlock != null && !isTarget(targetBlock, mode)) {
            sendCrack(targetBlock, 0.0f);
            targetBlock = null;
        }
        if (targetBlock == null) {
            targetBlock = pollNextClusterBlock(mode);
            breakingProgress = 0;
            if (targetBlock == null) {                    // 簇清空 → 回接近阶段
                replantSapling();
                lockedCluster.clear();
                return;
            }
        }

        // 充完汽回来：走回树根/矿脉起点附近再开砍（不走向可能悬空的 targetBlock）
        if (returningToWork) {
            Block anchor = (clusterRoot != null) ? clusterRoot : targetBlock;
            Location anchorLoc = anchor.getLocation().toCenterLocation();
            double distSq = golem.getLocation().distanceSquared(anchorLoc);
            if (distSq > 6.25) {  // >2.5 格
                activity = Activity.APPROACHING;
                setState(golem, CopperGolem.State.IDLE);
                Location navTarget = (mode == RobotType.CHOP)
                        ? findStandingSpot(anchor) : anchorLoc;
                golem.getPathfinder().moveTo(navTarget, moveSpeed);
                return;
            }
            returningToWork = false;
        }

        // 预扫描：簇剩余少量方块时，提前找到下一棵最近的树/矿脉（基于当前工作位置），
        // 砍完后直接衔接不用全扫。每簇只扫一次，避免末尾几块时每 tick 空扫大立方体。
        if (!preScanned && nextApproachTarget == null && lockedCluster.size() <= 3) {
            Location ref = (lastFocus != null) ? lastFocus : golem.getLocation();
            nextApproachTarget = findNearestTargetFrom(ref, mode);
            preScanned = true;
        }

        // 累积破坏进度：朝向目标 + 挥臂 + 裂纹 + 碎屑
        golem.lookAt(targetBlock.getLocation().toCenterLocation());
        golem.swingMainHand();
        setState(golem, CopperGolem.State.GETTING_ITEM);
        breakingProgress++;
        float progress = Math.min(1.0f, (float) breakingProgress / breakTicks);
        sendCrack(targetBlock, progress);
        // 挖掘过程中持续敲击音：决策 tick 播一次 + 中间补两次（模拟每 ~4gt 一次）
        Location hitLoc = targetBlock.getLocation().toCenterLocation();
        Sound hitSound = targetBlock.getBlockData().getSoundGroup().getHitSound();
        world.playSound(hitLoc, hitSound, SoundCategory.BLOCKS, 0.25f, 0.5f);
        Bukkit.getScheduler().runTaskLater(io.github.steamwork.Steamwork.getInstance(), () -> {
            if (targetBlock != null) world.playSound(hitLoc, hitSound, SoundCategory.BLOCKS, 0.25f, 0.5f);
        }, 4L);
        Bukkit.getScheduler().runTaskLater(io.github.steamwork.Steamwork.getInstance(), () -> {
            if (targetBlock != null) world.playSound(hitLoc, hitSound, SoundCategory.BLOCKS, 0.25f, 0.5f);
        }, 8L);
        world.spawnParticle(Particle.BLOCK, hitLoc,
                3, 0.2, 0.2, 0.2, targetBlock.getBlockData());

        if (breakingProgress >= breakTicks) {
            sendCrack(targetBlock, 0.0f);                 // 清裂纹
            breakTarget(golem, mode);                     // 实际移除 + 掉落 + 音效 + 耗汽
            targetBlock = null;
            breakingProgress = 0;
            if (lockedCluster.isEmpty()) {
                replantSapling();
            }
        }
    }

    /** 给附近玩家发送方块破坏裂纹（World 无此 API，需逐玩家发；source=机器人实体 id）。 */
    private void sendCrack(@NotNull Block b, float progress) {
        int id = getEntity().getEntityId();
        Location loc = b.getLocation();
        for (Player p : b.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 4096) { // 64 格内
                p.sendBlockDamage(loc, progress, id);
            }
        }
    }

    /**
     * 从起点 flood-fill（26 邻接）连通的同类目标，锁定为<b>一棵树 / 一条矿脉</b>。
     *
     * <p>限制：只纳入离起点（树根）<b>水平距离 ≤ {@link #CLUSTER_HORIZONTAL_RADIUS} 格</b>的方块，垂直不限。
     * 这样高树仍能整棵砍，但根部相距较远的<b>两棵树不会被并成一簇</b>——否则机器人会站在已砍空的第一棵
     * 树原地远程采伐另一棵树。</p>
     */
    private void lockCluster(@NotNull Block start, @NotNull RobotType mode) {
        lockedCluster.clear();
        preScanned = false; // 新簇：允许再做一次预扫描
        lastFocus = start.getLocation().toCenterLocation();
        if (mode == RobotType.CHOP && replantEnabled) {
            clusterRoot = start;
            clusterLogType = start.getType();
        }
        final int sx = start.getX(), sz = start.getZ();
        final int rSq = CLUSTER_HORIZONTAL_RADIUS * CLUSTER_HORIZONTAL_RADIUS;
        java.util.Set<Block> visited = new java.util.HashSet<>();
        java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && lockedCluster.size() < CLUSTER_CAP) {
            Block b = queue.poll();
            lockedCluster.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        if (!visited.add(nb)) continue;
                        int hx = nb.getX() - sx, hz = nb.getZ() - sz;
                        if (hx * hx + hz * hz > rSq) continue;   // 水平超出一棵树范围 → 不纳入
                        if (isTarget(nb, mode)) queue.add(nb);
                    }
                }
            }
        }
    }

    /** 取锁定簇中离机器人最近、仍有效的方块并移除；顺手剔除失效项；无则 null。 */
    private @Nullable Block pollNextClusterBlock(@NotNull RobotType mode) {
        // 基准取"上一块"的位置：沿连续相邻方块一路砍，砍完一棵树才会跳到另一棵
        Location ref = (lastFocus != null) ? lastFocus : getEntity().getLocation();
        Block best = null;
        double bestSq = Double.MAX_VALUE;
        java.util.Iterator<Block> it = lockedCluster.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (isBlocked(b) || !isTarget(b, mode)) { it.remove(); continue; }
            double sq = b.getLocation().toCenterLocation().distanceSquared(ref);
            if (sq < bestSq) { bestSq = sq; best = b; }
        }
        if (best != null) {
            lockedCluster.remove(best);
            lastFocus = best.getLocation().toCenterLocation(); // 更新基准，连续推进
        }
        return best;
    }

    private void blacklist(@NotNull Block b) {
        if (blockedTargets.size() > 512) blockedTargets.clear();
        blockedTargets.put(b, getEntity().getWorld().getFullTime());
    }

    private boolean isBlocked(@NotNull Block b) {
        Long t = blockedTargets.get(b);
        if (t == null) return false;
        if (getEntity().getWorld().getFullTime() - t > BLACKLIST_EXPIRY_TICKS) {
            blockedTargets.remove(b);
            return false;
        }
        return true;
    }

    private void returnToTerminal(@NotNull CopperGolem golem) {
        RobotControlTerminal term = terminal();
        if (term == null) {
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            return;
        }
        Location termLoc = term.getBlock().getLocation().add(0.5, 0, 0.5);
        double distSq = golem.getLocation().distanceSquared(termLoc);
        if (distSq > 9.0) {
            golem.getPathfinder().moveTo(termLoc, moveSpeed);
        } else {
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
        }
    }

    /** 清空当前作业状态（切换模式 / 拆除时调用），并抹掉残留裂纹。 */
    private void resetWork() {
        if (targetBlock != null) {
            sendCrack(targetBlock, 0.0f);
        }
        needsDelivery = false;
        returningToWork = false;
        closeOpenedLid();
        targetBlock = null;
        approachTarget = null;
        nextApproachTarget = null;
        preScanned = false;
        lastFocus = null;
        clusterRoot = null;
        clusterLogType = null;
        breakingProgress = 0;
        approachAge = 0;
        noProgress = 0;
        lastApproachDistSq = Double.MAX_VALUE;
        scanCooldown = 0;
        lockedCluster.clear();
        farmQueue.clear();
        resetPatrolChase();
    }

    /** 暂停作业（充汽时调用）：清裂纹视觉但保留簇/树根进度，充完回来继续。 */
    private void pauseWork() {
        if (targetBlock != null) {
            sendCrack(targetBlock, 0.0f);
        }
        closeOpenedLid();
        breakingProgress = 0;
        approachAge = 0;
        noProgress = 0;
        lastApproachDistSq = Double.MAX_VALUE;
        resetPatrolChase();
    }

    /** 砍完整棵树后在树根补种树苗（从树苗槽消耗，槽空则不补种）。 */
    private void replantSapling() {
        if (clusterRoot == null || clusterLogType == null) return;
        Block root = clusterRoot;
        Material sapling = saplingForLog(clusterLogType);
        clusterRoot = null;
        clusterLogType = null;
        if (sapling == null || !chunkLoaded(root)) return;
        if (root.getType() != Material.AIR) return;
        Block below = root.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) return;
        if (!consumeSapling(sapling)) return;
        root.setType(sapling);
        root.getWorld().playSound(root.getLocation().toCenterLocation(),
                sapling.createBlockData().getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS, 1.0f, 0.8f);
    }

    /** 从树苗槽扣减一个指定材质的树苗，成功返回 true。 */
    private boolean consumeSapling(@NotNull Material sapling) {
        for (int i = 0; i < saplingSlot.size(); i++) {
            ItemStack stack = saplingSlot.get(i);
            if (stack.getType() == sapling) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    saplingSlot.remove(i);
                }
                return true;
            }
        }
        return false;
    }

    /** 从 carrying 中自动回收种子/树苗到 saplingSlot（收割产物中的种子不浪费库存）。 */
    private void autoCollectSeeds() {
        if (saplingSlotFull()) return;
        java.util.Iterator<ItemStack> it = carrying.iterator();
        while (it.hasNext()) {
            if (saplingSlotFull()) break;
            ItemStack item = it.next();
            if (!isSaplingMaterial(item.getType())) continue;
            boolean merged = false;
            for (ItemStack existing : saplingSlot) {
                if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    int transfer = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + transfer);
                    item.setAmount(item.getAmount() - transfer);
                    if (item.getAmount() <= 0) it.remove();
                    merged = true;
                    break;
                }
            }
            if (!merged && saplingSlot.size() < saplingSlotCapacity) {
                saplingSlot.add(item.clone());
                it.remove();
            }
        }
    }

    /** 从出货点容器补充树苗/种子到 saplingSlot（交货后自动补货）。 */
    private void restockSaplings(@NotNull Location containerLoc) {
        if (saplingSlotFull()) return;
        Block b = containerLoc.getBlock();
        if (!chunkLoaded(b)) return;
        if (!(b.getState() instanceof Container c)) return;
        Inventory inv = c.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (saplingSlotFull()) break;
            ItemStack item = inv.getItem(i);
            if (item == null || item.isEmpty()) continue;
            if (!isSaplingMaterial(item.getType())) continue;
            boolean merged = false;
            for (ItemStack existing : saplingSlot) {
                if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    int transfer = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + transfer);
                    item.setAmount(item.getAmount() - transfer);
                    if (item.getAmount() <= 0) inv.setItem(i, null);
                    merged = true;
                    break;
                }
            }
            if (!merged && saplingSlot.size() < saplingSlotCapacity) {
                saplingSlot.add(item.clone());
                inv.setItem(i, null);
            }
        }
    }

    private boolean saplingSlotFull() {
        if (saplingSlot.size() < saplingSlotCapacity) return false;
        for (ItemStack s : saplingSlot) {
            if (s.getAmount() < s.getMaxStackSize()) return false;
        }
        return true;
    }

    private static @Nullable Material saplingForLog(@NotNull Material log) {
        String name = log.name();
        if (name.startsWith("STRIPPED_")) name = name.substring(9);
        return switch (name) {
            case "OAK_LOG"      -> Material.OAK_SAPLING;
            case "SPRUCE_LOG"   -> Material.SPRUCE_SAPLING;
            case "BIRCH_LOG"    -> Material.BIRCH_SAPLING;
            case "JUNGLE_LOG"   -> Material.JUNGLE_SAPLING;
            case "ACACIA_LOG"   -> Material.ACACIA_SAPLING;
            case "DARK_OAK_LOG" -> Material.DARK_OAK_SAPLING;
            case "CHERRY_LOG"   -> Material.CHERRY_SAPLING;
            case "MANGROVE_LOG" -> Material.MANGROVE_PROPAGULE;
            case "PALE_OAK_LOG" -> Material.PALE_OAK_SAPLING;
            case "CRIMSON_STEM" -> Material.CRIMSON_FUNGUS;
            case "WARPED_STEM"  -> Material.WARPED_FUNGUS;
            default -> null;
        };
    }

    /** 破坏目标方块：取正确掉落 → 移除 → 特效 → 回收到投放箱（放不下则散落）→ 破坏耗汽。 */
    private void breakTarget(@NotNull CopperGolem golem, @NotNull RobotType mode) {
        Block b = targetBlock;
        if (b == null) return;
        World world = b.getWorld();
        Material broken = b.getType();

        // 让保护类插件可拦截（以「实体改变方块」的形式）；被拦截则记入跳过集、不破坏
        EntityChangeBlockEvent ev = new EntityChangeBlockEvent(golem, b, Material.AIR.createBlockData());
        if (!ev.callEvent()) {
            blacklist(b);
            return;
        }

        Collection<ItemStack> drops = b.getDrops(mode == RobotType.CHOP ? CHOP_TOOL : MINE_TOOL);

        // 破坏特效（音 + 方块碎屑）；挥臂动画在 harvestStep 每 tick 已做
        world.playSound(b.getLocation().toCenterLocation(),
                broken.createBlockData().getSoundGroup().getBreakSound(), 1.0f, 0.9f);
        world.spawnParticle(Particle.BLOCK, b.getLocation().toCenterLocation(),
                20, 0.3, 0.3, 0.3, broken.createBlockData());
        b.setType(Material.AIR);

        collectDrops(drops, b.getLocation());
        if (mode == RobotType.CHOP) autoCollectSeeds();
        steam = Math.max(0.0, steam - steamPerBreak);
        if (carrying.size() >= inventoryCapacity) {
            needsDelivery = true;
        }
    }

    /**
     * 掉落收入机器人内置库存（合并堆叠）；库存满则在原地散落。
     * 采矿/砍树机器人满载后会自动前往出货点卸货。
     */
    private void collectDrops(@NotNull Collection<ItemStack> drops, @NotNull Location fallbackDrop) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            int remaining = drop.getAmount();
            for (ItemStack slot : carrying) {
                if (remaining <= 0) break;
                if (slot.isSimilar(drop) && slot.getAmount() < slot.getMaxStackSize()) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    int add = Math.min(space, remaining);
                    slot.setAmount(slot.getAmount() + add);
                    remaining -= add;
                }
            }
            while (remaining > 0) {
                if (carrying.size() < inventoryCapacity) {
                    ItemStack newSlot = drop.clone();
                    int thisStack = Math.min(remaining, drop.getMaxStackSize());
                    newSlot.setAmount(thisStack);
                    carrying.add(newSlot);
                    remaining -= thisStack;
                } else {
                    ItemStack overflow = drop.clone();
                    overflow.setAmount(remaining);
                    fallbackDrop.getWorld().dropItemNaturally(fallbackDrop.toCenterLocation(), overflow);
                    remaining = 0;
                }
            }
        }
    }

    /**
     * 采矿/砍树满载卸货：走到终端出货点 → 投放库存 → 回到工作区继续采伐。
     * 无出货点或容器满 → 散落在脚下。
     */
    private boolean deliverStep(@NotNull CopperGolem golem) {
        if (carrying.isEmpty()) {
            // 交完货 → 从出货点自动补充树苗/种子
            if (robotType == RobotType.CHOP || robotType == RobotType.FARM) {
                RobotControlTerminal term = terminal();
                Location dp = (term != null) ? term.getDeliveryPoint() : null;
                if (dp != null) restockSaplings(dp);
            }
            closeOpenedLid();
            depositTick = 0;
            needsDelivery = false;
            postDeliveryCooldown = 3; // 交完货后留几 tick 过渡
            return true;
        }

        RobotControlTerminal term = terminal();
        Location dp = (term != null) ? term.getDeliveryPoint() : null;
        if (dp == null) {
            dropCarrying(golem.getLocation());
            depositTick = 0;
            needsDelivery = false;
            return true;
        }

        Block destBlock = dp.getBlock();
        if (!chunkLoaded(destBlock)) {
            closeOpenedLid();
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }

        Location loc = golem.getLocation();
        Location dest = dp.toCenterLocation();
        golem.lookAt(dest);
        double distSq = loc.distanceSquared(dest);

        if (distSq > 6.25) {
            closeOpenedLid();
            depositTick = 0;
            activity = Activity.DELIVERING;
            setState(golem, CopperGolem.State.IDLE);
            clearLeavesAhead(golem, dest);
            haulNavAge++;
            if (haulNavAge % 8 == 1 || !golem.getPathfinder().hasPath()) {
                golem.getPathfinder().moveTo(dest, moveSpeed);
            }
            return true;
        }

        if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
        haulNavAge = 0;
        openLid(destBlock);

        // 节拍控制：开箱后等一会再开始放入
        depositTick++;
        if (depositTick < DEPOSIT_DELAY) {
            activity = Activity.DELIVERING;
            setState(golem, CopperGolem.State.IDLE);
            return true;
        }
        if ((depositTick - DEPOSIT_DELAY) % DEPOSIT_INTERVAL != 0) {
            activity = Activity.DEPOSITING;
            setState(golem, CopperGolem.State.IDLE);
            return true;
        }

        int deposited = depositToBlock(destBlock);
        if (deposited > 0) {
            activity = Activity.DEPOSITING;
            setState(golem, CopperGolem.State.DROPPING_ITEM);
            golem.swingMainHand();
            golem.getWorld().playSound(dest, Sound.ENTITY_ITEM_FRAME_ADD_ITEM,
                    SoundCategory.BLOCKS, 0.5f, 1.2f);
            return true;
        }

        if (!carrying.isEmpty()) {
            dropCarrying(golem.getLocation());
        }
        closeOpenedLid();
        depositTick = 0;
        needsDelivery = false;
        return true;
    }

    /** 在工作区（home ±workRadius 立方体）内找离机器人最近的目标方块。 */
    private @Nullable Block findNearestTarget(@NotNull CopperGolem golem, @NotNull RobotType mode) {
        return findNearestTargetFrom(golem.getLocation(), mode);
    }

    /** 在终端区域内找离指定参考点最近的目标方块（排除当前锁定簇中的方块）。 */
    private @Nullable Block findNearestTargetFrom(@NotNull Location ref, @NotNull RobotType mode) {
        RobotControlTerminal term = terminal();
        if (term == null || !term.hasRegion()) return null;
        World world = getEntity().getWorld();
        int minX = term.regionMinX(), maxX = term.regionMaxX();
        int minZ = term.regionMinZ(), maxZ = term.regionMaxZ();
        Block best = null;
        double bestSq = Double.MAX_VALUE;

        if (mode == RobotType.CHOP) {
            // 砍树：树在地表，用高度图限定 Y（地表 -5 ~ +40），避免扫全高 128 层
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    int surfaceY = world.getHighestBlockYAt(x, z);
                    int yLow = Math.max(world.getMinHeight(), surfaceY - 5);
                    int yHigh = Math.min(world.getMaxHeight(), surfaceY + 40);
                    for (int y = yLow; y <= yHigh; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (isBlocked(b) || !isTarget(b, mode)) continue;
                        if (lockedCluster.contains(b)) continue;
                        double sq = b.getLocation().toCenterLocation().distanceSquared(ref);
                        if (sq < bestSq) { bestSq = sq; best = b; }
                    }
                }
            }
        } else {
            // 采矿：全 Y 范围；大区域（>400 列）随机采样 256 列，避免单次扫描卡顿
            int minY = world.getMinHeight(), maxY = Math.min(world.getMaxHeight(), minY + 128);
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            if (width * depth > 400) {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                for (int i = 0; i < 256; i++) {
                    int x = minX + rng.nextInt(width);
                    int z = minZ + rng.nextInt(depth);
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        if (isBlocked(b) || !isTarget(b, mode)) continue;
                        if (lockedCluster.contains(b)) continue;
                        double sq = b.getLocation().toCenterLocation().distanceSquared(ref);
                        if (sq < bestSq) { bestSq = sq; best = b; }
                    }
                }
            } else {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                        for (int y = minY; y <= maxY; y++) {
                            Block b = world.getBlockAt(x, y, z);
                            if (isBlocked(b) || !isTarget(b, mode)) continue;
                            if (lockedCluster.contains(b)) continue;
                            double sq = b.getLocation().toCenterLocation().distanceSquared(ref);
                            if (sq < bestSq) { bestSq = sq; best = b; }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** 是否为当前模式的可作业目标：先匹配原版材质，再排除一切 Rebar 方块（机器绝不破坏）。 */
    private boolean isTarget(@NotNull Block b, @NotNull RobotType mode) {
        // 关键：绝不访问未加载区块的方块——否则 getType() 会在主线程同步加载/生成区块，导致服务器卡死
        if (!b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return false;
        Material m = b.getType();
        boolean matches = switch (mode) {
            case MINE -> m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
            case CHOP -> m.name().endsWith("_LOG") || m.name().endsWith("_STEM")
                    || m.name().endsWith("_WOOD") || m.name().endsWith("_HYPHAE");
            default -> false;
        };
        return matches && PneumaticEndpointSupport.loadedRebarBlock(b) == null;
    }

    /** 以 home 为原点、wanderRadius 为半径随机选一个地表点。 */
    /** 在终端区域内随机选一个地表点（fallback 到 home 附近）。 */
    private @NotNull Location pickWanderTarget(@NotNull CopperGolem golem) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        RobotControlTerminal term = terminal();
        double x, z;
        if (term != null && term.hasRegion()) {
            x = rng.nextDouble(term.regionMinX(), term.regionMaxX() + 1);
            z = rng.nextDouble(term.regionMinZ(), term.regionMaxZ() + 1);
        } else {
            x = home.getX() + (rng.nextDouble() * 2 - 1) * wanderRadius;
            z = home.getZ() + (rng.nextDouble() * 2 - 1) * wanderRadius;
        }
        int y = golem.getWorld().getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
        return new Location(golem.getWorld(), x, y, z);
    }

    @Override
    public void write(@NotNull PersistentDataContainer pdc) {
        pdc.set(HOME_X, PersistentDataType.DOUBLE, home.getX());
        pdc.set(HOME_Y, PersistentDataType.DOUBLE, home.getY());
        pdc.set(HOME_Z, PersistentDataType.DOUBLE, home.getZ());
        pdc.set(STEAM_KEY, PersistentDataType.DOUBLE, steam);
        if (boundChamber != null) {
            pdc.set(BOUND_X, PersistentDataType.DOUBLE, boundChamber.getX());
            pdc.set(BOUND_Y, PersistentDataType.DOUBLE, boundChamber.getY());
            pdc.set(BOUND_Z, PersistentDataType.DOUBLE, boundChamber.getZ());
        }
        pdc.set(JOB_KEY, PersistentDataType.STRING, robotType.name());
        if (terminalId != null) {
            pdc.set(TERMINAL_KEY, PersistentDataType.STRING, terminalId.toString());
        } else {
            pdc.remove(TERMINAL_KEY);
        }
        writeLoc(pdc, haulSource, HAUL_SRC_X, HAUL_SRC_Y, HAUL_SRC_Z);
        writeLoc(pdc, haulTarget, HAUL_TGT_X, HAUL_TGT_Y, HAUL_TGT_Z);
        if (!carrying.isEmpty()) {
            pdc.set(HAUL_CARRY, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(carrying));
        } else {
            pdc.remove(HAUL_CARRY);
        }
        if (!saplingSlot.isEmpty()) {
            pdc.set(SAPLING_KEY, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(saplingSlot));
        } else {
            pdc.remove(SAPLING_KEY);
        }
    }

    private static void writeLoc(@NotNull PersistentDataContainer pdc, @Nullable Location loc,
                                 @NotNull NamespacedKey kx, @NotNull NamespacedKey ky, @NotNull NamespacedKey kz) {
        if (loc == null) return;
        pdc.set(kx, PersistentDataType.DOUBLE, loc.getX());
        pdc.set(ky, PersistentDataType.DOUBLE, loc.getY());
        pdc.set(kz, PersistentDataType.DOUBLE, loc.getZ());
    }

    // ===== 终端归属 =====

    public @NotNull RobotType getRobotType() { return robotType; }
    public @Nullable java.util.UUID getTerminalId() { return terminalId; }

    /** 绑定到指定终端（部署时 / 认领时调用）。 */
    public void bindTerminal(@NotNull java.util.UUID id) { this.terminalId = id; }

    /** 解绑终端（召回时调用）。 */
    public void unbindTerminal() { this.terminalId = null; }

    /** 获取归属终端实例（区块未加载 / 终端已破坏 → null）。 */
    @Nullable RobotControlTerminal terminal() {
        return terminalId != null ? RobotControlTerminal.forTerminal(terminalId) : null;
    }

    // ===== 蒸汽燃料 / 充能 =====

    public double getSteam()         { return steam; }
    public double getSteamCapacity() { return steamCapacity; }

    /** 充入蒸汽（封顶到容量），返回实际充入量。供蒸汽充汽舱调用。巡逻机器人充汽时同时回满血。 */
    public double addSteam(double amount) {
        if (amount <= 0.0) return 0.0;
        double added = Math.min(amount, steamCapacity - steam);
        steam += added;
        CopperGolem entity = getEntity();
        if (robotType == RobotType.PATROL && entity != null && entity.getHealth() < entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
        return added;
    }

    /**
     * 被某台充汽舱充能时调用：自动绑定该舱（核心方块中心位置），低汽时回到此处充能。
     * 仅当绑定目标变化时更新。
     */
    public void bindChamber(@NotNull Location chamberCore) {
        if (boundChamber == null
                || boundChamber.getWorld() != chamberCore.getWorld()
                || boundChamber.distanceSquared(chamberCore) > 0.01) {
            this.boundChamber = chamberCore.clone();
        }
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        return WailaDisplay.of(
                        Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA))
                .add(jobLabel().color(NamedTextColor.GOLD))
                .add(activityLabel())
                .add(ProgressBar.fluidContents(SteamworkFluids.PRESSURIZED_STEAM, steamCapacity, steam));
    }

    /** 当前活动状态的可翻译名（带颜色）。 */
    private @NotNull Component activityLabel() {
        return Component.translatable("steamwork.gui.steam_robot.activity." + activity.name().toLowerCase())
                .color(activityColor(activity));
    }

    private static @NotNull NamedTextColor activityColor(@NotNull Activity a) {
        return switch (a) {
            case NO_STEAM, HAUL_UNBOUND -> NamedTextColor.RED;
            case CHARGING, APPROACHING, CLEARING, HAUL_PICKUP, HAUL_DELIVER, DELIVERING, HUNTING -> NamedTextColor.YELLOW;
            case WORKING, PATROLLING, HAUL_LOAD, HAUL_UNLOAD, DEPOSITING, PICKING, HARVESTING, PLANTING, ATTACKING -> NamedTextColor.GREEN;
            case SEARCHING -> NamedTextColor.AQUA;
            case IDLE, RETURNING -> NamedTextColor.GRAY;
        };
    }

    /** 当前任务模式的可翻译名。 */
    private @NotNull Component jobLabel() {
        return Component.translatable("steamwork.gui.steam_robot.job." + robotType.name().toLowerCase());
    }

    /** 构建头顶名牌：「XX机器人 - 工作状态」，带 text shadow、透明背景。 */
    private @NotNull Component buildNameTag() {
        return Component.translatable("steamwork.gui.steam_robot.job." + robotType.name().toLowerCase())
                .color(NamedTextColor.AQUA)
                .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                .append(activityLabel())
                .shadowColor(net.kyori.adventure.text.format.ShadowColor.shadowColor(63, 63, 63, 255));
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    // ===== 控制 GUI =====

    private @Nullable StatusItem statusItem;
    private @Nullable BindButton sourceBindButton, targetBindButton;
    private CarryingSlot @Nullable [] carryingSlots;
    private @Nullable SaplingSlotItem saplingSlotItem;

    /** 打开控制 GUI（由 {@link SteamRobotInteractGuard} 调用）。 */
    void openGui(@NotNull Player player) {
        Window.builder()
                .setUpperGui(buildGui())
                .setTitle(ni(Component.translatable("steamwork.item.steam_robot.name")))
                .setViewer(player)
                .build()
                .open();
    }

    private static final char[] SLOT_CHARS = "abcdefghi".toCharArray();

    private @NotNull Gui buildGui() {
        statusItem = new StatusItem();
        if (robotType == RobotType.HAUL) {
            sourceBindButton = new BindButton(true);
            targetBindButton = new BindButton(false);
            carryingSlots = new CarryingSlot[haulCapacity];
            var b = Gui.builder()
                    .setStructure(
                            "# # # # s # # # #",
                            "a b c d e f g h i",
                            "# # # U # D # # #")
                    .addIngredient('#', GuiItems.background())
                    .addIngredient('s', statusItem)
                    .addIngredient('U', sourceBindButton)
                    .addIngredient('D', targetBindButton);
            for (int i = 0; i < haulCapacity; i++) {
                carryingSlots[i] = new CarryingSlot(i);
                b.addIngredient(SLOT_CHARS[i], carryingSlots[i]);
            }
            return b.build();
        }
        carryingSlots = new CarryingSlot[inventoryCapacity];
        if (robotType == RobotType.CHOP || robotType == RobotType.FARM) {
            saplingSlotItem = new SaplingSlotItem();
            var b = Gui.builder()
                    .setStructure(
                            "# # # # s # # # #",
                            "# a b c d e f g #",
                            "# # # # p # # # #")
                    .addIngredient('#', GuiItems.background())
                    .addIngredient('s', statusItem)
                    .addIngredient('p', saplingSlotItem);
            for (int i = 0; i < inventoryCapacity; i++) {
                carryingSlots[i] = new CarryingSlot(i);
                b.addIngredient(SLOT_CHARS[i], carryingSlots[i]);
            }
            return b.build();
        }
        var b = Gui.builder()
                .setStructure(
                        "# # # # s # # # #",
                        "# a b c d e f g #",
                        "# # # # # # # # #")
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', statusItem);
        for (int i = 0; i < inventoryCapacity; i++) {
            carryingSlots[i] = new CarryingSlot(i);
            b.addIngredient(SLOT_CHARS[i], carryingSlots[i]);
        }
        return b.build();
    }

    private void refreshGuiItems() {
        if (statusItem != null) statusItem.notifyWindows();
        if (sourceBindButton != null) sourceBindButton.notifyWindows();
        if (targetBindButton != null) targetBindButton.notifyWindows();
        if (carryingSlots != null) for (CarryingSlot cs : carryingSlots) cs.notifyWindows();
        if (saplingSlotItem != null) saplingSlotItem.notifyWindows();
    }

    /** 把某玩家点选的方块绑定为搬运取货点（容器）/ 送货点（容器或产线入口）。 */
    private void bindHaulPoint(@NotNull Player player, @NotNull Block block, boolean source) {
        // 起点与终点不能是同一格（否则原地空转）
        Location other = source ? haulTarget : haulSource;
        if (other != null && sameBlock(other, block)) {
            player.sendActionBar(Component.translatable("steamwork.message.steam_robot.haul_same_point")
                    .color(NamedTextColor.RED));
            return;
        }
        if (source) {
            if (!(block.getState() instanceof Container)) {
                player.sendActionBar(Component.translatable("steamwork.message.steam_robot.haul_source_invalid")
                        .color(NamedTextColor.RED));
                return;
            }
            haulSource = block.getLocation().toCenterLocation();
            player.sendActionBar(Component.translatable("steamwork.message.steam_robot.haul_source_set")
                    .color(NamedTextColor.GREEN));
        } else {
            boolean ok = block.getState() instanceof Container
                    || PneumaticEndpointSupport.loadedRebarBlock(block) instanceof ProductionLineInlet;
            if (!ok) {
                player.sendActionBar(Component.translatable("steamwork.message.steam_robot.haul_target_invalid")
                        .color(NamedTextColor.RED));
                return;
            }
            haulTarget = block.getLocation().toCenterLocation();
            player.sendActionBar(Component.translatable("steamwork.message.steam_robot.haul_target_set")
                    .color(NamedTextColor.GREEN));
        }
        getEntity().getWorld().playSound(getEntity().getLocation(),
                Sound.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.6f, 1.4f);
    }

    /** 某绑定坐标是否与给定方块同格。 */
    private static boolean sameBlock(@NotNull Location loc, @NotNull Block block) {
        return loc.getWorld() == block.getWorld()
                && loc.getBlockX() == block.getX()
                && loc.getBlockY() == block.getY()
                && loc.getBlockZ() == block.getZ();
    }

    /** 取货点 / 送货点绑定按钮：点击后关界面，下一次右键方块完成绑定。 */
    private final class BindButton extends AbstractItem {
        private final boolean source;
        BindButton(boolean source) { this.source = source; }

        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            Location bound = source ? haulSource : haulTarget;
            Material icon = source ? Material.HOPPER : Material.DROPPER;
            Component name = Component.translatable(source
                    ? "steamwork.gui.steam_robot.haul_set_source"
                    : "steamwork.gui.steam_robot.haul_set_target").color(NamedTextColor.AQUA);
            Component status = (bound == null)
                    ? Component.translatable("steamwork.gui.steam_robot.haul_unbound").color(NamedTextColor.RED)
                    : Component.text("(" + bound.getBlockX() + ", " + bound.getBlockY()
                            + ", " + bound.getBlockZ() + ")").color(NamedTextColor.GREEN);
            return ItemStackBuilder.of(icon)
                    .name(ni(name))
                    .lore(List.of(ni(status),
                            ni(Component.translatable("steamwork.gui.steam_robot.haul_bind_hint")
                                    .color(NamedTextColor.DARK_GRAY))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            p.closeInventory();
            p.sendActionBar(Component.translatable(source
                    ? "steamwork.message.steam_robot.haul_prompt_source"
                    : "steamwork.message.steam_robot.haul_prompt_target").color(NamedTextColor.YELLOW));
            SteamworkBlockPrompt.await(p, block -> bindHaulPoint(p, block, source));
        }
    }

    /** 状态项：显示蒸汽量与当前状态（仅展示）。 */
    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            return ItemStackBuilder.of(Material.COPPER_BLOCK)
                    .name(ni(Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA)))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.steam_robot.steam",
                                    RebarArgument.of("cur", String.valueOf((int) steam)),
                                    RebarArgument.of("max", String.valueOf((int) steamCapacity)))
                                    .color(NamedTextColor.GRAY)),
                            ni(activityLabel())
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            refreshGuiItems();
        }
    }

    /** 库存槽位：只读显示 carrying 列表中对应索引的物品。 */
    private final class CarryingSlot extends AbstractItem {
        private final int index;
        CarryingSlot(int index) { this.index = index; }

        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            if (index < carrying.size()) {
                ItemStack item = carrying.get(index);
                if (item != null && !item.isEmpty()) return ItemStackBuilder.of(item);
            }
            return ItemStackBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name(ni(Component.text(" ")));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            refreshGuiItems();
        }
    }

    /** 树苗槽：可交互，左键放入树苗，右键取出。 */
    private final class SaplingSlotItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            if (!saplingSlot.isEmpty()) {
                ItemStack stack = saplingSlot.getFirst();
                return ItemStackBuilder.of(stack);
            }
            String slotKey = (robotType == RobotType.FARM)
                    ? "steamwork.gui.steam_robot.seed_slot"
                    : "steamwork.gui.steam_robot.sapling_slot";
            String hintKey = (robotType == RobotType.FARM)
                    ? "steamwork.gui.steam_robot.seed_slot_hint"
                    : "steamwork.gui.steam_robot.sapling_slot_hint";
            return ItemStackBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                    .name(ni(Component.translatable(slotKey)
                            .color(NamedTextColor.GREEN)))
                    .lore(List.of(ni(Component.translatable(hintKey)
                            .color(NamedTextColor.GRAY))));
        }

        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            if (t == ClickType.RIGHT || t == ClickType.SHIFT_RIGHT) {
                // 取出：返还到玩家背包
                if (!saplingSlot.isEmpty()) {
                    ItemStack give = saplingSlot.removeFirst();
                    var leftover = p.getInventory().addItem(give);
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                    }
                }
            } else {
                // 放入：优先从光标取，其次从主手取
                ItemStack cursor = p.getItemOnCursor();
                boolean fromCursor = !cursor.isEmpty() && isSaplingMaterial(cursor.getType());
                ItemStack source = fromCursor ? cursor : p.getInventory().getItemInMainHand();
                if (source.isEmpty() || !isSaplingMaterial(source.getType())) { refreshGuiItems(); return; }
                if (saplingSlot.size() >= saplingSlotCapacity) {
                    // 槽已满：尝试合并
                    ItemStack existing = saplingSlot.getFirst();
                    if (existing.isSimilar(source)) {
                        int space = existing.getMaxStackSize() - existing.getAmount();
                        if (space > 0) {
                            int transfer = Math.min(space, source.getAmount());
                            existing.setAmount(existing.getAmount() + transfer);
                            source.setAmount(source.getAmount() - transfer);
                            if (source.getAmount() <= 0) {
                                if (fromCursor) p.setItemOnCursor(null);
                                else p.getInventory().setItemInMainHand(null);
                            }
                        }
                    }
                } else {
                    saplingSlot.add(source.clone());
                    if (fromCursor) p.setItemOnCursor(null);
                    else p.getInventory().setItemInMainHand(null);
                }
            }
            refreshGuiItems();
        }
    }

    private static boolean isSaplingMaterial(@NotNull Material m) {
        String name = m.name();
        return name.endsWith("_SAPLING") || name.endsWith("_FUNGUS") || name.endsWith("_PROPAGULE")
                || name.endsWith("_SEEDS") || m == Material.CARROT || m == Material.POTATO
                || m == Material.PITCHER_POD;
    }

    /** 拆除机器人并掉回部署物品（由 {@link SteamRobotInteractGuard} 调用）。 */
    void dismantle(@NotNull Player player) {
        resetWork(); // 抹掉残留破坏裂纹
        CopperGolem golem = getEntity();
        Location loc = golem.getLocation();
        World world = golem.getWorld();

        dropCarrying(loc);
        // 掉落树苗槽内容
        for (ItemStack sap : saplingSlot) {
            world.dropItemNaturally(loc, sap);
        }
        saplingSlot.clear();

        // 回收：非创造模式掉回一个部署物品
        if (player.getGameMode() != GameMode.CREATIVE) {
            world.dropItemNaturally(loc, robotItemForType(robotType).clone());
        }
        // 拆除特效
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.8, 0), 12, 0.3, 0.4, 0.3, 0.02);
        world.playSound(loc, Sound.ENTITY_COPPER_GOLEM_DEATH, SoundCategory.BLOCKS, 0.8f, 1.2f);

        golem.remove(); // 移出世界 → EntityStorage 自动注销
    }

    static @NotNull ItemStack robotItemForType(@NotNull RobotType type) {
        return switch (type) {
            case MINE    -> SteamworkItems.MINING_ROBOT;
            case CHOP    -> SteamworkItems.LUMBER_ROBOT;
            case HAUL    -> SteamworkItems.HAUL_ROBOT;
            case PATROL  -> SteamworkItems.PATROL_ROBOT;
            case PICK    -> SteamworkItems.PICKER_ROBOT;
            case FARM    -> SteamworkItems.FARMER_ROBOT;
            case BUTCHER -> SteamworkItems.BUTCHER_ROBOT;
        };
    }

    void dropCarrying(@NotNull Location loc) {
        World world = loc.getWorld();
        for (ItemStack carried : carrying) {
            if (carried != null && !carried.isEmpty()) world.dropItemNaturally(loc, carried);
        }
        carrying.clear();
    }
}
