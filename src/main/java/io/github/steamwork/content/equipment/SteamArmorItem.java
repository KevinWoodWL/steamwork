package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.item.base.RebarInventoryTicker;
import io.github.steamwork.util.SteamCharge;
import io.github.steamwork.util.SteamworkUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 蒸汽盔甲（头盔 / 胸甲 / 护腿 / 靴子）。
 *
 * <h3>四级材质差异</h3>
 * <table>
 * <tr><th>级别</th><th>盔甲专属增益</th><th>套装加成</th><th>特效</th></tr>
 * <tr><td>黄铜</td><td>夜视+水呼吸 / 抗性I / 速度I / 缓降</td><td>抗性II</td><td>白色蒸汽云</td></tr>
 * <tr><td>青铜</td><td>+火焰抗性 / 跳跃强化I</td><td>同上</td><td>橙色火焰粒子</td></tr>
 * <tr><td>因瓦</td><td>护腿速度II / 靴子+速度I</td><td>+生命吸收I</td><td>蓝白电弧</td></tr>
 * <tr><td>钨</td><td>胸甲基础抗性II / 护腿速度II / 全套力量II</td><td>+<b>蒸汽飞行</b></td><td>白色蒸汽喷射</td></tr>
 * </table>
 *
 * <h3>钨套飞行</h3>
 * <p>全套钨盔甲均有蒸汽且完整穿戴时，长按跳跃键启动喷气推进（非创造/旁观模式）；
 * 前 2 秒为点火升压阶段，不能向上推进但会持续耗汽并播放高强度特效；
 * 点火完成后快速进入推进，蒸汽耗尽、松开跳跃或套装不完整时停止。</p>
 */
public class SteamArmorItem extends SteamEquipment implements RebarInventoryTicker {

    // ── 消耗常量 ──────────────────────────────────────────────────────────────

    // 注意：飞行物理由每 tick 运行的飞行引擎（FlightEngine）驱动，因此以下耗汽与时长
    // 常量都以"真实游戏 tick（20/s）"为单位，而非 0.5s 的库存 tick。

    /** 钨套推进飞行时每 tick 总消耗（mB），由四件钨甲平均分摊（≈16 mB/s）。 */
    private static final double STEAM_PER_TICK_FLIGHT_EXTRA = 0.8;
    /** 喷气点火阶段每 tick 总消耗（mB）（≈8 mB/s）。 */
    private static final double STEAM_PER_TICK_FLIGHT_IGNITION = 0.4;
    /** 点火升压阶段时长：40 真实 tick = 2.0s。 */
    private static final int FLIGHT_IGNITION_TICKS = 40;
    /** 点火结束后推力爬升到满的时长：16 tick = 0.8s。 */
    private static final int FLIGHT_RAMP_TICKS = 16;
    /** 点火起步：极短暂地把玩家温和托离地面（tick 数与升力），随后转为原地悬浮。 */
    private static final int    IGNITION_LIFTOFF_TICKS    = 5;
    private static final double IGNITION_LIFTOFF_VELOCITY = 0.30;
    /** 点火悬浮升力：刚好抵消重力、维持高度，缓慢上浮，绝不快速窜天，也绝不下坠。 */
    private static final double IGNITION_HOVER_LIFT = 0.045;
    private static final double JETPACK_MIN_LIFT = 0.16;
    private static final double JETPACK_MAX_LIFT = 0.34;
    private static final double JETPACK_MIN_FORWARD_THRUST = 0.030;
    private static final double JETPACK_MAX_FORWARD_THRUST = 0.085;
    /** 下坠动量抵消：下坠速度超过此值（向下）时，复推先进入"抵消阶段"而非立刻上升。
     *  ≈ 自由下落约 1 秒的速度（MC 重力 0.08/tick、阻力 0.98，终端 ~3.92），
     *  确保只有较明显的下坠才触发，普通短距离下降不受影响。 */
    private static final double BRAKE_MIN_SPEED = 1.1;
    /** 抵消阶段每 tick 的恒定反推力（向上）。恒力 → 抵消时长随下坠动量线性增长。 */
    private static final double BRAKE_DECEL_PER_TICK = 0.14;

    private static final int BUFF_DURATION            = 40;
    private static final int DEPLETED_PENALTY_DURATION = 60;

    // ── 飞行追踪 ──────────────────────────────────────────────────────────────

    /**
     * 处于"飞行会话"中的玩家 UUID 集合：从起飞点火开始，一直保留到落地为止。
     * 会话期间即使松开空格（空中滑翔）也保留，以便每 tick 飞行引擎能在 1 tick 内
     * 立刻响应再次按下的空格——空中复推无需重新点火、也几乎没有检测延迟。
     */
    private static final Set<UUID> STEAM_FLIGHT_PLAYERS =
            Collections.synchronizedSet(new HashSet<>());
    private static final Map<UUID, Integer> STEAM_FLIGHT_TICKS =
            Collections.synchronizedMap(new HashMap<>());
    /** 本次飞行会话中玩家是否已经离开地面（用于区分"起飞中"与"已落地"）。 */
    private static final Set<UUID> STEAM_FLIGHT_LEFT_GROUND =
            Collections.synchronizedSet(new HashSet<>());
    /** 当前"下坠动量抵消阶段"已持续的 tick 数（用于起飞式特效的节流与启动音）。 */
    private static final Map<UUID, Integer> STEAM_FLIGHT_BRAKE_TICKS =
            Collections.synchronizedMap(new HashMap<>());
    /** 上一 tick 玩家是否站在地面（按空格起飞那一刻 isOnGround 已被跳跃顶成 false，
     *  故用上一 tick 的状态判定"地面起飞 vs 空中起步"，决定是否需要点火）。 */
    private static final Set<UUID> GROUND_LAST_TICK =
            Collections.synchronizedSet(new HashSet<>());

