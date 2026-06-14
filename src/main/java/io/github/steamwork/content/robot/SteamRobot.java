package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.EntityStorage;
import io.github.pylonmc.rebar.entity.RebarEntity;
import io.github.pylonmc.rebar.entity.interfaces.InteractRebarEntityHandler;
import io.github.pylonmc.rebar.entity.interfaces.TickingRebarEntity;
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.util.gui.GuiItems;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.content.machines.SteamChargingChamber;
import io.github.steamwork.util.PneumaticEndpointSupport;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
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
 * <p><b>P1c 任务模式</b>：{@link JobMode}（巡逻/采矿/砍树），非潜行右键循环切换、持久化。</p>
 *
 * <p><b>P2 采矿/砍树</b>：在 {@code work-radius} 工作区内找最近的<b>原版</b>矿石/原木目标（绝不破坏
 * 任何 Rebar 方块），寻路过去用对应工具取正确掉落、移除方块、回收到 home 旁容器（放不下则散落），
 * 每次破坏额外耗汽 {@code steam-per-break}。</p>
 *
 * <p>后续阶段（见 {@code docs/design/steam-robot.md}）：补料/搬运、Pylon 自定义方块破坏、
 * 完整 GUI、铜傀儡放下动画等。</p>
 */
public class SteamRobot extends RebarEntity<CopperGolem> implements
        TickingRebarEntity, InteractRebarEntityHandler {

    /** 任务模式：巡逻 / 采矿 / 砍树。非潜行右键循环切换。 */
    public enum JobMode { PATROL, MINE, CHOP }

    // 作业取掉落用的工具（无附魔，保证矿石/原木掉落正确、无 Fortune/精准采集）
    private static final ItemStack MINE_TOOL = new ItemStack(Material.NETHERITE_PICKAXE);
    private static final ItemStack CHOP_TOOL = new ItemStack(Material.NETHERITE_AXE);
    // 找投放箱时扫描的方向（home 方块自身 + 六面）
    private static final BlockFace[] CHEST_FACES = {
        BlockFace.SELF, BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    /** 单个目标方块寻路失败的放弃阈值（决策次数）：避免卡在不可达目标上。 */
    private static final int TARGET_GIVE_UP = 40;
    /** 单次锁定的连通簇（整棵树 / 整条矿脉）最大方块数。 */
    private static final int CLUSTER_CAP = 512;
    /** 工作区扫不到目标后，待命多少次决策再重新扫描（避免空扫大立方体每 tick 跑）。 */
    private static final int SCAN_IDLE_COOLDOWN = 10;

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

    // ===== 设置（settings/steam_robot.yml，缺省时用默认值，避免缺配置崩溃）=====
    private final int    tickInterval  = getSetting("tick-interval",  ConfigAdapter.INTEGER, 10);
    private final double wanderRadius  = getSetting("wander-radius",  ConfigAdapter.DOUBLE,  6.0);
    private final double moveSpeed     = getSetting("move-speed",     ConfigAdapter.DOUBLE,  1.0);
    private final double steamCapacity = getSetting("steam-capacity", ConfigAdapter.DOUBLE,  2000.0);
    private final double steamPerTick  = getSetting("steam-per-tick", ConfigAdapter.DOUBLE,  10.0);
    /** 蒸汽低于容量该比例时回绑定充汽舱充能。 */
    private final double rechargeAt    = getSetting("recharge-at",    ConfigAdapter.DOUBLE,  0.25);
    /** 作业工作区半径（以 home 为中心的立方体半边长，格）。 */
    private final int    workRadius    = getSetting("work-radius",    ConfigAdapter.INTEGER, 12);
    /** 每破坏一个方块额外耗汽（mB）。 */
    private final double steamPerBreak = getSetting("steam-per-break", ConfigAdapter.DOUBLE, 30.0);
    /** 破坏一个方块需累积的决策次数（每次决策约 tick-interval 游戏刻），≥1，避免秒砍。 */
    private final int    breakTicks    = Math.max(1, getSetting("break-ticks", ConfigAdapter.INTEGER, 4));

    // ===== 状态 =====
    private @NotNull Location home;
    private @Nullable Location target = null;
    /** 距上次重新选点经过的决策次数，超过阈值即强制重选，避免目标不可达时永久卡住。 */
    private int ticksSinceRetarget = 0;
    /** 当前蒸汽储量（mB）。 */
    private double steam;
    /** 绑定的充汽舱核心位置（首次被某舱充能时自动绑定）；null = 未绑定。 */
    private @Nullable Location boundChamber = null;
    /** 是否正在回充汽舱充能途中（避免在路上耗汽、防抖）。 */
    private boolean seekingCharge = false;
    /** 当前任务模式。 */
    private @NotNull JobMode jobMode = JobMode.PATROL;
    /** 当前正在破坏的方块（来自锁定簇），瞬态；重载后重新扫描。 */
    private @Nullable Block targetBlock = null;
    /** 接近阶段寻路尝试计数（超过 {@link #TARGET_GIVE_UP} 放弃，避免卡死）。 */
    private int approachAge = 0;
    /** 当前方块已累积的破坏进度（决策次数），达到 {@link #breakTicks} 才真正破坏（非秒砍）。 */
    private int breakingProgress = 0;
    /** 锁定的连通簇（整棵树 / 整条矿脉）中尚未破坏的方块；瞬态。 */
    private final java.util.List<Block> lockedCluster = new java.util.ArrayList<>();
    /** 空扫冷却剩余决策次数（>0 时跳过扫描、原地待命）。 */
    private int scanCooldown = 0;
    /** 不可达 / 被保护插件拦截的方块（瞬态跳过集）。 */
    private final java.util.Set<Block> blockedTargets = new java.util.HashSet<>();

    /** 新生成：写入 rebar 实体 key 并记录部署点。 */
    public SteamRobot(@NotNull CopperGolem golem, @NotNull Location home) {
        super(SteamworkKeys.STEAM_ROBOT, golem);
        this.home = home.clone();
        this.steam = steamCapacity; // 新部署：满汽
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
            try { this.jobMode = JobMode.valueOf(job); } catch (IllegalArgumentException ignored) {}
        }
        configure();
    }

    /** 在指定位置生成一台机器人并登记到 {@link EntityStorage}。 */
    public static @NotNull SteamRobot spawn(@NotNull Location loc) {
        World world = loc.getWorld();
        CopperGolem golem = world.spawn(loc, CopperGolem.class, g -> {
            g.setPersistent(true);
            g.setRemoveWhenFarAway(false);
        });
        SteamRobot robot = new SteamRobot(golem, loc);
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
        golem.customName(Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA));
        golem.setCustomNameVisible(true);
        updateHeldTool();
    }

    /**
     * 反射清空铜傀儡的 brain（行为 + 记忆），使其无自主动作但导航仍可用。
     * 运行时为 mojang 映射（Paper/Purpur）：CraftMob.getHandle() → Mob.getBrain()
     * → Brain.removeAllBehaviors()/clearMemories()。失败仅告警、不致命。
     */
    private void neutralizeBrain(@NotNull CopperGolem golem) {
        try {
            Object nmsMob = golem.getClass().getMethod("getHandle").invoke(golem);
            Object brain  = nmsMob.getClass().getMethod("getBrain").invoke(nmsMob);
            brain.getClass().getMethod("removeAllBehaviors").invoke(brain);
            brain.getClass().getMethod("clearMemories").invoke(brain);
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
        ItemStack held = switch (jobMode) {
            case MINE -> new ItemStack(Material.IRON_PICKAXE);
            case CHOP -> new ItemStack(Material.IRON_AXE);
            case PATROL -> null;
        };
        eq.setItemInMainHand(held);
        eq.setItemInMainHandDropChance(0f);
    }

    @Override
    public void tick() {
        CopperGolem golem = getEntity();
        if (!golem.isValid()) return;

        // ── 回充模式：低汽且有可用绑定充汽舱 → 走过去站定等充（途中不耗汽）──
        if (seekingCharge) {
            if (steam >= steamCapacity || boundChamber == null || !chamberLoaded(boundChamber)) {
                seekingCharge = false; // 充满 / 舱失效 → 退出回充
            } else {
                navigateToChamber(golem);
                return;
            }
        } else if (steam < steamCapacity * rechargeAt
                && boundChamber != null && chamberLoaded(boundChamber)) {
            seekingCharge = true;
            resetWork();   // 放弃当前作业，充满回来后重新接近目标（避免远程采伐）
            navigateToChamber(golem);
            return;
        }

        // ── 缺汽停摆（无可用充汽舱）──
        if (steam <= 0.0) {
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            target = null;
            golem.setGolemState(CopperGolem.State.IDLE);
            return;
        }

        // ── 作业（按任务模式）──
        boolean active;
        switch (jobMode) {
            case MINE, CHOP -> active = mineStep(golem, jobMode);
            default         -> { patrolStep(golem); active = true; }
        }

        // 行动耗汽（仅在实际行动/作业时扣；待命不耗汽）。破坏的额外耗汽在 breakTarget 内单独扣
        if (active) steam = Math.max(0.0, steam - steamPerTick);
    }

    /** 巡逻：在部署点周围随机选点游走。 */
    private void patrolStep(@NotNull CopperGolem golem) {
        setState(golem, CopperGolem.State.IDLE);
        Location loc = golem.getLocation();
        ticksSinceRetarget++;

        boolean reached = target != null
                && loc.getWorld() == target.getWorld()
                && loc.distanceSquared(target) < 2.25;

        if (target == null || reached || ticksSinceRetarget > 6) {
            target = pickWanderTarget(golem);
            golem.getPathfinder().moveTo(target, moveSpeed);
            // 蒸汽喷气：行动可视信号
            golem.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.0, 0),
                    6, 0.2, 0.3, 0.2, 0.01);
            ticksSinceRetarget = 0;
        }
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
    private boolean mineStep(@NotNull CopperGolem golem, @NotNull JobMode mode) {
        // 采伐阶段：已锁定一棵树 / 一条矿脉
        if (targetBlock != null || !lockedCluster.isEmpty()) {
            harvestStep(golem, mode);
            return true;
        }
        // 空扫冷却：上次没扫到目标，等几次决策再扫，避免空扫大立方体每 tick 跑
        if (scanCooldown > 0) {
            scanCooldown--;
            setState(golem, CopperGolem.State.IDLE);
            return false;
        }
        // 接近阶段：扫描工作区找最近目标
        Block start = findNearestTarget(golem, mode);
        if (start == null) {
            // 工作区无目标：原地待命，等待目标出现（不游荡、不耗汽）
            scanCooldown = SCAN_IDLE_COOLDOWN;
            setState(golem, CopperGolem.State.IDLE);
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            target = null;
            return false;
        }
        setState(golem, CopperGolem.State.IDLE);          // 接近途中：非作业姿态
        Location center = start.getLocation().toCenterLocation();
        if (golem.getLocation().distanceSquared(center) <= 9.0) {
            lockCluster(start, mode);                     // 够近 → 锁定整簇，进入采伐
            approachAge = 0;
        } else if (++approachAge > TARGET_GIVE_UP) {      // 够不到 → 暂时拉黑，换一个
            blacklist(start);
            approachAge = 0;
        } else if (approachAge % 8 == 1 || !golem.getPathfinder().hasPath()) {
            golem.getPathfinder().moveTo(center, moveSpeed);
        }
        return true;
    }

    /** 采伐：逐个破坏锁定簇里的方块，每个需累积破坏进度（非秒砍）。 */
    private void harvestStep(@NotNull CopperGolem golem, @NotNull JobMode mode) {
        World world = golem.getWorld();

        // 当前目标失效 → 清裂纹换下一个
        if (targetBlock != null && !isTarget(targetBlock, mode)) {
            sendCrack(targetBlock, 0.0f);
            targetBlock = null;
        }
        if (targetBlock == null) {
            targetBlock = pollNextClusterBlock(mode);
            breakingProgress = 0;
            if (targetBlock == null) {                    // 簇清空 → 回接近阶段
                lockedCluster.clear();
                return;
            }
        }

        // 累积破坏进度：挥臂 + 裂纹 + 碎屑
        golem.swingMainHand();
        setState(golem, CopperGolem.State.GETTING_ITEM);
        breakingProgress++;
        float progress = Math.min(1.0f, (float) breakingProgress / breakTicks);
        sendCrack(targetBlock, progress);
        world.spawnParticle(Particle.BLOCK, targetBlock.getLocation().toCenterLocation(),
                3, 0.2, 0.2, 0.2, targetBlock.getBlockData());

        if (breakingProgress >= breakTicks) {
            sendCrack(targetBlock, 0.0f);                 // 清裂纹
            breakTarget(golem, mode);                     // 实际移除 + 掉落 + 音效 + 耗汽
            targetBlock = null;
            breakingProgress = 0;
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

    /** 从起点 flood-fill（26 邻接）连通的同类目标，锁定为一棵树 / 一条矿脉。 */
    private void lockCluster(@NotNull Block start, @NotNull JobMode mode) {
        lockedCluster.clear();
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
                        if (visited.add(nb) && isTarget(nb, mode)) queue.add(nb);
                    }
                }
            }
        }
    }

    /** 取锁定簇中离机器人最近、仍有效的方块并移除；顺手剔除失效项；无则 null。 */
    private @Nullable Block pollNextClusterBlock(@NotNull JobMode mode) {
        Location gl = getEntity().getLocation();
        Block best = null;
        double bestSq = Double.MAX_VALUE;
        java.util.Iterator<Block> it = lockedCluster.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (blockedTargets.contains(b) || !isTarget(b, mode)) { it.remove(); continue; }
            double sq = b.getLocation().toCenterLocation().distanceSquared(gl);
            if (sq < bestSq) { bestSq = sq; best = b; }
        }
        if (best != null) lockedCluster.remove(best);
        return best;
    }

    private void blacklist(@NotNull Block b) {
        if (blockedTargets.size() > 512) blockedTargets.clear();
        blockedTargets.add(b);
    }

    /** 清空当前作业状态（切换模式 / 拆除时调用），并抹掉残留裂纹。 */
    private void resetWork() {
        if (targetBlock != null) {
            sendCrack(targetBlock, 0.0f);
        }
        targetBlock = null;
        breakingProgress = 0;
        approachAge = 0;
        scanCooldown = 0;
        lockedCluster.clear();
    }

    /** 破坏目标方块：取正确掉落 → 移除 → 特效 → 回收到投放箱（放不下则散落）→ 破坏耗汽。 */
    private void breakTarget(@NotNull CopperGolem golem, @NotNull JobMode mode) {
        Block b = targetBlock;
        if (b == null) return;
        World world = b.getWorld();
        Material broken = b.getType();

        // 让保护类插件可拦截（以「实体改变方块」的形式）；被拦截则记入跳过集、不破坏
        EntityChangeBlockEvent ev = new EntityChangeBlockEvent(golem, b, Material.AIR.createBlockData());
        if (!ev.callEvent()) {
            if (blockedTargets.size() > 512) blockedTargets.clear();
            blockedTargets.add(b);
            return;
        }

        Collection<ItemStack> drops = b.getDrops(mode == JobMode.CHOP ? CHOP_TOOL : MINE_TOOL);

        // 破坏特效（音 + 方块碎屑）；挥臂动画在 harvestStep 每 tick 已做
        world.playSound(b.getLocation().toCenterLocation(),
                broken.createBlockData().getSoundGroup().getBreakSound(), 1.0f, 0.9f);
        world.spawnParticle(Particle.BLOCK, b.getLocation().toCenterLocation(),
                20, 0.3, 0.3, 0.3, broken.createBlockData());
        b.setType(Material.AIR);

        depositOrDrop(drops, b.getLocation());
        steam = Math.max(0.0, steam - steamPerBreak);
    }

    /** 掉落优先放入 home 旁的容器，放不下则在破坏位置散落（不丢失）。 */
    private void depositOrDrop(@NotNull Collection<ItemStack> drops, @NotNull Location at) {
        Inventory chest = findHomeContainer();
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            if (chest != null) {
                var overflow = chest.addItem(drop);
                if (overflow.isEmpty()) continue;
                for (ItemStack leftover : overflow.values()) {
                    at.getWorld().dropItemNaturally(at.toCenterLocation(), leftover);
                }
            } else {
                at.getWorld().dropItemNaturally(at.toCenterLocation(), drop);
            }
        }
    }

    /** home 方块自身或六面相邻的第一个容器（箱子/木桶等）的库存；无则 null。 */
    private @Nullable Inventory findHomeContainer() {
        Block homeBlock = home.getBlock();
        for (BlockFace face : CHEST_FACES) {
            if (homeBlock.getRelative(face).getState() instanceof Container c) {
                return c.getInventory();
            }
        }
        return null;
    }

    /** 在工作区（home ±workRadius 立方体）内找离机器人最近的目标方块。 */
    private @Nullable Block findNearestTarget(@NotNull CopperGolem golem, @NotNull JobMode mode) {
        Block homeBlock = home.getBlock();
        Location golemLoc = golem.getLocation();
        Block best = null;
        double bestSq = Double.MAX_VALUE;
        for (int dx = -workRadius; dx <= workRadius; dx++) {
            for (int dy = -workRadius; dy <= workRadius; dy++) {
                for (int dz = -workRadius; dz <= workRadius; dz++) {
                    Block b = homeBlock.getRelative(dx, dy, dz);
                    if (blockedTargets.contains(b) || !isTarget(b, mode)) continue;
                    double sq = b.getLocation().toCenterLocation().distanceSquared(golemLoc);
                    if (sq < bestSq) { bestSq = sq; best = b; }
                }
            }
        }
        return best;
    }

    /** 是否为当前模式的可作业目标：先匹配原版材质，再排除一切 Rebar 方块（机器绝不破坏）。 */
    private boolean isTarget(@NotNull Block b, @NotNull JobMode mode) {
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
    private @NotNull Location pickWanderTarget(@NotNull CopperGolem golem) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double x = home.getX() + (rng.nextDouble() * 2 - 1) * wanderRadius;
        double z = home.getZ() + (rng.nextDouble() * 2 - 1) * wanderRadius;
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
        pdc.set(JOB_KEY, PersistentDataType.STRING, jobMode.name());
    }

    // ===== 蒸汽燃料 / 充能 =====

    public double getSteam()         { return steam; }
    public double getSteamCapacity() { return steamCapacity; }

    /** 充入蒸汽（封顶到容量），返回实际充入量。供蒸汽充汽舱调用。 */
    public double addSteam(double amount) {
        if (amount <= 0.0) return 0.0;
        double added = Math.min(amount, steamCapacity - steam);
        steam += added;
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
        Component state;
        if (seekingCharge) {
            state = Component.translatable("steamwork.gui.steam_robot.state.charging").color(NamedTextColor.YELLOW);
        } else if (steam > 0.0) {
            state = Component.translatable("steamwork.gui.steam_robot.state.running").color(NamedTextColor.GREEN);
        } else {
            state = Component.translatable("steamwork.gui.steam_robot.state.no_steam").color(NamedTextColor.RED);
        }
        return WailaDisplay.of(
                        Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA))
                .add(jobLabel().color(NamedTextColor.GOLD))
                .add(state)
                .add(ProgressBar.fluidContents(SteamworkFluids.PRESSURIZED_STEAM, steamCapacity, steam));
    }

    /**
     * 右键机器人：潜行 → 拆除并掉回部署物品；非潜行 → 打开控制 GUI。
     *
     * <p>拆除时 {@code golem.remove()} 触发 {@code EntityRemoveFromWorldEvent}，Rebar 会自动把本实体
     * 从 {@link EntityStorage} 注销，无需手动清理。</p>
     */
    @Override
    @MultiHandler(priorities = {EventPriority.NORMAL})
    public void onInteractedWith(@NotNull PlayerInteractEntityEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.NORMAL) return;
        // 两手都取消：否则副手交互会漏到原版，被当作"拿走铜傀儡手持物"取下工具
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;   // 仅主手触发我们的逻辑（避免双触发）
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            dismantle(player);
        } else {
            openGui(player);
        }
    }

    /** 设置任务模式：作废旧目标、更新手持工具、刷新已开界面。 */
    private void setJob(@NotNull JobMode mode) {
        if (jobMode == mode) return;
        jobMode = mode;
        resetWork();
        blockedTargets.clear();
        target = null;
        updateHeldTool();
        getEntity().getWorld().playSound(getEntity().getLocation(),
                Sound.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.6f, 1.4f);
        refreshGuiItems();
    }

    /** 当前任务模式的可翻译名。 */
    private @NotNull Component jobLabel() {
        return Component.translatable("steamwork.gui.steam_robot.job." + jobMode.name().toLowerCase());
    }

    private static @NotNull Component ni(@NotNull Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    // ===== 控制 GUI =====

    private @Nullable StatusItem statusItem;
    private JobButton @Nullable [] jobButtons;

    private void openGui(@NotNull Player player) {
        Window.builder()
                .setUpperGui(buildGui())
                .setTitle(ni(Component.translatable("steamwork.item.steam_robot.name")))
                .setViewer(player)
                .build()
                .open();
    }

    private @NotNull Gui buildGui() {
        statusItem = new StatusItem();
        jobButtons = new JobButton[]{
                new JobButton(JobMode.PATROL), new JobButton(JobMode.MINE), new JobButton(JobMode.CHOP)
        };
        return Gui.builder()
                .setStructure(
                        "# # # # # # # # #",
                        "# s # P # M # C #",
                        "# # # # # # # # #")
                .addIngredient('#', GuiItems.background())
                .addIngredient('s', statusItem)
                .addIngredient('P', jobButtons[0])
                .addIngredient('M', jobButtons[1])
                .addIngredient('C', jobButtons[2])
                .build();
    }

    private void refreshGuiItems() {
        if (statusItem != null) statusItem.notifyWindows();
        if (jobButtons != null) for (JobButton b : jobButtons) b.notifyWindows();
    }

    /** 状态项：显示蒸汽量与当前状态（仅展示）。 */
    private final class StatusItem extends AbstractItem {
        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            String stateKey = seekingCharge ? "charging" : (steam > 0 ? "running" : "no_steam");
            return ItemStackBuilder.of(Material.COPPER_BLOCK)
                    .name(ni(Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA)))
                    .lore(List.of(
                            ni(Component.translatable("steamwork.gui.steam_robot.steam",
                                    RebarArgument.of("cur", String.valueOf((int) steam)),
                                    RebarArgument.of("max", String.valueOf((int) steamCapacity)))
                                    .color(NamedTextColor.GRAY)),
                            ni(Component.translatable("steamwork.gui.steam_robot.state." + stateKey)
                                    .color(NamedTextColor.GRAY))
                    ));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            refreshGuiItems(); // 点一下刷新数值
        }
    }

    /** 任务模式按钮：点击切到该模式，当前模式高亮（绿色名 + 选中提示）。 */
    private final class JobButton extends AbstractItem {
        private final JobMode mode;
        JobButton(@NotNull JobMode mode) { this.mode = mode; }

        @Override public @NotNull ItemProvider getItemProvider(@NotNull Player v) {
            boolean active = jobMode == mode;
            Material icon = switch (mode) {
                case PATROL -> Material.LEATHER_BOOTS;
                case MINE   -> Material.IRON_PICKAXE;
                case CHOP   -> Material.IRON_AXE;
            };
            Component name = Component.translatable("steamwork.gui.steam_robot.job." + mode.name().toLowerCase())
                    .color(active ? NamedTextColor.GREEN : NamedTextColor.GRAY);
            return ItemStackBuilder.of(icon)
                    .name(ni(name))
                    .lore(List.of(ni(Component.translatable(active
                                    ? "steamwork.gui.steam_robot.job_active"
                                    : "steamwork.gui.steam_robot.job_select")
                            .color(active ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))));
        }
        @Override public void handleClick(@NotNull ClickType t, @NotNull Player p, @NotNull Click c) {
            setJob(mode);
        }
    }

    private void dismantle(@NotNull Player player) {
        resetWork(); // 抹掉残留破坏裂纹
        CopperGolem golem = getEntity();
        Location loc = golem.getLocation();
        World world = golem.getWorld();

        // 回收：非创造模式掉回一个部署物品
        if (player.getGameMode() != GameMode.CREATIVE) {
            world.dropItemNaturally(loc, SteamworkItems.STEAM_ROBOT.clone());
        }
        // 拆除特效
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.8, 0), 12, 0.3, 0.4, 0.3, 0.02);
        world.playSound(loc, Sound.ENTITY_COPPER_GOLEM_DEATH, SoundCategory.BLOCKS, 0.8f, 1.2f);

        golem.remove(); // 移出世界 → EntityStorage 自动注销
    }
}
