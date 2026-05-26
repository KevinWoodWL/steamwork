package io.github.steamwork.content.machines.upgrade;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class EnergySaveUpgradeModule extends UpgradeModule {
    public EnergySaveUpgradeModule(@NotNull ItemStack stack) { super(stack); }

    @Override
    public @NotNull UpgradeType getUpgradeType() { return UpgradeType.ENERGY_SAVE; }
}
