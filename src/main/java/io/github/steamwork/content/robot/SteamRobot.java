package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.EntityStorage;
import io.github.pylonmc.rebar.entity.RebarEntity;
import io.github.pylonmc.rebar.entity.interfaces.InteractRebarEntityHandler;
import io.github.pylonmc.rebar.entity.interfaces.TickingRebarEntity;
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.util.ProgressBar;
import io.github.pylonmc.rebar.waila.WailaDisplay;
import io.github.steamwork.SteamworkFluids;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.SteamworkKeys;
import io.github.steamwork.content.machines.SteamChargingChamber;
import io.github.steamwork.util.PneumaticEndpointSupport;
import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * 低于 {@code recharge-at} 比例即进入回充模式，寻路回绑定充汽舱站定等充（途中不耗汽），充满恢复巡游。
 * 充汽舱侧识别站内机器人并调用 {@link #addSteam}。无绑定且耗尽 → 缺汽停摆。</p>
 *
 * <p>后续阶段（见 {@code docs/design/steam-robot.md}）：GUI 与任务模式、采矿/砍树/搬运作业、
 * 铜傀儡拿取/放下动作动画等。</p>
 */
public class SteamRobot extends RebarEntity<CopperGolem> implements
        TickingRebarEntity, InteractRebarEntityHandler {

    /** 任务模式：巡逻 / 采矿 / 砍树。非潜行右键循环切换。 */
    public enum JobMode { PATROL, MINE, CHOP }

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
        // 已涂蜡铜傀儡：固定为未氧化的亮铜外观，且不再随时间氧化
        golem.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        golem.setOxidizing(CopperGolem.Oxidizing.waxed());
        golem.customName(Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA));
        golem.setCustomNameVisible(true);
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
        switch (jobMode) {
            case PATROL -> patrolStep(golem);
            case MINE, CHOP -> patrolStep(golem); // TODO P2：实际采矿/砍树作业
        }

        // 行动耗汽
        steam = Math.max(0.0, steam - steamPerTick);
    }

    /** 巡逻：在部署点周围随机选点游走。 */
    private void patrolStep(@NotNull CopperGolem golem) {
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
     * 右键机器人：潜行 → 拆除并掉回部署物品；非潜行 → 循环切换任务模式。
     *
     * <p>拆除时 {@code golem.remove()} 触发 {@code EntityRemoveFromWorldEvent}，Rebar 会自动把本实体
     * 从 {@link EntityStorage} 注销，无需手动清理。</p>
     */
    @Override
    @MultiHandler(priorities = {EventPriority.NORMAL})
    public void onInteractedWith(@NotNull PlayerInteractEntityEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.NORMAL) return;
        if (event.getHand() != EquipmentSlot.HAND) return;   // 只主手触发一次
        Player player = event.getPlayer();

        event.setCancelled(true);
        if (player.isSneaking()) {
            dismantle(player);
        } else {
            cycleJob(player);
        }
    }

    /** 非潜行右键：循环切换任务模式（巡逻→采矿→砍树→…），actionbar 提示 + 音效。 */
    private void cycleJob(@NotNull Player player) {
        JobMode[] modes = JobMode.values();
        jobMode = modes[(jobMode.ordinal() + 1) % modes.length];
        player.sendActionBar(Component.empty()
                .append(Component.translatable("steamwork.message.steam_robot.job_set").color(NamedTextColor.GRAY))
                .append(jobLabel().color(NamedTextColor.AQUA)));
        CopperGolem golem = getEntity();
        golem.getWorld().playSound(golem.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.BLOCKS, 0.6f, 1.4f);
    }

    /** 当前任务模式的可翻译名。 */
    private @NotNull Component jobLabel() {
        return Component.translatable("steamwork.gui.steam_robot.job." + jobMode.name().toLowerCase());
    }

    private void dismantle(@NotNull Player player) {
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
