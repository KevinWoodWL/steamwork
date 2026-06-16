package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.entity.EntityStorage;
import io.github.steamwork.Steamwork;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 蒸汽机器人交互的<b>全局直接 Bukkit 监听</b>：拦掉原版交互（防止铜傀儡被右键取走手持工具），
 * 并接管 GUI / 拆除。
 *
 * <p>取消 Bukkit 事件后，客户端的预测状态（"物品已被拿走"）不会被服务端修正——因为服务端认为
 * 什么都没变。因此取消后必须<b>延迟 1 tick 强制重设装备</b>，触发服务端向客户端发送修正包。</p>
 */
public final class SteamRobotInteractGuard implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractAt(@NotNull PlayerInteractAtEntityEvent event) {
        if (!(EntityStorage.get(event.getRightClicked()) instanceof SteamRobot robot)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        // 延迟 1 tick：强制刷新装备 + 向交互玩家直接发送装备修正包
        Bukkit.getScheduler().runTaskLater(Steamwork.getInstance(), () -> {
            robot.refreshEquipment();
            sendEquipmentCorrection(player, robot);
        }, 1L);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (player.isSneaking()) {
            robot.dismantle(player);
        } else {
            robot.openGui(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(@NotNull PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) return;
        if (!(EntityStorage.get(event.getRightClicked()) instanceof SteamRobot robot)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(Steamwork.getInstance(), () -> {
            robot.refreshEquipment();
            sendEquipmentCorrection(player, robot);
        }, 1L);
    }

    @EventHandler
    public void onRobotDeath(@NotNull EntityDeathEvent event) {
        if (!(EntityStorage.get(event.getEntity()) instanceof SteamRobot)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    private void sendEquipmentCorrection(@NotNull Player player, @NotNull SteamRobot robot) {
        ItemStack held = robot.getEntity().getEquipment() != null
                ? robot.getEntity().getEquipment().getItemInMainHand()
                : ItemStack.empty();
        player.sendEquipmentChange(robot.getEntity(),
                Map.of(EquipmentSlot.HAND, held != null ? held : ItemStack.empty()));
    }
}