    /** 每 tick 飞行引擎是否已在调度中（避免重复注册任务）。 */
    private static volatile boolean flightEngineRunning = false;

    // ── 飞行模式切换 ──────────────────────────────────────────────────────────

    /** 钨套飞行模式。Ctrl+Shift（疾跑+潜行）循环切换：喷气推进 → 悬浮飞行 → 关闭。 */
    public enum FlightMode {
        /** 关闭：空格正常跳跃，不触发任何喷气/飞行。 */
        OFF,
        /** 喷气推进：长按空格点火起飞、按看向方向推进（默认，保持原有手感）。 */
        JETPACK,
        /** 悬浮自由飞行：类创造的自由飞行（WASD + 空格上升 / 潜行下降），持续耗汽。 */
        HOVER
    }

    /** 飞行模式持久化键（写入玩家 PDC，跟随存档与服务器重启）。 */
    private static final NamespacedKey FLIGHT_MODE_KEY = SteamworkUtils.steamworkKey("flight_mode");

    /** 各玩家当前飞行模式的内存缓存（首次访问时从玩家 PDC 懒加载；缺省 = JETPACK）。 */
    private static final Map<UUID, FlightMode> FLIGHT_MODE =
            Collections.synchronizedMap(new HashMap<>());
    /** 当前穿戴整套有汽钨甲、需被每 tick 引擎处理的玩家（由 onTick 每 0.5s 维护）。 */
    private static final Set<UUID> TUNGSTEN_WEARERS =
            Collections.synchronizedSet(new HashSet<>());
    /** 上一 tick 是否按住"疾跑+潜行"(Ctrl+Shift)组合（用于上升沿检测切换）。 */
    private static final Set<UUID> TOGGLE_COMBO_HELD =
            Collections.synchronizedSet(new HashSet<>());
    /** 上次切换模式的时间戳（毫秒），用于切换冷却，避免按住组合键连续翻页。 */
    private static final Map<UUID, Long> TOGGLE_COOLDOWN =
            Collections.synchronizedMap(new HashMap<>());
    /** 当前由本插件授予原版飞行（悬浮模式）的玩家，用于安全收回飞行能力。 */
    private static final Set<UUID> HOVER_ACTIVE =
            Collections.synchronizedSet(new HashSet<>());
    /** 悬浮接管起步宽限截止时间戳（毫秒）：此前不做落地/停飞判定，确保平滑接管。 */
    private static final Map<UUID, Long> HOVER_GRACE_UNTIL =
            Collections.synchronizedMap(new HashMap<>());

    /** 悬浮接管起步宽限时长（毫秒）。 */
    private static final long HOVER_ENGAGE_GRACE_MS = 300L;

    /** 模式切换冷却（毫秒）。 */
    private static final long TOGGLE_COOLDOWN_MS = 400L;
    /** 悬浮自由飞行时每 tick 耗汽（mB），仅在实际飞行（离地浮空）时扣除（≈16 mB/s）。 */
    private static final double HOVER_STEAM_PER_TICK = 0.8;
    /** 悬浮模式的飞行速度（低于原版默认 0.1，主打稳定悬停、速度与动量都更小）。 */
    private static final float HOVER_FLY_SPEED = 0.05f;
    /** 原版默认飞行速度，用于退出悬浮时恢复。 */
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    /** 读取玩家飞行模式：优先用内存缓存，缺失则从玩家 PDC 懒加载（缺省 JETPACK）。 */
    private static FlightMode modeOf(@NotNull Player player) {
        UUID id = player.getUniqueId();
        FlightMode cached = FLIGHT_MODE.get(id);
        if (cached != null) return cached;

        FlightMode mode = FlightMode.JETPACK;
        String stored = player.getPersistentDataContainer().get(FLIGHT_MODE_KEY, PersistentDataType.STRING);
        if (stored != null) {
            try {
                mode = FlightMode.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // 旧/非法值：回退默认
            }
        }
        FLIGHT_MODE.put(id, mode);
        return mode;
    }

    /** 设置并持久化玩家飞行模式（同时更新内存缓存与玩家 PDC）。 */
    private static void setMode(@NotNull Player player, @NotNull FlightMode mode) {
        FLIGHT_MODE.put(player.getUniqueId(), mode);
        player.getPersistentDataContainer().set(FLIGHT_MODE_KEY, PersistentDataType.STRING, mode.name());
    }

    // ── 构造 ──────────────────────────────────────────────────────────────────

    public SteamArmorItem(@NotNull ItemStack stack) {
        super(stack);
    }

    // ── Tick 主逻辑 ───────────────────────────────────────────────────────────

