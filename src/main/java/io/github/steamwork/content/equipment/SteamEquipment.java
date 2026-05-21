package io.github.steamwork.content.equipment;

import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 蒸汽装备/便携能源的共享基类：暴露 steam-capacity 设置 + %capacity% lore 占位。
 * 主动能力（耗汽换效果）由各装备的具体子类在 4e 阶段实现。
 */
public class SteamEquipment extends RebarItem {

    private final double capacity = getSettings().getOrThrow("steam-capacity", ConfigAdapter.DOUBLE);

    public SteamEquipment(@NotNull ItemStack stack) {
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
}
