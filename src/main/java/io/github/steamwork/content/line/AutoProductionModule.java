package io.github.steamwork.content.line;

import io.github.steamwork.content.machines.upgrade.UpgradeModule;
import io.github.steamwork.content.machines.upgrade.UpgradeType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 产线入口专属调校升级：安装后产线内需要手动触发的机器（{@link ManualInteractMember}）
 * 将每 tick 被自动触发一次，无需玩家手动右键。
 *
 * <p>通过精密调校仪右键产线入口，在机器升级页面安装到升级槽中生效。</p>
 */
public class AutoProductionModule extends UpgradeModule {

    public AutoProductionModule(@NotNull ItemStack stack) {
        super(stack);
    }

    @Override
    public @NotNull UpgradeType getUpgradeType() {
        return UpgradeType.AUTO_PRODUCTION;
    }
}
