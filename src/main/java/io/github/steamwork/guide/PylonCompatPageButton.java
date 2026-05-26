package io.github.steamwork.guide;

import io.github.pylonmc.rebar.guide.pages.item.ItemUsagesPage;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.SteamworkItems;
import io.github.steamwork.recipes.SteamProcessRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.item.AbstractBoundItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;
import java.util.function.Supplier;

/**
 * 指南按钮：以 Pylon 联动升级模组为图标，点击后展示该机器安装联动模组后可执行的所有 Pylon 配方。
 *
 * <ul>
 *   <li>左键 → 打开 {@link PylonCompatRecipesPage}，浏览各 Pylon 配方卡片。</li>
 *   <li>右键 → 打开联动升级模组的物品用途页（显示如何合成该模组）。</li>
 * </ul>
 *
 * <p>{@link PylonCompatRecipesPage} 在首次点击时才构建（懒初始化），
 * 保证所有 Pylon 配方注册完成后再去读取配方数据，避免初始化顺序问题。</p>
 */
public class PylonCompatPageButton extends AbstractBoundItem {

    private final Supplier<List<SteamProcessRecipe>> pylonRecipesSupplier;

    /** 懒初始化，首次左键时构建。 */
    @Nullable private PylonCompatRecipesPage page;

    /**
     * @param pylonRecipesSupplier 供应器，返回该机器支持的 Pylon 联动配方列表；
     *                             通常传入机器类的静态方法引用（例如 {@code SteamGrinder::pylonRecipesForItem}）
     */
    public PylonCompatPageButton(@NotNull Supplier<List<SteamProcessRecipe>> pylonRecipesSupplier) {
        this.pylonRecipesSupplier = pylonRecipesSupplier;
    }

    @Override
    public @NotNull ItemProvider getItemProvider(@NotNull Player player) {
        return ItemStackBuilder.of(SteamworkItems.UPGRADE_MODULE_PYLON_COMPAT);
    }

    @Override
    public void handleClick(@NotNull ClickType clickType, @NotNull Player player, @NotNull Click click) {
        if (clickType == ClickType.LEFT) {
            if (page == null) {
                page = new PylonCompatRecipesPage(pylonRecipesSupplier.get());
            }
            if (!page.getPages().isEmpty()) {
                page.open(player);
            }
        } else if (clickType == ClickType.RIGHT) {
            ItemUsagesPage usages = new ItemUsagesPage(SteamworkItems.UPGRADE_MODULE_PYLON_COMPAT);
            if (!usages.getPages().isEmpty()) {
                usages.open(player);
            }
        }
    }
}
