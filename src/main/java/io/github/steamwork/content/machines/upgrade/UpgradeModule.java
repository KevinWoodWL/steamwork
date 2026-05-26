package io.github.steamwork.content.machines.upgrade;

import io.github.pylonmc.rebar.item.RebarItem;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class UpgradeModule extends RebarItem {

    protected UpgradeModule(@NotNull ItemStack stack) {
        super(stack);
    }

    public abstract @NotNull UpgradeType getUpgradeType();
}