    @Override
    public void onTick(@NotNull Player player) {
        SteamEquipmentProfile profile = SteamEquipmentProfile.fromKey(getKey());
        if (profile == null || !profile.part().isArmor()) return;

        ItemStack worn = wornPieceFor(player, profile);
        if (worn == null) {
            // 盔甲未穿戴（在背包里）——对钨胸甲而言须停用飞行
            if (profile.material() == SteamEquipmentMaterial.TUNGSTEN
                    && profile.part() == SteamEquipmentPart.CHESTPLATE) {
                disableTungstenFlight(player);
            }
            return;
        }

        if (!SteamCharge.isPowered(worn)) {
            if (SteamCharge.hasCanister(worn)) {
                applyDepletedPenalty(player, profile);
                playDepletedEffects(player, profile);
            }
            // 蒸汽耗尽：无论哪件都要停用飞行
            if (profile.material() == SteamEquipmentMaterial.TUNGSTEN) {
                disableTungstenFlight(player);
            }
            return;
        }
        profile.part().setWorn(player.getInventory(), worn);

        boolean fullSet = profile.part() == SteamEquipmentPart.CHESTPLATE
                && isFullSetPowered(player, profile.material());

        applyPieceBuff(player, profile, fullSet);

        // 钨胸甲：注册/注销飞行资格（只由胸甲负责，避免四件各自独立处理）。
        // 真正的模式切换与飞行物理交给每 tick 飞行引擎。
        if (profile.material() == SteamEquipmentMaterial.TUNGSTEN
                && profile.part() == SteamEquipmentPart.CHESTPLATE) {
            registerTungstenFlight(player, fullSet);
        }
    }

    @Override
    public long getBaseTickInterval() {
        return 1L;
    }

    // ── 穿戴检测 ──────────────────────────────────────────────────────────────

    private @Nullable ItemStack wornPieceFor(
            @NotNull Player player,
            @NotNull SteamEquipmentProfile expectedProfile
    ) {
        ItemStack slot = expectedProfile.part().getWorn(player.getInventory());
        if (slot == null || slot.isEmpty()) return null;
        SteamEquipmentProfile actualProfile = SteamEquipmentProfile.fromStack(slot);
        return actualProfile == expectedProfile ? slot : null;
    }

    // ── 分级增益 ──────────────────────────────────────────────────────────────

    /**
     * 根据材质级别和部位应用不同的持续增益。
     *
     * <ul>
     *   <li>BRASS：基础（夜视/水呼/抗性I/速度I/缓降）</li>
     *   <li>BRONZE：+ 火焰抗性 / 跳跃强化I</li>
     *   <li>INVAR：+ 护腿速度II / 靴子速度I / 全套生命吸收I</li>
     *   <li>TUNGSTEN：+ 胸甲基础抗性II / 护腿速度II / 全套力量II</li>
     * </ul>
     */
    private void applyPieceBuff(
            @NotNull Player player,
            @NotNull SteamEquipmentProfile profile,
            boolean fullSet
    ) {
        SteamEquipmentMaterial mat = profile.material();
        int tier = mat.ordinal(); // 0=BRASS, 1=BRONZE, 2=INVAR, 3=TUNGSTEN

        switch (profile.part()) {
            case HELMET -> {
                addBuff(player, PotionEffectType.NIGHT_VISION, 0);
                addBuff(player, PotionEffectType.WATER_BREATHING, 0);
                if (tier >= 1) {  // BRONZE+: 火焰抗性
                    addBuff(player, PotionEffectType.FIRE_RESISTANCE, 0);
                }
            }
            case CHESTPLATE -> {
                if (fullSet) {
                    addBuff(player, PotionEffectType.RESISTANCE, mat.fullSetResistanceAmplifier());
                    if (tier >= 2) addBuff(player, PotionEffectType.ABSORPTION, 0); // INVAR+: 吸收I
                    if (tier >= 3) addBuff(player, PotionEffectType.STRENGTH,   0); // TUNGSTEN: 力量I（原II，收敛）
                }
            }
            case LEGGINGS -> {
                // 全部材质统一速度 I（钨原速度II，收敛）
                addBuff(player, PotionEffectType.SPEED, 0);
                if (tier >= 1) addBuff(player, PotionEffectType.JUMP_BOOST, 0); // BRONZE+: 跳跃强化I
            }
            case BOOTS -> {
                if (mat != SteamEquipmentMaterial.TUNGSTEN) {
                    addBuff(player, PotionEffectType.SLOW_FALLING, 0);
                }
                if (tier >= 2) addBuff(player, PotionEffectType.SPEED, 0); // INVAR+: 靴子也给速度I
            }
            default -> { /* 工具/武器部位由 SteamToolItem 处理 */ }
        }
    }

