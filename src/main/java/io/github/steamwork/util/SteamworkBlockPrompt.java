package io.github.steamwork.util;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 一次性「点选方块」工具。
 *
 * <p>调用 {@link #await(Player, Consumer)} 登记某玩家的下一次右键点击方块作为选择：
 * 该交互会被取消（不触发原版方块交互，如打开箱子），并把被点方块回传给回调（主线程）。</p>
 */
public final class SteamworkBlockPrompt implements Listener {

    private static final Map<UUID, Consumer<Block>> PENDING = new ConcurrentHashMap<>();
    private static Plugin plugin;

    private SteamworkBlockPrompt() {}

    /** 在插件启用时注册一次。 */
    public static void register(@NotNull Plugin pl) {
        plugin = pl;
        Bukkit.getPluginManager().registerEvents(new SteamworkBlockPrompt(), pl);
    }

    /** 登记 {@code player} 的下一次右键方块作为选择；同一玩家重复登记会覆盖前一次。 */
    public static void await(@NotNull Player player, @NotNull Consumer<Block> callback) {
        PENDING.put(player.getUniqueId(), callback);
    }

    /** 取消某玩家尚未消费的选择登记。 */
    public static void cancel(@NotNull Player player) {
        PENDING.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;   // 只主手，避免双触发
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Consumer<Block> callback = PENDING.remove(event.getPlayer().getUniqueId());
        if (callback == null) return;

        // 吞掉这次交互：不开箱、不放置
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        callback.accept(clicked);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        PENDING.remove(event.getPlayer().getUniqueId());
    }
}
