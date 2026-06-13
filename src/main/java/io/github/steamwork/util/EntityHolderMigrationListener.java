package io.github.steamwork.util;

import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock;
import io.github.pylonmc.rebar.event.RebarBlockDeserializeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 旧存档兼容：v0.4.7 给产线入口新增了 {@code FluidBufferRebarBlock}（→ {@code FluidRebarBlock}
 * → {@code EntityHolderRebarBlock}），使其成为「持有实体方块」。
 *
 * <p>在 v0.4.7 之前放置的此类方块，存档 PDC 里没有 Rebar 现在强制要求的「持有实体」键，
 * 加载时 {@code EntityHolderRebarBlock.onDeserialize} 会抛 {@code Held entities not found}
 * 并刷屏（非致命，但会反复报错且该方块的流体端点缺失）。</p>
 *
 * <p>本监听在 Rebar 自身的反序列化处理器（默认 {@code NORMAL} 优先级）<b>之前</b>（{@code LOWEST}）
 * 运行：对缺键的 <b>steamwork</b> 实体持有方块，往同一个 {@code event.pdc} 补一个空的持有实体表，
 * 让 Rebar 读到空表而非 null，从而消除报错。方块以「无持有实体」状态加载；存盘后该键固化，
 * 后续加载即正常。<br>
 * 配套地，{@code ProductionLineInlet} 对「无加压蒸汽缓冲」的老入口走不耗汽的兼容模式，
 * 避免补键后整条老产线因缺汽停摆。</p>
 */
public final class EntityHolderMigrationListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDeserialize(@NotNull RebarBlockDeserializeEvent event) {
        if (!(event.getRebarBlock() instanceof EntityHolderRebarBlock)) return;
        // 仅处理本插件的方块，避免影响其它 addon 的实体持有方块
        if (!"steamwork".equals(event.getRebarBlock().getKey().getNamespace())) return;

        PersistentDataContainer pdc = event.getPdc();
        if (pdc.get(EntityHolderRebarBlock.getEntityKey(), EntityHolderRebarBlock.getEntityType()) != null) {
            return; // 已有键（新存档）：不动
        }

        // 旧存档缺键：补空表，避免 Rebar 抛 "Held entities not found"
        Map<String, UUID> empty = new HashMap<>();
        pdc.set(EntityHolderRebarBlock.getEntityKey(), EntityHolderRebarBlock.getEntityType(), empty);
    }
}