    private void addBuff(@NotNull Player player, @NotNull PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, BUFF_DURATION, amplifier, true, false, true));
    }

    // ── 耗尽惩罚 ──────────────────────────────────────────────────────────────

    private void applyDepletedPenalty(
            @NotNull Player player,
            @NotNull SteamEquipmentProfile profile
    ) {
        // 钨级：虚弱 II；其余：虚弱 I
        addPenalty(player, PotionEffectType.WEAKNESS,
                profile.material() == SteamEquipmentMaterial.TUNGSTEN ? 1 : 0);

        switch (profile.part()) {
            case HELMET     -> addPenalty(player, PotionEffectType.MINING_FATIGUE, 0);
            case CHESTPLATE -> {
                addPenalty(player, PotionEffectType.WEAKNESS, 1);
                addPenalty(player, PotionEffectType.SLOWNESS, 0);
            }
            case LEGGINGS   -> addPenalty(player, PotionEffectType.SLOWNESS, 1);
            case BOOTS      -> addPenalty(player, PotionEffectType.SLOWNESS, 0);
            default         -> { }
        }
    }

    private void addPenalty(@NotNull Player player, @NotNull PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, DEPLETED_PENALTY_DURATION, amplifier, true, false, true));
    }

    // ── 钨套飞行 ──────────────────────────────────────────────────────────────

    /**
     * 由钨胸甲的 onTick（每 0.5s）调用：维护"飞行资格"。
     * 全套有汽 → 登记为飞行可用者并启动每 tick 引擎；否则停用飞行。
     * 模式切换与飞行物理全部由每 tick 引擎（{@link #driveFlight}）处理。
     */
    private void registerTungstenFlight(@NotNull Player player, boolean fullSet) {
        if (!fullSet) {
            disableTungstenFlight(player);
            return;
        }
        TUNGSTEN_WEARERS.add(player.getUniqueId());
        ensureFlightEngineRunning();
    }

    /** 停用某玩家的钨套飞行：移出飞行可用者，结束喷气会话并收回悬浮飞行。 */
    private static void disableTungstenFlight(@NotNull Player player) {
        TUNGSTEN_WEARERS.remove(player.getUniqueId());
        revokeSteamFlight(player);
        endHover(player);
    }

    /**
     * 启动每 tick 飞行引擎（若尚未运行）。引擎遍历所有"飞行可用者"，逐 tick 检测
     * 模式切换组合键并按当前模式驱动飞行；没有可用者时自动停止。
     */
    private static void ensureFlightEngineRunning() {
        if (flightEngineRunning) return;
        flightEngineRunning = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (TUNGSTEN_WEARERS.isEmpty()) {
                    flightEngineRunning = false;
                    cancel();
                    return;
                }
                for (UUID id : TUNGSTEN_WEARERS.toArray(new UUID[0])) {
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || !player.isOnline()) {
                        TUNGSTEN_WEARERS.remove(id);
                        STEAM_FLIGHT_PLAYERS.remove(id);
                        STEAM_FLIGHT_TICKS.remove(id);
                        STEAM_FLIGHT_LEFT_GROUND.remove(id);
                        TOGGLE_COMBO_HELD.remove(id);
                        HOVER_ACTIVE.remove(id);
                        HOVER_GRACE_UNTIL.remove(id);
                        STEAM_FLIGHT_BRAKE_TICKS.remove(id);
                        GROUND_LAST_TICK.remove(id);
                        continue;
                    }
                    driveFlight(player);
                }
            }
        }.runTaskTimer(io.github.steamwork.Steamwork.getInstance(), 1L, 1L);
    }

    /** 每 tick 的总驱动：检测模式切换组合键，再按当前模式分派。 */
    private static void driveFlight(@NotNull Player player) {
        UUID id = player.getUniqueId();

        if (isCreativeOrSpectator(player)) {
            // 创造/旁观：不干预原版飞行。endHover 仅在该玩家曾被悬浮接管时才恢复飞行速度，
            // 否则完全不碰其飞行速度，确保不影响原版创造飞行。
            endHover(player);
            revokeSteamFlight(player);
            player.setAllowFlight(true); // 防御性：保证创造飞行可用（对创造本就是 no-op）
            return;
        }

        // 套装不再完整有汽（例如整套被丢弃/耗尽）→ 注销飞行资格，停掉引擎
        if (!isFullSetPowered(player, SteamEquipmentMaterial.TUNGSTEN)) {
            disableTungstenFlight(player);
            return;
        }

        // ── 模式切换：Ctrl+Shift（疾跑+潜行）组合键上升沿 + 冷却 ──
        // 刻意不含空格：空格是喷气推进的起飞键，若切换键带空格会顺带触发点火起飞。
        var input = player.getCurrentInput();
        boolean combo = input.isSprint() && input.isSneak();
        boolean prevCombo = TOGGLE_COMBO_HELD.contains(id);
        long now = System.currentTimeMillis();
        if (combo && !prevCombo
                && now - TOGGLE_COOLDOWN.getOrDefault(id, 0L) > TOGGLE_COOLDOWN_MS) {
            cycleFlightMode(player);
            TOGGLE_COOLDOWN.put(id, now);
        }
        if (combo) TOGGLE_COMBO_HELD.add(id); else TOGGLE_COMBO_HELD.remove(id);

        // ── 按当前模式驱动 ──
        switch (modeOf(player)) {
            case OFF -> {
                revokeSteamFlight(player);
                endHover(player);
            }
            case JETPACK -> {
                endHover(player);
                driveJetpack(player);
            }
            case HOVER -> {
                revokeSteamFlight(player);
                driveHover(player);
            }
        }

        // 钨套下落保护：未在主动飞行（无喷气会话、未接管悬浮）且在空中明显下坠时给缓降；平常不给。
        boolean inFlight = STEAM_FLIGHT_PLAYERS.contains(id) || HOVER_ACTIVE.contains(id);
        if (!inFlight && !player.isOnGround() && player.getVelocity().getY() < -0.5) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING, 20, 0, true, false, false));
        }

        // 记录本 tick 的离地状态，供下一 tick 起飞判定（规避按空格瞬间 isOnGround 被跳跃顶成 false）
        if (player.isOnGround()) {
            GROUND_LAST_TICK.add(id);
        } else {
            GROUND_LAST_TICK.remove(id);
        }
    }

    /** Ctrl+Shift（疾跑+潜行）循环切换飞行模式：喷气推进 → 悬浮飞行 → 关闭 → …。 */
    private static void cycleFlightMode(@NotNull Player player) {
        FlightMode next = switch (modeOf(player)) {
            case JETPACK -> FlightMode.HOVER;
            case HOVER   -> FlightMode.OFF;
            case OFF      -> FlightMode.JETPACK;
        };
        UUID id = player.getUniqueId();
        // 悬浮模式只在"喷气推进飞行中（已离地 且 点火完成）"切入时才接管为自由飞行：
        //  · 要求存在已离地的喷气会话（真正在飞），而非只看 isOnGround；
        //  · 还要求已过点火阶段（ticks ≥ 点火时长），否则起飞前 2 秒点火升空期也会被误接管。
        // 必须在 revokeSteamFlight 清除会话状态之前读取。
        boolean engageHover = next == FlightMode.HOVER
                && STEAM_FLIGHT_LEFT_GROUND.contains(id)
                && STEAM_FLIGHT_TICKS.getOrDefault(id, 0) >= FLIGHT_IGNITION_TICKS;

        setMode(player, next);
        // 离开旧模式：清掉其遗留状态
        revokeSteamFlight(player);
        endHover(player);

        if (engageHover) {
            player.setAllowFlight(true);
            player.setFlySpeed(HOVER_FLY_SPEED);   // 悬浮：低速稳定悬停
            player.setFlying(true);
            player.setFallDistance(0.0f);
            HOVER_ACTIVE.add(id);
            HOVER_GRACE_UNTIL.put(id, System.currentTimeMillis() + HOVER_ENGAGE_GRACE_MS);
        }

        Component label = switch (next) {
            case JETPACK -> Component.text("喷气推进", NamedTextColor.AQUA);
            case HOVER   -> engageHover
                    ? Component.text("悬浮自由飞行", NamedTextColor.GREEN)
                    : Component.text("悬浮飞行（需在喷气推进飞行中切换，点火阶段无效）", NamedTextColor.YELLOW);
            case OFF      -> Component.text("关闭", NamedTextColor.GRAY);
        };
        player.sendActionBar(Component.text("飞行模式：", NamedTextColor.GOLD).append(label));
        player.playSound(player.getLocation(),
                next == FlightMode.OFF ? Sound.BLOCK_LEVER_CLICK : Sound.BLOCK_BEACON_ACTIVATE,
                0.6f, next == FlightMode.OFF ? 0.8f : 1.4f);
    }

    /**
     * 喷气推进模式的每 tick 处理（一次飞行会话从起飞持续到落地）：
     * 落地或在地面松开空格 → 结束会话（下次重新点火）；空中松开空格 → 滑翔保留会话；
     * 按住空格 → 首次点火升空 2 秒，点火完成后（含空中复推）直接推进耗汽。
     */
    private static void driveJetpack(@NotNull Player player) {
        UUID id = player.getUniqueId();

        boolean jump = player.getCurrentInput().isJump();
        boolean onGround = player.isOnGround();
        boolean inSession = STEAM_FLIGHT_PLAYERS.contains(id);

        // 起飞：按下空格即开启新会话。用"上一 tick 是否在地面"判定（按空格当 tick 已被跳跃顶离地）：
        //  · 地面起飞 → 从 0 开始，走 2 秒点火升空；
        //  · 空中起步（如从悬浮/空中切入喷气）→ 起始即"已点火"，直接进入推进，不再重走点火。
        if (!inSession) {
            if (jump) {
                boolean groundTakeoff = GROUND_LAST_TICK.contains(id);
                STEAM_FLIGHT_PLAYERS.add(id);
                STEAM_FLIGHT_TICKS.put(id, groundTakeoff ? 0 : FLIGHT_IGNITION_TICKS);
                STEAM_FLIGHT_LEFT_GROUND.remove(id);
            } else {
                return;
            }
        }

        if (!onGround) {
            STEAM_FLIGHT_LEFT_GROUND.add(id);
        }
        boolean leftGround = STEAM_FLIGHT_LEFT_GROUND.contains(id);

        // 结束会话：已离地后落地，或起飞前就在地面松开空格（中止起飞）
        boolean landed = onGround && leftGround && player.getVelocity().getY() <= 0.0;
        boolean abortedOnGround = onGround && !leftGround && !jump;
        if (landed || abortedOnGround) {
            revokeSteamFlight(player);
            return;
        }

        int flightTicks = STEAM_FLIGHT_TICKS.getOrDefault(id, 0);
        boolean ignited = flightTicks >= FLIGHT_IGNITION_TICKS;

        if (!jump) {
            // 空中松开空格：滑翔/自由下落，保留会话以便随时复推（不点火、不推进、不耗汽）
            STEAM_FLIGHT_BRAKE_TICKS.remove(id);
            player.setFallDistance(0.0f);
            return;
        }

        double cost = ignited ? STEAM_PER_TICK_FLIGHT_EXTRA : STEAM_PER_TICK_FLIGHT_IGNITION;
        if (!consumeTungstenFlightSteam(player.getInventory(), cost)) {
            revokeSteamFlight(player);
            return;
        }
        flightTicks++;
        STEAM_FLIGHT_TICKS.put(id, flightTicks);
        player.setFallDistance(0.0f);

        if (!ignited) {
            // 首次点火升空阶段
            applyIgnitionHover(player, flightTicks);
            spawnFlightParticles(player, flightTicks);
            return;
        }

        // 已点火的复推：若带着明显下坠动量，先进入"抵消阶段"——用恒定反推力把下坠拉到 0，
        // 期间绝不上升、复用起飞点火的大量粒子+音效；抵消完才转入正常推进上升。
        // 抵消时长随下坠动量线性增长（恒力减速）。
        if (player.getVelocity().getY() < -BRAKE_MIN_SPEED) {
            int brakeTicks = STEAM_FLIGHT_BRAKE_TICKS.merge(id, 1, Integer::sum);
            applyMomentumBrake(player);
            // 用点火阶段的特效（传入 ≤ 点火时长的计数触发"起飞式"粒子分支）
            spawnFlightParticles(player, Math.min(brakeTicks, FLIGHT_IGNITION_TICKS));
        } else {
            STEAM_FLIGHT_BRAKE_TICKS.remove(id);
            applyJetpackThrust(player, flightTicks - FLIGHT_IGNITION_TICKS);
            spawnFlightParticles(player, flightTicks);
        }
    }

    /**
     * 下坠动量抵消：以恒定反推力逐 tick 把向下速度拉向 0（封顶 0，绝不立即转为上升），
     * 并适度阻尼横向，模拟"喷气先顶住下坠再起飞"的真实感。
     */
    private static void applyMomentumBrake(@NotNull Player player) {
        Vector v = player.getVelocity().clone();
        v.setX(v.getX() * 0.85);
        v.setZ(v.getZ() * 0.85);
        v.setY(Math.min(v.getY() + BRAKE_DECEL_PER_TICK, 0.0));
        player.setVelocity(v);
    }

    /**
     * 悬浮自由飞行模式的每 tick 处理。
     * <p>该模式是"飞行中的延续"：只有在<b>空中飞行时切入</b>（见 {@link #cycleFlightMode}）才会
     * 接管为类创造自由飞行（WASD + 空格上升/潜行下降）。地面切入不接管，本方法直接跳过。</p>
     * <p>接管期间每 tick 稳定维持飞行并持续耗汽；蒸汽耗尽自动落下。想落地：再按一次
     * Ctrl+Shift 切到「关闭」即可。</p>
     */
    private static void driveHover(@NotNull Player player) {
        UUID id = player.getUniqueId();
        // 仅维持"已在空中接管"的悬浮飞行；未接管（在地面切入悬浮）→ 本模式不生效
        if (!HOVER_ACTIVE.contains(id)) return;

        double share = HOVER_STEAM_PER_TICK / 4.0;
        if (!isFullSetPowered(player, SteamEquipmentMaterial.TUNGSTEN)
                || !hasTungstenFlightSteam(player.getInventory(), share)) {
            player.sendActionBar(Component.text("蒸汽耗尽，悬浮飞行中断", NamedTextColor.RED));
            endHover(player);
            // 同样自动切回普通（喷气推进）模式
            if (modeOf(player) == FlightMode.HOVER) {
                setMode(player, FlightMode.JETPACK);
            }
            return;
        }

        player.setAllowFlight(true);
        player.setFlySpeed(HOVER_FLY_SPEED);   // 持续维持悬浮低速
        if (player.isSprinting()) {
            // 悬浮模式禁止冲刺：冲刺会让飞行速度翻倍，破坏低速悬停。
            // 取消的是冲刺"状态"，不影响切换检测读取的冲刺"按键输入"，故 Ctrl+Shift 仍可用。
            player.setSprinting(false);
        }
        boolean inGrace = System.currentTimeMillis()
                < HOVER_GRACE_UNTIL.getOrDefault(id, 0L);
        if (inGrace) {
            // 起步宽限：确保平滑接管，避免瞬时状态误判提前结束
            player.setFlying(true);
        } else if (player.isOnGround() || !player.isFlying()) {
            // 已落地 / 自行停飞 → 结束悬浮，并自动切回普通（喷气推进）模式，方便落地后正常起飞
            endHover(player);
            if (modeOf(player) == FlightMode.HOVER) {
                setMode(player, FlightMode.JETPACK);
                player.sendActionBar(Component.text("落地 · 飞行模式：", NamedTextColor.GOLD)
                        .append(Component.text("喷气推进", NamedTextColor.AQUA)));
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.0f);
            }
            return;
        }

        // 浮空持续耗汽 + 延续正常推进飞行的喷射尾焰特效（满推力强度，视觉无缝衔接）。
        // 传入 getTicksLived 使其稳定处于推进阶段（thrustRamp=1）并正确按 tick 节流。
        player.setFallDistance(0.0f);
        consumeTungstenFlightSteam(player.getInventory(), HOVER_STEAM_PER_TICK);
        spawnFlightParticles(player, player.getTicksLived());
    }

    /** 收回某玩家的悬浮飞行能力（非创造时关闭飞行），并恢复原版默认飞行速度。 */
    private static void endHover(@NotNull Player player) {
        UUID id = player.getUniqueId();
        HOVER_GRACE_UNTIL.remove(id);
        if (!HOVER_ACTIVE.remove(id)) return;
        player.setFlySpeed(DEFAULT_FLY_SPEED);     // 恢复我们调低过的飞行速度
        if (isCreativeOrSpectator(player)) return; // 不关闭创造自带飞行
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /** 检查全套钨甲是否每件都至少有 {@code share} mB 蒸汽（不扣除）。 */
    private static boolean hasTungstenFlightSteam(@NotNull PlayerInventory inv, double share) {
        return isPoweredTungstenArmor(inv.getHelmet(), SteamEquipmentPart.HELMET, share)
                && isPoweredTungstenArmor(inv.getChestplate(), SteamEquipmentPart.CHESTPLATE, share)
                && isPoweredTungstenArmor(inv.getLeggings(), SteamEquipmentPart.LEGGINGS, share)
                && isPoweredTungstenArmor(inv.getBoots(), SteamEquipmentPart.BOOTS, share);
    }

    private static boolean consumeTungstenFlightSteam(@NotNull PlayerInventory inv, double totalCost) {
        ItemStack helmet = inv.getHelmet();
        ItemStack chestplate = inv.getChestplate();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();
        double share = totalCost / 4.0;

        if (!isPoweredTungstenArmor(helmet, SteamEquipmentPart.HELMET, share)
                || !isPoweredTungstenArmor(chestplate, SteamEquipmentPart.CHESTPLATE, share)
                || !isPoweredTungstenArmor(leggings, SteamEquipmentPart.LEGGINGS, share)
                || !isPoweredTungstenArmor(boots, SteamEquipmentPart.BOOTS, share)) {
            return false;
        }

        SteamCharge.tryRemoveAmount(helmet, share);
        SteamCharge.tryRemoveAmount(chestplate, share);
        SteamCharge.tryRemoveAmount(leggings, share);
        SteamCharge.tryRemoveAmount(boots, share);
        inv.setHelmet(helmet);
        inv.setChestplate(chestplate);
        inv.setLeggings(leggings);
        inv.setBoots(boots);
        return true;
    }

    private static boolean isPoweredTungstenArmor(
            @Nullable ItemStack stack,
            @NotNull SteamEquipmentPart part,
            double requiredSteam
    ) {
        return stack != null
                && !stack.isEmpty()
                && SteamEquipmentProfile.isPoweredArmor(stack, SteamEquipmentMaterial.TUNGSTEN, part)
                && SteamCharge.hasAtLeast(stack, requiredSteam);
    }

    /**
     * 点火升压阶段（前 2 秒）的物理：原地温和悬浮。
     * 开头极短暂地托离地面，随后维持刚好抵消重力的微小升力——玩家原地缓慢上浮，
     * 既不会被重力拽着下坠（修复"起飞往下掉"），也不会快速窜天。
     */
    private static void applyIgnitionHover(@NotNull Player player, int flightTicks) {
        Vector next = player.getVelocity().clone();
        // 横向急刹，让玩家在点火期间钉在原地，不再滑行
        next.setX(next.getX() * 0.55);
        next.setZ(next.getZ() * 0.55);

        double y;
        if (flightTicks <= IGNITION_LIFTOFF_TICKS) {
            y = IGNITION_LIFTOFF_VELOCITY;   // 起步：轻轻托离地面（仍比普通跳跃更温和）
        } else {
            y = IGNITION_HOVER_LIFT;         // 悬浮：恒定微小升力，抵消重力、原地缓慢上浮
        }
        next.setY(y);
        player.setVelocity(next);

        if (flightTicks % 10 == 0) {
            double seconds = Math.max(0.0, (FLIGHT_IGNITION_TICKS - flightTicks) / 20.0);
            player.sendActionBar(Component.text("\u55b7\u6c14\u70b9\u706b\uff1a", NamedTextColor.GRAY)
                    .append(Component.text(String.format(Locale.ROOT, "%.1fs", seconds), NamedTextColor.GOLD)));
        }
    }

    private static void applyJetpackThrust(@NotNull Player player, int flightTicks) {
        double ramp = Math.min(1.0, Math.max(1, flightTicks) / (double) FLIGHT_RAMP_TICKS);

        Vector velocity = player.getVelocity();
        Vector look = player.getLocation().getDirection();
        Vector forward = new Vector(look.getX(), 0.0, look.getZ());
        if (forward.lengthSquared() > 1.0e-4) {
            forward.normalize().multiply(JETPACK_MIN_FORWARD_THRUST
                    + (JETPACK_MAX_FORWARD_THRUST - JETPACK_MIN_FORWARD_THRUST) * ramp);
        }

        Vector next = velocity.clone();
        next.setX(next.getX() * 0.90 + forward.getX());
        next.setZ(next.getZ() * 0.90 + forward.getZ());
        next.setY(next.getY() + JETPACK_MIN_LIFT + (JETPACK_MAX_LIFT - JETPACK_MIN_LIFT) * ramp);

        double horizontalMax = 0.45 + 0.55 * ramp;
        double horizontal = Math.hypot(next.getX(), next.getZ());
        if (horizontal > horizontalMax && horizontal > 1.0e-4) {
            double scale = horizontalMax / horizontal;
            next.setX(next.getX() * scale);
            next.setZ(next.getZ() * scale);
        }

        double verticalMax = 0.48 + 0.34 * ramp;
        if (next.getY() > verticalMax) {
            next.setY(verticalMax);
        }
        if (next.getY() < -0.35) {
            next.setY(-0.35);
        }

        player.setVelocity(next);

        if (flightTicks % 10 == 0) {
            int thrustPercent = (int) Math.round(ramp * 100.0);
            player.sendActionBar(Component.text("\u55b7\u6c14\u63a8\u8fdb\uff1a", NamedTextColor.GRAY)
                    .append(Component.text(thrustPercent + "%", NamedTextColor.AQUA)));
        }
    }

    /** 结束玩家的飞行会话并清除全部相关状态（下次起飞需重新点火）。 */
    public static void revokeSteamFlight(@NotNull Player player) {
        UUID id = player.getUniqueId();
        STEAM_FLIGHT_LEFT_GROUND.remove(id);
        STEAM_FLIGHT_BRAKE_TICKS.remove(id);
        if (!STEAM_FLIGHT_PLAYERS.remove(id)) return;
        STEAM_FLIGHT_TICKS.remove(id);
    }

    private static boolean isCreativeOrSpectator(@NotNull Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /** 钨套主动飞行时的喷射粒子特效（由每 tick 飞行引擎调用，已按 tick 节流避免刷屏）。 */
    private static void spawnFlightParticles(@NotNull Player player, int flightTicks) {
        Location loc = player.getLocation().add(0.0, 0.5, 0.0);
        Location nozzle = loc.clone().subtract(0.0, 0.45, 0.0);
        boolean igniting = flightTicks <= FLIGHT_IGNITION_TICKS;
        double ignitionRamp = Math.min(1.0, flightTicks / (double) FLIGHT_IGNITION_TICKS);
        double thrustRamp = Math.min(1.0, Math.max(0, flightTicks - FLIGHT_IGNITION_TICKS) / (double) FLIGHT_RAMP_TICKS);

        if (igniting) {
            // 点火喷发：粒子量很大，但每 2 tick 一次（≈10 次/秒）以免每 tick 刷屏卡顿
            if (flightTicks % 2 == 0) {
                player.getWorld().spawnParticle(Particle.CLOUD, nozzle,
                        16 + (int) (14 * ignitionRamp), 0.55, 0.22, 0.55, 0.16);
                player.getWorld().spawnParticle(Particle.WHITE_ASH, nozzle,
                        20 + (int) (14 * ignitionRamp), 0.58, 0.25, 0.58, 0.20);
                player.getWorld().spawnParticle(Particle.FLAME, nozzle,
                        4 + (int) (4 * ignitionRamp), 0.28, 0.10, 0.28, 0.04);
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc,
                        2 + (int) (3 * ignitionRamp), 0.36, 0.22, 0.36, 0.02);
            }
            if (flightTicks == 1) {
                player.getWorld().playSound(loc, Sound.BLOCK_PISTON_EXTEND, 0.65f, 0.55f);
                player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.75f, 0.55f);
            }
            if (flightTicks % 6 == 0) {
                player.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT,
                        0.05f + (float) (0.03 * ignitionRamp),
                        0.45f + (float) (0.22 * ignitionRamp));
            }
            if (flightTicks % 12 == 0) {
                player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH,
                        0.42f, 0.65f + (float) (0.25 * ignitionRamp));
            }
            return;
        }

        if (flightTicks % 2 == 0) {
            player.getWorld().spawnParticle(Particle.CLOUD, nozzle,
                    3 + (int) (3 * thrustRamp), 0.34, 0.14, 0.34, 0.06 + 0.04 * thrustRamp);
            player.getWorld().spawnParticle(Particle.WHITE_ASH, nozzle,
                    4 + (int) (4 * thrustRamp), 0.38, 0.18, 0.38, 0.09 + 0.05 * thrustRamp);
        }
        if (flightTicks % 12 == 0) {
            // 推进阶段的旋风人射击声降到最低，几乎听不到
            player.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT,
                    0.03f,
                    0.78f + (float) (0.24 * thrustRamp));
        }
    }

    private void playDepletedEffects(
            @NotNull Player player,
            @NotNull SteamEquipmentProfile profile
    ) {
        if (player.getTicksLived() % 20 != 0) return;

        Location loc = player.getLocation();
        Location particleLoc = switch (profile.part()) {
            case HELMET     -> loc.clone().add(0.0, 1.65, 0.0);
            case CHESTPLATE -> loc.clone().add(0.0, 1.05, 0.0);
            case LEGGINGS   -> loc.clone().add(0.0, 0.75, 0.0);
            case BOOTS      -> loc.clone().add(0.0, 0.12, 0.0);
            default         -> loc;
        };
        player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 3, 0.18, 0.12, 0.18, 0.01);
        if (player.getTicksLived() % 80 == 0)
            player.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.25f, 1.7f);
    }

    // ── 套装检测 ──────────────────────────────────────────────────────────────

    private static boolean isFullSetPowered(@NotNull Player player, @NotNull SteamEquipmentMaterial material) {
        PlayerInventory inv = player.getInventory();
        return SteamEquipmentProfile.isPoweredArmor(inv.getHelmet(),     material, SteamEquipmentPart.HELMET)
                && SteamEquipmentProfile.isPoweredArmor(inv.getChestplate(), material, SteamEquipmentPart.CHESTPLATE)
                && SteamEquipmentProfile.isPoweredArmor(inv.getLeggings(),   material, SteamEquipmentPart.LEGGINGS)
                && SteamEquipmentProfile.isPoweredArmor(inv.getBoots(),      material, SteamEquipmentPart.BOOTS);
    }
}
