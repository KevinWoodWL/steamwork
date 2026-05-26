package io.github.steamwork.content.machines.upgrade;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 任何"画刷右键可打开升级 GUI"的机器都实现该接口。
 * 让 {@link MachineUpgradeListener} 统一处理蒸汽加工机器和蒸汽涡轮。
 */
public interface UpgradeableMachine {

    /** 升级槽数量；返回 0 表示该实例不接受升级模组。 */
    int upgradeSlotCount();

    /** 打开升级 GUI。{@link #upgradeSlotCount()} == 0 时由实现方决定行为（建议直接 return）。 */
    void openUpgradeGui(@NotNull Player player);
}
