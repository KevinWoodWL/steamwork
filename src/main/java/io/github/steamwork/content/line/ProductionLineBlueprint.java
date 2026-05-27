package io.github.steamwork.content.line;

import io.github.pylonmc.rebar.item.RebarItem;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 产线蓝图道具。
 *
 * <p>手持此道具右键方块，按入口→机器…→出口的顺序配置产线：</p>
 * <ol>
 *   <li>右键 {@link ProductionLineInlet} —— 开始配置，记录入口位置</li>
 *   <li>依次右键各蒸汽加工机 —— 验证共线、紧密相邻后逐一加入序列</li>
 *   <li>右键 {@link ProductionLineOutlet} —— 完成验证，写入所有成员 PDC，激活产线</li>
 *   <li>潜行 + 右键任意方块 —— 取消当前配置</li>
 * </ol>
 *
 * <p>配置状态存储在 {@link ProductionLineListener} 的服务器端 Map 中（以玩家 UUID 为键），
 * 不写入物品 NBT，玩家下线时自动清除。</p>
 */
public class ProductionLineBlueprint extends RebarItem {

    public ProductionLineBlueprint(@NotNull ItemStack stack) {
        super(stack);
    }
}
