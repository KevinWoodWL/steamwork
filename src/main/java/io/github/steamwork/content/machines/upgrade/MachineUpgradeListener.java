package io.github.steamwork.content.machines.upgrade;

import io.github.pylonmc.rebar.block.BlockStorage;
import io.github.pylonmc.rebar.item.RebarItem;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MachineUpgradeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onCalibratorClick(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!RebarItem.isRebarItem(held, MachineCalibrator.class)) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!(BlockStorage.get(clicked) instanceof UpgradeableMachine machine)) return;
        if (machine.upgradeSlotCount() == 0) return;

        event.setCancelled(true);
        machine.openUpgradeGui(player);
    }
}
