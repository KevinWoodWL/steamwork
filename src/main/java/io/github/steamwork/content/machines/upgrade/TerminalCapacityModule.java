package io.github.steamwork.content.machines.upgrade;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TerminalCapacityModule extends UpgradeModule {
    public TerminalCapacityModule(@NotNull ItemStack stack) { super(stack); }

    @Override
    public @NotNull UpgradeType getUpgradeType() { return UpgradeType.TERMINAL_CAPACITY; }
}
