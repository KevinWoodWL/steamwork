package io.github.steamwork.content.equipment;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 便携蒸汽罐：工具/护甲的移动能源。
 * 容量由 settings YAML 配置（黄铜/因瓦/钨 三级共享本类，分级靠各自的 settings 文件）。
 * <p>
 * 目前的行为完全由 {@link SteamEquipment} 基类提供（容量字段 + lore 占位）。
 * 未来如果罐子需要"被动回汽"或"接近锅炉自动充"等特殊行为，再扩展本类。
 */
public class SteamCanister extends SteamEquipment {

    public SteamCanister(@NotNull ItemStack stack) {
        super(stack);
    }
}
