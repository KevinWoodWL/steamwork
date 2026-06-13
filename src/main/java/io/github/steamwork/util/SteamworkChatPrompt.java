package io.github.steamwork.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 一次性「聊天栏输入」工具。
 *
 * <p>调用 {@link #await(Player, Consumer)} 登记某玩家的下一条聊天消息作为输入：
 * 该消息会被取消（不向全服广播），并把纯文本回传给回调。</p>
 *
 * <p><b>线程：</b>{@link AsyncChatEvent} 在异步线程触发，因此回调也在异步线程执行，
 * 回调内若要修改方块/世界状态，必须自行用调度器切回主线程。</p>
 */
public final class SteamworkChatPrompt implements Listener {

    private static final Map<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();

    private SteamworkChatPrompt() {}

    /** 在插件启用时注册一次。 */
    public static void register(@NotNull Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(new SteamworkChatPrompt(), plugin);
    }

    /** 登记 {@code player} 的下一条聊天消息作为输入；同一玩家重复登记会覆盖前一次。 */
    public static void await(@NotNull Player player, @NotNull Consumer<String> callback) {
        PENDING.put(player.getUniqueId(), callback);
    }

    /** 取消某玩家尚未消费的输入登记。 */
    public static void cancel(@NotNull Player player) {
        PENDING.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(@NotNull AsyncChatEvent event) {
        Consumer<String> callback = PENDING.remove(event.getPlayer().getUniqueId());
        if (callback == null) return;
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        callback.accept(text);
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        PENDING.remove(event.getPlayer().getUniqueId());
    }
}
