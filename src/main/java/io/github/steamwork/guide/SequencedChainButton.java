package io.github.steamwork.guide;

import io.github.pylonmc.rebar.guide.pages.item.ItemUsagesPage;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.item.research.Research;
import io.github.steamwork.SteamworkItems;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractBoundItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;

/**
 * 指南按钮：显示为序列工件图标。
 * <ul>
 *   <li>左键 → 打开 {@link SequencedChainPage}，按工序顺序展示完整的 N 步配方。</li>
 *   <li>右键 → 打开物品用途页，列出以序列工件为原料的配方。</li>
 * </ul>
 *
 * <p>{@link SequencedChainPage} 在首次点击时才构建（懒初始化），
 * 保证在配方注册完成后再去查找配方数据，避免初始化顺序问题。</p>
 */
public class SequencedChainButton extends AbstractBoundItem {

    private final List<SequencedChainPage.Step> steps;
    private final ItemStack displayStack;

    /** 懒初始化，首次点击时构建。 */
    private @Nullable SequencedChainPage chainPage;

    /**
     * 以自定义物品作为按钮图标（例如用钯合金锭表示整条工序链）。
     */
    public SequencedChainButton(@NotNull ItemStack displayItem, @NotNull List<SequencedChainPage.Step> steps) {
        this.steps = steps;
        this.displayStack = displayItem.clone();
    }

    /**
     * 默认以 {@link SteamworkItems#SEQUENCED_WORKPIECE} 作为按钮图标。
     */
    public SequencedChainButton(@NotNull List<SequencedChainPage.Step> steps) {
        this(SteamworkItems.SEQUENCED_WORKPIECE, steps);
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public @NotNull ItemProvider getItemProvider(@NotNull Player player) {
        RebarItem ritem = RebarItem.fromStack(displayStack);
        if (ritem != null) {
            Research research = ritem.getSchema().getResearch();
            if (research != null && !research.isResearchedBy(player)) {
                // 未解锁：BARRIER 外观 + unlock-instructions lore，与 ItemButton 行为对齐
                String unlockKey = research.getKey().getNamespace()
                        + ".researches." + research.getKey().getKey() + ".unlock-instructions";
                return ItemStackBuilder.copyOf(displayStack)
                        .set(DataComponentTypes.ITEM_MODEL, Material.BARRIER.getKey())
                        .set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false)
                        .lore(Component.translatable(unlockKey)
                                .decoration(TextDecoration.ITALIC, false));
            }
        }
        return ItemStackBuilder.of(displayStack);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        if (clickType == ClickType.LEFT) {
            if (chainPage == null) {
                chainPage = new SequencedChainPage(steps);
            }
            if (!chainPage.getPages().isEmpty()) {
                chainPage.open(player);
            }
        } else if (clickType == ClickType.RIGHT) {
            ItemUsagesPage usages = new ItemUsagesPage(displayStack);
            if (!usages.getPages().isEmpty()) {
                usages.open(player);
            }
        }
    }
}
