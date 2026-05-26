package io.github.steamwork.content.machines.upgrade;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AutoOutputUpgradeModule extends UpgradeModule {
    public AutoOutputUpgradeModule(@NotNull ItemStack stack) { super(stack); }

    @Override
    public @NotNull UpgradeType getUpgradeType() { return UpgradeType.AUTO_OUTPUT; }
}
