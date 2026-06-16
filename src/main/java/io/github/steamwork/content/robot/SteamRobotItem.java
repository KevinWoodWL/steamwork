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
 * 蒸汽机器人部署物品：右键方块 → 生成对应类型的 {@link SteamRobot}（未绑终端，待命状态）。
 * 四种子类各对应一种 {@link SteamRobot.RobotType}。
 */
public class SteamRobotItem extends RebarItem implements BlockInteractRebarItemHandler {

    private final SteamRobot.RobotType robotType;

    protected SteamRobotItem(@NotNull ItemStack stack, @NotNull SteamRobot.RobotType type) {
        super(stack);
        this.robotType = type;
    }

    @Override
    @MultiHandler(priorities = {EventPriority.HIGH})
    public void onInteractWithBlock(@NotNull PlayerInteractEvent event, @NotNull EventPriority priority) {
        if (priority != EventPriority.HIGH) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);

        Location spawnLoc = clicked.getRelative(event.getBlockFace())
                .getLocation().add(0.5, 0.0, 0.5);
        SteamRobot.spawn(spawnLoc, robotType, null);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            ItemStack inHand = event.getItem();
            if (inHand != null) inHand.subtract();
        }
    }

    public static class Mining extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Mining(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.MINE); }
    }

    public static class Lumber extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Lumber(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.CHOP); }
    }

    public static class Haul extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Haul(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.HAUL); }
    }

    public static class Patrol extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Patrol(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.PATROL); }
    }

    public static class Picker extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Picker(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.PICK); }
    }

    public static class Farmer extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Farmer(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.FARM); }
    }

    public static class Butcher extends SteamRobotItem {
        @SuppressWarnings("unused")
        public Butcher(@NotNull ItemStack stack) { super(stack, SteamRobot.RobotType.BUTCHER); }
    }
}
