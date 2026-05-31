package io.github.steamwork.util;

import io.github.pylonmc.rebar.i18n.RebarArgument;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.steamwork.SteamworkKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 多步骤工序链的中间品（"序列工件"）。
 *
 * <p><b>为什么每一步都是独立 Rebar 物品：</b>Rebar 的配方原料匹配
 * （{@code RecipeInput.Item#matchesIgnoringAmount}）只比较物品的 Rebar key（schema），
 * 完全忽略 PDC。如果所有工序步骤共用同一个 key，配方系统就无法区分第 1/2/3 步——
 * 第 2 步配方（要工件 1）会错误地匹配第 3 步工件，导致第 4 步又回到第 2 步的死循环。
 * 同时配方书展示的是注册模板（schema 的 template），共用 key 时各步骤显示成同一个无耐久条的通用物品。</p>
 *
 * <p>因此这里给钯合金链的每一步注册一个独立 key，模板里烤入耐久条、步骤、下一步提示，
 * 让配方书能逐步正确显示，机器也能精确匹配。</p>
 */
public final class SequencedWorkpiece {

    private SequencedWorkpiece() {
        throw new AssertionError("Utility class");
    }

    public static final String PALLADIUM_ALLOY = "palladium_alloy";

    private static final int MAX_DAMAGE = 1000;
    private static final int TOTAL_STEPS = 4;

    /** 工序链通用 tooltip 文案的翻译前缀。 */
    private static final String BASE_KEY = "steamwork.item.sequenced_workpiece";

    private static final NamespacedKey[] PALLADIUM_KEYS = {
            SteamworkKeys.PALLADIUM_WORKPIECE_1,
            SteamworkKeys.PALLADIUM_WORKPIECE_2,
            SteamworkKeys.PALLADIUM_WORKPIECE_3,
    };

    /** 三步中间品的模板（第 4 步产物是钯合金锭，不在此列）。 */
    public static final ItemStack PALLADIUM_STEP_1 = build(1);
    public static final ItemStack PALLADIUM_STEP_2 = build(2);
    public static final ItemStack PALLADIUM_STEP_3 = build(3);

    /** 返回钯合金工序链第 {@code step}（1..3）步的中间品克隆。 */
    public static @NotNull ItemStack palladiumAlloy(int step) {
        return switch (step) {
            case 1 -> PALLADIUM_STEP_1.clone();
            case 2 -> PALLADIUM_STEP_2.clone();
            case 3 -> PALLADIUM_STEP_3.clone();
            default -> throw new IllegalArgumentException(
                    "palladium workpiece step must be 1..3, got " + step);
        };
    }

    /** 注册三步中间品为独立 Rebar 物品。需在配方注册前由 SteamworkItems 调用。 */
    public static void register() {
        RebarItem.register(RebarItem.class, PALLADIUM_STEP_1, SteamworkKeys.PALLADIUM_WORKPIECE_1);
        RebarItem.register(RebarItem.class, PALLADIUM_STEP_2, SteamworkKeys.PALLADIUM_WORKPIECE_2);
        RebarItem.register(RebarItem.class, PALLADIUM_STEP_3, SteamworkKeys.PALLADIUM_WORKPIECE_3);
    }

    private static @NotNull ItemStack build(int step) {
        NamespacedKey key = PALLADIUM_KEYS[step - 1];

        // 步骤越靠后，损耗越小、耐久条越满，直观表现"工序推进"。
        int pct = (int) Math.round(100.0 * step / TOTAL_STEPS);
        int damage = Math.max(1, MAX_DAMAGE - (int) Math.round((MAX_DAMAGE - 1) * pct / 100.0));

        Component header = noItalic(Component.translatable(BASE_KEY + ".tooltip.header"));
        Component line = noItalic(Component.translatable(BASE_KEY + ".tooltip.line",
                RebarArgument.of("line", Component.translatable(
                        BASE_KEY + "." + PALLADIUM_ALLOY + ".line_name"))));
        Component stepLine = noItalic(Component.translatable(BASE_KEY + ".tooltip.step",
                RebarArgument.of("step", step),
                RebarArgument.of("steps", TOTAL_STEPS)));
        Component nextLine = noItalic(Component.translatable(
                BASE_KEY + "." + PALLADIUM_ALLOY + ".step_" + step + ".next"));

        // rebar(key) 会自动把名字指向 steamwork.item.<key>.name；随后的 .lore() 覆盖默认 lore。
        return ItemStackBuilder.rebar(Material.RAW_IRON, key)
                .set(DataComponentTypes.MAX_DAMAGE, MAX_DAMAGE)
                .set(DataComponentTypes.DAMAGE, damage)
                .set(DataComponentTypes.MAX_STACK_SIZE, 1)
                .set(
                        DataComponentTypes.TOOLTIP_DISPLAY,
                        TooltipDisplay.tooltipDisplay()
                                .addHiddenComponents(DataComponentTypes.DAMAGE, DataComponentTypes.MAX_DAMAGE)
                )
                .lore(List.of(header, line, stepLine, nextLine))
                .build();
    }

    private static @NotNull Component noItalic(@NotNull Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
