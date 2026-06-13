package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.entity.EntityStorage;
import io.github.pylonmc.rebar.entity.RebarEntity;
import io.github.pylonmc.rebar.entity.interfaces.InteractRebarEntityHandler;
import io.github.pylonmc.rebar.entity.interfaces.TickingRebarEntity;
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
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
 * 蒸汽机器人 —— <b>P0 验证骨架</b>。
 *
 * <p>本阶段只验证最核心的实体地基是否跑通，<b>不含</b>任何作业 / 耗汽 / 充能逻辑：</p>
 * <ol>
 *   <li>以铜傀儡（{@link CopperGolem}）为底座，用 {@link RebarEntity} 包装、{@link EntityStorage} 托管；</li>
 *   <li>实现 {@link TickingRebarEntity}，按 {@code tick-interval} 周期决策；</li>
 *   <li>每次决策在部署点（home）周围随机选点，用原版寻路 {@code getPathfinder().moveTo} 走过去——
 *       验证「Rebar 包装的 Mob 能寻路移动」；</li>
 *   <li>{@link #write} 持久化 home，重载后由单参 load 构造器恢复——验证「重载不丢」。</li>
 * </ol>
 *
 * <p>后续阶段（见 {@code docs/design/steam-robot.md}）才加入：蒸汽罐燃料、采矿/砍树/搬运作业、
 * 复用蒸汽充汽舱四方向绑定充能、铜傀儡拿取/放下动作动画等。</p>
 */
public class SteamRobot extends RebarEntity<CopperGolem> implements
        TickingRebarEntity, InteractRebarEntityHandler {

    // home（部署点）持久化键，存为三个 double
    private static final NamespacedKey HOME_X = steamworkKey("robot_home_x");
    private static final NamespacedKey HOME_Y = steamworkKey("robot_home_y");
    private static final NamespacedKey HOME_Z = steamworkKey("robot_home_z");

    // ===== 设置（settings/steam_robot.yml，缺省时用默认值，避免 spike 阶段缺配置崩溃）=====
    private final int    tickInterval = getSetting("tick-interval", ConfigAdapter.INTEGER, 10);
    private final double wanderRadius = getSetting("wander-radius", ConfigAdapter.DOUBLE,  6.0);
    private final double moveSpeed    = getSetting("move-speed",    ConfigAdapter.DOUBLE,  1.0);

    // ===== 状态 =====
    private @NotNull Location home;
    private @Nullable Location target = null;
    /** 距上次重新选点经过的决策次数，超过阈值即强制重选，避免目标不可达时永久卡住。 */
    private int ticksSinceRetarget = 0;

    /** 新生成：写入 rebar 实体 key 并记录部署点。 */
    public SteamRobot(@NotNull CopperGolem golem, @NotNull Location home) {
        super(SteamworkKeys.STEAM_ROBOT, golem);
        this.home = home.clone();
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

        Location loc = golem.getLocation();
        ticksSinceRetarget++;

        boolean reached = target != null
                && loc.getWorld() == target.getWorld()
                && loc.distanceSquared(target) < 2.25;

        if (target == null || reached || ticksSinceRetarget > 6) {
            target = pickWanderTarget(golem);
            golem.getPathfinder().moveTo(target, moveSpeed);
            // 蒸汽喷气作为「正在决策」的可视信号（P0 调试用）
            golem.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1.0, 0),
                    6, 0.2, 0.3, 0.2, 0.01);
            ticksSinceRetarget = 0;
        }
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
