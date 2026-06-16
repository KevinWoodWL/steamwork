package io.github.steamwork.content.robot;

import io.github.steamwork.util.PneumaticEndpointSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人控制终端的粒子可视化反馈。
 * <ul>
 *   <li>选区过程：终端→点一连线、实时矩形预览框 + ActionBar 尺寸</li>
 *   <li>绑定点过程：终端→光标方块连线</li>
 *   <li>被动注视：看向终端时显示区域边界 + 绑定点连线</li>
 * </ul>
 */
public final class TerminalParticleVisualizer implements Listener {

    private static final Map<UUID, SelectionSession> SESSIONS = new ConcurrentHashMap<>();

    private static final double DENSITY = 2.0;
    private static final float DUST_SIZE = 0.8f;

    private static final Particle.DustOptions AQUA = new Particle.DustOptions(Color.AQUA, DUST_SIZE);
    private static final Particle.DustOptions YELLOW = new Particle.DustOptions(Color.YELLOW, DUST_SIZE);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.LIME, DUST_SIZE);
    private static final Particle.DustOptions ORANGE = new Particle.DustOptions(Color.ORANGE, DUST_SIZE);
    private static final Particle.DustOptions LIGHT_BLUE = new Particle.DustOptions(Color.fromRGB(100, 180, 255), 0.6f);

    // ===== Session 管理 =====

    enum SessionType { REGION, DELIVERY, CHARGE }

    static final class SelectionSession {
        final Location terminalLoc;
        final int regionMax;
        final SessionType type;
        @Nullable Location corner1;

        SelectionSession(@NotNull Location terminalLoc, @NotNull SessionType type, int regionMax) {
            this.terminalLoc = terminalLoc.clone().add(0.5, 0.5, 0.5);
            this.type = type;
            this.regionMax = regionMax;
        }
    }

    public static void startSession(@NotNull Player player, @NotNull Location terminalLoc,
                                    @NotNull SessionType type, int regionMax) {
        SESSIONS.put(player.getUniqueId(), new SelectionSession(terminalLoc, type, regionMax));
    }

    public static void setCorner1(@NotNull Player player, @NotNull Location corner1) {
        SelectionSession s = SESSIONS.get(player.getUniqueId());
        if (s != null) s.corner1 = corner1.clone().add(0.5, 0.5, 0.5);
    }

    public static void endSession(@NotNull Player player) {
        SESSIONS.remove(player.getUniqueId());
    }

    // ===== 初始化 =====

    public static void register(@NotNull Plugin plugin) {
        TerminalParticleVisualizer vis = new TerminalParticleVisualizer();
        Bukkit.getPluginManager().registerEvents(vis, plugin);
        new BukkitRunnable() {
            @Override
            public void run() {
                vis.tick();
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    // ===== Tick 主循环 =====

    private void tick() {
        for (var entry : SESSIONS.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                SESSIONS.remove(entry.getKey());
                continue;
            }
            renderSession(player, entry.getValue());
        }
        renderPassiveLookers();
    }

    // ===== 渲染逻辑 =====

    private void renderSession(@NotNull Player player, @NotNull SelectionSession session) {
        Block target = player.getTargetBlockExact(50);
        Location targetLoc = target != null ? target.getLocation().add(0.5, 0.5, 0.5) : null;

        switch (session.type) {
            case REGION -> {
                if (session.corner1 == null) {
                    // 还没选点一：终端→光标连线
                    if (targetLoc != null) drawLine(player, session.terminalLoc, targetLoc, AQUA);
                } else {
                    // 选了点一：终端→点一连线 + 矩形预览框
                    drawLine(player, session.terminalLoc, session.corner1, AQUA);
                    if (targetLoc != null && target != null) {
                        drawRectFrame(player, session.corner1, targetLoc, YELLOW);
                        // ActionBar 尺寸信息
                        int sizeX = Math.abs(target.getX() - (int) Math.floor(session.corner1.getX())) + 1;
                        int sizeZ = Math.abs(target.getZ() - (int) Math.floor(session.corner1.getZ())) + 1;
                        NamedTextColor color = (sizeX > session.regionMax || sizeZ > session.regionMax)
                                ? NamedTextColor.RED : NamedTextColor.YELLOW;
                        player.sendActionBar(Component.text(sizeX + " × " + sizeZ, color));
                    }
                }
            }
            case DELIVERY -> {
                if (targetLoc != null) drawLine(player, session.terminalLoc, targetLoc, GREEN);
            }
            case CHARGE -> {
                if (targetLoc != null) drawLine(player, session.terminalLoc, targetLoc, ORANGE);
            }
        }
    }

    private void renderPassiveLookers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (SESSIONS.containsKey(player.getUniqueId())) continue;
            Block target = player.getTargetBlockExact(6);
            if (target == null) continue;
            if (!(PneumaticEndpointSupport.loadedRebarBlock(target) instanceof RobotControlTerminal term)) continue;
            if (!term.hasRegion()) continue;

            Location termLoc = target.getLocation().add(0.5, 0.5, 0.5);
            double y = termLoc.getY();
            Location min = new Location(target.getWorld(),
                    term.regionMinX(), y, term.regionMinZ());
            Location max = new Location(target.getWorld(),
                    term.regionMaxX() + 1.0, y, term.regionMaxZ() + 1.0);
            drawRectFrame(player, min, max, LIGHT_BLUE);

            Location delivery = term.getDeliveryPoint();
            if (delivery != null) drawLine(player, termLoc, delivery.clone().add(0.5, 0.5, 0.5), GREEN);
            Location charge = term.getChargePoint();
            if (charge != null) drawLine(player, termLoc, charge.clone().add(0.5, 0.5, 0.5), ORANGE);
        }
    }

    // ===== 绘制工具 =====

    private void drawLine(@NotNull Player player, @NotNull Location from, @NotNull Location to,
                          @NotNull Particle.DustOptions dust) {
        double dist = from.distance(to);
        int points = Math.max(2, (int) (dist * DENSITY));
        double dx = (to.getX() - from.getX()) / points;
        double dy = (to.getY() - from.getY()) / points;
        double dz = (to.getZ() - from.getZ()) / points;
        for (int i = 0; i <= points; i++) {
            player.spawnParticle(Particle.DUST,
                    from.getX() + dx * i, from.getY() + dy * i, from.getZ() + dz * i,
                    1, 0, 0, 0, 0, dust);
        }
    }

    private static final double FRAME_HEIGHT = 5.0;

    private void drawRectFrame(@NotNull Player player, @NotNull Location a, @NotNull Location b,
                               @NotNull Particle.DustOptions dust) {
        double minX = Math.min(a.getX(), b.getX());
        double maxX = Math.max(a.getX(), b.getX());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxZ = Math.max(a.getZ(), b.getZ());
        double yBottom = a.getY();
        double yTop = yBottom + FRAME_HEIGHT;

        org.bukkit.World w = a.getWorld();
        Location b1 = new Location(w, minX, yBottom, minZ);
        Location b2 = new Location(w, maxX, yBottom, minZ);
        Location b3 = new Location(w, maxX, yBottom, maxZ);
        Location b4 = new Location(w, minX, yBottom, maxZ);
        Location t1 = new Location(w, minX, yTop, minZ);
        Location t2 = new Location(w, maxX, yTop, minZ);
        Location t3 = new Location(w, maxX, yTop, maxZ);
        Location t4 = new Location(w, minX, yTop, maxZ);

        // 底框
        drawLine(player, b1, b2, dust);
        drawLine(player, b2, b3, dust);
        drawLine(player, b3, b4, dust);
        drawLine(player, b4, b1, dust);
        // 顶框
        drawLine(player, t1, t2, dust);
        drawLine(player, t2, t3, dust);
        drawLine(player, t3, t4, dust);
        drawLine(player, t4, t1, dust);
        // 4 竖直棱
        drawLine(player, b1, t1, dust);
        drawLine(player, b2, t2, dust);
        drawLine(player, b3, t3, dust);
        drawLine(player, b4, t4, dust);
    }
}
