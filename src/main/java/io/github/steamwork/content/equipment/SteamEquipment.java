package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.item.base.RebarItemDamageable;
import io.github.pylonmc.rebar.item.RebarItem;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 蒸汽装备的共享基类（武器/工具/盔甲）。
 *
 * <p>装备本身<b>不</b>预置蒸汽容量——容量由通过「蒸汽装备改装台」安装的蒸汽罐决定
 * （见 {@link io.github.steamwork.util.SteamCharge} 的插槽 API）。三档状态：</p>
 * <ul>
 *   <li><b>未装罐</b>（socket=none）：半成品，属性显著降低，无蒸汽能力。</li>
 *   <li><b>装罐·有汽</b>：满血，基础属性 + 蒸汽增益。</li>
 *   <li><b>装罐·耗尽</b>：明显降级，失去全部蒸汽增益。</li>
 * </ul>
 *
 * <p>具体的攻击/挖掘/穿戴增益由子类 {@link SteamToolItem} / {@link SteamArmorItem} 实现。
 * 便携蒸汽罐本身是 {@link SteamCanister}（带固定容量，可被充汽舱充能、可装入装备）。</p>
 */
public class SteamEquipment extends RebarItem implements RebarItemDamageable {

    public SteamEquipment(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    public void onItemDamaged(@NotNull PlayerItemDamageEvent event, @NotNull EventPriority priority) {
        event.setCancelled(true);
        event.setDamage(0);
    }
}
