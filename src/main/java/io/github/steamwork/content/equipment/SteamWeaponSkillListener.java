package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.item.RebarItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽剑右键技能监听器。
 *
 * <p>{@code BlockInteractRebarItemHandler.onInteractWithBlock} 的分发器开头有
 * {@code if (!event.hasBlock()) return;}，因此右键<b>空气</b>不会回调——剑作为武器
 * 通常对空/对怪右键，故无法触发蒸汽喷射。本监听器直接处理 {@code RIGHT_CLICK_AIR}
 * 与 {@code RIGHT_CLICK_BLOCK}，统一驱动蒸汽剑的右键技能。</p>
 */
public final class SteamWeaponSkillListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onRightClick(@NotNull PlayerInteractEvent event) {
        // 仅主手，避免副手重复触发
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.isEmpty()) return;

        if (!(RebarItem.fromStack(item) instanceof SteamToolItem tool)) return;

        SteamEquipmentProfile profile = SteamEquipmentProfile.fromStack(item);
        if (profile == null || profile.part() != SteamEquipmentPart.SWORD) return;

        tool.tryExecuteSteamBurst(event.getPlayer(), item, profile, event);
    }
}
