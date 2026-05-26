package io.github.steamwork.content.machines.upgrade;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PylonCompatUpgradeModule extends UpgradeModule {
    public PylonCompatUpgradeModule(@NotNull ItemStack stack) { super(stack); }

    @Override
    public @NotNull UpgradeType getUpgradeType() { return UpgradeType.PYLON_COMPAT; }
}
