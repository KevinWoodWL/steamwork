package io.github.steamwork.content.robot;

import io.github.pylonmc.rebar.event.api.annotation.MultiHandler;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.interfaces.BlockInteractRebarItemHandler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽机器人部署物品（P0）。
 *
 * <p>右键点击方块 → 在该方块面向的相邻位置生成一台 {@link SteamRobot}，非创造模式消耗一个。</p>
 */
public class SteamRobotItem extends RebarItem implements BlockInteractRebarItemHandler {

    @SuppressWarnings("unused")
    public SteamRobotItem(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    @MultiHandler(priorities = {EventPriority.HIGH})
    public void onInteractWithBlock(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;   // 只主手触发一次
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);

        Location spawnLoc = clicked.getRelative(event.getBlockFace())
                .getLocation().add(0.5, 0.0, 0.5);
        SteamRobot.spawn(spawnLoc);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack inHand = event.getItem();
            if (inHand != null) inHand.subtract();
        }
    }
}
