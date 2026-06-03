package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.base.RebarBlockInteractor;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import org.bukkit.Material;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 便携蒸汽罐：装备的可插拔「电池」。
 *
 * <p>三级（黄铜 / 因瓦 / 钨）共享本类，容量由各自 settings yml 的 {@code steam-capacity} 决定。
 * 蒸汽罐本身可被充汽舱充能（{@link io.github.steamwork.util.SteamCharge} 储能），
 * 也可在「蒸汽装备改装台」装入蒸汽装备，把容量赋予装备。</p>
 *
 * <p>底层材质为音乐唱片（非堆叠），实现 {@link RebarBlockInteractor} 以阻止玩家将其
 * 放入唱片机（{@link Material#JUKEBOX}）。</p>
 */
public class SteamCanister extends SteamEquipment implements RebarBlockInteractor {

    private final double capacity = getSettings().getOrThrow("steam-capacity", ConfigAdapter.DOUBLE);

    public SteamCanister(@NotNull ItemStack stack) {
        super(stack);
    }

    public double getCapacity() {
        return capacity;
    }

    @Override
    public @NotNull List<RebarArgument> getPlaceholders() {
        return List.of(
                RebarArgument.of("capacity", UnitFormat.MILLIBUCKETS.format(capacity))
        );
    }

    /**
     * 阻止蒸汽罐被放入唱片机。
     *
     * <p>底层材质是音乐唱片，原版会允许右键唱片机插入唱片；
     * 在 {@link EventPriority#HIGH} 取消该交互，保证蒸汽罐不会被意外插入唱片机。</p>
     */
    @Override
    public void onUsedToClickBlock(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        var block = event.getClickedBlock();
        if (block != null && block.getType() == Material.JUKEBOX) {
            event.setCancelled(true);
        }
    }
}
