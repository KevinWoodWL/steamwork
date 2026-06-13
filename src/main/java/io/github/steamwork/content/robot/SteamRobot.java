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
 * {@code steamPerTick}；耗尽即<b>缺汽停摆</b>（停止寻路、不再行动），WAILA 显示蒸汽条与状态。
 * 充能（复用蒸汽充汽舱四方向绑定）在 P1b 接入，会调用 {@link #addSteam}。</p>
 *
 * <p>后续阶段（见 {@code docs/design/steam-robot.md}）：充汽舱充能、采矿/砍树/搬运作业、
 * 铜傀儡拿取/放下动作动画等。</p>
 */
public class SteamRobot extends RebarEntity<CopperGolem> implements
        TickingRebarEntity, InteractRebarEntityHandler {

    // home（部署点）持久化键，存为三个 double
    private static final NamespacedKey HOME_X = steamworkKey("robot_home_x");
    private static final NamespacedKey HOME_Y = steamworkKey("robot_home_y");
    private static final NamespacedKey HOME_Z = steamworkKey("robot_home_z");
    // 蒸汽储量持久化键
    private static final NamespacedKey STEAM_KEY = steamworkKey("robot_steam");

    // ===== 设置（settings/steam_robot.yml，缺省时用默认值，避免缺配置崩溃）=====
    private final int    tickInterval  = getSetting("tick-interval",  ConfigAdapter.INTEGER, 10);
    private final double wanderRadius  = getSetting("wander-radius",  ConfigAdapter.DOUBLE,  6.0);
    private final double moveSpeed     = getSetting("move-speed",     ConfigAdapter.DOUBLE,  1.0);
    private final double steamCapacity = getSetting("steam-capacity", ConfigAdapter.DOUBLE,  2000.0);
    private final double steamPerTick  = getSetting("steam-per-tick", ConfigAdapter.DOUBLE,  10.0);

    // ===== 状态 =====
    private @NotNull Location home;
    private @Nullable Location target = null;
    /** 距上次重新选点经过的决策次数，超过阈值即强制重选，避免目标不可达时永久卡住。 */
    private int ticksSinceRetarget = 0;
    /** 当前蒸汽储量（mB）。 */
    private double steam;

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

        // 缺汽停摆：停止寻路、不再行动（充能在 P1b 接入后恢复）
        if (steam <= 0.0) {
            if (golem.getPathfinder().hasPath()) golem.getPathfinder().stopPathfinding();
            target = null;
            golem.setGolemState(CopperGolem.State.IDLE);
            return;
        }

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

        // 行动耗汽
        steam = Math.max(0.0, steam - steamPerTick);
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
    }

    // ===== 蒸汽燃料 =====

    public double getSteam()         { return steam; }
    public double getSteamCapacity() { return steamCapacity; }

    /** 充入蒸汽（封顶到容量），返回实际充入量。供蒸汽充汽舱（P1b）调用。 */
    public double addSteam(double amount) {
        if (amount <= 0.0) return 0.0;
        double added = Math.min(amount, steamCapacity - steam);
        steam += added;
        return added;
    }

    @Override
    public @Nullable WailaDisplay getWaila(@NotNull Player player) {
        boolean hasSteam = steam > 0.0;
        return WailaDisplay.of(
                        Component.translatable("steamwork.item.steam_robot.name").color(NamedTextColor.AQUA))
                .add(Component.translatable(hasSteam
                                ? "steamwork.gui.steam_robot.state.running"
                                : "steamwork.gui.steam_robot.state.no_steam")
                        .color(hasSteam ? NamedTextColor.GREEN : NamedTextColor.RED))
                .add(ProgressBar.fluidContents(SteamworkFluids.PRESSURIZED_STEAM, steamCapacity, steam));
    }

    /**
     * 潜行 + 右键机器人 → 拆除并掉回部署物品。
     *
     * <p>{@code golem.remove()} 触发 {@code EntityRemoveFromWorldEvent}，Rebar 会自动把本实体从
     * {@link EntityStorage} 注销，无需手动清理。非潜行右键留作 P1 打开 GUI。</p>
     */
    @Override
    @MultiHandler(priorities = {EventPriority.NORMAL})
    public void onInteractedWith(@NotNull PlayerInteractEntityEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.NORMAL) return;
        if (event.getHand() != EquipmentSlot.HAND) return;   // 只主手触发一次
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;                    // 仅潜行+右键 = 拆除

        event.setCancelled(true);
        dismantle(player);
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
